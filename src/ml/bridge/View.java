package ml.bridge;

import java.util.*;

final class View {
    @SuppressWarnings("unchecked")
    static String text(Map<String,Object> state) {
        StringBuilder b = new StringBuilder();
        if (state.containsKey("scene")) b.append("场景=").append(state.get("scene")).append('\n');
        Map<String,Object> h = (Map<String,Object>)state.get("hero");
        if (h != null) {
            b.append(h.get("name")).append(" 层").append(h.get("depth"))
                .append(" Lv").append(h.get("level")).append(" HP")
                .append(h.get("hp")).append('/').append(h.get("maxHp"))
                .append(" 盾").append(h.get("shield"))
                .append(" 力").append(h.get("strength")).append(" 金").append(h.get("gold"))
                .append(" XP").append(h.get("xp")).append('/').append(h.get("nextXp"))
                .append(" 饱食").append(h.getOrDefault("satiety","?"))
                .append('/').append(h.getOrDefault("satietyMax","?"))
                .append(" 坐标").append(h.get("x")).append(',').append(h.get("y"))
                .append(" 可行动=").append(h.get("ready")).append('\n');
            b.append("状态=").append(h.get("buffs")).append('\n');
            b.append("职业与天赋=").append(Json.encode(h.get("profession"))).append('\n');
            b.append("状态详情=").append(Json.encode(h.get("buffDetails"))).append('\n');
        }
        List<Map<String,Object>> items = (List<Map<String,Object>>)state.get("inventory");
        if (items != null) {
            StringJoiner j = new StringJoiner("；");
            for (Map<String,Object> item : items) j.add(item.get("name")
                + (item.containsKey("equipped") ? "[装备:" + item.get("equipped") + "]" : "")
                + (Boolean.FALSE.equals(item.get("identified")) ? "[未鉴定]" : "")
                + (item.containsKey("status") ? "[" + item.get("status") + "]" : ""));
            b.append("物品=").append(j).append('\n');
        }
        for (String key : Arrays.asList("visibleActors","features")) {
            List<Map<String,Object>> list = (List<Map<String,Object>>)state.get(key);
            if (list == null) continue;
            StringJoiner j = new StringJoiner("；");
            for (Map<String,Object> o : list) j.add(o.get("name") + "@(" + o.get("dx")
                + "," + o.get("dy") + ")"
                + (o.containsKey("screen") ? " 屏幕" + o.get("screen") : "")
                + (o.containsKey("health") ? "[" + o.get("alignment") + ",血" + o.get("health") + "]" : "")
                + (o.containsKey("active") ? "[生效=" + o.get("active") + "]" : ""));
            b.append(key.equals("features") ? "地物=" : "可见角色=").append(j).append('\n');
        }
        Map<String,Object> map = (Map<String,Object>)state.get("map");
        if (state.containsKey("visibleEffects") && !((List<?>)state.get("visibleEffects")).isEmpty())
            b.append("可见区域效果=").append(Json.encode(state.get("visibleEffects"))).append('\n');
        if (map != null) {
            if (map.containsKey("screenProjection")) b.append("格子屏幕投影=")
                .append(Json.encode(map.get("screenProjection"))).append('\n');
            b.append("局部地图 原点").append(map.get("origin")).append("（仅当前视野）\n");
            for (String row : (List<String>)map.get("rows")) b.append(row).append('\n');
        }
        Map<String,Object> ui = (Map<String,Object>)state.get("ui");
        if (ui != null) {
            List<?> texts = (List<?>)ui.get("text"), buttons = (List<?>)ui.get("buttons");
            if (!texts.isEmpty()) b.append("弹窗=").append(String.join(" / ",(List<String>)texts)).append('\n');
            if (!buttons.isEmpty()) b.append("按钮=").append(Json.encode(buttons)).append('\n');
        }
        if (state.containsKey("screenLog")) b.append("屏幕日志=").append(state.get("screenLog")).append('\n');
        for (String key:Arrays.asList("containers","knownMap","coverage","unresolved"))
            if (state.containsKey(key)) b.append(key).append('=').append(Json.encode(state.get(key))).append('\n');
        return b.toString();
    }
    static Map<String,Object> diff(Map<String,Object> before, Map<String,Object> after) {
        Map<String,Object> patch = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>(before.keySet()); keys.addAll(after.keySet());
        for (String key : keys) if (!Objects.equals(before.get(key),after.get(key)))
            patch.put(key,after.get(key));
        return patch;
    }
}
