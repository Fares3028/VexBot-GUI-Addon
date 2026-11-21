package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FileBackupTest {
    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 21, 14, 5, 9);

    @Test void movesTheBrokenFileUnderADatedName(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("vexbot_gui_presets.json");
        Files.writeString(f, "{ not json");
        Path kept = FileBackup.moveAside(f, "bad", T);
        assertEquals("vexbot_gui_presets.json.bad-20261121-140509", kept.getFileName().toString());
        assertFalse(Files.exists(f));
        assertEquals("{ not json", Files.readString(kept));
    }

    @Test void neverOverwritesAnEarlierBackup(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("c.json");
        Files.writeString(f, "one");
        Path first = FileBackup.moveAside(f, "bad", T);
        Files.writeString(f, "two");
        Path second = FileBackup.moveAside(f, "bad", T);   // same second
        assertNotEquals(first, second);
        assertEquals("one", Files.readString(first));
        assertEquals("two", Files.readString(second));
    }

    @Test void copyKeepsTheOriginalInPlace(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("p.json");
        Files.writeString(f, "data");
        Path copy = FileBackup.copyAside(f, "pre-migration", T);
        assertEquals("p.json.pre-migration-20261121-140509", copy.getFileName().toString());
        assertTrue(Files.exists(f));
        assertEquals("data", Files.readString(copy));
    }

    @Test void copyNeverOverwritesAnEarlierCopy(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("p.json");
        Files.writeString(f, "one");
        Path a = FileBackup.copyAside(f, "x", T);
        Files.writeString(f, "two");
        Path b = FileBackup.copyAside(f, "x", T);
        assertNotEquals(a, b);
        assertEquals("one", Files.readString(a));
        assertEquals("two", Files.readString(b));
    }

    @Test void copyIfDifferentOnlyWritesWhenTheContentChanged(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("p.json");
        Path bak = dir.resolve("p.json.bak");
        Files.writeString(f, "v1");
        assertTrue(FileBackup.copyIfDifferent(f, bak));      // first copy
        assertFalse(FileBackup.copyIfDifferent(f, bak));     // identical -> nothing to do (no pile of copies)
        Files.writeString(f, "v2");
        assertTrue(FileBackup.copyIfDifferent(f, bak));      // changed -> refreshed
        assertEquals("v2", Files.readString(bak));
    }
}
