package com.magicling.myriad.host;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 0.9.6.5 adapter for StaffofMyriadThings. Reflection keeps the plugin source
 * separate from the host source, while still refusing an incompatible object.
 */
public final class ReflectiveStaffKillSource implements StaffKillSource {
    public static final String EXPECTED_CLASS =
            "com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.spdtomlpd.StaffofMyriadThings";
    private static final String FIELD = "killedTypes";

    @Override
    public Set<String> killedClassNames(Object staff) throws PluginCompatibilityException {
        if (staff == null) {
            throw new PluginCompatibilityException("万象之杖对象为空");
        }
        if (!EXPECTED_CLASS.equals(staff.getClass().getName())) {
            throw new PluginCompatibilityException(
                    "当前对象不是 0.9.6.5 的 StaffofMyriadThings: " + staff.getClass().getName());
        }

        Field field = findField(staff.getClass(), FIELD);
        if (field == null) {
            throw new PluginCompatibilityException("找不到 StaffofMyriadThings.killedTypes，版本可能不兼容");
        }
        try {
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            Object value = field.get(staff);
            if (!(value instanceof Set)) {
                throw new PluginCompatibilityException("killedTypes 不是 Set，版本可能不兼容");
            }
            Set<String> result = new LinkedHashSet<>();
            for (Object item : (Set<?>) value) {
                if (!(item instanceof String)) {
                    throw new PluginCompatibilityException("killedTypes 含有非字符串值");
                }
                result.add((String) item);
            }
            return Collections.unmodifiableSet(result);
        } catch (IllegalAccessException | SecurityException e) {
            throw new PluginCompatibilityException("无法读取 killedTypes", e);
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
