import importlib.util
import pathlib
import unittest

source = pathlib.Path(__file__).resolve().parents[1] / "ai_client.py"
spec = importlib.util.spec_from_file_location("bridge_client", source)
client = importlib.util.module_from_spec(spec)
spec.loader.exec_module(client)


class TextClientTest(unittest.TestCase):
    def test_text_uses_latest_field_but_preserves_all_real_logs(self):
        data = {"seq": 3, "events": [
            {"set": {"scene": "GameScene", "ui": {"text": ["旧弹窗"], "buttons": []}},
             "newLog": ["你着火了！"]},
            {"set": {"ui": {"text": [], "buttons": []}}, "newLog": ["你着火了！"]},
        ]}
        text = client.render_text(data)
        self.assertNotIn("旧弹窗", text)
        self.assertEqual(text.count("新日志：你着火了！"), 2)
        self.assertIn("仅列各字段最新值", text)

    def test_deleted_field_and_unchanged_response_are_explicit(self):
        self.assertIn("已清空", client.text_state({"hero": None}))
        self.assertEqual(client.render_text({"seq": 12, "events": []}), "无变化 seq=12")

    def test_cell_projection_keeps_fractional_camera_offset(self):
        state = {"map": {"origin": [2, 3], "rows": [".?"], "screenProjection":
                        {"worldZero": [-25.75, -31.25], "cellPixels": 16}}}
        target = client.project_cell(state, 2, 3)
        self.assertEqual(target["screen"], [14, 24])
        self.assertEqual(target["symbol"], ".")

    def test_hidden_cell_and_out_of_range_cannot_be_projected(self):
        state = {"map": {"origin": [2, 3], "rows": [".?"], "screenProjection":
                        {"worldZero": [0, 0], "cellPixels": 16}}}
        for x, y in ((3, 3), (2, 4), (1, 3)):
            with self.assertRaises(ValueError):
                client.project_cell(state, x, y)

    def test_known_but_offscreen_cell_is_not_a_click_target(self):
        state = {"map": {"origin": [2, 3], "rows": ["."], "screenProjection":
                        {"worldZero": [1000, 1000], "cellPixels": 16},
                        "viewport": [1120, 760]}}
        with self.assertRaises(ValueError):
            client.project_cell(state, 2, 3)

    def test_full_map_uses_visibility_and_rejects_modals_and_controls(self):
        grid = {"origin": [0, 0], "rows": [".."], "visibility": ["vr"],
                "screenProjection": {"worldZero": [0, 0], "cellPixels": 16}}
        state = {"knownMap": grid}
        self.assertEqual(client.project_cell(state, 0, 0)["screen"], [8, 8])
        with self.assertRaises(ValueError):
            client.project_cell(state, 1, 0)
        for ui in ({"modal": True}, {"buttons": [{"bounds": [0, 0, 16, 16]}]}):
            state["ui"] = ui
            with self.assertRaises(ValueError):
                client.project_cell(state, 0, 0)


if __name__ == "__main__":
    unittest.main()
