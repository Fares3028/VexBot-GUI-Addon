package com.fares.vexbotgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Editable command templates + saved presets (JSON files in the config folder). */
final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("spawn", "vexbot spawn {bot}");
        DEFAULTS.put("kill", "vexbot kill {bot}");
        DEFAULTS.put("list", "vexbot list");
        DEFAULTS.put("difficulty", "vexbot difficulty preset {arg}");
        DEFAULTS.put("randomgear", "vexbot randomgear");
        DEFAULTS.put("playstyle", "vexbot playstyle {bot} {arg}");
        DEFAULTS.put("randomplaystyle", "vexbot randomplaystyle");
        DEFAULTS.put("follow", "vexbot follow {bot}");
        DEFAULTS.put("attack", "vexbot attack {bot}");
        DEFAULTS.put("stop", "vexbot stop {bot}");
        DEFAULTS.put("hide", "vexbot hide {bot}");
        DEFAULTS.put("wander", "vexbot wander {bot}");
        DEFAULTS.put("tp", "vexbot tp {bot}");
        DEFAULTS.put("settings", "vexbot settings");
        DEFAULTS.put("help", "vexbot help");
    }

    private static final Map<String, String> commands = new LinkedHashMap<>(DEFAULTS);
    static final Map<String, int[]> presets = new LinkedHashMap<>();

    private static Path commandsFile() { return FabricLoader.getInstance().getConfigDir().resolve("vexbot_gui_commands.json"); }
    private static Path presetsFile() { return FabricLoader.getInstance().getConfigDir().resolve("vexbot_gui_presets.json"); }

    static void load() {
        reloadCommands();
        loadPresets();
    }

    /** Returns a short message describing the result. */
    static String reloadCommands() {
        Path p = commandsFile();
        try {
            if (!Files.exists(p)) {
                Files.writeString(p, GSON.toJson(DEFAULTS));
                commands.clear();
                commands.putAll(DEFAULTS);
                return "Created " + p.getFileName();
            }
            Type t = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(Files.readString(p), t);
            commands.clear();
            commands.putAll(DEFAULTS);
            if (loaded != null) commands.putAll(loaded);
            return "Commands reloaded (" + p.getFileName() + ")";
        } catch (Exception e) {
            commands.clear();
            commands.putAll(DEFAULTS);
            return "Command file error, using defaults";
        }
    }

    /** {bot} and {arg} are replaced. */
    static String cmd(String key, String bot, String arg) {
        String t = commands.getOrDefault(key, DEFAULTS.getOrDefault(key, ""));
        return t.replace("{bot}", bot == null ? "" : bot).replace("{arg}", arg == null ? "" : arg);
    }

    static void loadPresets() {
        presets.clear();
        Path p = presetsFile();
        try {
            if (!Files.exists(p)) return;
            Type t = new TypeToken<LinkedHashMap<String, int[]>>() {}.getType();
            Map<String, int[]> loaded = GSON.fromJson(Files.readString(p), t);
            if (loaded != null) {
                for (Map.Entry<String, int[]> e : loaded.entrySet()) {
                    if (e.getValue() != null && e.getValue().length == 6) presets.put(e.getKey(), e.getValue());
                }
            }
        } catch (Exception ignored) { }
    }

    static void savePresets() {
        try {
            Files.writeString(presetsFile(), GSON.toJson(presets));
        } catch (IOException ignored) { }
    }
}
