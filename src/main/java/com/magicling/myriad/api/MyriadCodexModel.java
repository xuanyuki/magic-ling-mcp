package com.magicling.myriad.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure data model for the full 万象之杖 codex. It has no game/UI dependency.
 */
public final class MyriadCodexModel {
    private static final Comparator<MonsterRow> ORDER =
            Comparator.comparing((MonsterRow row) -> row.definition().category())
                    .thenComparing(row -> row.definition().displayName())
                    .thenComparing(row -> row.definition().className());

    private final List<MonsterRow> rows;
    private final int killedCount;
    private final int notKilledCount;
    private final int unmappedCount;

    private MyriadCodexModel(List<MonsterRow> rows) {
        List<MonsterRow> copy = new ArrayList<>(rows);
        copy.sort(ORDER);
        this.rows = Collections.unmodifiableList(copy);
        int killed = 0;
        int notKilled = 0;
        int unmapped = 0;
        for (MonsterRow row : copy) {
            switch (row.status()) {
                case KILLED_BY_STAFF:
                    killed++;
                    break;
                case NOT_KILLED_BY_STAFF:
                    notKilled++;
                    break;
                case UNMAPPED_KILL:
                    unmapped++;
                    break;
                default:
                    throw new AssertionError(row.status());
            }
        }
        this.killedCount = killed;
        this.notKilledCount = notKilled;
        this.unmappedCount = unmapped;
    }

    /**
     * Creates a model from all catalog entries and the staff's class-name set.
     * Duplicate catalog classes are collapsed deterministically.
     */
    public static MyriadCodexModel of(
            Collection<MonsterDefinition> catalog,
            Collection<String> killedClassNames) {
        Map<String, MonsterDefinition> definitions = new LinkedHashMap<>();
        if (catalog != null) {
            for (MonsterDefinition definition : catalog) {
                if (definition != null) {
                    definitions.put(definition.className(), definition);
                }
            }
        }

        Set<String> killed = new HashSet<>();
        if (killedClassNames != null) {
            for (String className : killedClassNames) {
                if (className != null && !className.trim().isEmpty()) {
                    killed.add(className);
                }
            }
        }

        List<MonsterRow> rows = new ArrayList<>();
        for (MonsterDefinition definition : definitions.values()) {
            StaffKillStatus status = killed.contains(definition.className())
                    ? StaffKillStatus.KILLED_BY_STAFF
                    : StaffKillStatus.NOT_KILLED_BY_STAFF;
            rows.add(new MonsterRow(definition, status));
        }

        // Keep data that the game's catalog does not expose instead of hiding it.
        for (String className : killed) {
            if (!definitions.containsKey(className)) {
                MonsterDefinition unknown = new MonsterDefinition(
                        className,
                        className,
                        "未映射",
                        false,
                        0);
                rows.add(new MonsterRow(unknown, StaffKillStatus.UNMAPPED_KILL));
            }
        }
        return new MyriadCodexModel(rows);
    }

    public List<MonsterRow> rows() {
        return rows;
    }

    /** Number of all rows, including preserved unmapped save records. */
    public int totalCount() {
        return rows.size();
    }

    public int killedCount() {
        return killedCount;
    }

    /** Number of known catalog monsters that this staff has not killed. */
    public int notKilledCount() {
        return notKilledCount;
    }

    /** Number of staff records not currently represented by the catalog. */
    public int unmappedCount() {
        return unmappedCount;
    }

    public List<MonsterRow> rows(StaffKillStatus status) {
        List<MonsterRow> result = new ArrayList<>();
        for (MonsterRow row : rows) {
            if (row.status() == status) result.add(row);
        }
        return Collections.unmodifiableList(result);
    }
}
