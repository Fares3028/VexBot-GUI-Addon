package com.fares.vexbotgui;

import java.util.List;
import java.util.Locale;

/** Decides whether a server message looks like a failure. Pure Java, unit-testable. One list for the whole addon. */
final class ReplyClassifier {
    private static final String[] ERROR_KEYS = {
            "unknown or incomplete", "no entity was found", "no player was found", "permission", "no bot",
            "does not exist", "not found", "already exists", "incorrect argument", "expected", "invalid",
            "failed", "error", "cannot"};

    private ReplyClassifier() {}

    static boolean isError(String text) {
        if (text == null) return false;
        String low = text.toLowerCase(Locale.ROOT);
        for (String k : ERROR_KEYS) if (low.contains(k)) return true;
        return false;
    }

    /** First line that looks like an error, or null. */
    static String firstError(List<String> lines) {
        if (lines == null) return null;
        for (String l : lines) if (isError(l)) return l;
        return null;
    }
}
