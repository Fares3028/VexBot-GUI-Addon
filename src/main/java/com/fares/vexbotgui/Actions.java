package com.fares.vexbotgui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Shared state + bot logic, used by both the GUI and the quick-spawn key. */
final class Actions {
    // VexBot 3.2.0 presets: EASY, MEDIUM, HARD, EXPERT, PERFECT
    static final String[] DIFFICULTIES = {"easy", "medium", "hard", "expert", "perfect"};
    static final String[] COUNTS = {"1", "2", "3", "5", "10"};
    static final String[] ARMORS = {"none", "leather", "chainmail", "iron", "diamond", "netherite", "elytra"};
    static final String[] WEAPONS = {"none", "diamond_sword", "netherite_sword", "diamond_axe", "mace", "bow", "crossbow", "trident",
            "wooden_sword", "stone_sword", "iron_sword"};
    static final String[] OFFHANDS = {"none", "shield", "totem_of_undying", "golden_apple"};
    // VexBot 3.2.0 play styles: DEFAULT, SAFE, DEFENSIVE, BALANCED, AGGRESSIVE, ADAPTIVE
    static final String[] STYLES = {"default", "safe", "defensive", "balanced", "aggressive", "adaptive"};
    static final String[] WANDER = {"5", "10", "20", "40", "80"};
    /** Server settings that are plain on/off switches (verified in VexBot 3.2.0). */
    static final String[] SETTINGS = {"advancedspawn", "autotarget", "targetplayers", "targetbots", "targetmobs", "revengemode", "retreat",
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
        for (int i = 0; i < 80; i++) {
            String n = NAME_POOL[RNG.nextInt(NAME_POOL.length)] + (10 + RNG.nextInt(90));
            String key = n.toLowerCase(Locale.ROOT);
            if (!usedNames.contains(key) && !VexBotGuiAddon.botOnline(n)) {
                usedNames.add(key);
                return n;
            }
        }
        // A larger fallback space avoids colliding with names reserved during this session.
        for (int i = 0; i < 9000; i++) {
            String n = "Vex" + (1000 + RNG.nextInt(9000));
            String key = n.toLowerCase(Locale.ROOT);
            if (!usedNames.contains(key) && !VexBotGuiAddon.botOnline(n)) {
                usedNames.add(key);
                return n;
            }
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
    /** Deployments still being configured: an error in the shared log may belong to any of them. */
    private static int activeDeploys = 0;

    static void deploy(String n, Recipe r, String team, String tag) {
        long mark = VexBotGuiAddon.logMark();
        activeDeploys++;
        if (VexBotGuiAddon.botOnline(n)) {
            tell("\u00a7e[VexBot GUI] " + n + " is already online - the server may refuse this spawn.");
        }
        VexBotGuiAddon.runLater(Config.cmd("spawn", n, null), 1);
        VexBotGuiAddon.runLaterTask(() -> VexBotGuiAddon.whenBotReady(n, 100,
                () -> configure(n, r, team, tag, mark, true),
                () -> configure(n, r, team, tag, mark, false)), 2);
    }

    private static void configure(String n, Recipe r, String team, String tag, long mark, boolean online) {
        if (online) lastBot = n; // only a bot that really joined becomes the default target for orders
        int d = online ? 8 : 2; // short pause: let the bot finish joining
        if (team != null && !team.isEmpty()) {
            // members add calls TeamManager.addBotsToTeam(), which getOrCreate()s the team.
            // This is the correct non-GUI creation path in VexBot 3.2.5.
            VexBotGuiAddon.runLater(Config.cmd("teamadd2", n, team), 1);
            int safetyDelay = 3; // after add: the team now exists, so team-scoped settings can apply
            for (String command : TeamSafetyPolicy.commands(team)) {
                VexBotGuiAddon.runLater(command, safetyDelay++);
            }
            d = Math.max(d, safetyDelay + 1);
            String activeFollow = teamFollowTargets.get(team.toLowerCase(Locale.ROOT));
            if (activeFollow != null && !activeFollow.isBlank()) {
                VexBotGuiAddon.runLater(Config.cmd("teamfollow2", "", team, activeFollow), d + 1);
                d += 3;
            }
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

    /**
     * Final word about one bot. Only "online" is real evidence (the bot is in the player list).
     * The setup commands cannot be confirmed one by one, so the text says "sent", not "done".
     */
    private static void report(String n, String tag, long mark, boolean online) {
        boolean shared = activeDeploys > 1;
        activeDeploys = Math.max(0, activeDeploys - 1);
        String err = ReplyClassifier.firstError(VexBotGuiAddon.logSince(mark));
        String state = online ? "online" : "NOT seen in the player list";
        if (err != null) {
            String low = err.toLowerCase(Locale.ROOT);
            String why = low.contains("unknown or incomplete")
                    ? "command refused (OP / cheats needed, or VexBot missing on the server)" : err;
            tell("\u00a7c[VexBot GUI] " + n + " (" + state + ") FAILED: " + (shared ? "(shared log, may belong to another bot) " : "") + why);
        } else if (!online) {
            tell("\u00a7e[VexBot GUI] " + n + " NOT CONFIRMED: never appeared in the player list; setup commands were sent anyway.");
        } else {
            tell("\u00a7a[VexBot GUI] " + n + " is ONLINE (confirmed)  \u2022  setup sent: " + tag + "  \u2022  no server error seen");
        }
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

    // ---------------- teams this client knows about ----------------
    /** Teams built or used from this client during this server session (name -> time added). */
    static final Map<String, Long> sessionTeams = new LinkedHashMap<>();
    /** Expected bot names for teams built by this GUI, keyed case-insensitively. */
    static final Map<String, List<String>> teamExpectedBots = new LinkedHashMap<>();
    /** Active team follow targets are replayed when a late bot joins the team. */
    static final Map<String, String> teamFollowTargets = new LinkedHashMap<>();
    /** Last result of "/vexbot team list" (empty until a refresh worked). */
    static List<TeamListParser.TeamInfo> serverTeams = List.of();
    static long serverTeamsAt = 0;
    /** Why the last refresh gave nothing ("" = fine). */
    static String teamsNote = "";
    static boolean teamsRefreshing = false;
    /** True when the newest refresh failed: serverTeams then shows OLD data and must not look current. */
    static boolean teamsStale = false;
    private static boolean teamsAutoTried = false;
    /** The team the MANAGE page acts on. */
    static String selectedTeam = "";
    static String rosterTeam = "";
    static List<String> rosterLines = List.of();
    static boolean rosterLoading = false;
    /** True when the last members request got no usable reply. */
    static boolean rosterMissing = false;
    private static int rosterToken = 0;

    /** Server teams plus teams created here, sorted, no duplicates. */
    static List<String> teamNames() {
        TreeSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (TeamListParser.TeamInfo t : serverTeams) all.add(t.name());
        all.addAll(sessionTeams.keySet());
        return new ArrayList<>(all);
    }

    static TeamListParser.TeamInfo teamInfo(String name) {
        for (TeamListParser.TeamInfo t : serverTeams) if (t.name().equalsIgnoreCase(name)) return t;
        return null;
    }

    /** Keeps the selection valid; picks the first team when nothing (valid) is selected. */
    static void ensureSelection() {
        List<String> names = teamNames();
        for (String n : names) if (n.equalsIgnoreCase(selectedTeam)) { selectedTeam = n; return; }
        selectedTeam = names.isEmpty() ? "" : names.get(0);
    }

    static void cycleTeam() {
        List<String> names = teamNames();
        if (names.isEmpty()) { selectedTeam = ""; return; }
        int i = -1;
        for (int k = 0; k < names.size(); k++) if (names.get(k).equalsIgnoreCase(selectedTeam)) i = k;
        selectedTeam = names.get((i + 1) % names.size());
    }

    static boolean teamConfirmed(String name) { return teamInfo(name) != null; }

    /** Looks the teams up once per server session (when MANAGE is first shown); REFRESH can always ask again. */
    static void autoRefreshOnce() {
        if (teamsAutoTried || !VexBotGuiAddon.connected()) return;
        teamsAutoTried = true;
        refreshTeams(null);
    }

    static void refreshTeams() { refreshTeams(null); }

    /**
     * Asks the server for its team list and reads the reply (read-only command).
     * done runs after the reply was handled, also when it could not be read (then teamsStale is true).
     */
    static void refreshTeams(Runnable done) {
        if (!VexBotGuiAddon.connected()) return;
        if (teamsRefreshing) {
            if (done != null) VexBotGuiAddon.runLaterTask(() -> refreshTeams(done), 45);
            return;
        }
        teamsRefreshing = true;
        teamsNote = "";
        final int sess = session;
        VexBotGuiAddon.collectReplies(40, lines -> {
            teamsRefreshing = false;
            if (sess != session) return; // belongs to a previous server
            TeamListParser.Result r = TeamListParser.parse(lines);
            if (!r.recognized()) {
                teamsStale = true;
                teamsNote = lines.isEmpty() ? "no reply from the server (OP needed?)" : "reply not recognized";
            } else if (!TeamListParser.complete(r)) {
                teamsStale = true;
                teamsNote = "list incomplete (" + r.teams().size() + " of " + r.reported() + " teams recognized)";
            } else {
                serverTeams = r.teams();
                serverTeamsAt = System.currentTimeMillis();
                teamsStale = false;
                teamsNote = r.empty() ? "the server has no teams yet" : "";
                // forget teams that are gone (but not ones built in the last 30 s: their bots may still be joining)
                long now = System.currentTimeMillis();
                sessionTeams.entrySet().removeIf(e -> now - e.getValue() > 30_000 && teamInfo(e.getKey()) == null);
                ensureSelection();
            }
            if (done != null) done.run();
        });
        if (!VexBotGuiAddon.runCommand(Config.cmd("teamslist", null, null))) {
            teamsRefreshing = false;
            teamsNote = "command not sent";
        }
    }

    /** Asks for one team's roster and keeps the reply lines. A newer request replaces an older one. */
    static void fetchRoster(String team) { fetchRoster(team, null); }

    static void fetchRoster(String team, java.util.function.Consumer<List<String>> done) {
        if (!VexBotGuiAddon.connected() || !Validate.teamName(team)) return;
        final int token = ++rosterToken;
        rosterTeam = team;
        rosterLines = List.of();
        rosterLoading = true;
        rosterMissing = false;
        VexBotGuiAddon.collectReplies(40, lines -> {
            if (token != rosterToken) return; // the answer belongs to an older request
            rosterLoading = false;
            rosterLines = TeamListParser.rosterLines(lines);
            rosterMissing = rosterLines.isEmpty();
            if (done != null) done.accept(rosterLines);
        });
        if (!VexBotGuiAddon.runCommand(Config.cmd("teamlist2", null, team))) {
            rosterLoading = false;
            if (done != null) done.accept(List.of());
        }
    }

    /** Called after DELETE TEAM was sent: stop offering it until the next refresh says otherwise. */
    static void forgetTeam(String team) {
        sessionTeams.keySet().removeIf(n -> n.equalsIgnoreCase(team));
        teamExpectedBots.remove(team.toLowerCase(Locale.ROOT));
        teamFollowTargets.remove(team.toLowerCase(Locale.ROOT));
        serverTeams = serverTeams.stream().filter(t -> !t.name().equalsIgnoreCase(team)).toList();
        if (rosterTeam.equalsIgnoreCase(team)) { rosterTeam = ""; rosterLines = List.of(); }
        ensureSelection();
    }

    /** Sends the delete, hides the team, then asks the server again to learn whether it is really gone. */
    static void deleteTeam(String team) {
        if (!Validate.teamName(team)) return;
        if (!VexBotGuiAddon.runCommand(Config.cmd("teamdelete2", "", team, null))) return;
        forgetTeam(team);
        VexBotGuiAddon.runLaterTask(() -> refreshTeams(() -> {
            if (teamsStale) {
                tell("\u00a7e[VexBot GUI] Team " + team + ": delete sent, but the team list could not be read - NOT CONFIRMED.");
            } else if (teamInfo(team) != null) {
                tell("\u00a7c[VexBot GUI] Team " + team + " still exists on the server - delete FAILED (OP needed?).");
            } else {
                tell("\u00a7a[VexBot GUI] Team " + team + " is gone (confirmed by the server's team list).");
            }
        }), 30);
    }

    private static void verifyTeam(String team, int expected, List<String> expectedNames) {
        if (teamsStale) {
            tell("\u00a7e[VexBot GUI] Team " + team + ": built, but the team list could not be read - bots NOT CONFIRMED.");
            return;
        }
        TeamListParser.TeamInfo ti = teamInfo(team);
        if (ti == null) {
            tell("\u00a7c[VexBot GUI] Team " + team + " was not found on the server (OP needed, or the bots never joined).");
        } else if (ti.bots() < expected) {
            tell("\u00a7e[VexBot GUI] Team " + team + ": only " + ti.bots() + " of " + expected + " bots joined.");
        } else {
            fetchRoster(team, lines -> {
                List<String> missing = TeamRosterVerifier.missing(lines, expectedNames);
                if (lines.isEmpty()) {
                    tell("\u00a7e[VexBot GUI] Team " + team + ": count is " + ti.bots()
                            + ", but member names could not be read - roster NOT CONFIRMED.");
                } else if (missing.isEmpty()) {
                    tell("\u00a7a[VexBot GUI] Team " + team + " confirmed: all " + expected
                            + " expected bot names appear in its member roster.");
                } else {
                    tell("\u00a7e[VexBot GUI] Team " + team + ": " + (expected - missing.size())
                            + "/" + expected + " expected bot names found. Missing: " + String.join(", ", missing));
                }
            });
        }
    }

    /** Builds a team with staggered spawns through the verified VexBot 3.2.5 members-add path. */
    static void buildTeam(String team, int amount, int tierIdx) {
        if (!Validate.teamName(team)) return;
        lastTeam = team;
        sessionTeams.put(team, System.currentTimeMillis());
        selectedTeam = team;
        Tier tier = TIERS[tierIdx];
        Recipe recipe = Recipe.of(tier);
        List<String> expectedNames = new ArrayList<>();
        for (int i = 0; i < amount; i++) expectedNames.add(randomName());
        teamExpectedBots.put(team.toLowerCase(Locale.ROOT), List.copyOf(expectedNames));
        teamFollowTargets.remove(team.toLowerCase(Locale.ROOT));

        // Do not issue "vexbot team create <name>" here: VexBot 3.2.5 opens
        // its interactive Team Builder GUI for a player source. The first
        // "team <name> members add <bot>" creates the team server-side.
        for (int i = 0; i < expectedNames.size(); i++) {
            String botName = expectedNames.get(i);
            VexBotGuiAddon.runLaterTask(() -> deploy(botName, recipe, team, tier.describe()), 1 + i * 12);
        }
        // Verify after the normal spawn/configuration window; this is a request,
        // not a success claim. The roster check reports missing members explicitly.
        VexBotGuiAddon.runLaterTask(() -> refreshTeams(() -> verifyTeam(team, amount, List.copyOf(expectedNames))),
                12 + amount * 12 + 100);
    }

    /** Re-adds this GUI-built team's currently online expected bots, then issues one team-wide follow order. */
    static void followTeam(String team, String target) {
        if (!Validate.teamName(team) || target == null || target.isBlank()) return;
        teamFollowTargets.put(team.toLowerCase(Locale.ROOT), target);
        List<String> expected = teamExpectedBots.getOrDefault(team.toLowerCase(Locale.ROOT), List.of());
        TeamFollowPlan.Plan plan = TeamFollowPlan.partition(expected, VexBotGuiAddon::botOnline);
        int delay = 1;
        for (String botName : plan.online()) {
            // Idempotent: TeamManager stores members in a set. This repairs missed/late joins
            // without adding unrelated bots that happen to exist on the server.
            VexBotGuiAddon.runLater(Config.cmd("teamadd2", botName, team), delay++);
        }
        if (!plan.missing().isEmpty()) {
            tell("§e[VexBot GUI] Team " + team + ": not visible in player list: " + String.join(", ", plan.missing()));
        }
        final int followDelay = delay + 2;
        VexBotGuiAddon.runLaterTask(() -> {
            VexBotGuiAddon.collectReplies(30, lines -> {
                if (lines == null || lines.isEmpty()) {
                    tell("§e[VexBot GUI] Team follow was SENT, but the server gave no reply - NOT CONFIRMED.");
                } else {
                    tell("§b[VexBot GUI] Team follow server reply: " + String.join(" | ", lines));
                }
            });
            if (!VexBotGuiAddon.runCommand(Config.cmd("teamfollow2", "", team, target))) {
                tell("§c[VexBot GUI] Team follow was NOT SENT (no active connection).");
            }
        }, followDelay);
        tell("§7[VexBot GUI] Team follow QUEUED for " + plan.online().size()
                + " expected online member(s). The server reply will determine the result.");
    }


    /** Stops the team-wide follow order and prevents late joins from inheriting it. */
    static void stopTeamFollow(String team) {
        if (!Validate.teamName(team)) return;
        teamFollowTargets.remove(team.toLowerCase(Locale.ROOT));
        VexBotGuiAddon.collectReplies(25, lines -> {
            if (lines == null || lines.isEmpty()) tell("§e[VexBot GUI] Team follow-stop SENT, but no server reply was received.");
            else tell("§b[VexBot GUI] Team follow-stop server reply: " + String.join(" | ", lines));
        });
        if (!VexBotGuiAddon.runCommand(Config.cmd("teamfollowstop2", "", team, null))) {
            tell("§c[VexBot GUI] Team follow-stop was NOT SENT (no active connection).");
        }
    }

    /** Vanilla /item replace (needs operator permission) using the current LOADOUT selection. */
    static void equip(String n, int d) { equipSteps(n, d, armor, weapon, offhand); }

    /** One command per tick so nothing is dropped. Returns the next free tick. */
    static int equipSteps(String n, int d, int a, int w, int o) {
        if (!ARMORS[a].equals("none")) {
            if (ARMORS[a].equals("elytra")) {
                VexBotGuiAddon.runLater("item replace entity " + n + " armor.chest with minecraft:elytra", d++);
            } else {
                String[][] slots = {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}};
                for (String[] s : slots) {
                    VexBotGuiAddon.runLater("item replace entity " + n + " armor." + s[0]
                            + " with minecraft:" + ARMORS[a] + "_" + s[1], d++);
                }
            }
        }
        if (!WEAPONS[w].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.mainhand with minecraft:" + WEAPONS[w], d++);
        }
        if (!OFFHANDS[o].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.offhand with minecraft:" + OFFHANDS[o], d++);
        }
        // Ammunition / consumables required by the selected equipment. These are
        // separate inventory grants: they do not replace the weapon or offhand.
        for (LoadoutSupplies.Supply supply : LoadoutSupplies.forLoadout(ARMORS[a], WEAPONS[w])) {
            VexBotGuiAddon.runLater("give " + n + " minecraft:" + supply.item() + " " + supply.count(), d++);
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
            new Tier("medium",  "chainmail", "stone_sword",     "none",             "safe"),
            new Tier("hard",    "iron",      "iron_sword",     "shield",           "safe"),
            new Tier("expert",  "diamond",   "diamond_sword",   "shield",           "safe"),
            new Tier("perfect", "netherite", "netherite_sword", "totem_of_undying", "safe"),
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
    private static final Confirm massConfirm = new Confirm(CONFIRM_MS);
    private static final Confirm teamConfirm = new Confirm(CONFIRM_MS);
    private static final Confirm fastConfirm = new Confirm(CONFIRM_MS);
    private static final Confirm kitConfirm = new Confirm(CONFIRM_MS);
    /** Changes on every join/leave so a pending confirmation can never carry over to another server. */
    private static int session = 0;

    static void resetConfirm() { massConfirm.reset(); teamConfirm.reset(); fastConfirm.reset(); kitConfirm.reset(); }

    // ---------------- VexBot's own kits (createkit / givekit / ...) ----------------
    static List<KitListParser.KitInfo> serverKits = List.of();
    static long serverKitsAt = 0;
    /** True when the newest kit-list read failed: serverKits then shows OLD data. */
    static boolean kitsStale = false;
    static boolean kitsRefreshing = false;
    static String kitsNote = "";
    static String selectedKit = "";
    private static boolean kitsAutoTried = false;

    static List<String> kitNames() {
        List<String> out = new ArrayList<>();
        for (KitListParser.KitInfo k : serverKits) out.add(k.name());
        return out;
    }

    static KitListParser.KitInfo kitInfo(String name) {
        for (KitListParser.KitInfo k : serverKits) if (k.name().equalsIgnoreCase(name)) return k;
        return null;
    }

    static void ensureKitSelection() {
        KitListParser.KitInfo k = kitInfo(selectedKit);
        selectedKit = k != null ? k.name() : (serverKits.isEmpty() ? "" : serverKits.get(0).name());
    }

    static void cycleKit() {
        List<String> names = kitNames();
        if (names.isEmpty()) { selectedKit = ""; return; }
        int i = -1;
        for (int n = 0; n < names.size(); n++) if (names.get(n).equalsIgnoreCase(selectedKit)) i = n;
        selectedKit = names.get((i + 1) % names.size());
    }

    /** Reads the kit list once per server session; REFRESH can always ask again. */
    static void autoRefreshKitsOnce() {
        if (kitsAutoTried || !VexBotGuiAddon.connected()) return;
        kitsAutoTried = true;
        refreshKits(null);
    }

    /** done runs after the reply was handled, also when it could not be read (then kitsStale is true). */
    static void refreshKits(Runnable done) {
        if (!VexBotGuiAddon.connected()) return;
        if (kitsRefreshing) {
            if (done != null) VexBotGuiAddon.runLaterTask(() -> refreshKits(done), 45);
            return;
        }
        kitsRefreshing = true;
        kitsNote = "";
        final int sess = session;
        VexBotGuiAddon.collectReplies(40, lines -> {
            kitsRefreshing = false;
            if (sess != session) return; // belongs to a previous server
            KitListParser.Result r = KitListParser.parse(lines);
            if (!r.recognized()) {
                kitsStale = true;
                kitsNote = lines.isEmpty() ? "no reply from the server (OP needed?)" : "reply not recognized";
            } else {
                serverKits = r.kits();
                serverKitsAt = System.currentTimeMillis();
                kitsStale = false;
                kitsNote = r.empty() ? "no kits saved yet - use SAVE MY INVENTORY" : "";
                ensureKitSelection();
            }
            if (done != null) done.run();
        });
        if (!VexBotGuiAddon.runCommand(Config.cmd("kitlist", null, null))) {
            kitsRefreshing = false;
            kitsNote = "command not sent";
        }
    }

    static boolean confirmKitAction(String what, String kit) {
        return kitConfirm.ask(session + "|" + what + "|" + kit, System.currentTimeMillis());
    }

    static boolean kitConfirmPending(String what, String kit) {
        return kitConfirm.pending(session + "|" + what + "|" + kit, System.currentTimeMillis());
    }

    /** createkit / updatekit take the items of YOUR inventory. The result is verified in the kit list. */
    static void saveKit(String name, boolean update) {
        if (!Validate.kitName(name)) return;
        KitListParser.KitInfo old = kitInfo(name);
        final int before = old == null ? -1 : old.items();
        if (!VexBotGuiAddon.runCommand(Config.cmd(update ? "kitupdate" : "kitcreate", null, name))) return;
        VexBotGuiAddon.runLaterTask(() -> refreshKits(() -> verifyKit(name, update, before)), 20);
    }

    private static void verifyKit(String name, boolean update, int before) {
        if (kitsStale) {
            tell("\u00a7e[VexBot GUI] Kit " + name + ": command sent, but the kit list could not be read - NOT CONFIRMED.");
            return;
        }
        KitListParser.KitInfo ki = kitInfo(name);
        if (ki == null) {
            tell("\u00a7c[VexBot GUI] Kit " + name + " was not found after saving (it needs a player source and OP).");
        } else if (update && before >= 0 && ki.items() == before) {
            tell("\u00a7e[VexBot GUI] Kit " + name + " is listed with " + ki.items() + " items, the same count as before (other changes cannot be seen).");
        } else {
            tell("\u00a7a[VexBot GUI] Kit " + name + " is saved (confirmed in the kit list, " + ki.items() + " items).");
            selectedKit = ki.name();
        }
    }

    static void deleteKit(String name) {
        if (name == null || name.isBlank()) return;
        if (!VexBotGuiAddon.runCommand(Config.cmd("kitdelete", null, name))) return;
        VexBotGuiAddon.runLaterTask(() -> refreshKits(() -> {
            if (kitsStale) {
                tell("\u00a7e[VexBot GUI] Kit " + name + ": delete sent, but the kit list could not be read - NOT CONFIRMED.");
            } else if (kitInfo(name) != null) {
                tell("\u00a7c[VexBot GUI] Kit " + name + " is still listed - delete FAILED (OP needed?).");
            } else {
                tell("\u00a7a[VexBot GUI] Kit " + name + " is deleted (confirmed by the kit list).");
            }
        }), 20);
    }

    // ---------------- VexBot's native mass_spawn (random names, VexBot defaults) ----------------
    static boolean confirmFastSpawn(int amount, boolean randomGear) {
        if (amount <= 1) return true;
        return fastConfirm.ask(session + "|fast|" + amount + "|" + randomGear, System.currentTimeMillis());
    }

    static boolean fastSpawnPending(int amount, boolean randomGear) {
        return amount > 1 && fastConfirm.pending(session + "|fast|" + amount + "|" + randomGear, System.currentTimeMillis());
    }

    /** Forget everything that belongs to the previous server/world. */
    static void resetSession() {
        session++;
        lastBot = "";
        lastTeam = "";
        usedNames.clear();
        resetConfirm();
        serverKits = List.of();
        serverKitsAt = 0;
        kitsStale = false;
        kitsRefreshing = false;
        kitsNote = "";
        selectedKit = "";
        kitsAutoTried = false;
        sessionTeams.clear();
        teamExpectedBots.clear();
        teamFollowTargets.clear();
        serverTeams = List.of();
        serverTeamsAt = 0;
        teamsNote = "";
        teamsRefreshing = false;
        teamsStale = false;
        teamsAutoTried = false;
        selectedTeam = "";
        rosterTeam = "";
        rosterLines = List.of();
        rosterLoading = false;
        rosterMissing = false;
        rosterToken++;
        activeDeploys = 0;
    }

    /** Name must be empty (random) or a valid player-style name. */
    static boolean nameOk(String n) { return n == null || n.isBlank() || Validate.botName(n.trim()); }

    /** First call returns false and starts a 5 s window; a second call with the same settings inside it returns true. */
    static boolean confirmMassSpawn(String base) {
        if (amount() <= 1) return true;
        return massConfirm.ask(massKey(base), System.currentTimeMillis());
    }

    private static String massKey(String base) {
        return session + "|" + base + "|" + summary() + "|" + offhand + "|" + style;
    }

    static int amount() { return Integer.parseInt(COUNTS[count]); }

    /** True while a mass-spawn confirmation for exactly these settings is waiting. */
    static boolean massConfirmPending(String base) {
        return amount() > 1 && massConfirm.pending(massKey(base), System.currentTimeMillis());
    }

    static boolean teamConfirmPending(String team, int amount, int level) {
        return amount > 3 && teamConfirm.pending(session + "|" + team + "|" + amount + "|" + level, System.currentTimeMillis());
    }

    /** Same idea for BUILD TEAM (more than 3 bots): bound to team name, size, level and server session. */
    static boolean confirmTeamBuild(String team, int amount, int level) {
        if (amount <= 3) return true;
        return teamConfirm.ask(session + "|" + team + "|" + amount + "|" + level, System.currentTimeMillis());
    }

    static boolean confirmPending() { return massConfirm.anyPending(System.currentTimeMillis()); }

    static void quickSpawn() {
        if (!VexBotGuiAddon.connected()) { tell("\u00a7c[VexBot GUI] No server connection."); return; }
        String base = name.trim();
        if (!nameOk(base)) { tell("\u00a7c[VexBot GUI] Invalid bot name (1-16 letters, digits or _)."); return; }
        if (!confirmMassSpawn(base)) {
            tell("\u00a7e[VexBot GUI] Press N again within 5 s to spawn " + COUNTS[count] + " bots.");
            return;
        }
        spawn(base);
        tell("VexBot GUI: quick spawn QUEUED  " + summary());
    }

    /** Your own player name (used for follow / attack / goto / look targets). */
    static String me() { return Minecraft.getInstance().getUser().getName(); }

    static void tell(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), false);
    }
}
