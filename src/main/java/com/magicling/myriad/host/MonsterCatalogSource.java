package com.magicling.myriad.host;

import java.util.Collection;
import com.magicling.myriad.api.MonsterDefinition;

/** Supplies the host game's complete monster catalog. */
public interface MonsterCatalogSource {
    Collection<MonsterDefinition> readCatalog() throws PluginCompatibilityException;
}
