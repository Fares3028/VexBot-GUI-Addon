package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresetMigrationTest {
    // layout: [count, difficulty, armor, weapon, offhand, style]
    @Test void proBecomesExpertAndPerfectShiftsDown() {
        assertArrayEquals(new int[]{0, 3, 0, 0, 0, 0}, PresetMigration.fromV1(new int[]{0, 4, 0, 0, 0, 0}));
        assertArrayEquals(new int[]{0, 4, 0, 0, 0, 0}, PresetMigration.fromV1(new int[]{0, 5, 0, 0, 0, 0}));
        assertArrayEquals(new int[]{0, 2, 0, 0, 0, 0}, PresetMigration.fromV1(new int[]{0, 2, 0, 0, 0, 0}));
    }

    @Test void passiveBecomesSafeAndLaterStylesShiftDown() {
        assertEquals(1, PresetMigration.fromV1(new int[]{0, 0, 0, 0, 0, 2})[5]);   // passive -> safe
        assertEquals(2, PresetMigration.fromV1(new int[]{0, 0, 0, 0, 0, 3})[5]);   // defensive
        assertEquals(4, PresetMigration.fromV1(new int[]{0, 0, 0, 0, 0, 5})[5]);   // aggressive
        assertEquals(5, PresetMigration.fromV1(new int[]{0, 0, 0, 0, 0, 6})[5]);   // adaptive
        assertEquals(0, PresetMigration.fromV1(new int[]{0, 0, 0, 0, 0, 0})[5]);   // default
    }

    @Test void doesNotModifyTheInput() {
        int[] in = {1, 5, 2, 3, 1, 6};
        PresetMigration.fromV1(in);
        assertArrayEquals(new int[]{1, 5, 2, 3, 1, 6}, in);
    }
}
