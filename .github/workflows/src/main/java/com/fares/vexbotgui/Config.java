package com.fares.vexbotgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editable command templates + saved presets (JSON files in the config folder).
 * Placeholders: {bot}, {arg}, {arg2}. Syntax verified against VexBot 3.2.0.
 */
final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Bump when DEFAULTS change incompatibly; an older commands file is backed up and reset. */
    private static final String CMD_VERSION = "2";
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
        DEFAULTS.put("teamadd", "vexbot teammembers {arg} add {bot}");
        DEFAULTS.put("teamremove", "vexbot teammembers {arg} remove {bot}");
        DEFAULTS.put("teamlist", "vexbot teammembers {arg} list");
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

    static void load() {
        reloadCommands();
        loadPresets();
    }

    private static void writeDefaults(Path p) throws IOException {
        Files.writeString(p, GSON.toJson(DEFAULTS));
        commands.clear();
        commands.putAll(DEFAULTS);
    }

    /** Returns a short message describing the result. */
    static String reloadCommands() {
        Path p = commandsFile();
        try {
            if (!Files.exists(p)) {
                writeDefaults(p);
                return "Created " + p.getFileName();
            }
            Type t = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(Files.readString(p), t);
            if (loaded == null || !CMD_VERSION.equals(loaded.get("_v"))) {
                Files.move(p, p.resolveSibling(p.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
                writeDefaults(p);
                return "Outdated commands file reset (old one kept as .old)";
            }
            commands.clear();
            commands.putAll(DEFAULTS);
            commands.putAll(loaded);
            return "Commands reloaded (" + p.getFileName() + ")";
        } catch (Exception e) {
            commands.clear();
            commands.putAll(DEFAULTS);
            return "Command file error, using defaults";
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
        presets.clear();
        Path p = presetsFile();
        try {
            if (!Files.exists(p)) return;
            Type t = new TypeToken<LinkedHashMap<String, int[]>>() {}.getType();
            Map<String, int[]> loaded = GSON.fromJson(Files.readString(p), t);
            if (loaded == null) return;
            boolean migrated = loaded.containsKey(PRESET_MARK);
            for (Map.Entry<String, int[]> e : loaded.entrySet()) {
                int[] v = e.getValue();
                if (PRESET_MARK.equals(e.getKey()) || v == null || v.length != 6) continue;
                if (!migrated) {
                    v = v.clone();
                    // old lists had "pro" (difficulty idx 4) and "passive" (style idx 2) which VexBot does not have
                    if (v[1] >= 4) v[1] -= 1;
                    if (v[5] == 2) v[5] = 1; else if (v[5] > 2) v[5] -= 1;
                }
                presets.put(e.getKey(), v);
            }
            if (!migrated) savePresets();
        } catch (Exception ignored) { }
    }

    static void savePresets() {
        try {
            Map<String, int[]> out = new LinkedHashMap<>(presets);
            out.put(PRESET_MARK, new int[]{2});
            Files.writeString(presetsFile(), GSON.toJson(out));
        } catch (IOException ignored) { }
    }
}
