package com.fares.vexbotgui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Keeps a file we cannot use instead of overwriting it. Pure Java, unit-testable. */
final class FileBackup {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private FileBackup() {}

    static Path moveAside(Path file, String tag) throws IOException {
        return moveAside(file, tag, LocalDateTime.now());
    }

    /** Copies the file next to itself as "name.json.TAG-yyyyMMdd-HHmmss" and keeps the original in place. */
    static Path copyAside(Path file, String tag) throws IOException {
        return copyAside(file, tag, LocalDateTime.now());
    }

    static Path copyAside(Path file, String tag, LocalDateTime now) throws IOException {
        String base = file.getFileName() + "." + tag + "-" + STAMP.format(now);
        Path target = file.resolveSibling(base);
        for (int i = 1; Files.exists(target); i++) target = file.resolveSibling(base + "-" + i);
        return Files.copy(file, target);
    }

    /** Copies file to target unless target already holds the same bytes. @return true when a copy was written. */
    static boolean copyIfDifferent(Path file, Path target) throws IOException {
        if (Files.exists(target) && Files.mismatch(file, target) == -1L) return false;
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Renames "name.json" to "name.json.TAG-yyyyMMdd-HHmmss" (adds -1, -2 ... if that already exists). */
    static Path moveAside(Path file, String tag, LocalDateTime now) throws IOException {
        String base = file.getFileName() + "." + tag + "-" + STAMP.format(now);
        Path target = file.resolveSibling(base);
        for (int i = 1; Files.exists(target); i++) target = file.resolveSibling(base + "-" + i);
        return Files.move(file, target);
    }
}
