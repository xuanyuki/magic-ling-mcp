package com.magicling.myriad.host;

/** A clear failure for a game build that does not match this adapter. */
public final class PluginCompatibilityException extends Exception {
    public PluginCompatibilityException(String message) {
        super(message);
    }

    public PluginCompatibilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
