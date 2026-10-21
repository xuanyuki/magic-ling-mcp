"""Shared local reader. Never uploads observations or executes game input."""
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request

from ai_client import project_cell, render_text


class ObservationError(Exception):
    def __init__(self, code, message, health=None):
        super().__init__(message)
        self.code, self.health = code, health

    def data(self):
        return {"error": self.code, "message": str(self), "health": self.health}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *unused):
        raise ObservationError("invalid_connection", "本机观察接口不允许重定向")


class BridgeClient:
    def __init__(self, output=None):
        self.output = pathlib.Path(output or pathlib.Path(__file__).parent / "output")
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def _connection(self):
        try:
            connection = json.loads((self.output / "connection.json").read_text(encoding="utf-8"))
            url = connection["url"]
            parsed = urllib.parse.urlsplit(url)
            if (parsed.scheme != "http" or parsed.hostname != "127.0.0.1" or
                    not parsed.port or parsed.username or parsed.password or
                    parsed.path or parsed.query or parsed.fragment):
                raise ValueError("Invalid localhost address")
            if not isinstance(connection["session"], str):
                raise ValueError("Missing session")
            return connection
        except (OSError, ValueError, KeyError, TypeError) as exc:
            raise ObservationError("connection_unavailable", "无法读取本机连接信息：" + str(exc)) from exc

    def _request(self, connection, path, method="GET"):
        request = urllib.request.Request(connection["url"] + path, method=method,
                                         data=b"" if method == "POST" else None)
        try:
            with self.http.open(request, timeout=8 if method == "POST" else 3) as response:
                data = json.loads(response.read().decode("utf-8"))
                if not isinstance(data, dict):
                    raise ObservationError("invalid_response", "观察接口返回了非对象数据")
                return data
        except urllib.error.HTTPError as exc:
            try:
                error = json.loads(exc.read().decode("utf-8"))
            except (ValueError, UnicodeError):
                error = {"error": "http_error", "message": str(exc)}
            if not isinstance(error, dict):
                error = {}
            raise ObservationError(error.get("error", "http_error"), error.get("message", str(exc))) from exc
        except (OSError, ValueError, UnicodeError, urllib.error.URLError) as exc:
            raise ObservationError("bridge_unavailable", "无法读取游戏观察器：" + str(exc)) from exc

    def _health(self, connection, require_ready=True):
        health = self._request(connection, "/health")
        if (type(health.get("seq")) is not int or not isinstance(health.get("session"), str) or
                type(health.get("stale")) is not bool):
            raise ObservationError("invalid_response", "观察器健康信息格式错误")
        if health.get("session") != connection["session"]:
            raise ObservationError("session_mismatch", "连接文件与游戏会话不一致", health)
        compatibility = health.get("compatibility") or {}
        if not isinstance(compatibility, dict):
            raise ObservationError("invalid_response", "观察器版本信息格式错误")
        if compatibility.get("compatible") is not True:
            raise ObservationError("unsupported_game_version", "游戏版本或观察器协议不兼容，请使用新版观察器", health)
        if require_ready and (health.get("connected") is not True or not health.get("installed") or health.get("stale", True) or
                              not health.get("seq") or health.get("lastError")):
            raise ObservationError("observation_unavailable", "观察尚未就绪、已陈旧或发生错误", health)
        return health

    def health(self):
        try:
            connection = self._connection()
            return self._health(connection, require_ready=False)
        except ObservationError as exc:
            if exc.health is not None:
                return {**exc.health, "error": exc.code, "message": str(exc)}
            return {"connected": False, **exc.data()}

    def _validate(self, connection, data):
        health = self._health(connection)
        if data.get("session") != health["session"]:
            raise ObservationError("session_mismatch", "采集期间游戏会话发生变化", health)
        if type(data.get("seq")) is not int or not 0 < data["seq"] <= health["seq"]:
            raise ObservationError("invalid_response", "观察器返回了无效序号", health)
        return health

    def observe(self, include_screenshot=False):
        connection = self._connection()
        self._health(connection)
        if include_screenshot:
            result = self._request(connection, "/capture?session=" +
                                   urllib.parse.quote(connection["session"], safe=""), "POST")
            if not isinstance(result.get("snapshot"), dict) or not isinstance(result.get("screenshot"), dict):
                raise ObservationError("invalid_response", "截图缺少对应快照或元数据")
            data = {"type": "snapshot", **result["snapshot"], "screenshot": result["screenshot"]}
        else:
            data = {"type": "snapshot", **self._request(connection, "/snapshot")}
        data["health"] = self._validate(connection, data)
        if not isinstance(data.get("state"), dict):
            raise ObservationError("invalid_response", "观察器快照缺少状态对象", data["health"])
        if include_screenshot and (data["screenshot"].get("session") != data["session"] or
                                   data["screenshot"].get("seq") != data["seq"] or
                                   not pathlib.Path(data["screenshot"].get("path", "")).is_absolute()):
            raise ObservationError("invalid_response", "截图未绑定到该观测版本或路径无效", data["health"])
        data["cursor"] = {"session": data["session"], "seq": data["seq"]}
        return data

    def changes(self, cursor=None):
        if cursor is not None and (not isinstance(cursor, dict) or
                not isinstance(cursor.get("session"), str) or type(cursor.get("seq")) is not int):
            raise ObservationError("invalid_cursor", "游标必须包含 session 字符串和 seq 整数")
        connection = self._connection()
        health = self._health(connection)
        if cursor is None or cursor["session"] != health["session"]:
            return self.observe()
        query = urllib.parse.urlencode({"since": cursor["seq"], "session": cursor["session"]})
        data = self._request(connection, "/changes?" + query)
        if type(data.get("resync")) is not bool or not isinstance(data.get("events"), list):
            raise ObservationError("invalid_response", "观察器增量格式错误")
        if data["resync"]:
            return self.observe()
        expected = cursor["seq"]
        for event in data["events"]:
            if (not isinstance(event, dict) or event.get("session") != cursor["session"] or
                    event.get("base") != expected or type(event.get("seq")) is not int or
                    event["seq"] != expected + 1 or not isinstance(event.get("set"), dict) or
                    not isinstance(event.get("newLog"), list)):
                raise ObservationError("invalid_response", "观察器增量不连续，请重新全量同步")
            expected = event["seq"]
        if data.get("seq") != expected:
            raise ObservationError("invalid_response", "增量末尾序号与游标不一致")
        data["type"] = "changes"
        data["health"] = self._validate(connection, data)
        data["cursor"] = {"session": data["session"], "seq": data["seq"]}
        return data

    def project(self, x, y, session, seq):
        data = self.observe()
        if data["session"] != session or data["seq"] != seq:
            raise ObservationError("stale_coordinates", "会话或观测序号已变化，请重新观察", data["health"])
        try:
            result = project_cell(data["state"], x, y)
        except (KeyError, ValueError) as exc:
            raise ObservationError("invalid_target", str(exc), data["health"]) from exc
        # A second read prevents returning coordinates after a published scene/camera change.
        health = self._health(self._connection())
        if health["session"] != session or health["seq"] != seq:
            raise ObservationError("stale_coordinates", "坐标换算期间画面已变化，请重新观察", health)
        return {"session": session, "seq": seq, **result, "health": health}


def format_text(data):
    text = render_text(data)
    if data.get("cursor"):
        text += "\n游标=" + json.dumps(data["cursor"], ensure_ascii=False, separators=(",", ":"))
    if data.get("health"):
        text += "\n健康=" + json.dumps(data["health"], ensure_ascii=False, separators=(",", ":"))
    if data.get("screenshot"):
        text += "\n截图=" + json.dumps(data["screenshot"], ensure_ascii=False, separators=(",", ":"))
    return text
