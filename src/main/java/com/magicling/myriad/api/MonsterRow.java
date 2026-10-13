package com.magicling.myriad.api;

import java.util.Objects;

/** One row in the complete codex view. */
public final class MonsterRow {
    private final MonsterDefinition definition;
    private final StaffKillStatus status;

    public MonsterRow(MonsterDefinition definition, StaffKillStatus status) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.status = Objects.requireNonNull(status, "status");
    }

    public MonsterDefinition definition() {
        return definition;
    }

    public StaffKillStatus status() {
        return status;
    }

    public boolean killedByStaff() {
        return status == StaffKillStatus.KILLED_BY_STAFF;
    }
}
