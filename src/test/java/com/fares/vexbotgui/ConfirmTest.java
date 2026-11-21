package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmTest {
    @Test void secondIdenticalPressConfirms() {
        Confirm c = new Confirm(5000);
        assertFalse(c.ask("team|5|2", 1000));
        assertTrue(c.pending("team|5|2", 2000));
        assertTrue(c.ask("team|5|2", 2000));
        assertFalse(c.anyPending(2001));           // consumed
    }

    @Test void editingAnythingInvalidatesIt() {
        Confirm c = new Confirm(5000);
        assertFalse(c.ask("Red|5|2", 0));
        assertFalse(c.ask("Red|10|2", 100));       // size changed -> starts over
        assertFalse(c.pending("Red|5|2", 150));
        assertTrue(c.ask("Red|10|2", 200));
    }

    @Test void expiresAfterTheWindow() {
        Confirm c = new Confirm(5000);
        assertFalse(c.ask("k", 0));
        assertFalse(c.ask("k", 5000));             // window is over -> re-armed, not confirmed
        assertTrue(c.ask("k", 5100));
    }

    @Test void resetClearsPending() {
        Confirm c = new Confirm(5000);
        c.ask("k", 0);
        c.reset();
        assertFalse(c.anyPending(1));
        assertFalse(c.ask("k", 2));
    }

    @Test void sessionPrefixKeepsServersApart() {
        Confirm c = new Confirm(5000);
        assertFalse(c.ask("1|Red|5|2", 0));
        assertFalse(c.ask("2|Red|5|2", 10));       // another server/session
    }

    @Test void pendingIsBoundToTheExactKey() {
        Confirm c = new Confirm(5000);
        c.ask("Bob|3|HARD", 0);
        assertTrue(c.pending("Bob|3|HARD", 100));
        assertFalse(c.pending("Bob|3|EASY", 100));     // a changed option is not "pending"
        assertTrue(c.anyPending(100));
    }

    @Test void windowEndsExactlyAtTheLimit() {
        Confirm c = new Confirm(5000);
        c.ask("k", 1000);
        assertTrue(c.pending("k", 5999));
        assertFalse(c.pending("k", 6000));
        assertFalse(c.anyPending(6000));
    }

    @Test void aConfirmedActionCannotBeConfirmedTwice() {
        Confirm c = new Confirm(5000);
        c.ask("k", 0);
        assertTrue(c.ask("k", 10));
        assertFalse(c.ask("k", 20));                   // needs a fresh first press
    }
}
