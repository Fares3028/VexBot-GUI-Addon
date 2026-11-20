package com.fares.vexbotgui;

import net.minecraft.client.gui.Font;
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
import java.util.function.Consumer;

/**
 * Calm, airy control panel: sidebar navigation on the left, one focused page on the right.
 * Buttons are drawn flat by this class; the real (invisible) vanilla buttons only handle clicks.
 */
public final class VexBotScreen extends Screen {
    private static final Identifier MASCOT = Identifier.fromNamespaceAndPath("vexbot_gui", "textures/gui/mascot.png");
    private static final String[] TABS = {"QUICK", "SPAWN", "LOADOUT", "COMBAT", "ORDERS", "TEAMS", "SETTINGS", "PRESETS", "CONSOLE"};
    private static final String[] TITLES = {"Quick bots", "Deploy", "Loadout", "Combat", "Orders", "Teams", "Server settings", "Presets", "Console"};
    private static final String[] SUBS = {
            "One click spawns a ready bot, gear matched to its level",
            "Spawn bots with your own count and difficulty",
            "Armor, weapons and server kits",
            "Tune difficulty and play style",
            "Tell the named bot what to do",
            "Group bots and command them together",
            "Common on/off switches  \u2022  needs OP",
            "Save and reuse your favourite setups",
            "Send any command and read the server's answer"};
    private static final int T_QUICK = 0, T_SPAWN = 1, T_LOADOUT = 2, T_COMBAT = 3, T_ORDERS = 4, T_TEAMS = 5,
            T_SETTINGS = 6, T_PRESETS = 7, T_CONSOLE = 8;

    // palette: slate + soft mint
    private static final int BG = 0xF0172326, SIDE = 0xF0111B1D, LINE = 0xFF263A3D;
    private static final int TEXT = 0xFFE8F3EF, MUTED = 0xFF8FAFA6, FAINT = 0xFF5F7C75, ACCENT = 0xFF5EEAD4;
    private static final int[] QCOL = {0xFF6EE7A8, 0xFFF6E27A, 0xFFF5A25D, 0xFFEF6A6A, 0xFFB48CFF, 0xFF5EEAD4};

    private static int tab = 0, presetIdx = 0;
    private static String presetText = "", commandText = "", kitText = "", teamText = "";

    /** One flat button as drawn by us. style: 0 normal, 1 primary (mint), 2 danger (rose). */
    private static final class Fx {
        int x, y, w, h, style, accent, labelColor = TEXT, subColor = MUTED;
        String label, sub;
        boolean row;
    }

    private static final class Fld { int x, y, w, h; EditBox box; }

    private final List<Fx> fxs = new ArrayList<>();
    private final List<Fld> flds = new ArrayList<>();

    private EditBox botName, input;
    private String status = "READY  \u2022  Pick a level or enter a bot name";
    private int panelX, panelY, panelW, panelH, sideW, contentX, contentW, contentY, columnW, rightX;
    private int fieldY, footerY, step, bh, navY, navStep;
    private boolean confirmMass, confirmKill, confirmTeam;

    public VexBotScreen() { super(Component.literal("VexBot Control Center")); }

    @Override
    protected void init() {
        clearWidgets();
        fxs.clear();
        flds.clear();
        panelW = Math.max(330, Math.min(650, width - 24));
        panelH = Math.max(260, Math.min(420, height - 24));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        sideW = panelW < 470 ? 86 : 124;
        contentX = panelX + sideW + 20;
        contentW = panelW - sideW - 40;
        columnW = (contentW - 12) / 2;
        rightX = contentX + columnW + 12;
        fieldY = panelY + 58;
        contentY = panelY + 100;
        footerY = panelY + panelH - 34;

        int avail = footerY - 8 - contentY;
        step = Math.max(26, Math.min(36, (avail - 26) / 6));
        bh = Math.min(26, step - 3);

        // sidebar navigation (invisible click targets, drawn in renderBackground)
        navY = panelY + 70;
        navStep = Math.max(17, Math.min(23, (panelH - 70 - 14) / TABS.length));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button t = Button.builder(Component.literal(TABS[i]), b -> {
                tab = idx; confirmMass = false; confirmKill = false; confirmTeam = false; rebuildWidgets();
            }).bounds(panelX + 8, navY + i * navStep, sideW - 16, navStep - 2).build();
            t.setAlpha(0.0F);
            addRenderableWidget(t);
        }

