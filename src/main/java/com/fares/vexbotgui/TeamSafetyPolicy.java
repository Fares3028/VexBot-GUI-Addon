package com.fares.vexbotgui;

import java.util.List;

/** Safe-by-default team settings verified against VexBot 3.2.5 SettingsCli. */
final class TeamSafetyPolicy {
    private TeamSafetyPolicy() {}

    static List<String> commands(String team) {
        if (!Validate.teamName(team)) throw new IllegalArgumentException("Invalid team name");
        String prefix = "vexbot team " + team + " settings ";
        return List.of(
                prefix + "autotarget off",
                prefix + "targetplayers off",
                prefix + "targetbots off",
                prefix + "targetmobs off",
                prefix + "revengemode off"
        );
    }
}
