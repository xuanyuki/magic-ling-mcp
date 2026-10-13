package com.magicling.myriad.api;

/** Host UI boundary; the plugin never owns the game's Window implementation. */
public interface CodexWindowPresenter {
    void open(MyriadCodexModel model);
}
