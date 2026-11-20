package com.fares.vexbotgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VexBotScreen extends Screen {
    private static final Identifier MASCOT = Identifier.fromNamespaceAndPath("vexbot_gui", "textures/gui/mascot.png");
    private static final String[] TABS = {"SPAWN", "LOADOUT", "COMBAT", "PRESETS", "CONSOLE"};

    private static int tab = 0, presetIdx = 0;
    private static String presetText = "", commandText = "";

    private EditBox botName, input;
    private String status = "READY  \u2022  Enter a bot name to begin";
    private int panelX, panelY, panelW, panelH, contentX, contentW, contentY, columnW;
    private boolean confirmMass, confirmKill;

    public VexBotScreen() { super(Component.literal("VexBot Control Center")); }

    @Override
    protected void init() {
        clearWidgets();
        panelW = Math.max(300, Math.min(610, width - 24));
        panelH = Math.max(250, Math.min(410, height - 24));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int pad = panelW < 420 ? 14 : 20;
        contentX = panelX + pad;
        contentW = panelW - pad * 2;
        contentY = panelY + 126;
        columnW = (contentW - 12) / 2;

        int gap = 5, tabW = (contentW - gap * (TABS.length - 1)) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button t = Button.builder(Component.literal(TABS[i]), b -> {
                tab = idx; confirmMass = false; confirmKill = false; rebuildWidgets();
            }).bounds(contentX + i * (tabW + gap), panelY + 91, tabW, 22).build();
            t.active = i != tab;
            addRenderableWidget(t);
        }

        botName = new EditBox(font, contentX, panelY + 51, contentW, 20, Component.literal("Bot name"));
        botName.setMaxLength(16);
        botName.setHint(Component.literal("Enter bot name  \u2022  e.g. TrainingBot"));
        botName.setValue(Actions.name);
        botName.setResponder(v -> Actions.name = v);
        addRenderableWidget(botName);

        int y = contentY, right = contentX + columnW + 12;
        switch (tab) {
            case 0 -> buildSpawn(y, right);
            case 1 -> buildLoadout(y, right);
            case 2 -> buildCombat(y, right);
            case 3 -> buildPresets(y, right);
            default -> buildConsole(y, right);
        }
    }

    // ---------------- pages ----------------
    private void buildSpawn(int y, int right) {
        cycle(contentX, y, columnW, "BOT COUNT", Actions.COUNTS, () -> Actions.count, v -> Actions.count = v);
        cycle(right, y, columnW, "DIFFICULTY", Actions.DIFFICULTIES, () -> Actions.difficulty, v -> Actions.difficulty = v);
        action(contentX, y + 48, columnW, "SPAWN BOT(S)", this::spawn);
        action(right, y + 48, columnW, confirmKill ? "CONFIRM KILL" : "KILL BOT", () -> {
            if (!confirmKill) { confirmKill = true; status = "CONFIRM  \u2022  Press again to kill"; rebuildWidgets(); return; }
            confirmKill = false;
            withBot("kill", null, "KILL SENT");
            rebuildWidgets();
        });
        action(contentX, y + 90, columnW, "LIST BOTS", () -> sendKey("list", "LIST REQUESTED"));
        action(right, y + 90, columnW, "RANDOM GEAR", () -> sendKey("randomgear", "RANDOM GEAR SENT"));
    }

    private void buildLoadout(int y, int right) {
        cycle(contentX, y, columnW, "ARMOR SET", Actions.ARMORS, () -> Actions.armor, v -> Actions.armor = v);
        cycle(right, y, columnW, "MAIN-HAND", Actions.WEAPONS, () -> Actions.weapon, v -> Actions.weapon = v);
        cycle(contentX, y + 48, columnW, "OFF-HAND", Actions.OFFHANDS, () -> Actions.offhand, v -> Actions.offhand = v);
        action(right, y + 48, columnW, "RANDOM GEAR", () -> sendKey("randomgear", "RANDOM GEAR SENT"));
        action(contentX, y + 96, contentW, "APPLY EQUIPMENT TO NAMED BOT", () -> {
            String n = bot(); if (n == null) return;
            Actions.equip(n, 1);
            status = "QUEUED  \u2022  Equipment for " + n;
        });
    }

    private void buildCombat(int y, int right) {
        cycle(contentX, y, columnW, "DIFFICULTY", Actions.DIFFICULTIES, () -> Actions.difficulty, v -> Actions.difficulty = v);
        cycle(right, y, columnW, "PLAYSTYLE", Actions.STYLES, () -> Actions.style, v -> Actions.style = v);
        action(contentX, y + 48, columnW, "APPLY DIFFICULTY", () ->
                sendRaw(Config.cmd("difficulty", bot0(), Actions.DIFFICULTIES[Actions.difficulty]), "DIFFICULTY SENT"));
        action(right, y + 48, columnW, "APPLY PLAYSTYLE", () ->
                withBot("playstyle", Actions.STYLES[Actions.style], "PLAYSTYLE SENT"));
        action(contentX, y + 90, columnW, "FOLLOW ME", () -> withBot("follow", null, "FOLLOW SENT"));
        action(right, y + 90, columnW, "ATTACK", () -> withBot("attack", null, "ATTACK SENT"));
        action(contentX, y + 132, columnW, "STOP", () -> withBot("stop", null, "STOP SENT"));
        action(right, y + 132, columnW, "WANDER", () -> withBot("wander", null, "WANDER SENT"));
    }

    private void buildPresets(int y, int right) {
        List<String> names = new ArrayList<>(Config.presets.keySet());
        if (presetIdx >= names.size()) presetIdx = 0;

        input = new EditBox(font, contentX, y, contentW, 22, Component.literal("Preset name"));
        input.setMaxLength(24);
        input.setHint(Component.literal("New preset name  \u2022  then SAVE CURRENT"));
        input.setValue(presetText);
        input.setResponder(v -> presetText = v);
        addRenderableWidget(input);

        String label = names.isEmpty() ? "NONE SAVED" : names.get(presetIdx).toUpperCase(Locale.ROOT);
        addRenderableWidget(Button.builder(Component.literal("SAVED PRESET  \u2022  " + label), b -> {
            List<String> n = new ArrayList<>(Config.presets.keySet());
            if (n.isEmpty()) return;
            presetIdx = (presetIdx + 1) % n.size();
            rebuildWidgets();
        }).bounds(contentX, y + 30, contentW, 28).build());

        action(contentX, y + 66, columnW, "LOAD", () -> {
            String n = selectedPreset(); if (n == null) return;
            Actions.apply(Config.presets.get(n));
            status = "LOADED  \u2022  " + n;
        });
        action(right, y + 66, columnW, "SAVE CURRENT", () -> {
            String n = presetText.trim();
            if (n.isEmpty()) { status = "NAME REQUIRED  \u2022  Type a preset name first"; return; }
            Config.presets.put(n, Actions.snapshot());
            Config.savePresets();
            presetIdx = new ArrayList<>(Config.presets.keySet()).indexOf(n);
            status = "SAVED  \u2022  " + n;
            rebuildWidgets();
        });
        action(contentX, y + 102, columnW, "SPAWN WITH PRESET", () -> {
            String n = selectedPreset(); if (n == null) return;
            Actions.apply(Config.presets.get(n));
            spawn();
        });
        action(right, y + 102, columnW, "DELETE", () -> {
            String n = selectedPreset(); if (n == null) return;
            Config.presets.remove(n);
            Config.savePresets();
            presetIdx = 0;
            status = "DELETED  \u2022  " + n;
            rebuildWidgets();
        });
    }

    private void buildConsole(int y, int right) {
        input = new EditBox(font, contentX, y, contentW, 22, Component.literal("Command"));
        input.setMaxLength(256);
        input.setHint(Component.literal("Command without slash  \u2022  e.g. vexbot help"));
        input.setValue(commandText);
        input.setResponder(v -> commandText = v);
        addRenderableWidget(input);

        action(contentX, y + 30, columnW, "SEND", () -> {
            String c = input.getValue().trim();
            if (c.startsWith("/")) c = c.substring(1);
            if (c.isEmpty()) { status = "INPUT REQUIRED  \u2022  Enter a command"; return; }
            VexBotGuiAddon.runCommand(c);
            status = "SENT  \u2022  /" + c;
        });
        action(right, y + 30, columnW, "RELOAD COMMANDS FILE", () -> status = Config.reloadCommands().toUpperCase(Locale.ROOT));
    }

    // ---------------- helpers ----------------
    private interface Getter { int get(); }
    private interface Setter { void set(int v); }

    private void cycle(int x, int y, int w, String label, String[] opts, Getter g, Setter s) {
        addRenderableWidget(Button.builder(Component.literal(label + "  \u2022  " + opts[g.get()].toUpperCase(Locale.ROOT)), b -> {
            int next = (g.get() + 1) % opts.length;
            s.set(next);
            b.setMessage(Component.literal(label + "  \u2022  " + opts[next].toUpperCase(Locale.ROOT)));
        }).bounds(x, y, w, 28).build());
    }

    private void action(int x, int y, int w, String label, Runnable r) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, w, 27).build());
    }

    private String bot0() { return botName.getValue().trim(); }

    private String bot() {
        String n = bot0();
        if (n.isEmpty()) { status = "BOT NAME REQUIRED  \u2022  Enter a name above"; return null; }
        return n;
    }

    private String selectedPreset() {
        List<String> n = new ArrayList<>(Config.presets.keySet());
        if (n.isEmpty()) { status = "NO PRESETS  \u2022  Save one first"; return null; }
        return n.get(Math.min(presetIdx, n.size() - 1));
    }

    private void withBot(String key, String arg, String msg) {
        String n = bot(); if (n == null) return;
        VexBotGuiAddon.runCommand(Config.cmd(key, n, arg));
        status = msg;
    }

    private void sendKey(String key, String msg) { sendRaw(Config.cmd(key, bot0(), null), msg); }
    private void sendRaw(String cmd, String msg) { VexBotGuiAddon.runCommand(cmd); status = msg; }

    private void spawn() {
        String base = bot(); if (base == null) return;
        int amount = Integer.parseInt(Actions.COUNTS[Actions.count]);
        if (amount > 1 && !confirmMass) {
            confirmMass = true;
            status = "CONFIRM  \u2022  Press SPAWN again to create " + amount + " bots";
            return;
        }
        confirmMass = false;
        Actions.spawn(base);
        status = "QUEUED  \u2022  " + Actions.summary();
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    // ---------------- drawing ----------------
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF050B0A);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF011191B);
        g.fill(panelX, panelY, panelX + panelW, panelY + 3, 0xFF35D6A2);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 40, 0xFF1A2728);
        g.fill(panelX + 1, panelY + 41, panelX + panelW - 1, panelY + 80, 0xFF152022);
        g.fill(panelX + 18, panelY + 119, panelX + panelW - 18, panelY + 120, 0xFF344548);
        g.fill(panelX + 18, panelY + panelH - 42, panelX + panelW - 18, panelY + panelH - 41, 0xFF344548);
        g.fill(panelX + 16, panelY + 10, panelX + 19, panelY + 29, 0xFF35D6A2);
        g.fill(contentX - 5, contentY - 8, contentX + contentW + 5, panelY + panelH - 51, 0x661D2A2C);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        g.blit(RenderPipelines.GUI_TEXTURED, MASCOT, panelX + 16, panelY + 3, 0.0F, 0.0F, 44, 42, 256, 246, 256, 246);
        g.drawString(font, "VEXBOT", panelX + 66, panelY + 9, 0xFFF1F8F5, false);
        g.drawString(font, "CONTROL CENTER  /  CLIENT ADDON", panelX + 66, panelY + 22, 0xFF91AFA4, false);
        g.drawString(font, "BOT IDENTITY", contentX, panelY + 43, 0xFF8EA99F, false);

        String section = switch (tab) {
            case 0 -> "BOT DEPLOYMENT";
            case 1 -> "EQUIPMENT LOADOUT";
            case 2 -> "COMBAT INTELLIGENCE";
            case 3 -> "SAVED PRESETS";
            default -> "COMMAND CONSOLE  /  SERVER RESPONSES";
        };
        g.drawString(font, section, contentX, panelY + 112, 0xFF4CE0AE, false);

        if (tab == 4) {
            int top = contentY + 66;
            int bottom = panelY + panelH - 54;
            int lines = Math.max(1, (bottom - top) / 10);
            List<String> log = VexBotGuiAddon.LOG;
            if (log.isEmpty()) {
                g.drawString(font, "No replies yet. Send a command and the server's answer shows here.", contentX, top, 0xFF81978F, false);
            } else {
                int from = Math.max(0, log.size() - lines);
                for (int i = from; i < log.size(); i++) {
                    String s = log.get(i);
                    String low = s.toLowerCase(Locale.ROOT);
                    boolean bad = low.contains("unknown") || low.contains("incomplete") || low.contains("error")
                            || low.contains("permission") || low.contains("invalid") || low.contains("expected");
                    g.drawString(font, fit(s, contentW), contentX, top + (i - from) * 10, bad ? 0xFFFF8A80 : 0xFFD1E2DA, false);
                }
            }
        }

        g.drawString(font, fit(status, panelW - 160), contentX, panelY + panelH - 31, 0xFFD1E2DA, false);
        g.drawString(font, "N = QUICK SPAWN", panelX + panelW - 110, panelY + panelH - 31, 0xFF48CFA1, false);

        String last = VexBotGuiAddon.lastLog();
        String foot = last.isEmpty() ? "CURRENT  \u2022  " + Actions.summary() : "SERVER  \u2022  " + last;
        g.drawString(font, fit(foot, contentW), contentX, panelY + panelH - 18, 0xFF81978F, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
