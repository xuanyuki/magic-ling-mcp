package com.magicling.myriad.api;

import java.util.Objects;

/** Immutable description of one entry in the host game's monster catalog. */
public final class MonsterDefinition {
    private final String className;
    private final String displayName;
    private final String description;
    private final String category;
    private final boolean seenInBestiary;
    private final int globalEncounterCount;

    public MonsterDefinition(
            String className,
            String displayName,
            String category,
            boolean seenInBestiary,
            int globalEncounterCount) {
        this(className, displayName, "", category, seenInBestiary, globalEncounterCount);
    }

    public MonsterDefinition(
            String className,
            String displayName,
            String description,
            String category,
            boolean seenInBestiary,
            int globalEncounterCount) {
        this.className = requireText(className, "className");
        this.displayName = displayName == null || displayName.trim().isEmpty()
                ? simpleName(className)
                : displayName;
        this.description = description == null ? "" : description;
        this.category = category == null || category.trim().isEmpty()
                ? "未分类"
                : category;
        this.seenInBestiary = seenInBestiary;
        this.globalEncounterCount = Math.max(0, globalEncounterCount);
    }

    public String className() {
        return className;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String category() {
        return category;
    }

    public boolean seenInBestiary() {
        return seenInBestiary;
    }

    /** Global encyclopedia count; it is deliberately not used as staff status. */
    public int globalEncounterCount() {
        return globalEncounterCount;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 && dot + 1 < className.length()
                ? className.substring(dot + 1)
                : className;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MonsterDefinition)) return false;
        MonsterDefinition that = (MonsterDefinition) other;
        return className.equals(that.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className);
    }
}
