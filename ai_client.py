"""Read-only local AI tool. No model calls, API keys, uploads or third-party dependencies."""
import argparse
import json
import pathlib
import sys
import urllib.request
import urllib.error


def text_state(state):
    lines = []
    names = {"scene": "场景", "hero": "角色", "inventory": "物品",
             "visibleActors": "可见角色", "features": "地物",
             "visibleEffects": "区域效果", "screenLog": "屏幕日志", "containers": "容器",
             "unresolved": "待解析内容", "coverage": "覆盖状态"}
    compact = lambda value: json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    for key, value in state.items():
        if value is None:
            lines.append(f"{names.get(key, key)}：已清空")
        elif key == "hero":
            h = value
            lines.append(
                f'{h["name"]} 层{h["depth"]} Lv{h["level"]} '
                f'HP{h["hp"]}/{h["maxHp"]} 盾{h.get("shield", 0)} '
                f'力{h["strength"]} 金{h["gold"]} XP{h["xp"]}/{h["nextXp"]} '
                f'饱食{h.get("satiety", "?")}/{h.get("satietyMax", "?")} '
                f'坐标({h["x"]},{h["y"]}) 可行动={h["ready"]} '
                f'状态={compact(h["buffs"])}'
            )
            for detail in ("profession", "buffDetails"):
                if detail in h:
                    lines.append(("职业与天赋" if detail == "profession" else "状态详情") + "：" + compact(h[detail]))
        elif key in ("inventory", "visibleActors", "features"):
            entries = []
            for item in value:
                s = item["name"]
                if "equipped" in item:
                    s += f'[装备:{item["equipped"]}]'
                if item.get("identified") is False:
                    s += "[未鉴定]"
                if "status" in item:
                    s += f'[{item["status"]}]'
                if "quantity" in item:
                    s += f' ×{item["quantity"]}'
                if item.get("description"):
                    s += " " + item["description"]
                if item.get("detailStatus") == "open_page_required":
                    s += "[详情需正常打开检视页]"
                if "dx" in item:
                    s += f' 相对({item["dx"]},{item["dy"]})'
                if "screen" in item:
                    s += f' 点击{compact(item["screen"])}'
                if item.get("offscreen"):
                    s += "[屏幕外]"
                if "alignment" in item:
                    s += f'[{item["alignment"]},血{item["health"]}]'
                if "active" in item:
                    s += f'[生效={item["active"]}]'
                entries.append(s)
            lines.append(names[key] + "：" + ("；".join(entries) if entries else "无"))
        elif key == "ui":
            lines.append("弹窗：" + (" / ".join(value["text"]) or "无"))
            controls = [
                f'{b["label"]}@{compact(b["screen"])}'
                + ("[禁用]" if not b["enabled"] else "")
                + ("[窗口]" if b.get("inWindow") else "")
                + ("[选中]" if b.get("selected") else "")
                + (f'[勾选={b["checked"]}]' if "checked" in b else "")
                + ("[部分裁剪]" if b.get("clipped") else "")
                for b in value["buttons"]
                if b["label"]
            ]
            lines.append("界面按钮：" + ("；".join(controls) or "无"))
        elif key == "map":
            lines.append(f'地图原点{compact(value["origin"])}，仅当前视野：')
            lines.extend(value["rows"])
        elif key == "knownMap":
            lines.append("本层已知地图（?未知；可见性 v当前可见/r历史已知；历史地形不代表当前安全）：")
            lines.extend(value["rows"])
            lines.append("可见性：")
            lines.extend(value["visibility"])
            lines.append("已知地形=" + compact(value.get("cells", [])))
        else:
            lines.append(names.get(key, key) + "：" + compact(value))
    return "\n".join(lines)


def render_text(data):
    if data.get("type") == "snapshot":
        return f'完整状态 seq={data["seq"]}\n' + text_state(data["state"])
    events = data.get("events", [])
    if not events:
        return f'无变化 seq={data["seq"]}'
    # Merge observation frames into the current value of each changed field.
    # Preserve every actual log, and explicitly state that intermediate states
    # are omitted; the lossless JSON protocol and disk journal remain available.
    changed, logs = {}, []
    for event in events:
        changed.update(event["set"])
        logs.extend(event["newLog"])
    lines = [f'变化至 seq={data["seq"]}（{len(events)}次更新，仅列各字段最新值）',
             text_state(changed)]
    lines.extend("新日志：" + line for line in logs)
    return "\n".join(line for line in lines if line)


