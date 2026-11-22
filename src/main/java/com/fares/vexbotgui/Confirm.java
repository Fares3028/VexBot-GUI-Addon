package com.fares.vexbotgui;

/**
 * Two-press confirmation bound to a key (everything that matters about the action) and a time window.
 * Pure Java, no Minecraft classes, unit-testable.
 */
final class Confirm {
    private final long windowMs;
    private String key = "";
    private long until = 0;

    Confirm(long windowMs) { this.windowMs = windowMs; }

    /**
     * The first call with a key arms it and returns false. A second call with the SAME key before the
     * window ends returns true and disarms. A different key (something was edited) or an expired
     * window starts over.
     */
    boolean ask(String k, long now) {
        if (k.equals(key) && now < until) {
            reset();
            return true;
        }
        key = k;
        until = now + windowMs;
        return false;
    }

    boolean pending(String k, long now) { return k.equals(key) && now < until; }

    boolean anyPending(long now) { return now < until; }

    void reset() { key = ""; until = 0; }
}
