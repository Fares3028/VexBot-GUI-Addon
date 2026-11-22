package com.fares.vexbotgui;

import net.minecraft.client.gui.Font;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Calm, airy control panel: sidebar navigation on the left, one focused page on the right.
 * Buttons are drawn flat by this class; the real (invisible) vanilla buttons only handle clicks.
 */
public final class VexBotScreen extends Screen {
    private static final Identifier MASCOT = Identifier.fromNamespaceAndPath("vexbot_gui", "textures/gui/mascot.png");
    private static final String[] ICON_NAMES = {"dashboard", "quick", "spawn", "loadout", "kits", "combat", "orders", "teams", "settings", "presets", "console"};
    private static final Identifier[] ICON = new Identifier[ICON_NAMES.length];
    private static final Identifier[] ICON_ON = new Identifier[ICON_NAMES.length];
    private static final int ICON_SIZE = 12;
    static {
        for (int i = 0; i < ICON_NAMES.length; i++) {
            ICON[i] = Identifier.fromNamespaceAndPath("vexbot_gui", "textures/gui/icons/" + ICON_NAMES[i] + ".png");
            ICON_ON[i] = Identifier.fromNamespaceAndPath("vexbot_gui", "textures/gui/icons/" + ICON_NAMES[i] + "_on.png");
        }
    }
    private static final String[] TABS = {"DASHBOARD", "QUICK", "SPAWN", "LOADOUT", "KITS", "COMBAT", "ORDERS", "TEAMS", "SETTINGS", "PRESETS", "CONSOLE"};
    private static final String[] TITLES = {"Dashboard", "Quick bots", "Deploy", "Loadout", "Combat", "Orders", "Teams", "Server settings", "Presets", "Console"};
    private static final String[] SUBS = {
            "A quick overview and shortcuts for your VexBot setup",
            "One click spawns a ready bot, gear matched to its level",
            "Spawn bots with your own count and difficulty",
            "Armor, weapons and server kits",
            "Tune difficulty and play style",
            "Tell the named bot what to do",
            "1 Name it  \u2022  2 Build or add bots  \u2022  3 Give orders",
            "Common on/off switches  \u2022  needs OP",
            "Save and reuse your favourite setups",
            "Send any command and read the server's answer"};
    private static final int T_DASHBOARD = 0, T_QUICK = 1, T_SPAWN = 2, T_LOADOUT = 3, T_KITS = 4, T_COMBAT = 5, T_ORDERS = 6, T_TEAMS = 7,
            T_SETTINGS = 8, T_PRESETS = 9, T_CONSOLE = 10;

    // palette: slate + soft mint
    private static final int BG = 0xF0172326, SIDE = 0xF0111B1D, LINE = 0xFF263A3D;
    private static final int TEXT = 0xFFE8F3EF, MUTED = 0xFF8FAFA6, FAINT = 0xFF5F7C75, ACCENT = 0xFF5EEAD4;
    private static final int[] QCOL = {0xFF6EE7A8, 0xFFF6E27A, 0xFFF5A25D, 0xFFEF6A6A, 0xFFB48CFF, 0xFF5EEAD4};

    private static int tab = 0, presetIdx = 0;
    private static String presetText = "", commandText = "", kitText = "", teamText = "";
    /** TEAMS page: 0 = create a new team, 1 = manage an existing one. */
    private static int teamMode = 0;

    /** One flat button as drawn by us. style: 0 normal, 1 primary (mint), 2 danger (rose). */
    private static final class Fx {
        int x, y, w, h, style, accent, labelColor = TEXT, subColor = MUTED;
        String label, sub;
        boolean row, info; // info = display only, not clickable
        Supplier<String> dynLabel, dynSub; // text recomputed every frame
        int tier = -1; // QUICK cards: which tier this card previews on hover
        float hv;      // 0..1 animated hover amount
    }

    private static final class Fld { int x, y, w, h; EditBox box; }

    // ---------------- animation state ----------------
    private long lastNanos = System.nanoTime();
    private float frameDt = 0f;
    private float openT = 0f;          // 0 -> 1 when the panel opens
    private float navAnim = Float.NaN; // animated y of the sidebar highlight

    private void tickAnim() {
        long now = System.nanoTime();
        frameDt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        openT = Math.min(1f, openT + frameDt / 0.28f);
    }

    /** Exponential smoothing step that does not depend on the frame rate. */
    private float smooth(float speed) { return 1f - (float) Math.exp(-frameDt * speed); }

    private static int lerpColor(int a, int b, float t) {
        int ar = a >> 24 & 255, rr = a >> 16 & 255, gr = a >> 8 & 255, br = a & 255;
        int a2 = b >> 24 & 255, r2 = b >> 16 & 255, g2 = b >> 8 & 255, b2 = b & 255;
        return ((int) (ar + (a2 - ar) * t) << 24) | ((int) (rr + (r2 - rr) * t) << 16)
                | ((int) (gr + (g2 - gr) * t) << 8) | (int) (br + (b2 - br) * t);
    }

    private final List<Fx> fxs = new ArrayList<>();
    private final List<Fld> flds = new ArrayList<>();

    private EditBox botName, input;
    private String status = "READY  \u2022  Pick a level or enter a bot name";
    private int panelX, panelY, panelW, panelH, sideW, contentX, contentW, contentY, columnW, rightX;
    private int fieldY, footerY, step, bh, navY, navStep;
    private boolean confirmKill, confirmTeam, confirmPresetDelete;
    private String confirmKillName = "", confirmTeamName = "", confirmPresetDeleteName = "";
    private long confirmKillUntil, confirmTeamUntil, confirmPresetDeleteUntil;
    private int consoleScroll;
    /** Honest status: after a send we wait briefly for a server message before saying "no reply". */
    private String pendingText = "", pendingStatus = "";
    /** Teams page: which background read (1 = list, 2 = roster) to report when it ends, and the status it replaces. */
    private int watchKind = 0;
    private String watchStatus = "";
    private long pendingAt, pendingMark;
    private int previewX, previewW, hoverTier = -1;
    private boolean previewOn;

    public VexBotScreen() {
        super(Component.literal("VexBot Control Center"));
        String problem = Config.problemSummary();
        if (problem != null) status = "CONFIG  \u2022  " + problem;
    }

    @Override
    protected void init() {
        clearWidgets();
        fxs.clear();
        flds.clear();
        previewOn = width >= 600;
        previewW = previewOn ? 142 : 0;
        int reserve = previewOn ? previewW + 10 : 0;
        panelW = Math.max(280, Math.min(650, width - 24 - reserve));
        panelH = Math.max(240, Math.min(420, height - 20));
        panelX = (width - panelW - reserve) / 2 + reserve;
        panelY = (height - panelH) / 2;
        previewX = panelX - 10 - previewW;
        sideW = panelW < 390 ? 76 : panelW < 470 ? 86 : 124;
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
        navY = panelY + 58;
        navStep = Math.max(15, Math.min(23, (panelH - 58 - 12) / TABS.length));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button t = Button.builder(Component.literal(TABS[i]), b -> {
                tab = idx; confirmKill = false; confirmTeam = false; confirmPresetDelete = false;
                confirmKillName = confirmTeamName = confirmPresetDeleteName = ""; consoleScroll = 0; rebuildWidgets();
            }).bounds(panelX + 8, navY + i * navStep, sideW - 16, navStep - 2).build();
            t.setAlpha(0.0F);
            addRenderableWidget(t);
        }

        String nameHint = Actions.lastBot.isEmpty() ? "Bot name  \u2022  empty = random name"
                : "Bot name  \u2022  empty = random, orders use " + Actions.lastBot;
        botName = field(contentX, fieldY, contentW, nameHint, Actions.name, 16, v -> Actions.name = v);

