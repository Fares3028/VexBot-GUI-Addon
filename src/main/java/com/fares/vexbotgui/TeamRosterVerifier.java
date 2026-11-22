package com.fares.vexbotgui;

import java.util.List;
import java.util.Locale;

/** Compares expected bot names with the server's team roster reply. */
final class TeamRosterVerifier {
    private TeamRosterVerifier() {}

    static List<String> missing(List<String> rosterLines, List<String> expectedNames) {
        String roster = String.join(" ", rosterLines == null ? List.of() : rosterLines).toLowerCase(Locale.ROOT);
        return (expectedNames == null ? List.<String>of() : expectedNames).stream()
                .filter(n -> n != null && !n.isBlank() && !roster.contains(n.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
