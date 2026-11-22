package com.fares.vexbotgui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Separates expected team members that are online from names not currently visible. */
final class TeamFollowPlan {
    record Plan(List<String> online, List<String> missing) {}
    private TeamFollowPlan() {}

    static Plan partition(List<String> expected, Predicate<String> isOnline) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (expected != null) for (String name : expected) {
            if (name == null || name.isBlank()) continue;
            unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        List<String> online = new ArrayList<>(), missing = new ArrayList<>();
        for (String name : unique.values()) {
            if (isOnline.test(name)) online.add(name); else missing.add(name);
        }
        return new Plan(List.copyOf(online), List.copyOf(missing));
    }
}
