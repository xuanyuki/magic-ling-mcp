package com.magicling.myriad.host;

import com.magicling.myriad.api.MonsterDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reflection-only bridge to the public Bestiary API in the 0.9.6.5 host.
 * It does not copy the game's monster list into the plugin.
 */
public final class ReflectiveBestiaryCatalogSource implements MonsterCatalogSource {
    private static final String BESTIARY =
            "com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary";

    @Override
    public Collection<MonsterDefinition> readCatalog() throws PluginCompatibilityException {
        try {
            Class<?> bestiary = Class.forName(BESTIARY);
            Object[] categories = bestiary.getEnumConstants();
            Method entities = bestiary.getMethod("entities");
            Method isSeen = bestiary.getMethod("isSeen", Class.class);
            Method encounterCount = bestiary.getMethod("encounterCount", Class.class);
            Class<?> mobType = Class.forName(
                    "com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob");

            Map<String, MonsterDefinition> result = new LinkedHashMap<>();
            for (Object category : categories) {
                String categoryName = ((Enum<?>) category).name();
                Object values = entities.invoke(category);
                if (!(values instanceof Iterable)) {
                    throw new PluginCompatibilityException("Bestiary.entities() 不是可遍历集合");
                }
                for (Object entity : (Iterable<?>) values) {
                    if (!(entity instanceof Class)) continue;
                    Class<?> entityClass = (Class<?>) entity;
                    if (!mobType.isAssignableFrom(entityClass)) continue;
                    String className = entityClass.getName();
                    boolean seen = Boolean.TRUE.equals(isSeen.invoke(null, entityClass));
                    int count = ((Number) encounterCount.invoke(null, entityClass)).intValue();
                    String displayName = resolveName(entityClass, className);
                    result.putIfAbsent(className, new MonsterDefinition(
                            className, displayName, categoryName, seen, count));
                }
            }
            return new ArrayList<>(result.values());
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new PluginCompatibilityException("当前游戏没有可用的 Bestiary API", e);
        } catch (IllegalAccessException | InvocationTargetException | LinkageError e) {
            throw new PluginCompatibilityException("读取 Bestiary 失败，版本可能不兼容", e);
        }
    }

    private static String resolveName(Class<?> entityClass, String fallback) {
        try {
            Constructor<?> ctor = entityClass.getDeclaredConstructor();
            if (!ctor.isAccessible()) ctor.setAccessible(true);
            Object entity = ctor.newInstance();
            Method name = entityClass.getMethod("name");
            Object value = name.invoke(entity);
            if (value != null && !value.toString().trim().isEmpty()) {
                return value.toString();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // A catalog row remains useful with its Java simple name.
        }
        int dot = fallback.lastIndexOf('.');
        return dot >= 0 ? fallback.substring(dot + 1) : fallback;
    }
}

