package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplyClassifierTest {
    @Test void recognisesCommonFailures() {
        assertTrue(ReplyClassifier.isError("Unknown or incomplete command, see below for error"));
        assertTrue(ReplyClassifier.isError("No entity was found"));
        assertTrue(ReplyClassifier.isError("You do not have PERMISSION"));
        assertTrue(ReplyClassifier.isError("Team Red already exists"));
        assertTrue(ReplyClassifier.isError("Failed to ..."));
    }

    @Test void normalMessagesAreNotErrors() {
        assertFalse(ReplyClassifier.isError("[VexBot] Teams (1):"));
        assertFalse(ReplyClassifier.isError("Blaze47 joined the game"));
        assertFalse(ReplyClassifier.isError(""));
        assertFalse(ReplyClassifier.isError(null));
    }

    @Test void firstErrorSkipsHarmlessLines() {
        assertNull(ReplyClassifier.firstError(List.of("a", "b")));
        assertNull(ReplyClassifier.firstError(null));
        assertEquals("Invalid argument", ReplyClassifier.firstError(List.of("ok", "Invalid argument", "failed too")));
    }
}
