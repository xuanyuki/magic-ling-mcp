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
            Class<?> random = Class.forName("com.watabou.utils.Random");
            synchronized (random) {
                Method push = random.getMethod("pushGenerator", long.class);
                Method pop = random.getMethod("popGenerator");
                // Mob constructors can roll preview variants. Keep those rolls
                // off the generator used for combat and level generation.
                push.invoke(null, 0x4D59524941444CL);
                try {
                    return readCatalogEntries();
                } finally {
                    pop.invoke(null);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new PluginCompatibilityException("当前游戏没有可用的 Bestiary API", e);
        } catch (IllegalAccessException | InvocationTargetException | LinkageError e) {
            throw new PluginCompatibilityException("读取 Bestiary 失败，版本可能不兼容", e);
        }
    }

    private Collection<MonsterDefinition> readCatalogEntries()
            throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException, PluginCompatibilityException {
        Class<?> bestiary = Class.forName(BESTIARY);
        Object[] categories = bestiary.getEnumConstants();
        Method entities = bestiary.getMethod("entities");
        Method title = bestiary.getMethod("title");
        Method isSeen = bestiary.getMethod("isSeen", Class.class);
        Method encounterCount = bestiary.getMethod("encounterCount", Class.class);
        Class<?> mobType = Class.forName(
                "com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob");

        Map<String, MonsterDefinition> result = new LinkedHashMap<>();
        for (Object category : categories) {
            String categoryName = String.valueOf(title.invoke(category));
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
                EntityText text = resolveText(entityClass, className);
                result.putIfAbsent(className, new MonsterDefinition(
                        className, text.name, text.description, categoryName, seen, count));
            }
        }
        return new ArrayList<>(result.values());
    }

    private static EntityText resolveText(Class<?> entityClass, String fallback) {
        try {
            Constructor<?> ctor = entityClass.getDeclaredConstructor();
            if (!ctor.isAccessible()) ctor.setAccessible(true);
            Object entity = ctor.newInstance();
            Method name = entityClass.getMethod("name");
            Object value = name.invoke(entity);
            String resolvedName = value != null && !value.toString().trim().isEmpty()
                    ? value.toString()
                    : fallback;
            String description = "";
            try {
                Object desc = entityClass.getMethod("description").invoke(entity);
                if (desc != null) description = desc.toString();
            } catch (ReflectiveOperationException ignored) {
                // Keep the row even if this host entity has no description method.
            }
            return new EntityText(resolvedName, description);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // A catalog row remains useful with its Java simple name.
        }
        int dot = fallback.lastIndexOf('.');
        return new EntityText(dot >= 0 ? fallback.substring(dot + 1) : fallback, "");
    }

    private static final class EntityText {
        private final String name;
        private final String description;

        private EntityText(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}

