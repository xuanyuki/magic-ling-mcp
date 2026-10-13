package com.magicling.myriad.host;

import java.util.Set;

/** Reads the per-item kill set from a host weapon instance. */
public interface StaffKillSource {
    Set<String> killedClassNames(Object staff) throws PluginCompatibilityException;
}