        switch (tab) {
            case T_DASHBOARD -> buildDashboard();
            case T_QUICK -> buildQuick();
            case T_SPAWN -> buildSpawn();
            case T_LOADOUT -> buildLoadout();
            case T_KITS -> buildKits();
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
        Button b = Button.builder(Component.literal(label), bt -> guarded(r)).bounds(x, y, w, bh).build();
        b.setAlpha(0.0F);
        addRenderableWidget(b);
        return f;
    }

    /** A bug in one button must not crash the game: show it in the status bar and the console instead. */
    private void guarded(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            status = "INTERNAL ERROR  \u2022  " + e.getClass().getSimpleName() + " (see CONSOLE)";
            VexBotGuiAddon.note("INTERNAL ERROR in a button: " + e, true);
            e.printStackTrace();
        }
    }

    private Fx action(int x, int y, int w, String label, Runnable r) { return action(x, y, w, label, 0, r); }

    /** Wide row with a coloured dot, title on the left and detail on the right. */
    private Fx card(int x, int y, int w, String title, String sub, int accent, Runnable r) {
        Fx f = action(x, y, w, title, 0, r);
        f.row = true; f.sub = sub; f.accent = accent;
        return f;
    }

    /** Read-only row whose text is recomputed every frame. */
    private Fx info(int x, int y, int w, Supplier<String> text) {
        Fx f = new Fx();
        f.x = x; f.y = y; f.w = w; f.h = bh; f.row = true; f.info = true;
        f.dynLabel = text; f.labelColor = MUTED;
        fxs.add(f);
        return f;
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
    private void buildDashboard() {
        action(contentX, y(1), columnW, "QUICK SPAWN  \u2022  RANDOM LEVEL", 1, () -> quick(5));
        action(rightX, y(1), columnW, "LIST BOTS", () -> sendRaw(Config.cmd("list", null, null), "LIST REQUESTED"));
        action(contentX, y(2), columnW, "OPEN QUICK", () -> { tab = T_QUICK; rebuildWidgets(); });
        action(rightX, y(2), columnW, "OPEN PRESETS", () -> { tab = T_PRESETS; rebuildWidgets(); });
        action(contentX, y(3), columnW, "MANAGE TEAMS", () -> { tab = T_TEAMS; rebuildWidgets(); });
        action(rightX, y(3), columnW, "OPEN CONSOLE", () -> { tab = T_CONSOLE; consoleScroll = 0; rebuildWidgets(); });
        info(contentX, y(4), contentW, () -> {
            List<String> p = Config.allProblems();
            return p.isEmpty() ? "CONFIG: OK" : "CONFIG: " + p.size() + " warning(s)  \u2022  see CONSOLE";
        });
        action(contentX, y(5), contentW, "COPY DIAGNOSTICS  \u2022  for bug reports", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(VexBotGuiAddon.diagnostics());
            status = "COPIED  \u2022  diagnostics are in your clipboard";
        });
    }

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
            Fx card = card(contentX, y(i), contentW, title, sub, QCOL[i], () -> quick(idx));
            if (i < Actions.TIERS.length) card.tier = i;
        }
        action(contentX, y(6), columnW, "LIST BOTS", () -> sendRaw(Config.cmd("list", null, null), "LIST REQUESTED"));
        action(rightX, y(6), columnW, "KILL LAST BOT", 2, () -> {
            if (Actions.lastBot.isEmpty()) { status = "NOTHING TO KILL  \u2022  no confirmed bot yet"; return; }
            if (!needConnection()) return;
            String n = Actions.lastBot;
            sendRaw(Config.cmd("kill", n, null), "KILL SENT  \u2022  " + n);
            afterKill(n);
        });
    }

    private void quick(int i) {
        int t = i < Actions.TIERS.length ? i : new java.util.Random().nextInt(Actions.TIERS.length);
        if (!needConnection()) return;
        if (!Actions.nameOk(bot0())) { status = "INVALID NAME  \u2022  1-16 letters, digits or _"; return; }
        String n = Actions.spawnTier(t, bot0());
        status = "QUEUED  \u2022  " + Actions.TIERS[t].describe() + "  \u2022  " + n;
    }

    private void buildSpawn() {
        cycle(contentX, y(0), columnW, "BOT COUNT", Actions.COUNTS, () -> Actions.count, v -> Actions.count = v);
        cycle(rightX, y(0), columnW, "DIFFICULTY", Actions.DIFFICULTIES, () -> Actions.difficulty, v -> Actions.difficulty = v);
        Fx spawnBtn = action(contentX, y(1), columnW, "SPAWN BOT(S)", 1, this::spawn);
        spawnBtn.dynLabel = () -> Actions.massConfirmPending(bot0()) ? "CONFIRM SPAWN  \u2022  press again" : "SPAWN BOT(S)";
        action(rightX, y(1), columnW, confirmKill ? "CONFIRM KILL" : "KILL BOT", 2, () -> {
            if (!needConnection()) return;
            String target = bot(); if (target == null) return;
            long now = System.currentTimeMillis();
            if (!confirmKill || !target.equals(confirmKillName) || now > confirmKillUntil) {
                confirmKill = true; confirmKillName = target; confirmKillUntil = now + 5000;
                status = "CONFIRM  \u2022  Kill " + target + " within 5 s"; rebuildWidgets(); return;
            }
            confirmKill = false; confirmKillName = "";
            withBot("kill", null, "KILL SENT");
            afterKill(target);
            rebuildWidgets();
        });
        action(contentX, y(2), columnW, "LIST BOTS", () -> sendRaw(Config.cmd("list", null, null), "LIST REQUESTED"));
        action(rightX, y(2), columnW, "RANDOM GEAR", () -> withBot("randomgear", null, "RANDOM GEAR SENT"));

        // VexBot's own mass_spawn: ONE command instead of ~20 per bot, but random names and VexBot defaults
        Fx fast = action(contentX, y(3), columnW, "FAST SPAWN", 1, () -> fastSpawn(false));
        fast.dynLabel = () -> Actions.fastSpawnPending(Actions.amount(), false) ? "CONFIRM FAST SPAWN" : "FAST SPAWN";
        Fx fastGear = action(rightX, y(3), columnW, "FAST + RANDOM GEAR", 1, () -> fastSpawn(true));
        fastGear.dynLabel = () -> Actions.fastSpawnPending(Actions.amount(), true) ? "CONFIRM FAST + GEAR" : "FAST + RANDOM GEAR";
        info(contentX, y(4), contentW, () -> "FAST = VexBot mass_spawn  \u2022  random names, no per-bot gear/difficulty/team");
    }

    private void fastSpawn(boolean randomGear) {
        if (!needConnection()) return;
        int amount = Actions.amount();
        if (!Actions.confirmFastSpawn(amount, randomGear)) {
            status = "CONFIRM  \u2022  Press again within 5 s to mass-spawn " + amount + " bots";
            return;
        }
        VexBotGuiAddon.runCommand(Config.cmd(randomGear ? "massspawngear" : "massspawn", null, String.valueOf(amount)));
        sent("FAST SPAWN  \u2022  " + amount + " bot(s) near you, VexBot defaults");
    }

    // ---------------- KITS: VexBot's own kit system ----------------
    private static String newKitText = "";

    private void buildKits() {
        Actions.autoRefreshKitsOnce();
        Actions.ensureKitSelection();

        int refreshW = Math.max(80, contentW / 3);
        Fx pick = action(contentX, y(0), contentW - refreshW - 8, "KIT", 0, () -> Actions.cycleKit());
        pick.row = true; pick.labelColor = TEXT;
        pick.dynLabel = () -> Actions.selectedKit.isEmpty() ? "No kit selected" : Actions.selectedKit;
        pick.dynSub = () -> {
            if (Actions.selectedKit.isEmpty()) return "REFRESH, or save your inventory below";
            KitListParser.KitInfo k = Actions.kitInfo(Actions.selectedKit);
            if (k == null) return "not confirmed by the server";
            long age = (System.currentTimeMillis() - Actions.serverKitsAt) / 1000;
            return k.items() + " items  \u2022  " + age + "s" + (Actions.kitsStale ? "  \u2022  OLD DATA" : "");
        };
        Fx refresh = action(contentX + contentW - refreshW, y(0), refreshW, "REFRESH", 0, () -> {
            if (!needConnection()) return;
            Actions.refreshKits(null);
            status = "REFRESHING  \u2022  asking the server for its kit list";
            watchStatus = status; watchKind = 3;
        });
        refresh.dynLabel = () -> Actions.kitsRefreshing ? "..." : "REFRESH";

        action(contentX, y(1), columnW, "GIVE TO BOT", () -> {
            String k = selectedKitOrNull(); if (k == null) return;
            withBot("givekit", k, "KIT SENT  \u2022  " + k);
        });
        action(rightX, y(1), columnW, "GIVE TO TEAM", () -> {
            String k = selectedKitOrNull(); if (k == null) return;
            String t = selectedTeamOrNull(); if (t == null) return;
            if (!needConnection()) return;
            VexBotGuiAddon.runCommand(Config.cmd("kitgiveteam", "", t, k));
            sent("KIT " + k + " TO TEAM " + t);
        });

        field(contentX, y(2), contentW, "New kit name  \u2022  letters, digits, _ -", newKitText, 32, v -> newKitText = v);

        action(contentX, y(3), columnW, "SAVE MY INVENTORY", 1, () -> {
            String n = newKitText.trim();
            if (!Validate.kitName(n)) { status = "INVALID KIT NAME  \u2022  1-32 letters, digits, _ or -"; return; }
            if (Actions.kitInfo(n) != null) { status = "KIT EXISTS  \u2022  select it and use UPDATE"; return; }
            if (!needConnection()) return;
            Actions.saveKit(n, false);
            sent("SAVE " + n + "  \u2022  takes the items you carry; result is checked in chat");
        });
        Fx upd = action(rightX, y(3), columnW, "UPDATE FROM INVENTORY", 2, () -> {
            String k = selectedKitOrNull(); if (k == null) return;
            if (!needConnection()) return;
            if (!Actions.confirmKitAction("update", k)) {
                status = "CONFIRM  \u2022  Press again within 5 s: kit " + k + " will be overwritten by your inventory";
                return;
            }
            Actions.saveKit(k, true);
            sent("UPDATE " + k + "  \u2022  result is checked in chat");
        });
        upd.dynLabel = () -> Actions.kitConfirmPending("update", Actions.selectedKit) ? "CONFIRM UPDATE" : "UPDATE FROM INVENTORY";

        Fx del = action(contentX, y(4), columnW, "DELETE KIT", 2, () -> {
            String k = selectedKitOrNull(); if (k == null) return;
            if (!needConnection()) return;
            if (!Actions.confirmKitAction("delete", k)) {
                status = "CONFIRM  \u2022  Press again within 5 s to delete kit " + k;
                return;
            }
            Actions.deleteKit(k);
            sent("DELETE " + k + "  \u2022  result is checked in chat");
        });
        del.dynLabel = () -> Actions.kitConfirmPending("delete", Actions.selectedKit) ? "CONFIRM DELETE" : "DELETE KIT";
        action(rightX, y(4), columnW, "RANDOM KIT FOR BOT", () -> {
            List<String> names = Actions.kitNames();
            if (names.isEmpty()) { status = "NO KITS KNOWN  \u2022  press REFRESH first"; return; }
            withBot("kitrandom", String.join(" ", names.subList(0, Math.min(20, names.size()))), "RANDOM KIT SENT");
        });
        info(contentX, y(5), contentW, () -> Actions.kitsNote.isEmpty()
                ? "Kits are saved on the SERVER from the inventory of the player who clicks (needs OP)."
                : "KITS: " + Actions.kitsNote + (Actions.kitsStale && Actions.serverKitsAt > 0 ? "  (showing OLD data)" : ""));
    }

    private String selectedKitOrNull() {
        String k = Actions.selectedKit;
        if (k.isEmpty()) { status = "NO KIT SELECTED  \u2022  Press REFRESH, or save one first"; return null; }
        return k;
    }

    private void buildLoadout() {
        cycle(contentX, y(0), columnW, "ARMOR SET", Actions.ARMORS, () -> Actions.armor, v -> Actions.armor = v);
        cycle(rightX, y(0), columnW, "MAIN-HAND", Actions.WEAPONS, () -> Actions.weapon, v -> Actions.weapon = v);
        cycle(contentX, y(1), columnW, "OFF-HAND", Actions.OFFHANDS, () -> Actions.offhand, v -> Actions.offhand = v);
        action(rightX, y(1), columnW, "RANDOM GEAR", () -> withBot("randomgear", null, "RANDOM GEAR SENT"));
        action(contentX, y(2), contentW, "APPLY EQUIPMENT TO NAMED BOT", 1, () -> {
            if (!needConnection()) return;
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
            if (!needConnection()) return;
            VexBotGuiAddon.runCommand(Config.cmd("spawnkit", n, k));
            sent("SPAWN WITH KIT  \u2022  needs settings advancedspawn on");
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
            if (!needConnection()) return;
            String n = bot(); if (n == null) return;
            String[] keys = {"followstop", "attackstop", "hidestop", "gotostop", "wanderstop", "lookstop"};
            for (int i = 0; i < keys.length; i++) {
                VexBotGuiAddon.runLater(Config.cmd(keys[i], n, null), i * 2 + 1);
            }
            status = "QUEUED  \u2022  6 stop orders for " + n;
        });
    }

    private void buildTeams() {
        // two clearly separated jobs: make a new team, or manage one that exists
        int half = (contentW - 8) / 2;
        action(contentX, y(0), half, "CREATE NEW TEAM", teamMode == 0 ? 1 : 0, () -> { teamMode = 0; rebuildWidgets(); });
        action(contentX + half + 8, y(0), contentW - half - 8, "MANAGE A TEAM", teamMode == 1 ? 1 : 0, () -> { teamMode = 1; rebuildWidgets(); });
        if (teamMode == 0) buildTeamCreate(); else buildTeamManage();
    }

    private void buildTeamCreate() {
        field(contentX, y(1), contentW, "New team name  \u2022  letters, digits, _ or -", teamText, 24, v -> teamText = v);
        cycle(contentX, y(2), columnW, "TEAM SIZE", Actions.COUNTS, () -> Actions.teamSize, v -> Actions.teamSize = v);
        cycle(rightX, y(2), columnW, "LEVEL", Actions.DIFFICULTIES, () -> Actions.teamLevel, v -> Actions.teamLevel = v);
        Fx build = action(contentX, y(3), contentW, "BUILD TEAM  \u2022  spawn bots with gear and add them", 1, () -> {
            if (!needConnection()) return;
            String t = typedTeam(); if (t == null) return;
            int amount = Integer.parseInt(Actions.COUNTS[Actions.teamSize]);
            if (!Actions.confirmTeamBuild(t, amount, Actions.teamLevel)) {
                status = "CONFIRM  \u2022  Press BUILD TEAM again within 5 s: " + amount + " bots into " + t;
                return;
            }
            Actions.buildTeam(t, amount, Actions.teamLevel);
            teamMode = 1;
            status = "QUEUED  \u2022  " + t + "  \u2022  " + amount + " x " + Actions.DIFFICULTIES[Actions.teamLevel].toUpperCase(Locale.ROOT)
                    + "  \u2022  now managing it";
            rebuildWidgets();
        });
        build.dynLabel = () -> Actions.teamConfirmPending(teamText.trim(), Integer.parseInt(Actions.COUNTS[Actions.teamSize]), Actions.teamLevel)
                ? "CONFIRM BUILD  \u2022  press again" : "BUILD TEAM  \u2022  spawn bots with gear and add them";
        info(contentX, y(4), contentW, () -> "After building, the team appears under MANAGE A TEAM.");
    }

    private void buildTeamManage() {
        Actions.autoRefreshOnce(); // once per server session; a failed try is shown, never retried in a loop
        Actions.ensureSelection();

        // picker (click = next team) + refresh
        int refreshW = Math.max(80, contentW / 3);
        Fx pick = action(contentX, y(1), contentW - refreshW - 8, "TEAM", 0, () -> { Actions.cycleTeam(); confirmTeam = false; });
        pick.row = true; pick.labelColor = TEXT;
        pick.dynLabel = () -> Actions.selectedTeam.isEmpty() ? "No team selected" : Actions.selectedTeam;
        pick.dynSub = () -> {
            if (Actions.selectedTeam.isEmpty()) return "REFRESH or CREATE";
            TeamListParser.TeamInfo ti = Actions.teamInfo(Actions.selectedTeam);
            if (ti == null) return Actions.teamsStale ? "not confirmed \u2022 list unreadable" : "not confirmed by the server yet";
            long age = (System.currentTimeMillis() - Actions.serverTeamsAt) / 1000;
            return ti.bots() + " bots \u2022 " + ti.players() + " players \u2022 " + age + "s" + (Actions.teamsStale ? " \u2022 OLD DATA" : "");
        };
        Fx refresh = action(contentX + contentW - refreshW, y(1), refreshW, "REFRESH", 0, () -> {
            if (!needConnection()) return;
            Actions.refreshTeams();
            status = "REFRESHING  \u2022  asking the server for its team list";
            watchStatus = status; watchKind = 1;
        });
        refresh.dynLabel = () -> Actions.teamsRefreshing ? "..." : "REFRESH";

        action(contentX, y(2), columnW, "ADD BOT", () -> manage("teamadd2", null, true, "ADD SENT"));
        action(rightX, y(2), columnW, "REMOVE BOT", () -> manage("teamremove2", null, true, "REMOVE SENT"));
        action(contentX, y(3), columnW, "FOLLOW ME", () -> {
            if (!needConnection()) return;
            String team = selectedTeamOrNull(); if (team == null) return;
            Actions.followTeam(team, Actions.me());
            status = "TEAM FOLLOW QUEUED  •  rechecking this GUI-built team's members";
            watchStatus = status; watchKind = 0;
        });
        action(rightX, y(3), columnW, "STOP FOLLOW", () -> {
            if (!needConnection()) return;
            String team = selectedTeamOrNull(); if (team == null) return;
            Actions.stopTeamFollow(team);
            status = "TEAM FOLLOW-STOP SENT  •  waiting for server reply";
            watchStatus = status; watchKind = 0;
        });
        action(contentX, y(4), columnW, "APPLY LEVEL", () ->
                manage("teamdifficulty2", Actions.DIFFICULTIES[Actions.teamLevel], false,
                        "LEVEL SENT  \u2022  " + Actions.DIFFICULTIES[Actions.teamLevel].toUpperCase(Locale.ROOT)));
        action(rightX, y(4), columnW, "APPLY PLAYSTYLE", () ->
                manage("teamplaystyle2", Actions.STYLES[Actions.style], false,
                        "STYLE SENT  \u2022  " + Actions.STYLES[Actions.style].toUpperCase(Locale.ROOT)));
        action(contentX, y(5), columnW, "SHOW MEMBERS", () -> {
            if (!needConnection()) return;
            String t = selectedTeamOrNull(); if (t == null) return;
            Actions.fetchRoster(t);
            status = "ASKING  \u2022  members of " + t;
            watchStatus = status; watchKind = 2;
        });
        action(rightX, y(5), columnW, confirmTeam ? "CONFIRM DELETE" : "DELETE TEAM", 2, () -> {
            String target = selectedTeamOrNull(); if (target == null) return;
            long now = System.currentTimeMillis();
            if (!confirmTeam || !target.equals(confirmTeamName) || now > confirmTeamUntil) {
                confirmTeam = true; confirmTeamName = target; confirmTeamUntil = now + 5000;
                status = "CONFIRM  \u2022  Delete " + target + " within 5 s"; rebuildWidgets(); return;
            }
            if (!needConnection()) return;
            confirmTeam = false; confirmTeamName = "";
            Actions.deleteTeam(target); // sends, hides the team, then re-reads the server list to confirm
            sent("DELETE SENT  \u2022  " + target + "  \u2022  the result is reported in chat");
            rebuildWidgets();
        });

        // what we know about the selected team
        info(contentX, y(6), contentW, () -> {
            if (Actions.rosterLoading) return "Reading members...";
            if (!Actions.selectedTeam.isEmpty() && Actions.rosterTeam.equalsIgnoreCase(Actions.selectedTeam)) {
                if (!Actions.rosterLines.isEmpty()) return "MEMBERS  " + String.join("   |   ", Actions.rosterLines);
                if (Actions.rosterMissing) return "No member list received (not confirmed)";
            }
            if (!Actions.teamsNote.isEmpty()) {
                return "LIST: " + Actions.teamsNote + (Actions.teamsStale && Actions.serverTeamsAt > 0 ? "  (showing OLD data)" : "");
            }
            return "SHOW MEMBERS lists who is in the selected team.";
        });
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
            confirmPresetDelete = false; confirmPresetDeleteName = "";
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
            if (!Validate.presetName(n)) { status = "INVALID NAME  \u2022  1-24 chars, no leading/trailing spaces"; return; }
            if (!Config.presets.containsKey(n) && Config.presets.size() >= Validate.MAX_PRESETS) {
                status = "PRESET LIMIT  \u2022  Delete one before saving more"; return;
            }
            boolean existed = Config.presets.containsKey(n);
            confirmPresetDelete = false; confirmPresetDeleteName = "";
            Config.presets.put(n, Actions.snapshot());
            Config.savePresets();
            presetIdx = new ArrayList<>(Config.presets.keySet()).indexOf(n);
            status = Config.saveError == null ? (existed ? "UPDATED  \u2022  " : "SAVED  \u2022  ") + n
                    : "SAVE FAILED  \u2022  " + Config.saveError;
            rebuildWidgets();
        });
        action(contentX, y + 108, columnW, "SPAWN WITH PRESET", () -> {
            String n = selectedPreset(); if (n == null) return;
            Actions.apply(Config.presets.get(n));
            spawn();
        });
        action(right, y + 108, columnW, confirmPresetDelete ? "CONFIRM DELETE" : "DELETE", 2, () -> {
            String n = selectedPreset(); if (n == null) return;
            long now = System.currentTimeMillis();
            if (!confirmPresetDelete || !n.equals(confirmPresetDeleteName) || now > confirmPresetDeleteUntil) {
                confirmPresetDelete = true; confirmPresetDeleteName = n; confirmPresetDeleteUntil = now + 5000;
                status = "CONFIRM  \u2022  Delete " + n + " within 5 s";
                rebuildWidgets(); return;
            }
            confirmPresetDelete = false; confirmPresetDeleteName = "";
            Config.presets.remove(n);
            Config.savePresets();
            presetIdx = 0;
            status = Config.saveError == null ? "DELETED  \u2022  " + n : "SAVE FAILED  \u2022  " + Config.saveError;
            rebuildWidgets();
        });
    }

    private void buildConsole(int y, int right) {
        input = field(contentX, y, contentW, "Command without slash  \u2022  e.g. vexbot list", commandText, 256, v -> commandText = v);

        int gap = 8, w3 = (contentW - gap * 2) / 3;
        action(contentX, y + 32, w3, "SEND", 1, () -> {
            String c = Validate.cleanCommand(input.getValue());
            if (c.isEmpty()) { status = "INPUT REQUIRED  \u2022  Enter a command"; return; }
            if (!needConnection()) return;
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

    /** A bot we just asked to kill must not stay the default target of later orders. */
    private void afterKill(String n) {
        if (n.equalsIgnoreCase(Actions.lastBot)) Actions.lastBot = "";
    }

    private boolean needConnection() {
        if (VexBotGuiAddon.connected()) return true;
        status = "NO SERVER CONNECTION  \u2022  Join a world first";
        return false;
    }

    /** Name typed above, otherwise the bot spawned last. */
    private String bot() {
        String n = bot0();
        if (!n.isEmpty() && !Validate.botName(n)) { status = "INVALID NAME  \u2022  1-16 letters, digits or _"; return null; }
        if (n.isEmpty()) n = Actions.lastBot;
        if (n.isEmpty()) { status = "NO BOT YET  \u2022  Spawn one first or type a name above"; return null; }
        return n;
    }

    private String kit() {
        String k = kitText.trim();
        if (k.isEmpty()) { status = "KIT NAME REQUIRED  \u2022  Use LIST KITS to see names"; return null; }
        return k;
    }

    /** Team typed in the CREATE field; always explicit (never falls back to an older team). */
    private String typedTeam() {
        String n = teamText.trim();
        if (n.isEmpty()) { status = "TYPE THE TEAM NAME  \u2022  1-24 letters, digits, _ or -"; return null; }
        if (!Validate.teamName(n)) { status = "INVALID TEAM NAME  \u2022  1-24 letters, digits, _ or -"; return null; }
        return n;
    }

    /** The team picked on the MANAGE page, or null (with a status message). */
    private String selectedTeamOrNull() {
        String t = Actions.selectedTeam;
        if (t.isEmpty() || !Validate.teamName(t)) {
            status = "NO TEAM SELECTED  \u2022  Press REFRESH, or build one under CREATE";
            return null;
        }
        return t;
    }

    private String selectedPreset() {
        List<String> n = new ArrayList<>(Config.presets.keySet());
        if (n.isEmpty()) { status = "NO PRESETS  \u2022  Save one first"; return null; }
        return n.get(Math.min(presetIdx, n.size() - 1));
    }

    /** Status for a command that was sent: we only know it left this client, not that the server did it. */
    private void sent(String msg) {
        if (!VexBotGuiAddon.lastSendOk) { // nothing left this client: never show a success text
            status = "NOT SENT  \u2022  " + msg + "  \u2022  empty command or no connection";
            pendingAt = 0;
            return;
        }
        pendingText = msg;
        pendingMark = VexBotGuiAddon.logMark();
        pendingAt = System.currentTimeMillis();
        pendingStatus = msg + "  \u2022  sent, waiting for server";
        status = pendingStatus;
    }

    /** Called every frame: shows the first server message, or says plainly that none came. */
    private void updatePending() {
        if (pendingAt == 0) return;
        if (!status.equals(pendingStatus)) { pendingAt = 0; return; } // something newer replaced it
        List<String> replies = VexBotGuiAddon.logSince(pendingMark);
        if (!replies.isEmpty()) {
            // replies are NOT matched to one command: an error text is shown as such, the rest as "a message"
            String err = ReplyClassifier.firstError(replies);
            status = err != null ? "SERVER ERROR  \u2022  " + err + "  \u2022  (may belong to another command)"
                    : "SERVER MESSAGE  \u2022  " + replies.get(0) + "  \u2022  (not matched to this command)";
            pendingAt = 0;
        } else if (System.currentTimeMillis() - pendingAt > 4000) {
            status = pendingText + "  \u2022  no reply (not confirmed)";
            pendingAt = 0;
        }
    }

    /** Sends a template with the bot name from the top box; {arg} = arg. */
    private void withBot(String key, String arg, String msg) {
        if (!needConnection()) return;
        String n = bot(); if (n == null) return;
        VexBotGuiAddon.runCommand(Config.cmd(key, n, arg));
        sent(msg);
    }

    /** Team template on the SELECTED team: {arg} = team, {arg2} = arg2, {bot} = bot box when needBot. */
    private void manage(String key, String arg2, boolean needBot, String msg) {
        String t = selectedTeamOrNull(); if (t == null) return;
        String b = "";
        if (needBot) { b = bot(); if (b == null) return; }
        if (!needConnection()) return;
        Actions.lastTeam = t;
        VexBotGuiAddon.runCommand(Config.cmd(key, b, t, arg2));
        sent(msg + "  \u2022  " + t);
    }

    private void sendRaw(String cmd, String msg) {
        if (!needConnection()) return;
        VexBotGuiAddon.runCommand(cmd);
        sent(msg);
    }

    private void spawn() {
        if (!needConnection()) return;
        String base = bot0(); // empty = random names
        if (!Actions.nameOk(base)) { status = "INVALID NAME  \u2022  1-16 letters, digits or _"; return; }
        int amount = Integer.parseInt(Actions.COUNTS[Actions.count]);
        // same confirmation as the N key: one shared 5 s window
        if (!Actions.confirmMassSpawn(base)) {
            status = "CONFIRM  \u2022  Press SPAWN (or N) again within 5 s to create " + amount + " bots";
            return;
        }
        Actions.spawn(base);
        status = "QUEUED  \u2022  " + Actions.summary();
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        int lo = 0, hi = s.length(); // longest prefix that still fits together with "..."
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (font.width(s.substring(0, mid) + "...") <= maxW) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "...";
    }

    // ---------------- live preview (left card) ----------------
    private static final int[] ARM = {0, 0xFFA06540, 0xFF9EA4AA, 0xFFD9DADA, 0xFF3CE0D0, 0xFF5A4E55}; // none, leather, chain, iron, diamond, netherite
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    private static ItemStack stack(String id) {
        return STACKS.computeIfAbsent(id, k -> switch (k) {
            case "wooden_sword" -> new ItemStack(Items.WOODEN_SWORD);
            case "stone_sword" -> new ItemStack(Items.STONE_SWORD);
            case "iron_sword" -> new ItemStack(Items.IRON_SWORD);
            case "diamond_sword" -> new ItemStack(Items.DIAMOND_SWORD);
            case "netherite_sword" -> new ItemStack(Items.NETHERITE_SWORD);
            case "diamond_axe" -> new ItemStack(Items.DIAMOND_AXE);
            case "mace" -> new ItemStack(Items.MACE);
            case "bow" -> new ItemStack(Items.BOW);
            case "crossbow" -> new ItemStack(Items.CROSSBOW);
            case "trident" -> new ItemStack(Items.TRIDENT);
            case "shield" -> new ItemStack(Items.SHIELD);
            case "totem_of_undying" -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case "golden_apple" -> new ItemStack(Items.GOLDEN_APPLE);
            default -> ItemStack.EMPTY;
        });
    }

    private static int mul(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f));
        int gr = Math.min(255, (int) (((c >> 8) & 255) * f));
        int b = Math.min(255, (int) ((c & 255) * f));
        return (c & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    /** Filled rectangle with a light top-left and dark bottom-right edge. */
    private static void box(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
        g.fill(x, y, x + w, y + 1, mul(c, 1.18f));
        g.fill(x, y, x + 1, y + h, mul(c, 1.10f));
        g.fill(x, y + h - 1, x + w, y + h, mul(c, 0.78f));
        g.fill(x + w - 1, y, x + w, y + h, mul(c, 0.85f));
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy += 2) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 2, argb);
        }
    }

    /** Glow behind the bot: bigger, brighter and with more sparks the higher the difficulty (d = 0..4). */
    private static void aura(GuiGraphics g, int cx, int cy, int maxR, int d) {
        double ph = (System.currentTimeMillis() % 100000L) / 700.0;
        double scale = Math.min(1.0, 0.45 + 0.14 * d);
        double radius = maxR * scale * (1.0 + 0.03 * Math.sin(ph));
        int rgb = QCOL[Math.min(d, 4)] & 0xFFFFFF;
        int layers = 5 + d * 2, alpha = 12 + d * 4;
        for (int j = 0; j < layers; j++) {
            disc(g, cx, cy, (int) (radius * (1.0 - 0.85 * j / layers)), (alpha << 24) | rgb);
        }
        int sparks = d * 2, size = d >= 4 ? 3 : 2;
        for (int i = 0; i < sparks; i++) {
            double ang = ph * (0.8 + 0.1 * i) + i * (Math.PI * 2 / sparks);
            double rad = radius * (0.72 + 0.16 * Math.sin(ph * 1.7 + i));
            int sx = cx + (int) (Math.cos(ang) * rad), sy = cy + (int) (Math.sin(ang) * rad);
            g.fill(sx, sy, sx + size, sy + size, 0xE0000000 | rgb);
        }
    }

    /** Blocky bot, front view: k = pixels per "model unit" (a head is 8 units wide). */
    private static void figure(GuiGraphics g, int cx, int y0, int k, Actions.Recipe r) {
        int skin = 0xFFE2B08A, hair = 0xFF4B3524, shirt = 0xFF2FA4A4, pants = 0xFF3A3F8F, shoe = 0xFF4A4A50;
        // legs, body, arms, head
        box(g, cx - 4 * k, y0 + 20 * k, 4 * k, 12 * k, pants);
        box(g, cx, y0 + 20 * k, 4 * k, 12 * k, pants);
        box(g, cx - 4 * k, y0 + 30 * k, 4 * k, 2 * k, shoe);
        box(g, cx, y0 + 30 * k, 4 * k, 2 * k, shoe);
        box(g, cx - 4 * k, y0 + 8 * k, 8 * k, 12 * k, shirt);
        box(g, cx - 8 * k, y0 + 8 * k, 4 * k, 12 * k, skin);
        box(g, cx + 4 * k, y0 + 8 * k, 4 * k, 12 * k, skin);
        box(g, cx - 8 * k, y0 + 8 * k, 4 * k, 4 * k, shirt);
        box(g, cx + 4 * k, y0 + 8 * k, 4 * k, 4 * k, shirt);
        box(g, cx - 4 * k, y0, 8 * k, 8 * k, skin);
        g.fill(cx - 4 * k, y0, cx + 4 * k, y0 + 2 * k, hair);
        g.fill(cx - 4 * k, y0 + 2 * k, cx - 3 * k, y0 + 3 * k, hair);
        g.fill(cx + 3 * k, y0 + 2 * k, cx + 4 * k, y0 + 3 * k, hair);
        g.fill(cx - 3 * k, y0 + 4 * k, cx - 2 * k, y0 + 5 * k, 0xFFFFFFFF);
        g.fill(cx - 2 * k, y0 + 4 * k, cx - k, y0 + 5 * k, 0xFF3A2A6A);
        g.fill(cx + k, y0 + 4 * k, cx + 2 * k, y0 + 5 * k, 0xFF3A2A6A);
        g.fill(cx + 2 * k, y0 + 4 * k, cx + 3 * k, y0 + 5 * k, 0xFFFFFFFF);

        // armor, tinted by material
        int a = r.armor();
        if (a > 0 && a < ARM.length) {
            int c = ARM[a];
            box(g, cx - 4 * k, y0 + 20 * k, 4 * k, 8 * k, c);          // leggings
            box(g, cx, y0 + 20 * k, 4 * k, 8 * k, c);
            box(g, cx - 4 * k, y0 + 28 * k, 4 * k, 4 * k, mul(c, 0.82f)); // boots
            box(g, cx, y0 + 28 * k, 4 * k, 4 * k, mul(c, 0.82f));
            box(g, cx - 4 * k, y0 + 8 * k, 8 * k, 12 * k, c);          // chestplate
            g.fill(cx - 1, y0 + 8 * k, cx + 1, y0 + 20 * k, mul(c, 0.8f));
            box(g, cx - 8 * k, y0 + 8 * k, 4 * k, 10 * k, c);
            box(g, cx + 4 * k, y0 + 8 * k, 4 * k, 10 * k, c);
            box(g, cx - 4 * k, y0, 8 * k, 3 * k, c);                    // helmet (face stays visible)
            box(g, cx - 4 * k, y0 + 3 * k, k, 5 * k, c);
            box(g, cx + 3 * k, y0 + 3 * k, k, 5 * k, c);
        }
    }

    // ---------------- real player model ----------------
    private RemotePlayer previewBot;
    private boolean botFailed;

    private static ItemStack armorStack(String material, String piece) {
        return STACKS.computeIfAbsent(material + "_" + piece, k -> switch (k) {
            case "leather_helmet" -> new ItemStack(Items.LEATHER_HELMET);
            case "leather_chestplate" -> new ItemStack(Items.LEATHER_CHESTPLATE);
            case "leather_leggings" -> new ItemStack(Items.LEATHER_LEGGINGS);
            case "leather_boots" -> new ItemStack(Items.LEATHER_BOOTS);
            case "chainmail_helmet" -> new ItemStack(Items.CHAINMAIL_HELMET);
            case "chainmail_chestplate" -> new ItemStack(Items.CHAINMAIL_CHESTPLATE);
            case "chainmail_leggings" -> new ItemStack(Items.CHAINMAIL_LEGGINGS);
            case "chainmail_boots" -> new ItemStack(Items.CHAINMAIL_BOOTS);
            case "iron_helmet" -> new ItemStack(Items.IRON_HELMET);
            case "iron_chestplate" -> new ItemStack(Items.IRON_CHESTPLATE);
            case "iron_leggings" -> new ItemStack(Items.IRON_LEGGINGS);
            case "iron_boots" -> new ItemStack(Items.IRON_BOOTS);
            case "diamond_helmet" -> new ItemStack(Items.DIAMOND_HELMET);
            case "diamond_chestplate" -> new ItemStack(Items.DIAMOND_CHESTPLATE);
            case "diamond_leggings" -> new ItemStack(Items.DIAMOND_LEGGINGS);
            case "diamond_boots" -> new ItemStack(Items.DIAMOND_BOOTS);
            case "netherite_helmet" -> new ItemStack(Items.NETHERITE_HELMET);
            case "netherite_chestplate" -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case "netherite_leggings" -> new ItemStack(Items.NETHERITE_LEGGINGS);
            case "netherite_boots" -> new ItemStack(Items.NETHERITE_BOOTS);
            case "elytra_elytra" -> new ItemStack(Items.ELYTRA);
            default -> ItemStack.EMPTY;
        });
    }

    private static void wear(RemotePlayer p, EquipmentSlot slot, ItemStack stack) {
        if (p.getItemBySlot(slot).getItem() != stack.getItem()) p.setItemSlot(slot, stack.copy());
    }

    private static void dress(RemotePlayer p, Actions.Recipe r) {
        String mat = Actions.ARMORS[r.armor()];
        boolean none = mat.equals("none");
        boolean elytra = mat.equals("elytra");
        wear(p, EquipmentSlot.HEAD, none || elytra ? ItemStack.EMPTY : armorStack(mat, "helmet"));
        wear(p, EquipmentSlot.CHEST, none ? ItemStack.EMPTY : armorStack(mat, elytra ? "elytra" : "chestplate"));
        wear(p, EquipmentSlot.LEGS, none || elytra ? ItemStack.EMPTY : armorStack(mat, "leggings"));
        wear(p, EquipmentSlot.FEET, none || elytra ? ItemStack.EMPTY : armorStack(mat, "boots"));
        wear(p, EquipmentSlot.MAINHAND, stack(Actions.WEAPONS[r.weapon()]));
        wear(p, EquipmentSlot.OFFHAND, stack(Actions.OFFHANDS[r.offhand()]));
    }

    /** Draws the same kind of live player model the inventory screen shows. It follows your mouse. */
    private boolean drawRealBot(GuiGraphics g, int areaTop, int areaBottom, Actions.Recipe r, int mouseX, int mouseY) {
        if (botFailed) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;
            if (previewBot == null) {
                UUID id = UUID.nameUUIDFromBytes("vexbot-preview".getBytes(StandardCharsets.UTF_8));
                previewBot = new RemotePlayer(mc.level, new GameProfile(id, "VexBot"));
            }
            dress(previewBot, r);
            int areaH = areaBottom - areaTop;
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, previewX + 6, areaTop, previewX + previewW - 6, areaBottom,
                    (int) (areaH * 0.36), 0.0625F, mouseX, mouseY, previewBot);
            return true;
        } catch (Throwable ex) {
            botFailed = true;
            previewBot = null;
            return false;
        }
    }

    private void drawPreview(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 5; i >= 1; i--) soft(g, previewX - i, panelY - i + 2, previewX + previewW + i, panelY + panelH + i + 2, 0x12000000);
        soft(g, previewX, panelY, previewX + previewW, panelY + panelH, BG);

        Actions.Recipe r;
        String who;
        if (tab == T_QUICK && hoverTier >= 0) {
            r = Actions.Recipe.of(Actions.TIERS[hoverTier]); who = "HOVERED LEVEL";
        } else if (tab == T_TEAMS) {
            r = Actions.Recipe.of(Actions.TIERS[Actions.teamLevel]); who = "TEAM BOTS";
        } else {
            r = Actions.Recipe.current(); who = "YOUR SETUP";
        }
        int d = Math.max(0, Math.min(4, r.difficulty()));

        bold(g, font, "PREVIEW", previewX + 14, panelY + 16, TEXT);
        g.drawString(font, who, previewX + 14, panelY + 30, FAINT, false);

        int areaTop = panelY + 44, areaBottom = panelY + panelH - 92;
        int areaH = Math.max(60, areaBottom - areaTop);
        int cx = previewX + previewW / 2, cy = areaTop + areaH / 2;
        int k = areaH >= 170 ? 4 : areaH >= 110 ? 3 : 2;
        int y0 = cy - 16 * k;

        g.enableScissor(previewX + 1, areaTop, previewX + previewW - 1, areaBottom);
        aura(g, cx, cy, Math.min(areaH / 2, previewW / 2 - 2), d);
        g.disableScissor();

        // a REAL player model wearing / holding the selected items (falls back to the drawn bot if unavailable)
        if (!drawRealBot(g, areaTop, areaBottom, r, mouseX, mouseY)) {
            figure(g, cx, y0, k, r);
            ItemStack main = stack(Actions.WEAPONS[r.weapon()]);
            if (!main.isEmpty()) g.renderItem(main, cx - 8 * k - 4, y0 + 20 * k - 14);
            String off = Actions.OFFHANDS[r.offhand()];
            ItemStack offStack = stack(off);
            if (!offStack.isEmpty()) {
                if (off.equals("shield")) g.renderItem(offStack, cx + 4 * k - 2, y0 + 12 * k);
                else g.renderItem(offStack, cx + 8 * k - 12, y0 + 19 * k - 8);
            }
        }

        // caption: level pips + what the bot wears
        int ly = panelY + panelH - 84, lx = previewX + 14, valueX = lx + 46;
        for (int i = 0; i < 5; i++) g.fill(lx + i * 13, ly, lx + i * 13 + 10, ly + 6, i <= d ? QCOL[i] : 0xFF263A3D);
        g.drawString(font, Actions.DIFFICULTIES[d].toUpperCase(Locale.ROOT), lx, ly + 11, QCOL[d], false);
        int vw = previewW - 14 - (valueX - previewX);
        String[][] rows = {
                {"ARMOR", Actions.ARMORS[r.armor()]},
                {"MAIN", Actions.WEAPONS[r.weapon()]},
                {"OFF", Actions.OFFHANDS[r.offhand()]},
                {"STYLE", Actions.STYLES[r.style()]}};
        for (int i = 0; i < rows.length; i++) {
            int ry = ly + 27 + i * 11;
            g.drawString(font, rows[i][0], lx, ry, FAINT, false);
            g.drawString(font, fit(rows[i][1].replace('_', ' ').toUpperCase(Locale.ROOT), vw), valueX, ry, TEXT, false);
        }
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
        tickAnim();

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
        {   // highlight glides to the active tab
            float target = navY + tab * navStep;
            navAnim = Float.isNaN(navAnim) ? target : navAnim + (target - navAnim) * smooth(16f);
            int ay = Math.round(navAnim), anx = panelX + 8, anw = sideW - 16, anh = navStep - 2;
            soft(g, anx, ay, anx + anw, ay + anh, 0x335EEAD4);
            g.fill(anx, ay + 3, anx + 2, ay + anh - 3, ACCENT);
        }
        for (int i = 0; i < TABS.length; i++) {
            int ny = navY + i * navStep, nx = panelX + 8, nw = sideW - 16, nh = navStep - 2;
            boolean act = i == tab, hov = in(mouseX, mouseY, nx, ny, nw, nh);
            if (hov && !act) soft(g, nx, ny, nx + nw, ny + nh, 0x1AFFFFFF);
            if (nh >= ICON_SIZE) {
                // 1:1 pixel icons (same blit call form as the mascot)
                g.blit(RenderPipelines.GUI_TEXTURED, act ? ICON_ON[i] : ICON[i], nx + 8, ny + (nh - ICON_SIZE) / 2,
                        0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            }
            g.drawString(font, fit(TABS[i], nw - 34), nx + 25, ny + (nh - 8) / 2, act ? TEXT : hov ? 0xFFC3D8D0 : MUTED, false);
        }

        // page header
        bold(g, font, TITLES[tab], contentX, panelY + 16, TEXT);
        g.drawString(font, fit(SUBS[tab], contentW), contentX, panelY + 30, MUTED, false);
        g.drawString(font, "BOT NAME  (optional)", contentX, fieldY - 10, FAINT, false);

        // input fields
        for (Fld f : flds) {
            soft(g, f.x, f.y, f.x + f.w, f.y + f.h, 0xFF101B1D);
            g.fill(f.x + 1, f.y + f.h - 1, f.x + f.w - 1, f.y + f.h, f.box.isFocused() ? ACCENT : LINE);
        }

        // console log card
        if (tab == T_CONSOLE) {
            soft(g, contentX, contentY + 70, contentX + contentW, footerY - 10, 0x66101A1C);
        }

        if (tab == T_DASHBOARD) {
            int gap = 8, tileW = (contentW - gap) / 2, tileH = Math.max(20, Math.min(28, bh + 2));
            String server = VexBotGuiAddon.connected() ? "CONNECTED" : "NO SERVER";
            String left = "SERVER: " + server + "  \u2022  PRESETS: " + Config.presets.size();
            String right = "LAST BOT: " + (Actions.lastBot.isEmpty() ? "—" : Actions.lastBot)
                    + "  \u2022  LAST TEAM: " + (Actions.lastTeam.isEmpty() ? "—" : Actions.lastTeam);
            int[] colors = {VexBotGuiAddon.connected() ? 0xFF6EE7A8 : 0xFFFF9A90, ACCENT};
            String[] values = {left, right};
            for (int i = 0; i < 2; i++) {
                int tx = contentX + i * (tileW + gap), ty = y(0), tw = i == 0 ? tileW : contentW - tileW - gap;
                soft(g, tx, ty, tx + tw, ty + tileH, 0xFF172528);
                g.fill(tx + 1, ty + 3, tx + 3, ty + tileH - 3, colors[i]);
                g.drawString(font, fit(values[i], tw - 18), tx + 10, ty + (tileH - 8) / 2, TEXT, false);
            }
        }

        // flat buttons
        hoverTier = -1;
        for (Fx f : fxs) {
            boolean hov = !f.info && in(mouseX, mouseY, f.x, f.y, f.w, f.h);
            if (hov && f.tier >= 0) hoverTier = f.tier;
            String fLabel = f.dynLabel != null ? f.dynLabel.get() : f.label;
            String fSub = f.dynSub != null ? f.dynSub.get() : f.sub;
            f.hv += ((hov ? 1f : 0f) - f.hv) * smooth(22f);
            int bg = f.info ? 0xFF152427 : switch (f.style) {
                case 1 -> lerpColor(0xFF265B56, 0xFF2F6F68, f.hv);
                case 2 -> lerpColor(0xFF40272B, 0xFF5A3036, f.hv);
                default -> lerpColor(0xFF1E3033, 0xFF2A4141, f.hv);
            };
            soft(g, f.x, f.y, f.x + f.w, f.y + f.h, bg);
            int ty = f.y + (f.h - 8) / 2;
            if (f.row) {
                int lx = f.x + 12;
                if (f.accent != 0) {
                    g.fill(f.x + 11, ty, f.x + 19, ty + 8, f.accent);
                    lx = f.x + 26;
                }
                int sw = fSub == null ? 0 : font.width(fSub);
                g.drawString(font, fit(fLabel, f.w - (lx - f.x) - sw - 22), lx, ty, f.labelColor, false);
                if (fSub != null) {
                    int shown = Math.min(sw, f.w / 2 + 20);
                    g.drawString(font, fit(fSub, f.w / 2 + 20), f.x + f.w - 12 - shown, ty, f.subColor, false);
                }
            } else {
                String l = fit(fLabel, f.w - 12);
                g.drawString(font, l, f.x + (f.w - font.width(l)) / 2, ty, TEXT, false);
            }
        }

        if (previewOn) drawPreview(g, mouseX, mouseY);

        updatePending();

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
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (tab == T_CONSOLE && mouseX >= contentX && mouseX <= contentX + contentW
                && mouseY >= contentY + 70 && mouseY <= footerY - 10) {
            int max = Math.max(0, VexBotGuiAddon.LOG.size() - Math.max(1, (footerY - 14 - (contentY + 76)) / 10));
            consoleScroll = Math.max(0, Math.min(max, consoleScroll + (deltaY > 0 ? 3 : deltaY < 0 ? -3 : 0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        if (tab == T_CONSOLE) {
            int top = contentY + 76;
            int bottom = footerY - 14;
            int lines = Math.max(1, (bottom - top) / 10);
            // config warnings stay on top: they are not part of the scrolling log and cannot scroll away
            List<String> warn = Config.allProblems();
            int shownWarn = Math.min(warn.size(), Math.max(0, lines - 2));
            for (int i = 0; i < shownWarn; i++) {
                g.drawString(font, fit("! " + warn.get(i), contentW - 16), contentX + 8, top + i * 10, 0xFFF6E27A, false);
            }
            top += shownWarn * 10;
            lines -= shownWarn;
            LogBuffer log = VexBotGuiAddon.LOG;
            if (log.size() == 0) {
                g.drawString(font, fit("Empty. Replies are recorded for 5 s after each send; they are not matched to one command.", contentW - 16),
                        contentX + 8, top, FAINT, false);
            } else {
                int from = Math.max(0, log.size() - lines - consoleScroll);
                int to = Math.min(log.size(), from + lines);
                for (int i = from; i < to; i++) {
                    LogBuffer.Entry e = log.get(i);
                    String mark = e.dir() == LogBuffer.Dir.OUT ? "> " : e.dir() == LogBuffer.Dir.IN ? "< " : "! ";
                    int color = e.error() ? 0xFFFF9A90 : e.dir() == LogBuffer.Dir.OUT ? 0xFF7FE3C9
                            : e.dir() == LogBuffer.Dir.INFO ? 0xFFF6E27A : 0xFFD8EAE2;
                    g.drawString(font, fit("[" + e.time() + "] " + mark + e.text(), contentW - 16), contentX + 8,
                            top + (i - from) * 10, color, false);
                }
            }
        }
        // opening fade: the panel eases in over ~0.28 s
        if (openT < 1f) {
            float e = 1f - (1f - openT) * (1f - openT) * (1f - openT);
            int a = (int) (235 * (1f - e));
            if (a > 0) g.fill(panelX - 8, panelY - 8, panelX + panelW + 8, panelY + panelH + 8, (a << 24) | 0x0C1416);
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        boolean expired = false;
        if (confirmKill && now > confirmKillUntil) { confirmKill = false; confirmKillName = ""; expired = true; }
        if (confirmTeam && now > confirmTeamUntil) { confirmTeam = false; confirmTeamName = ""; expired = true; }
        if (confirmPresetDelete && now > confirmPresetDeleteUntil) { confirmPresetDelete = false; confirmPresetDeleteName = ""; expired = true; }
        if (expired) {
            status = "CONFIRMATION EXPIRED  \u2022  nothing was done";
            rebuildWidgets(); // the button goes back to its normal label
        }
        if (watchKind == 1 && !Actions.teamsRefreshing && status.equals(watchStatus)) {
            status = Actions.teamsStale ? "REFRESH FAILED  \u2022  " + Actions.teamsNote
                    : "REFRESHED  \u2022  " + Actions.teamNames().size() + " team(s) known";
            watchKind = 0;
        } else if (watchKind == 2 && !Actions.rosterLoading && status.equals(watchStatus)) {
            status = Actions.rosterMissing ? "NO MEMBER LIST RECEIVED  \u2022  not confirmed"
                    : "MEMBERS READ  \u2022  " + Actions.rosterTeam;
            watchKind = 0;
        } else if (watchKind == 3 && !Actions.kitsRefreshing && status.equals(watchStatus)) {
            status = Actions.kitsStale ? "REFRESH FAILED  \u2022  " + Actions.kitsNote
                    : "REFRESHED  \u2022  " + Actions.kitNames().size() + " kit(s) known";
            watchKind = 0;
        } else if (!status.equals(watchStatus)) {
            watchKind = 0;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
