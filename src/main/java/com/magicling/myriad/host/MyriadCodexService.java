package com.magicling.myriad.host;

import com.magicling.myriad.api.MyriadCodexModel;

/** Small host-neutral service used by the eventual Window adapter. */
public final class MyriadCodexService {
    private final StaffKillSource staffKillSource;
    private final MonsterCatalogSource catalogSource;

    public MyriadCodexService(StaffKillSource staffKillSource, MonsterCatalogSource catalogSource) {
        this.staffKillSource = staffKillSource;
        this.catalogSource = catalogSource;
    }

    public MyriadCodexModel snapshot(Object staff) throws PluginCompatibilityException {
        return MyriadCodexModel.of(
                catalogSource.readCatalog(),
                staffKillSource.killedClassNames(staff));
    }
}