        botName = field(contentX, fieldY, contentW, "Enter bot name  \u2022  e.g. TrainingBot", Actions.name, 16, v -> Actions.name = v);

        switch (tab) {
            case T_QUICK -> buildQuick();
            case T_SPAWN -> buildSpawn();
            case T_LOADOUT -> buildLoadout();
            case T_COMBAT -> buildCombat();
            case T_ORDERS -> buildOrders();
            case T_TEAMS -> buildTeams();
            case T_SETTINGS -> buildSettings();
            case T_PRESETS -> buildPresets(contentY, rightX);
            default -> buildConsole(contentY, rightX);
        }
    }

    private int y(int row) { return contentY + row * step; }

    // ---------------- widget factories ----------------
    private EditBox field(int x, int y, int w, String hint, String value, int max, Consumer<String> responder) {
        EditBox e = new EditBox(font, x + 10, y + 8, w - 20, 16, Component.literal(hint));
        e.setBordered(false);
        e.setMaxLength(max);
        e.setHint(Component.literal(hint));
        e.setValue(value);
        e.setResponder(responder);
        addRenderableWidget(e);
        Fld f = new Fld();
        f.x = x; f.y = y; f.w = w; f.h = 24; f.box = e;
        flds.add(f);
        return e;
    }

    private Fx action(int x, int y, int w, String label, int style, Runnable r) {
        Fx f = new Fx();
        f.x = x; f.y = y; f.w = w; f.h = bh; f.label = label; f.style = style;
        fxs.add(f);
        Button b = Button.builder(Component.literal(label), bt -> r.run()).bounds(x, y, w, bh).build();
        b.setAlpha(0.0F);
        addRenderableWidget(b);
        return f;
    }

    private Fx action(int x, int y, int w, String label, Runnable r) { return action(x, y, w, label, 0, r); }

    /** Wide row with a coloured dot, title on the left and detail on the right. */
    private void card(int x, int y, int w, String title, String sub, int accent, Runnable r) {
        Fx f = action(x, y, w, title, 0, r);
        f.row = true; f.sub = sub; f.accent = accent;
    }

    private void cycle(int x, int y, int w, String label, String[] opts, Getter g, Setter s) {
        Fx f = new Fx();
        f.x = x; f.y = y; f.w = w; f.h = bh; f.label = label; f.row = true;
        f.labelColor = MUTED; f.subColor = ACCENT;
        f.sub = opts[g.get()].toUpperCase(Locale.ROOT);
        fxs.add(f);
        Button b = Button.builder(Component.literal(label), bt -> {
            int next = (g.get() + 1) % opts.length;
            s.set(next);
            f.sub = opts[next].toUpperCase(Locale.ROOT);
        }).bounds(x, y, w, bh).build();
        b.setAlpha(0.0F);
        addRenderableWidget(b);
    }

    // ---------------- pages ----------------
    private void buildQuick() {
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            String title, sub;
            if (i < Actions.TIERS.length) {
                Actions.Tier t = Actions.TIERS[i];
                title = t.difficulty().toUpperCase(Locale.ROOT);
                sub = t.armor().toUpperCase(Locale.ROOT) + " + " + t.weapon().replace('_', ' ').toUpperCase(Locale.ROOT);
            } else {
                title = "RANDOM";
                sub = "ANY LEVEL";
            }
            card(contentX, y(i), contentW, title, sub, QCOL[i], () -> quick(idx));
        }
        action(contentX, y(6), columnW, "LIST BOTS", () -> sendRaw(Config.cmd("list", null, null), "LIST REQUESTED"));
        action(rightX, y(6), columnW, "KILL LAST QUICK BOT", 2, () -> {
            if (Actions.lastQuick.isEmpty()) { status = "NOTHING TO KILL  \u2022  Spawn a quick bot first"; return; }
            sendRaw(Config.cmd("kill", Actions.lastQuick, null), "KILL SENT  \u2022  " + Actions.lastQuick);
        });
    }

    private void quick(int i) {
        int t = i < Actions.TIERS.length ? i : new java.util.Random().nextInt(Actions.TIERS.length);
        String n = Actions.spawnTier(t, bot0());
        status = "QUEUED  \u2022  " + Actions.TIERS[t].describe() + "  \u2022  " + n;
    }

    private void buildSpawn() {
        cycle(contentX, y(0), columnW, "BOT COUNT", Actions.COUNTS, () -> Actions.count, v -> Actions.count = v);
        cycle(rightX, y(0), columnW, "DIFFICULTY", Actions.DIFFICULTIES, () -> Actions.difficulty, v -> Actions.difficulty = v);
        action(contentX, y(1), columnW, "SPAWN BOT(S)", 1, this::spawn);
        action(rightX, y(1), columnW, confirmKill ? "CONFIRM KILL" : "KILL BOT", 2, () -> {
            if (!confirmKill) { confirmKill = true; status = "CONFIRM  \u2022  Press again to kill"; rebuildWidgets(); return; }
            confirmKill = false;
            withBot("kill", null, "KILL SENT");
            rebuildWidgets();
        });
        action(contentX, y(2), columnW, "LIST BOTS", () -> sendRaw(Config.cmd("list", null, null), "LIST REQUESTED"));
        action(rightX, y(2), columnW, "RANDOM GEAR", () -> withBot("randomgear", null, "RANDOM GEAR SENT"));
    }

    private void buildLoadout() {
        cycle(contentX, y(0), columnW, "ARMOR SET", Actions.ARMORS, () -> Actions.armor, v -> Actions.armor = v);
        cycle(rightX, y(0), columnW, "MAIN-HAND", Actions.WEAPONS, () -> Actions.weapon, v -> Actions.weapon = v);
        cycle(contentX, y(1), columnW, "OFF-HAND", Actions.OFFHANDS, () -> Actions.offhand, v -> Actions.offhand = v);
        action(rightX, y(1), columnW, "RANDOM GEAR", () -> withBot("randomgear", null, "RANDOM GEAR SENT"));
        action(contentX, y(2), contentW, "APPLY EQUIPMENT TO NAMED BOT", 1, () -> {
            String n = bot(); if (n == null) return;
            Actions.equip(n, 1);
            status = "QUEUED  \u2022  Equipment for " + n;
        });

        // --- VexBot kits (server-side kits made with /vexbot createkit)
        field(contentX, y(3), contentW, "Kit name  \u2022  see LIST KITS", kitText, 32, v -> kitText = v);
        action(contentX, y(4), columnW, "GIVE KIT TO BOT", () -> {
            String k = kit(); if (k == null) return;
            withBot("givekit", k, "KIT SENT");
        });
        action(rightX, y(4), columnW, "SPAWN WITH KIT", () -> {
            String k = kit(); if (k == null) return;
            String n = bot(); if (n == null) return;
            VexBotGuiAddon.runCommand(Config.cmd("spawnkit", n, k));
            status = "SPAWN WITH KIT SENT  \u2022  needs settings advancedspawn on";
        });
        action(contentX, y(5), contentW, "LIST KITS", () -> sendRaw(Config.cmd("kitlist", null, null), "KIT LIST REQUESTED"));
    }

    private void buildCombat() {
        cycle(contentX, y(0), columnW, "DIFFICULTY", Actions.DIFFICULTIES, () -> Actions.difficulty, v -> Actions.difficulty = v);
        cycle(rightX, y(0), columnW, "PLAYSTYLE", Actions.STYLES, () -> Actions.style, v -> Actions.style = v);
        action(contentX, y(1), columnW, "APPLY DIFFICULTY", () ->
                withBot("difficulty", Actions.DIFFICULTIES[Actions.difficulty], "DIFFICULTY SENT"));
        action(rightX, y(1), columnW, "APPLY PLAYSTYLE", () ->
                withBot("playstyle", Actions.STYLES[Actions.style], "PLAYSTYLE SENT"));
        action(contentX, y(2), columnW, "DIFFICULTY: ALL BOTS", () ->
                sendRaw(Config.cmd("difficultyall", null, Actions.DIFFICULTIES[Actions.difficulty]), "GLOBAL DIFFICULTY SENT"));
        action(rightX, y(2), columnW, "RANDOM PLAYSTYLE", () -> withBot("randomplaystyle", null, "RANDOM PLAYSTYLE SENT"));
    }

    private void buildOrders() {
        action(contentX, y(0), columnW, "FOLLOW ME", () -> withBot("follow", Actions.me(), "FOLLOW SENT"));
        action(rightX, y(0), columnW, "STOP FOLLOW", () -> withBot("followstop", null, "FOLLOW STOP SENT"));
        action(contentX, y(1), columnW, "ATTACK ME", () -> withBot("attack", Actions.me(), "ATTACK SENT"));
        action(rightX, y(1), columnW, "STOP ATTACK", () -> withBot("attackstop", null, "ATTACK STOP SENT"));
        action(contentX, y(2), columnW, "HIDE", () -> withBot("hide", null, "HIDE SENT"));
        action(rightX, y(2), columnW, "UNHIDE", () -> withBot("hidestop", null, "UNHIDE SENT"));
        cycle(contentX, y(3), columnW, "WANDER RADIUS", Actions.WANDER, () -> Actions.wander, v -> Actions.wander = v);
        action(rightX, y(3), columnW, "START WANDER", () -> withBot("wander", Actions.WANDER[Actions.wander], "WANDER SENT"));
        action(contentX, y(4), columnW, "GO TO ME", () -> withBot("goto", Actions.me(), "GOTO SENT"));
        action(rightX, y(4), columnW, "LOOK AT ME", () -> withBot("look", Actions.me(), "LOOK SENT"));
        action(contentX, y(5), contentW, "STOP EVERYTHING", 2, () -> {
            String n = bot(); if (n == null) return;
            String[] keys = {"followstop", "attackstop", "hidestop", "gotostop", "wanderstop", "lookstop"};
            for (int i = 0; i < keys.length; i++) {
                VexBotGuiAddon.runLater(Config.cmd(keys[i], n, null), i * 2 + 1);
            }
            status = "STOP SENT  \u2022  all orders for " + n;
        });
    }

    private void buildTeams() {
        field(contentX, y(0), contentW, "Team name  \u2022  e.g. RedTeam", teamText, 32, v -> teamText = v);

        action(contentX, y(1), columnW, "CREATE TEAM", 1, () -> teamCmd("teamcreate", null, false, "TEAM CREATED"));
        action(rightX, y(1), columnW, confirmTeam ? "CONFIRM DELETE" : "DELETE TEAM", 2, () -> {
            if (!confirmTeam) { confirmTeam = true; status = "CONFIRM  \u2022  Press again to delete the team"; rebuildWidgets(); return; }
            confirmTeam = false;
            teamCmd("teamdelete", null, false, "TEAM DELETE SENT");
            rebuildWidgets();
        });
        action(contentX, y(2), columnW, "ADD BOT TO TEAM", () -> teamCmd("teamadd", null, true, "ADD SENT"));
        action(rightX, y(2), columnW, "REMOVE BOT", () -> teamCmd("teamremove", null, true, "REMOVE SENT"));
        action(contentX, y(3), columnW, "SPAWN INTO TEAM", () -> teamCmd("teamspawn", null, true, "TEAM SPAWN SENT"));
        action(rightX, y(3), columnW, "LIST MEMBERS", () -> teamCmd("teamlist", null, false, "MEMBERS REQUESTED"));
        action(contentX, y(4), columnW, "TEAM FOLLOW ME", () -> teamCmd("teamfollow", Actions.me(), false, "TEAM FOLLOW SENT"));
        action(rightX, y(4), columnW, "TEAM STOP FOLLOW", () -> teamCmd("teamfollowstop", null, false, "TEAM STOP SENT"));
        action(contentX, y(5), columnW, "TEAM DIFFICULTY", () ->
                teamCmd("teamdifficulty", Actions.DIFFICULTIES[Actions.difficulty], false, "TEAM DIFFICULTY SENT"));
        action(rightX, y(5), columnW, "TEAM PLAYSTYLE", () ->
                teamCmd("teamplaystyle", Actions.STYLES[Actions.style], false, "TEAM PLAYSTYLE SENT"));
    }

    private void buildSettings() {
        cycle(contentX, y(0), columnW, "SETTING", Actions.SETTINGS, () -> Actions.settingIdx, v -> Actions.settingIdx = v);
        cycle(rightX, y(0), columnW, "VALUE", Actions.ON_OFF, () -> Actions.settingVal, v -> Actions.settingVal = v);
        action(contentX, y(1), columnW, "APPLY SETTING", 1, () -> {
            String key = Actions.SETTINGS[Actions.settingIdx];
            sendRaw(Config.cmd("settingset", null, key + " " + Actions.ON_OFF[Actions.settingVal]),
                    "SETTING SENT  \u2022  " + key + " " + Actions.ON_OFF[Actions.settingVal]);
        });
        action(rightX, y(1), columnW, "SHOW VALUE", () -> {
            String key = Actions.SETTINGS[Actions.settingIdx];
            sendRaw(Config.cmd("settingshow", null, key), "SHOW SENT  \u2022  " + key);
        });
        action(contentX, y(2), columnW, "LIST ALL SETTINGS", () -> sendRaw(Config.cmd("settingslist", null, null), "SETTINGS LIST REQUESTED"));
        action(rightX, y(2), columnW, "DIFFICULTY KNOBS", () -> sendRaw(Config.cmd("difficultylist", null, null), "KNOB LIST REQUESTED"));
        action(contentX, y(3), contentW, "OPEN SERVER SETTINGS MENU", () -> sendRaw(Config.cmd("settings", null, null), "SETTINGS MENU REQUESTED"));
    }

    private void buildPresets(int y, int right) {
        List<String> names = new ArrayList<>(Config.presets.keySet());
        if (presetIdx >= names.size()) presetIdx = 0;

        input = field(contentX, y, contentW, "New preset name  \u2022  then SAVE CURRENT", presetText, 24, v -> presetText = v);

        String label = names.isEmpty() ? "NONE SAVED" : names.get(presetIdx).toUpperCase(Locale.ROOT);
        Fx pick = action(contentX, y + 32, contentW, "SAVED PRESET", () -> {
            List<String> n = new ArrayList<>(Config.presets.keySet());
            if (n.isEmpty()) return;
            presetIdx = (presetIdx + 1) % n.size();
            rebuildWidgets();
        });
        pick.row = true; pick.labelColor = MUTED; pick.subColor = ACCENT; pick.sub = label;

        action(contentX, y + 70, columnW, "LOAD", () -> {
            String n = selectedPreset(); if (n == null) return;
            Actions.apply(Config.presets.get(n));
            status = "LOADED  \u2022  " + n;
        });
        action(right, y + 70, columnW, "SAVE CURRENT", 1, () -> {
            String n = presetText.trim();
            if (n.isEmpty()) { status = "NAME REQUIRED  \u2022  Type a preset name first"; return; }
            Config.presets.put(n, Actions.snapshot());
            Config.savePresets();
            presetIdx = new ArrayList<>(Config.presets.keySet()).indexOf(n);
            status = "SAVED  \u2022  " + n;
            rebuildWidgets();
        });
        action(contentX, y + 108, columnW, "SPAWN WITH PRESET", () -> {
            String n = selectedPreset(); if (n == null) return;
            Actions.apply(Config.presets.get(n));
            spawn();
        });
        action(right, y + 108, columnW, "DELETE", 2, () -> {
            String n = selectedPreset(); if (n == null) return;
            Config.presets.remove(n);
            Config.savePresets();
            presetIdx = 0;
            status = "DELETED  \u2022  " + n;
            rebuildWidgets();
        });
    }

    private void buildConsole(int y, int right) {
        input = field(contentX, y, contentW, "Command without slash  \u2022  e.g. vexbot list", commandText, 256, v -> commandText = v);

        int gap = 8, w3 = (contentW - gap * 2) / 3;
        action(contentX, y + 32, w3, "SEND", 1, () -> {
            String c = input.getValue().trim();
            if (c.startsWith("/")) c = c.substring(1);
            if (c.isEmpty()) { status = "INPUT REQUIRED  \u2022  Enter a command"; return; }
            VexBotGuiAddon.runCommand(c);
            status = "SENT  \u2022  /" + c;
        });
        action(contentX + w3 + gap, y + 32, w3, "CLEAR LOG", () -> {
            VexBotGuiAddon.clearLog();
            status = "LOG CLEARED";
        });
        action(contentX + (w3 + gap) * 2, y + 32, w3, "RELOAD CMDS", () ->
                status = Config.reloadCommands().toUpperCase(Locale.ROOT));
    }

    // ---------------- helpers ----------------
    private interface Getter { int get(); }
    private interface Setter { void set(int v); }

    private String bot0() { return botName.getValue().trim(); }

    private String bot() {
        String n = bot0();
        if (n.isEmpty()) { status = "BOT NAME REQUIRED  \u2022  Enter a name above"; return null; }
        return n;
    }

    private String kit() {
        String k = kitText.trim();
        if (k.isEmpty()) { status = "KIT NAME REQUIRED  \u2022  Use LIST KITS to see names"; return null; }
        return k;
    }

    private String team() {
        String t = teamText.trim();
        if (t.isEmpty()) { status = "TEAM NAME REQUIRED  \u2022  Type it in the team box"; return null; }
        return t;
    }

    private String selectedPreset() {
        List<String> n = new ArrayList<>(Config.presets.keySet());
        if (n.isEmpty()) { status = "NO PRESETS  \u2022  Save one first"; return null; }
        return n.get(Math.min(presetIdx, n.size() - 1));
    }

    /** Sends a template with the bot name from the top box; {arg} = arg. */
    private void withBot(String key, String arg, String msg) {
        String n = bot(); if (n == null) return;
        VexBotGuiAddon.runCommand(Config.cmd(key, n, arg));
        status = msg;
    }

    /** Team template: {arg} = team name, {arg2} = arg2, {bot} = bot box when needBot. */
    private void teamCmd(String key, String arg2, boolean needBot, String msg) {
        String t = team(); if (t == null) return;
        String b = "";
        if (needBot) { b = bot(); if (b == null) return; }
        VexBotGuiAddon.runCommand(Config.cmd(key, b, t, arg2));
        status = msg;
    }

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
    // ---------------- drawing ----------------
    /** Rectangle with softly notched corners. */
    private static void soft(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1 + 1, y1, x2 - 1, y2, c);
        g.fill(x1, y1 + 1, x2, y2 - 1, c);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void bold(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x, y, c, false);
        g.drawString(f, s, x + 1, y, c, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);

        // soft shadow + body
        for (int i = 5; i >= 1; i--) soft(g, panelX - i, panelY - i + 2, panelX + panelW + i, panelY + panelH + i + 2, 0x12000000);
        soft(g, panelX, panelY, panelX + panelW, panelY + panelH, BG);

        // sidebar
        g.fill(panelX, panelY + 1, panelX + sideW, panelY + panelH - 1, SIDE);
        g.fill(panelX + sideW, panelY + 14, panelX + sideW + 1, panelY + panelH - 14, LINE);
        g.blit(RenderPipelines.GUI_TEXTURED, MASCOT, panelX + 12, panelY + 12, 0.0F, 0.0F, 38, 36, 256, 246, 256, 246);
        if (sideW > 100) {
            bold(g, font, "VEXBOT", panelX + 56, panelY + 20, TEXT);
            g.drawString(font, "CONTROL", panelX + 56, panelY + 32, FAINT, false);
        }
        for (int i = 0; i < TABS.length; i++) {
            int ny = navY + i * navStep, nx = panelX + 8, nw = sideW - 16, nh = navStep - 2;
            boolean act = i == tab, hov = in(mouseX, mouseY, nx, ny, nw, nh);
            if (act) soft(g, nx, ny, nx + nw, ny + nh, 0x335EEAD4);
            else if (hov) soft(g, nx, ny, nx + nw, ny + nh, 0x1AFFFFFF);
            if (act) g.fill(nx, ny + 3, nx + 2, ny + nh - 3, ACCENT);
            g.drawString(font, fit(TABS[i], nw - 18), nx + 12, ny + (nh - 8) / 2, act ? TEXT : hov ? 0xFFC3D8D0 : MUTED, false);
        }

        // page header
        bold(g, font, TITLES[tab], contentX, panelY + 16, TEXT);
        g.drawString(font, fit(SUBS[tab], contentW), contentX, panelY + 30, MUTED, false);
        g.drawString(font, "BOT NAME", contentX, fieldY - 10, FAINT, false);

        // input fields
        for (Fld f : flds) {
            soft(g, f.x, f.y, f.x + f.w, f.y + f.h, 0xFF101B1D);
            g.fill(f.x + 1, f.y + f.h - 1, f.x + f.w - 1, f.y + f.h, f.box.isFocused() ? ACCENT : LINE);
        }

        // console log card
        if (tab == T_CONSOLE) {
            soft(g, contentX, contentY + 70, contentX + contentW, footerY - 10, 0x66101A1C);
        }

        // flat buttons
        for (Fx f : fxs) {
            boolean hov = in(mouseX, mouseY, f.x, f.y, f.w, f.h);
            int bg = switch (f.style) {
                case 1 -> hov ? 0xFF2F6F68 : 0xFF265B56;
                case 2 -> hov ? 0xFF5A3036 : 0xFF40272B;
                default -> hov ? 0xFF2A4141 : 0xFF1E3033;
            };
            soft(g, f.x, f.y, f.x + f.w, f.y + f.h, bg);
            int ty = f.y + (f.h - 8) / 2;
            if (f.row) {
                int lx = f.x + 12;
                if (f.accent != 0) {
                    g.fill(f.x + 11, ty, f.x + 19, ty + 8, f.accent);
                    lx = f.x + 26;
                }
                int sw = f.sub == null ? 0 : font.width(f.sub);
                g.drawString(font, fit(f.label, f.w - (lx - f.x) - sw - 22), lx, ty, f.labelColor, false);
                if (f.sub != null) {
                    int shown = Math.min(sw, f.w / 2 + 20);
                    g.drawString(font, fit(f.sub, f.w / 2 + 20), f.x + f.w - 12 - shown, ty, f.subColor, false);
                }
            } else {
                String l = fit(f.label, f.w - 12);
                g.drawString(font, l, f.x + (f.w - font.width(l)) / 2, ty, TEXT, false);
            }
        }

        // footer
        g.fill(contentX, footerY - 6, contentX + contentW, footerY - 5, LINE);
        g.drawString(font, fit(status, contentW - 90), contentX, footerY, TEXT, false);
        String hint = "N  QUICK SPAWN";
        g.drawString(font, hint, contentX + contentW - font.width(hint), footerY, ACCENT, false);
        String last = VexBotGuiAddon.lastLog();
        String foot = last.isEmpty() ? "CURRENT  \u2022  " + Actions.summary() : "SERVER  \u2022  " + last;
        g.drawString(font, fit(foot, contentW), contentX, footerY + 12, FAINT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        if (tab == T_CONSOLE) {
            int top = contentY + 76;
            int bottom = footerY - 14;
            int lines = Math.max(1, (bottom - top) / 10);
            List<String> log = VexBotGuiAddon.LOG;
            if (log.isEmpty()) {
                g.drawString(font, fit("No replies yet. Send a command and the answer shows here.", contentW - 16), contentX + 8, top, FAINT, false);
            } else {
                int from = Math.max(0, log.size() - lines);
                for (int i = from; i < log.size(); i++) {
                    String s = log.get(i);
                    String low = s.toLowerCase(Locale.ROOT);
                    boolean bad = low.contains("unknown") || low.contains("incomplete") || low.contains("error")
                            || low.contains("permission") || low.contains("invalid") || low.contains("expected");
                    g.drawString(font, fit(s, contentW - 16), contentX + 8, top + (i - from) * 10, bad ? 0xFFFF9A90 : 0xFFD8EAE2, false);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
