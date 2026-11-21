package com.fares.vexbotgui;

import java.util.regex.Pattern;

/** Pure input validation (no Minecraft classes, unit-testable). */
final class Validate {
    static final Pattern BOT_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    static final Pattern TEAM_NAME = Pattern.compile("[A-Za-z0-9_-]{1,24}");
    static final int MAX_COMMAND = 256;
    static final int MAX_TEMPLATE = 200;
    static final int MAX_PRESET_NAME = 24;
    static final int MAX_PRESETS = 50;

    private Validate() {}

    /** Minecraft-style player name: 1-16 chars of letters, digits, underscore. */
    static boolean botName(String s) { return s != null && BOT_NAME.matcher(s).matches(); }

    /** Team name: 1-24 letters, digits, underscore or hyphen (safe as one command word). */
    static boolean teamName(String s) { return s != null && TEAM_NAME.matcher(s).matches(); }

    /** Removes control chars and leading slashes, trims and caps the length. */
    static String cleanCommand(String s) {
        if (s == null) return "";
        String c = s.replaceAll("\\p{Cntrl}", " ").trim();
        while (c.startsWith("/")) c = c.substring(1).trim();
        return c.length() > MAX_COMMAND ? c.substring(0, MAX_COMMAND).trim() : c;
    }

    /** Returns the cleaned template, or null if it is unusable. */
    static String template(String t) {
        String c = cleanCommand(t);
        return (c.isEmpty() || c.length() > MAX_TEMPLATE) ? null : c;
    }

    static boolean presetName(String s) {
        if (s == null || s.isBlank() || s.length() > MAX_PRESET_NAME || !s.equals(s.trim())) return false;
        for (int i = 0; i < s.length(); i++) if (Character.isISOControl(s.charAt(i))) return false;
        return true;
    }

    /** values[i] must be within [0, sizes[i]). */
    static boolean presetValues(int[] values, int[] sizes) {
        if (values == null || values.length != sizes.length) return false;
        for (int i = 0; i < sizes.length; i++) if (values[i] < 0 || values[i] >= sizes[i]) return false;
        return true;
    }
}
