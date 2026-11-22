package com.fares.vexbotgui;

/** Upgrades presets saved by addon versions before 1.5. Pure Java, unit-testable. */
final class PresetMigration {
    private PresetMigration() {}

    /**
     * Old lists had "pro" (difficulty index 4) and "passive" (style index 2), which VexBot does not have.
     * Layout: [count, difficulty, armor, weapon, offhand, style]. Returns a migrated copy.
     */
    static int[] fromV1(int[] v) {
        int[] c = v.clone();
        if (c[1] >= 4) c[1] -= 1;
        if (c[5] == 2) c[5] = 1; else if (c[5] > 2) c[5] -= 1;
        return c;
    }
}
