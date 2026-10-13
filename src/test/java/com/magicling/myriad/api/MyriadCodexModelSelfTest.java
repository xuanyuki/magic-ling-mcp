package com.magicling.myriad.api;

import java.util.Arrays;
import java.util.Collections;

/** Lightweight no-dependency regression test; run with `java -ea ...`. */
public final class MyriadCodexModelSelfTest {
    public static void main(String[] args) {
        MonsterDefinition rat = new MonsterDefinition("mob.Rat", "老鼠", "森林", true, 7);
        MonsterDefinition bat = new MonsterDefinition("mob.Bat", "蝙蝠", "冰牢", false, 0);

        MyriadCodexModel model = MyriadCodexModel.of(
                Arrays.asList(rat, bat),
                Arrays.asList("mob.Rat", "mob.Unknown", "mob.Rat"));

        check(model.totalCount() == 3, "unknown kill must be retained");
        check(model.killedCount() == 1, "only catalog rows count as killed");
        check(model.notKilledCount() == 1, "not killed excludes unmapped rows");
        check(model.unmappedCount() == 1, "unmapped count");
        check(model.rows(StaffKillStatus.KILLED_BY_STAFF).size() == 1, "killed filter");
        check(model.rows(StaffKillStatus.UNMAPPED_KILL).size() == 1, "unmapped filter");
        check(model.rows().get(0).definition().category().equals("冰牢"), "stable category ordering");

        MyriadCodexModel empty = MyriadCodexModel.of(Collections.<MonsterDefinition>emptyList(), null);
        check(empty.totalCount() == 0 && empty.killedCount() == 0, "empty snapshot");
        System.out.println("MyriadCodexModelSelfTest: OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

