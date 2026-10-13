package com.magicling.myriad;

import com.magicling.myriad.api.CodexWindowPresenter;
import com.magicling.myriad.host.MyriadCodexService;
import com.magicling.myriad.host.PluginCompatibilityException;

/** Public entry point that a host hook can call after the player inspects the staff. */
public final class MyriadPlugin {
    public static final String GAME_VERSION = "0.9.6.5";
    public static final int BUILD_CODE = 2026100600;

    private final MyriadCodexService service;

    public MyriadPlugin(MyriadCodexService service) {
        this.service = service;
    }

    public void openFor(Object staff, CodexWindowPresenter presenter)
            throws PluginCompatibilityException {
        if (presenter == null) throw new IllegalArgumentException("presenter must not be null");
        presenter.open(service.snapshot(staff));
    }
}
