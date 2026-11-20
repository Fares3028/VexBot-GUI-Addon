package com.fares.vexbotgui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Random;

/** Shared state + bot logic, used by both the GUI and the quick-spawn key. */
final class Actions {
    // VexBot 3.2.0 presets: EASY, MEDIUM, HARD, EXPERT, PERFECT
    static final String[] DIFFICULTIES = {"easy", "medium", "hard", "expert", "perfect"};
    static final String[] COUNTS = {"1", "2", "3", "5", "10"};
    static final String[] ARMORS = {"none", "leather", "chainmail", "iron", "diamond", "netherite"};
    static final String[] WEAPONS = {"none", "diamond_sword", "netherite_sword", "diamond_axe", "mace", "bow", "crossbow", "trident",
            "wooden_sword", "stone_sword", "iron_sword"};
    static final String[] OFFHANDS = {"none", "shield", "totem_of_undying", "golden_apple"};
    // VexBot 3.2.0 play styles: DEFAULT, SAFE, DEFENSIVE, BALANCED, AGGRESSIVE, ADAPTIVE
    static final String[] STYLES = {"default", "safe", "defensive", "balanced", "aggressive", "adaptive"};
    static final String[] WANDER = {"5", "10", "20", "40", "80"};
    /** Server settings that are plain on/off switches (verified in VexBot 3.2.0). */
    static final String[] SETTINGS = {"advancedspawn", "targetplayers", "targetbots", "targetmobs", "retreat",
            "usepotions", "usebows", "enderpearl", "bunnyhop", "vexnav", "botdontleave", "botleaveondeath", "voicelines"};
    static final String[] ON_OFF = {"on", "off"};

    static String name = "";
    static int count = 0, difficulty = 1, armor = 0, weapon = 0, offhand = 0, style = 4;
    /** Not part of presets. */
    static int wander = 1, settingIdx = 0, settingVal = 0;

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
            if (!STYLES[style].equals("default")) {
                VexBotGuiAddon.runLater(Config.cmd("playstyle", n, STYLES[style]), delay + 11);
            }
            equip(n, delay + 14);
        }
    }

    /** Vanilla /item replace (needs operator permission) using the current LOADOUT selection. */
    static void equip(String n, int d) { equip(n, d, armor, weapon, offhand); }

    static void equip(String n, int d, int a, int w, int o) {
        if (!ARMORS[a].equals("none")) {
            String[][] slots = {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}};
            for (String[] s : slots) {
                VexBotGuiAddon.runLater("item replace entity " + n + " armor." + s[0]
                        + " with minecraft:" + ARMORS[a] + "_" + s[1], d);
            }
        }
        if (!WEAPONS[w].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.mainhand with minecraft:" + WEAPONS[w], d + 1);
        }
        if (!OFFHANDS[o].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.offhand with minecraft:" + OFFHANDS[o], d + 2);
        }
    }

    // ---------------- ready-made one-click bots ----------------
    record Tier(String difficulty, String armor, String weapon, String offhand, String style) {
        String label() {
            return difficulty.toUpperCase(Locale.ROOT) + "  \u2022  " + armor.toUpperCase(Locale.ROOT)
                    + " + " + weapon.replace('_', ' ').toUpperCase(Locale.ROOT);
        }
        String describe() { return label().replace("  \u2022  ", " / "); }
    }

    /** One per difficulty. Gear gets stronger with the level. */
    static final Tier[] TIERS = {
            new Tier("easy",    "leather",   "wooden_sword",    "none",             "safe"),
            new Tier("medium",  "chainmail", "stone_sword",     "none",             "balanced"),
            new Tier("hard",    "iron",      "iron_sword",      "shield",           "aggressive"),
            new Tier("expert",  "diamond",   "diamond_sword",   "shield",           "aggressive"),
            new Tier("perfect", "netherite", "netherite_sword", "totem_of_undying", "adaptive"),
    };

    static String lastQuick = "";
    private static final Random RNG = new Random();

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    /** Spawns one bot with the tier's difficulty, play style, armor and weapons. Returns the bot name. */
    static String spawnTier(int t, String given) {
        Tier x = TIERS[t];
        String n = (given == null || given.isBlank())
                ? "Vex" + Character.toUpperCase(x.difficulty().charAt(0)) + x.difficulty().substring(1) + (10 + RNG.nextInt(90))
                : given.trim();
        lastQuick = n;
        VexBotGuiAddon.runLater(Config.cmd("spawn", n, null), 1);
        VexBotGuiAddon.runLater(Config.cmd("difficulty", n, x.difficulty()), 8);
        if (!x.style().equals("default")) {
            VexBotGuiAddon.runLater(Config.cmd("playstyle", n, x.style()), 11);
        }
        equip(n, 14, idx(ARMORS, x.armor()), idx(WEAPONS, x.weapon()), idx(OFFHANDS, x.offhand()));
        return n;
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

    /** Your own player name (used for follow / attack / goto / look targets). */
    static String me() { return Minecraft.getInstance().getUser().getName(); }

    static void tell(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), false);
    }
}