def project_cell(state, x, y):
    if type(x) is not int or type(y) is not int:
        raise ValueError("地图坐标必须是整数")
    grid = state.get("knownMap", state.get("map"))
    if not grid:
        raise ValueError("当前场景没有可换算的地图")
    if grid.get("interactionBlocked") or state.get("ui", {}).get("modal"):
        raise ValueError("当前窗口或场景遮挡地图，请正常关闭后重新观察")
    column, row = x - grid["origin"][0], y - grid["origin"][1]
    rows = grid["rows"]
    if row < 0 or row >= len(rows) or column < 0 or column >= len(rows[row]):
        raise ValueError("目标不在当前文字地图范围内；先正常探索或查看可见地物坐标")
    symbol = rows[row][column]
    if symbol == "?" or ("visibility" in grid and grid["visibility"][row][column] != "v"):
        raise ValueError("目标当前不可见，拒绝推算其操作坐标")
    projection = grid.get("screenProjection")
    if not projection:
        raise ValueError("当前观察器尚无地图投影")
    x0, y0 = projection["worldZero"]
    step = projection["cellPixels"]
    screen = [int(x0 + (x + 0.5) * step), int(y0 + (y + 0.5) * step)]
    viewport = grid.get("viewport")
    if viewport and not (0 <= screen[0] < viewport[0] and 0 <= screen[1] < viewport[1]):
        raise ValueError("目标已知但在屏幕之外，需先通过正常界面调整视野再读取")
    for control in state.get("ui", {}).get("buttons", []):
        bounds = control.get("bounds")
        if bounds and bounds[0] <= screen[0] < bounds[2] and bounds[1] <= screen[1] < bounds[3]:
            raise ValueError("目标被可见界面控件遮挡")
    return {"map": [x, y], "symbol": symbol, "screen": screen,
            "note": "仅坐标换算；不保证可通行、不执行点击；相机或弹窗变化后须重新观察"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("compact", "snapshot", "delta", "health", "cell"), default="delta")
    parser.add_argument("--format", choices=("json", "text"), default="json",
                        help="text renders a short latest-state view; json keeps every event")
    parser.add_argument("--x", type=int, help="Absolute map x, for --mode cell")
    parser.add_argument("--y", type=int, help="Absolute map y, for --mode cell")
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path(__file__).parent / "output")
    parser.add_argument("--cursor", type=pathlib.Path, help="Use one cursor file per AI conversation")
    args = parser.parse_args()
    connection = json.loads((args.output / "connection.json").read_text(encoding="utf-8"))
    base = connection["url"]
    # Disallow redirects/proxies to keep this a strictly local reader.
    if not base.startswith("http://127.0.0.1:") or not base.rsplit(":", 1)[1].isdigit():
        raise ValueError("Invalid local bridge address")
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *unused):
            raise ValueError("Redirect refused")
    client = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def get(path):
        with client.open(base + path, timeout=3) as response:
            return response.read().decode("utf-8")

    health = json.loads(get("/health"))
    if args.mode == "health":
        print(json.dumps(health, ensure_ascii=False))
        return
    if health.get("compatibility", {}).get("compatible") is not True:
        print(json.dumps({"error": "unsupported_game_version", "health": health}, ensure_ascii=False))
        sys.exit(2)
    if health["stale"] or health["seq"] == 0 or not health.get("installed") or not health.get("connected") or health["lastError"]:
        print(json.dumps({"error": "observation_unavailable", "health": health}, ensure_ascii=False))
        sys.exit(2)
    if health["lastError"] or health["droppedLog"]:
        # Keep partial failures explicit for the AI.
        print(json.dumps({"observerWarning": health}, ensure_ascii=False), file=sys.stderr)
    if args.mode == "cell":
        if args.x is None or args.y is None:
            raise ValueError("--mode cell requires --x and --y")
        full = json.loads(get("/snapshot"))
        print(json.dumps({"seq": full["seq"], **project_cell(full["state"], args.x, args.y)},
                         ensure_ascii=False))
        return
    if args.mode in ("compact", "snapshot"):
        print(get("/" + args.mode), end="\n")
        return
    cursor = None
    if args.cursor and args.cursor.exists():
        cursor = json.loads(args.cursor.read_text(encoding="utf-8"))
    if not cursor or cursor["session"] != health["session"]:
        full = json.loads(get("/snapshot"))
        data = {"type": "snapshot", **full}
        cursor = {"session": full["session"], "seq": full["seq"]}
    else:
        data = json.loads(get(f'/changes?since={int(cursor["seq"])}&session={cursor["session"]}'))
        if data["resync"]:
            full = json.loads(get("/snapshot"))
            data = {"type": "snapshot", **full}
            cursor = {"session": full["session"], "seq": full["seq"]}
        else:
            cursor = {"session": data["session"], "seq": data["seq"]}
    print(render_text(data) if args.format == "text"
          else json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    if args.cursor:
        args.cursor.parent.mkdir(parents=True, exist_ok=True)
        temp = args.cursor.with_name(args.cursor.name + ".tmp")
        temp.write_text(json.dumps(cursor), encoding="utf-8")
        temp.replace(args.cursor)


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    try:
        main()
    except (OSError, ValueError, KeyError, urllib.error.URLError) as exc:
        print(json.dumps({"error": str(exc)}, ensure_ascii=False))
        sys.exit(1)
