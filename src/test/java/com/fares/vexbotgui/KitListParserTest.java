package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KitListParserTest {
    @Test void readsTheRealReplyFormat() {
        KitListParser.Result r = KitListParser.parse(List.of(
                "[VexBot] Saved kits:", "Diamond (36 item(s))", "Nethrite_Pvp (41 item(s))",
                "Use /vexbot givekit <kit> <player> to give a kit."));
        assertTrue(r.recognized());
        assertFalse(r.empty());
        assertEquals(2, r.kits().size());
        assertEquals("Diamond", r.kits().get(0).name());
        assertEquals(41, r.kits().get(1).items());
    }

    @Test void colourCodesAndIndentationAreIgnored() {
        KitListParser.Result r = KitListParser.parse(List.of(
                "\u00a7a[VexBot] Saved kits:", "  \u00a7eStarter \u00a78(3 item(s))"));
        assertEquals(1, r.kits().size());
        assertEquals("Starter", r.kits().get(0).name());
    }

    @Test void noKitsYetIsAnEmptyButRecognisedList() {
        KitListParser.Result r = KitListParser.parse(List.of("[VexBot] No kits saved yet. Use /vexbot createkit <name> first."));
        assertTrue(r.recognized());
        assertTrue(r.empty());
        assertTrue(r.kits().isEmpty());
    }

    @Test void unrelatedMessagesAreNotRecognised() {
        assertFalse(KitListParser.parse(List.of("Blaze47 joined the game")).recognized());
        assertFalse(KitListParser.parse(List.of()).recognized());
        assertFalse(KitListParser.parse(null).recognized());
    }

    @Test void rowsBeforeTheHeaderAndAfterTheHintAreIgnored() {
        KitListParser.Result r = KitListParser.parse(List.of(
                "Fake (1 item(s))", "[VexBot] Saved kits:", "Real (2 item(s))",
                "Use /vexbot givekit <kit> <player> to give a kit.", "Late (9 item(s))"));
        assertEquals(1, r.kits().size());
        assertEquals("Real", r.kits().get(0).name());
    }

    @Test void duplicateNamesAreCountedOnce() {
        KitListParser.Result r = KitListParser.parse(List.of("[VexBot] Saved kits:", "A (1 item(s))", "a (1 item(s))"));
        assertEquals(1, r.kits().size());
    }
}
