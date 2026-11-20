package com.fares.vexbotgui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Shared state + bot logic, used by both the GUI and the quick-spawn key. */
final class Actions {
    static final String[] DIFFICULTIES = {"easy", "medium", "hard", "expert", "pro", "perfect"};
    static final String[] COUNTS = {"1", "2", "3", "5", "10"};
    static final String[] ARMORS = {"none", "leather", "chainmail", "iron", "diamond", "netherite"};
    static final String[] WEAPONS = {"none", "diamond_sword", "netherite_sword", "diamond_axe", "mace", "bow", "crossbow", "trident"};
    static final String[] OFFHANDS = {"none", "shield", "totem_of_undying", "golden_apple"};
    static final String[] STYLES = {"default", "safe", "passive", "defensive", "balanced", "aggressive", "adaptive"};

    static String name = "";
    static int count = 0, difficulty = 1, armor = 0, weapon = 0, offhand = 0, style = 5;

    static int[] snapshot() { return new int[]{count, difficulty, armor, weapon, offhand, style}; }

    static void apply(int[] p) {
        count = clamp(p[0], COUNTS.length);
        difficulty = clamp(p[1], DIFFICULTIES.length);
        armor = clamp(p[2], ARMORS.length);
        weapon = clamp(p[3], WEAPONS.length);
        offhand = clamp(p[4], OFFHANDS.length);
        style = clamp(p[5], STYLES.length);
    }

    private static int clamp(int v, int len) { return v < 0 || v >= len ? 0 : v; }

    static String summary() {
        return COUNTS[count] + " BOT(S)  \u2022  " + DIFFICULTIES[difficulty].toUpperCase()
                + "  \u2022  " + ARMORS[armor].toUpperCase() + "  \u2022  " + WEAPONS[weapon].toUpperCase();
    }

    static void spawn(String base) {
        int amount = Integer.parseInt(COUNTS[count]);
        for (int i = 1; i <= amount; i++) {
            String n = amount == 1 ? base : (base.length() > 13 ? base.substring(0, 13) : base) + i;
            int delay = (i - 1) * 10;
            VexBotGuiAddon.runLater(Config.cmd("spawn", n, null), delay + 1);
            VexBotGuiAddon.runLater(Config.cmd("difficulty", n, DIFFICULTIES[difficulty]), delay + 8);
            equip(n, delay + 14);
        }
    }

    /** Vanilla /item replace (needs operator permission). */
    static void equip(String n, int d) {
        if (!ARMORS[armor].equals("none")) {
            String[][] slots = {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}};
            for (String[] s : slots) {
                VexBotGuiAddon.runLater("item replace entity " + n + " armor." + s[0]
                        + " with minecraft:" + ARMORS[armor] + "_" + s[1], d);
            }
        }
        if (!WEAPONS[weapon].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.mainhand with minecraft:" + WEAPONS[weapon], d + 1);
        }
        if (!OFFHANDS[offhand].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.offhand with minecraft:" + OFFHANDS[offhand], d + 2);
        }
    }

    static void quickSpawn() {
        String base = name.trim();
        if (base.isEmpty()) {
            tell("VexBot GUI: open the panel (B) and enter a bot name first.");
            return;
        }
        spawn(base);
        tell("VexBot GUI: quick spawn  " + summary());
    }

    static void tell(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), false);
    }
}
