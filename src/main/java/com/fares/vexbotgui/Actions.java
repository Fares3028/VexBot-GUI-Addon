package com.fares.vexbotgui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

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

    // ---------------- naming ----------------
    private static final String[] NAME_POOL = {"Shadow", "Blaze", "Frost", "Viper", "Rogue", "Titan", "Ghost", "Raven",
            "Storm", "Nova", "Drake", "Flint", "Ember", "Onyx", "Hawk", "Wolf", "Zephyr", "Cobra", "Pixel", "Comet"};
    private static final Set<String> usedNames = new HashSet<>();
    /** Last bot this addon spawned: used when the name box is empty. */
    static String lastBot = "";
    static String lastTeam = "";
    static int teamSize = 1, teamLevel = 1;

    /** Random, unused bot name like "Blaze47" (max 16 chars). */
    static String randomName() {
        for (int i = 0; i < 40; i++) {
            String n = NAME_POOL[RNG.nextInt(NAME_POOL.length)] + (10 + RNG.nextInt(90));
            if (usedNames.add(n.toLowerCase(Locale.ROOT)) && !VexBotGuiAddon.botOnline(n)) return n;
        }
        return "Vex" + (1000 + RNG.nextInt(9000));
    }

    // ---------------- one bot, start to finish ----------------
    record Recipe(int difficulty, int armor, int weapon, int offhand, int style) {
        static Recipe current() { return new Recipe(Actions.difficulty, Actions.armor, Actions.weapon, Actions.offhand, Actions.style); }
        static Recipe of(Tier t) {
            return new Recipe(idx(DIFFICULTIES, t.difficulty()), idx(ARMORS, t.armor()), idx(WEAPONS, t.weapon()),
                    idx(OFFHANDS, t.offhand()), idx(STYLES, t.style()));
        }
    }

    /**
     * Spawns the bot, WAITS until it is really in the world, then (optionally) adds it to a team,
     * sets level + play style and equips it. Reports problems in chat at the end.
     */
    static void deploy(String n, Recipe r, String team, String tag) {
        lastBot = n;
        long mark = VexBotGuiAddon.logMark();
        VexBotGuiAddon.runLater(Config.cmd("spawn", n, null), 1);
        VexBotGuiAddon.runLaterTask(() -> VexBotGuiAddon.whenBotReady(n, 100,
                () -> configure(n, r, team, tag, mark, true),
                () -> configure(n, r, team, tag, mark, false)), 2);
    }

    private static void configure(String n, Recipe r, String team, String tag, long mark, boolean online) {
        int d = online ? 8 : 2; // short pause: let the bot finish joining
        if (team != null && !team.isEmpty()) {
            VexBotGuiAddon.runLater(Config.cmd("teamadd", n, team), d);
            d += 4;
        }
        VexBotGuiAddon.runLater(Config.cmd("difficulty", n, DIFFICULTIES[r.difficulty()]), d);
        d += 4;
        if (!STYLES[r.style()].equals("default")) {
            VexBotGuiAddon.runLater(Config.cmd("playstyle", n, STYLES[r.style()]), d);
            d += 4;
        }
        d = equipSteps(n, d, r.armor(), r.weapon(), r.offhand());
        VexBotGuiAddon.runLaterTask(() -> report(n, tag, mark, online), d + 25);
    }

    private static void report(String n, String tag, long mark, boolean online) {
        String err = firstError(VexBotGuiAddon.logSince(mark));
        if (err != null) {
            String low = err.toLowerCase(Locale.ROOT);
            String why = low.contains("unknown or incomplete")
                    ? "command refused (OP / cheats needed, or VexBot missing on the server)" : err;
            tell("\u00a7c[VexBot GUI] " + n + ": " + why);
        } else if (!online) {
            tell("\u00a7e[VexBot GUI] " + n + " never appeared in the player list - commands were sent anyway.");
        } else {
            tell("\u00a7a[VexBot GUI] " + n + " is ready  \u2022  " + tag);
        }
    }

    private static String firstError(List<String> lines) {
        String[] keys = {"unknown or incomplete", "no entity was found", "no player was found", "permission",
                "no bot", "does not exist", "not found", "already exists", "incorrect argument", "expected", "invalid", "failed"};
        for (String l : lines) {
            String low = l.toLowerCase(Locale.ROOT);
            for (String k : keys) if (low.contains(k)) return l;
        }
        return null;
    }

    static void spawn(String base) {
        int amount = Integer.parseInt(COUNTS[count]);
        Recipe r = Recipe.current();
        String tag = summary();
        boolean named = base != null && !base.isBlank();
        String b = named ? base.trim() : "";
        for (int i = 1; i <= amount; i++) {
            String n = !named ? randomName()
                    : amount == 1 ? b : (b.length() > 13 ? b.substring(0, 13) : b) + i;
            VexBotGuiAddon.runLaterTask(() -> deploy(n, r, null, tag), (i - 1) * 12 + 1);
        }
    }

    /** Builds a team: spawns N bots of one level and puts each into the team (created on the first bot). */
    static void buildTeam(String team, int amount, int tierIdx) {
        lastTeam = team;
        Tier x = TIERS[tierIdx];
        Recipe r = Recipe.of(x);
        for (int i = 0; i < amount; i++) {
            String n = randomName();
            VexBotGuiAddon.runLaterTask(() -> deploy(n, r, team, x.describe()), i * 12 + 1);
        }
    }

    /** Vanilla /item replace (needs operator permission) using the current LOADOUT selection. */
    static void equip(String n, int d) { equipSteps(n, d, armor, weapon, offhand); }

    /** One command per tick so nothing is dropped. Returns the next free tick. */
    static int equipSteps(String n, int d, int a, int w, int o) {
        if (!ARMORS[a].equals("none")) {
            String[][] slots = {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}};
            for (String[] s : slots) {
                VexBotGuiAddon.runLater("item replace entity " + n + " armor." + s[0]
                        + " with minecraft:" + ARMORS[a] + "_" + s[1], d++);
            }
        }
        if (!WEAPONS[w].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.mainhand with minecraft:" + WEAPONS[w], d++);
        }
        if (!OFFHANDS[o].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.offhand with minecraft:" + OFFHANDS[o], d++);
        }
        return d;
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

    private static final Random RNG = new Random();

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    /** Spawns one ready bot of the given tier. Empty name = random name. Returns the bot name. */
    static String spawnTier(int t, String given) {
        Tier x = TIERS[t];
        String n = (given == null || given.isBlank()) ? randomName() : given.trim();
        deploy(n, Recipe.of(x), null, x.describe());
        return n;
    }

    // ---------------- spawn safety (shared by the GUI and the N key) ----------------
    static final long CONFIRM_MS = 5000;
    private static long confirmUntil = 0;
    private static String confirmKey = "";

    static void resetConfirm() { confirmUntil = 0; confirmKey = ""; }

    /** Name must be empty (random) or a valid player-style name. */
    static boolean nameOk(String n) { return n == null || n.isBlank() || Validate.botName(n.trim()); }

    /** First call returns false and starts a 5 s window; a second identical call inside it returns true. */
    static boolean confirmMassSpawn(String base) {
        int amount = Integer.parseInt(COUNTS[count]);
        if (amount <= 1) return true;
        String key = base + "|" + summary();
        long now = System.currentTimeMillis();
        if (now >= confirmUntil || !key.equals(confirmKey)) {
            confirmUntil = now + CONFIRM_MS;
            confirmKey = key;
            return false;
        }
        resetConfirm();
        return true;
    }

    static boolean confirmPending() { return System.currentTimeMillis() < confirmUntil; }

    static void quickSpawn() {
        if (!VexBotGuiAddon.connected()) { tell("\u00a7c[VexBot GUI] No server connection."); return; }
        String base = name.trim();
        if (!nameOk(base)) { tell("\u00a7c[VexBot GUI] Invalid bot name (1-16 letters, digits or _)."); return; }
        if (!confirmMassSpawn(base)) {
            tell("\u00a7e[VexBot GUI] Press N again within 5 s to spawn " + COUNTS[count] + " bots.");
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
