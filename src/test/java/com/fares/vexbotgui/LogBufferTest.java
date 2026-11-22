package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogBufferTest {
    @Test void marksOnlyReturnRepliesAfterThePoint() {
        LogBuffer b = new LogBuffer(50);
        b.add("00:00:01", LogBuffer.Dir.IN, "old reply");
        long mark = b.mark();
        b.add("00:00:02", LogBuffer.Dir.OUT, "vexbot list");
        b.add("00:00:03", LogBuffer.Dir.IN, "new reply");
        assertEquals(List.of("new reply"), b.repliesSince(mark));
    }

    @Test void trimmingKeepsTheNewestAndStaysConsistent() {
        LogBuffer b = new LogBuffer(3);
        long mark = b.mark();
        for (int i = 1; i <= 6; i++) b.add("t", LogBuffer.Dir.IN, "r" + i);
        assertEquals(3, b.size());
        assertEquals(List.of("r4", "r5", "r6"), b.repliesSince(mark));
        assertEquals("r6", b.lastReply());
    }

    @Test void clearForgetsOldEntriesButNewRepliesStillCount() {
        LogBuffer b = new LogBuffer(10);
        long mark = b.mark();
        b.add("t", LogBuffer.Dir.IN, "before");
        b.clear();                                  // e.g. leaving the server
        b.add("t", LogBuffer.Dir.IN, "after");
        assertEquals(List.of("after"), b.repliesSince(mark));
        assertEquals("after", b.lastReply());
    }

    @Test void errorsAreFlaggedOnlyForServerReplies() {
        LogBuffer b = new LogBuffer(10);
        b.add("t", LogBuffer.Dir.IN, "Unknown or incomplete command");
        b.add("t", LogBuffer.Dir.OUT, "vexbot invalid test");
        b.add("t", LogBuffer.Dir.INFO, "warning text", true);
        assertTrue(b.get(0).error());
        assertFalse(b.get(1).error());              // our own command is never an "error"
        assertTrue(b.get(2).error());               // explicit flag
    }

    @Test void newlinesAreFlattened() {
        LogBuffer b = new LogBuffer(10);
        b.add("t", LogBuffer.Dir.IN, "line1\nline2\r\n");
        assertEquals("line1 line2", b.get(0).text());
    }

    @Test void markNewerThanContentGivesNothing() {
        LogBuffer b = new LogBuffer(10);
        b.add("t", LogBuffer.Dir.IN, "x");
        assertTrue(b.repliesSince(b.mark()).isEmpty());
        assertTrue(b.repliesSince(b.mark() + 100).isEmpty());
        assertEquals("", new LogBuffer(5).lastReply());
    }
}
