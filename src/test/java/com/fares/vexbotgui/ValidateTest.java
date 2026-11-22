package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidateTest {
    @Test void botNames() {
        assertTrue(Validate.botName("Bot_1"));
        assertTrue(Validate.botName("A"));
        assertTrue(Validate.botName("abcdefghijklmnop"));        // 16
        assertFalse(Validate.botName("abcdefghijklmnopq"));      // 17
        assertFalse(Validate.botName(""));
        assertFalse(Validate.botName(null));
        assertFalse(Validate.botName("a b"));
        assertFalse(Validate.botName("bob;kill"));
        assertFalse(Validate.botName("../x"));
    }

    @Test void cleanCommand() {
        assertEquals("vexbot list", Validate.cleanCommand("  /vexbot list "));
        assertEquals("vexbot list", Validate.cleanCommand("//vexbot list"));
        assertEquals("a b", Validate.cleanCommand("a\nb"));
        assertEquals("", Validate.cleanCommand(null));
        assertEquals(Validate.MAX_COMMAND, Validate.cleanCommand("x".repeat(1000)).length());
    }

    @Test void templates() {
        assertEquals("vexbot spawn {bot}", Validate.template("/vexbot spawn {bot}"));
        assertNull(Validate.template(null));
        assertNull(Validate.template("   "));
        assertNull(Validate.template("x".repeat(Validate.MAX_TEMPLATE + 1)));
    }

    @Test void presetNames() {
        assertTrue(Validate.presetName("Arena 1"));
        assertFalse(Validate.presetName(" lead"));
        assertFalse(Validate.presetName(""));
        assertFalse(Validate.presetName(null));
        assertFalse(Validate.presetName("x".repeat(25)));
    }

    @Test void presetValues() {
        int[] sizes = {5, 6, 6, 8, 4, 7};
        assertTrue(Validate.presetValues(new int[]{0, 1, 2, 3, 3, 6}, sizes));
        assertFalse(Validate.presetValues(new int[]{5, 0, 0, 0, 0, 0}, sizes));   // out of range
        assertFalse(Validate.presetValues(new int[]{-1, 0, 0, 0, 0, 0}, sizes));
        assertFalse(Validate.presetValues(new int[]{0, 0, 0}, sizes));            // wrong length
        assertFalse(Validate.presetValues(null, sizes));
    }

    @Test void teamNames() {
        assertTrue(Validate.teamName("RedTeam"));
        assertTrue(Validate.teamName("red-team_2"));
        assertTrue(Validate.teamName("x".repeat(24)));
        assertFalse(Validate.teamName("x".repeat(25)));
        assertFalse(Validate.teamName(""));
        assertFalse(Validate.teamName(null));
        assertFalse(Validate.teamName("my team"));
        assertFalse(Validate.teamName("a;b"));
        assertFalse(Validate.teamName("../x"));
        assertFalse(Validate.teamName("team\n"));
    }

    @Test void kitNames() {
        assertTrue(Validate.kitName("Diamond_Pvp-2"));
        assertTrue(Validate.kitName("x".repeat(32)));
        assertFalse(Validate.kitName("x".repeat(33)));
        assertFalse(Validate.kitName(""));
        assertFalse(Validate.kitName(null));
        assertFalse(Validate.kitName("my kit"));
        assertFalse(Validate.kitName("kit;give"));
    }
}
