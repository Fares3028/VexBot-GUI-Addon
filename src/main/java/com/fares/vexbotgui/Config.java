package com.fares.vexbotgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editable command templates + saved presets (JSON files in the config folder).
 * Placeholders: {bot}, {arg}, {arg2}. Syntax verified against VexBot 3.2.0.
 */
final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Bump when DEFAULTS change incompatibly; an older commands file is backed up and reset. */
    private static final String CMD_VERSION = "3";
    private static final String PRESET_MARK = "__v2";
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("_v", CMD_VERSION);
        // --- basics
        DEFAULTS.put("spawn", "vexbot spawn {bot}");
        DEFAULTS.put("spawnkit", "vexbot spawn {bot} ~ ~ ~ {arg}");
        DEFAULTS.put("kill", "vexbot kill {bot}");
        DEFAULTS.put("list", "vexbot list");
        // --- difficulty / playstyle / gear
        DEFAULTS.put("difficulty", "vexbot difficulty player {bot} preset {arg}");
        DEFAULTS.put("difficultyall", "vexbot difficulty preset {arg}");
        DEFAULTS.put("difficultylist", "vexbot difficulty list");
        DEFAULTS.put("playstyle", "vexbot playstyle {arg} {bot}");
        DEFAULTS.put("randomplaystyle", "vexbot randomplaystyle {bot}");
        DEFAULTS.put("randomgear", "vexbot randomgear {bot}");
        // --- orders ({arg} = target player name where needed)
        DEFAULTS.put("follow", "vexbot follow {bot} {arg}");
        DEFAULTS.put("followstop", "vexbot follow {bot} stop");
        DEFAULTS.put("attack", "vexbot attack {arg} {bot}");
        DEFAULTS.put("attackstop", "vexbot attack stop {bot}");
        DEFAULTS.put("hide", "vexbot hide {bot}");
        DEFAULTS.put("hidestop", "vexbot hide {bot} stop");
        DEFAULTS.put("wander", "vexbot wander {bot} {arg}");
        DEFAULTS.put("wanderstop", "vexbot wander {bot} stop");
        DEFAULTS.put("goto", "vexbot goto {bot} {arg}");
        DEFAULTS.put("gotostop", "vexbot goto {bot} stop");
        DEFAULTS.put("look", "vexbot look {bot} {arg}");
        DEFAULTS.put("lookstop", "vexbot look {bot} stop");
        // --- kits
        DEFAULTS.put("givekit", "vexbot givekit {arg} {bot}");
        DEFAULTS.put("kitlist", "vexbot givekit list");
        // --- teams ({arg} = team name, {arg2} = extra)
        DEFAULTS.put("teamcreate", "vexbot teamcreate {arg}");
        DEFAULTS.put("teamdelete", "vexbot teamdelete {arg} confirm");
        DEFAULTS.put("teamadd", "vexbot teamcreate {arg} addbot {bot}");
        DEFAULTS.put("teamremove", "vexbot teamcreate {arg} removebot {bot}");
        DEFAULTS.put("teamslist", "vexbot team list");
        DEFAULTS.put("teamlist", "vexbot teamcreate {arg} list");
        DEFAULTS.put("teamspawn", "vexbot teamspawn {arg} {bot}");
        DEFAULTS.put("teamfollow", "vexbot teamfollow {arg} {arg2}");
        DEFAULTS.put("teamfollowstop", "vexbot teamfollow {arg} stop");
        DEFAULTS.put("teamdifficulty", "vexbot teamdifficulty {arg} preset {arg2}");
        DEFAULTS.put("teamplaystyle", "vexbot teamplaystyle {arg} {arg2}");
        // --- server settings ({arg} = "<key> <on|off>" or "<key>")
        DEFAULTS.put("settings", "vexbot settings");
        DEFAULTS.put("settingslist", "vexbot settings list");
        DEFAULTS.put("settingset", "vexbot settings {arg}");
        DEFAULTS.put("settingshow", "vexbot settings {arg}");
    }

    private static final Map<String, String> commands = new LinkedHashMap<>(DEFAULTS);
    static final Map<String, int[]> presets = new LinkedHashMap<>();

    private static Path commandsFile() { return FabricLoader.getInstance().getConfigDir().resolve("vexbot_gui_commands.json"); }
    private static Path presetsFile() { return FabricLoader.getInstance().getConfigDir().resolve("vexbot_gui_presets.json"); }

    /** Problems while reading the commands file. Cleared on every reload. */
    static final List<String> commandWarnings = new ArrayList<>();
    /** Problems while reading the presets file. Cleared on every load. */
    static final List<String> presetWarnings = new ArrayList<>();
    /** Last failure while saving presets (null = the last save worked). Independent of the load warnings. */
    static String saveError = null;

    private static final int[] PRESET_SIZES = {
            Actions.COUNTS.length, Actions.DIFFICULTIES.length, Actions.ARMORS.length,
            Actions.WEAPONS.length, Actions.OFFHANDS.length, Actions.STYLES.length};

    /** All current problems, most important first. */
    static List<String> allProblems() {
        List<String> all = new ArrayList<>();
        if (saveError != null) all.add(saveError);
        all.addAll(commandWarnings);
        all.addAll(presetWarnings);
        return all;
    }

    /** One line for the status bar, or null when everything is fine. */
    static String problemSummary() {
        List<String> all = allProblems();
        if (all.isEmpty()) return null;
        return all.size() == 1 ? all.get(0) : all.get(0) + "  (+" + (all.size() - 1) + " more)";
    }

    static void load() {
        reloadCommands();
        loadPresets();
        for (String p : allProblems()) System.err.println("[VexBot GUI] " + p);
    }

    private static void writeDefaults(Path p) throws IOException {
        Files.writeString(p, GSON.toJson(DEFAULTS));
        commands.clear();
        commands.putAll(DEFAULTS);
    }

    /** Keeps the unusable file under a dated name and starts again from the defaults. */
    private static String resetCommands(Path p, String why, boolean warn) throws IOException {
        Path kept = FileBackup.moveAside(p, warn ? "bad" : "old");
        writeDefaults(p);
        String msg = why + " (old file kept as " + kept.getFileName() + ")";
        commandWarnings.add(msg); // never silent: the user may have edited that file
        return msg;
    }

    /** Returns a short message describing the result. */
    static String reloadCommands() {
        commandWarnings.clear();
        Path p = commandsFile();
        try {
            if (!Files.exists(p)) {
                writeDefaults(p);
                return "Created " + p.getFileName();
            }
            Type t = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> loaded;
            try {
                loaded = GSON.fromJson(Files.readString(p), t);
            } catch (JsonParseException e) {
                return resetCommands(p, "Commands file was not valid JSON - defaults restored", true);
            }
            if (loaded == null || !CMD_VERSION.equals(loaded.get("_v"))) {
                return resetCommands(p, "Outdated commands file reset", false);
            }
            commands.clear();
            commands.putAll(DEFAULTS);
            List<String> bad = new ArrayList<>();
            for (String key : DEFAULTS.keySet()) {
                if (key.equals("_v") || !loaded.containsKey(key)) continue;
                String clean = Validate.template(loaded.get(key)); // null/empty/too long -> rejected
                if (clean == null) bad.add(key); else commands.put(key, clean);
            }
            if (!bad.isEmpty()) {
                String msg = "Invalid command entries ignored: " + String.join(", ", bad);
                commandWarnings.add(msg);
                return msg;
            }
            return "Commands reloaded (" + p.getFileName() + ")";
        } catch (Exception e) {
            commands.clear();
            commands.putAll(DEFAULTS);
            String msg = "Cannot read commands file (" + e.getClass().getSimpleName() + "), using defaults";
            commandWarnings.add(msg);
            return msg;
        }
    }

    /** {bot}, {arg} and {arg2} are replaced. */
    static String cmd(String key, String bot, String arg) { return cmd(key, bot, arg, null); }

    static String cmd(String key, String bot, String arg, String arg2) {
        String t = commands.getOrDefault(key, DEFAULTS.getOrDefault(key, ""));
        return t.replace("{bot}", bot == null ? "" : bot)
                .replace("{arg2}", arg2 == null ? "" : arg2)
                .replace("{arg}", arg == null ? "" : arg)
                .replaceAll("\\s+", " ").trim();
    }

    static void loadPresets() {
        presetWarnings.clear();
        presets.clear();
        Path p = presetsFile();
        try {
            if (!Files.exists(p)) return;
            Type t = new TypeToken<LinkedHashMap<String, int[]>>() {}.getType();
            Map<String, int[]> loaded;
            try {
                loaded = GSON.fromJson(Files.readString(p), t);
            } catch (JsonParseException e) {
                // keep the broken file: the next save would otherwise overwrite it
                Path kept = FileBackup.moveAside(p, "bad");
                presetWarnings.add("Presets file was not valid JSON (kept as " + kept.getFileName() + ")");
                return;
            }
            if (loaded == null) return;
            boolean migrated = loaded.containsKey(PRESET_MARK);
            int invalid = 0;
            for (Map.Entry<String, int[]> e : loaded.entrySet()) {
                if (PRESET_MARK.equals(e.getKey())) continue;
                int[] v = e.getValue();
                if (v != null && v.length == 6 && !migrated) v = PresetMigration.fromV1(v);
                if (!Validate.presetName(e.getKey()) || !Validate.presetValues(v, PRESET_SIZES)) {
                    if (++invalid <= 3) presetWarnings.add("Invalid preset skipped: " + e.getKey());
                    continue;
                }
                if (presets.size() >= Validate.MAX_PRESETS) {
                    presetWarnings.add("Preset limit (" + Validate.MAX_PRESETS + ") reached; extra presets ignored");
                    break;
                }
                presets.put(e.getKey(), v);
            }
            if (invalid > 3) presetWarnings.add((invalid - 3) + " more invalid presets skipped");
            boolean skipped = invalid > 0 || presetWarnings.stream().anyMatch(w -> w.startsWith("Preset limit"));
            if (!migrated || skipped) keepOriginalPresets(p, !migrated);
            if (!migrated) savePresets();
        } catch (Exception e) {
            presetWarnings.add("Cannot read presets file (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Before anything is rewritten or dropped, the original file is kept once (same content = no new copy). */
    private static void keepOriginalPresets(Path p, boolean migrating) {
        Path bak = p.resolveSibling(p.getFileName() + (migrating ? ".pre-migration.bak" : ".skipped.bak"));
        try {
            if (FileBackup.copyIfDifferent(p, bak)) {
                presetWarnings.add("Original presets file kept as " + bak.getFileName());
            }
        } catch (IOException e) {
            presetWarnings.add("Could not back up the presets file: " + e.getMessage());
        }
    }

    static void savePresets() {
        // rolling backup of the previous version; a failed backup is a warning, not a failed save
        try {
            Path current = presetsFile();
            if (Files.exists(current)) FileBackup.copyIfDifferent(current, current.resolveSibling(current.getFileName() + ".bak"));
        } catch (IOException e) {
            String w = "Could not write the presets backup: " + e.getMessage();
            if (!presetWarnings.contains(w)) presetWarnings.add(w);
        }
        try {
            Map<String, int[]> out = new LinkedHashMap<>(presets);
            out.put(PRESET_MARK, new int[]{2});
            Path target = presetsFile();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(out));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            saveError = null;
        } catch (IOException e) {
            saveError = "Cannot save presets: " + e.getMessage();
        }
    }
}
