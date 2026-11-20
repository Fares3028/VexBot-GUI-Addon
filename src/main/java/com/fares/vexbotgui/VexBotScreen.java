package com.fares.vexbotgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VexBotScreen extends Screen {
    // ===== Command templates: edit here if a command needs different syntax =====
    // {bot} = bot name, {style} = playstyle
    private static final String CMD_SPAWN = "vexbot spawn {bot}";
    private static final String CMD_KILL = "vexbot kill {bot}";
    private static final String CMD_LIST = "vexbot list";
    private static final String CMD_DIFF = "vexbot difficulty preset {style}";
    private static final String CMD_RANDOMGEAR = "vexbot randomgear";
    private static final String CMD_PLAYSTYLE = "vexbot playstyle {bot} {style}";
    private static final String CMD_RANDOMSTYLE = "vexbot randomplaystyle";
    private static final String CMD_FOLLOW = "vexbot follow {bot}";
    private static final String CMD_ATTACK = "vexbot attack {bot}";
    private static final String CMD_STOP = "vexbot stop {bot}";
    private static final String CMD_HIDE = "vexbot hide {bot}";
    private static final String CMD_WANDER = "vexbot wander {bot}";
    private static final String CMD_TP = "vexbot tp {bot}";
    // ============================================================================

    private static final String[] TABS = {"SPAWN", "EQUIP", "AI", "CONTROL", "CUSTOM"};
    private static final String[] DIFFS = {"easy", "medium", "hard", "expert", "pro", "perfect"};
    private static final String[] COUNTS = {"1", "2", "3", "5", "10"};
    private static final String[] ARMORS = {"none", "leather", "chainmail", "iron", "diamond", "netherite"};
    private static final String[] WEAPONS = {"none", "diamond_sword", "netherite_sword", "diamond_axe", "mace", "bow", "crossbow", "trident"};
    private static final String[] OFFHANDS = {"none", "shield", "totem_of_undying", "golden_apple"};
    private static final String[] STYLES = {"default", "safe", "passive", "defensive", "balanced", "aggressive", "adaptive"};

    // Remembered between openings
    private static String lastName = "", lastCustom = "";
    private static int tab = 0;
    private static final int[] DIFF = {1}, COUNT = {0}, ARMOR = {0}, WEAPON = {0}, OFFHAND = {0}, STYLE = {5};

    private EditBox botName, custom;
    private String status = "Choose a name and configure your bot.";
    private final int panelW = 330, panelH = 250;
    private int panelX, panelY;

    public VexBotScreen() { super(Component.literal("VexBot Control")); }

    @Override
    protected void init() {
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int left = panelX + 20;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button t = Button.builder(Component.literal(TABS[i]), b -> { tab = idx; rebuildWidgets(); })
                    .bounds(left + i * 58, panelY + 12, 56, 18).build();
            t.active = (i != tab);
            addRenderableWidget(t);
        }

        botName = new EditBox(font, left, panelY + 42, 290, 20, Component.literal("Bot name"));
        botName.setMaxLength(16);
        botName.setHint(Component.literal("Bot name, e.g. TrainingBot"));
        botName.setValue(lastName);
        botName.setResponder(s -> lastName = s);
        addRenderableWidget(botName);

        int y = panelY + 76;
        int c2 = left + 150;
        switch (tab) {
            case 0 -> { // SPAWN
                cyc(left, y, "Bots", COUNTS, COUNT);
                cyc(c2, y, "Difficulty", DIFFS, DIFF);
                btn(left, y + 30, 140, 24, "Spawn Bot", this::spawn);
                btn(c2, y + 30, 140, 24, "Kill Bot", () -> withBot(CMD_KILL, "Kill sent."));
                btn(left, y + 60, 140, 22, "List Bots", () -> { send(CMD_LIST); status = "List requested."; });
                btn(c2, y + 60, 140, 22, "Close", this::onClose);
            }
            case 1 -> { // EQUIP
                cyc(left, y, "Armor", ARMORS, ARMOR);
                cyc(c2, y, "Weapon", WEAPONS, WEAPON);
                cyc(left, y + 28, "Offhand", OFFHANDS, OFFHAND);
                btn(c2, y + 28, 140, 22, "Random Gear", () -> { send(CMD_RANDOMGEAR); status = "Random gear sent."; });
                btn(left, y + 58, 290, 24, "Equip Bot (uses name above)", () -> {
                    String n = name(); if (n == null) return;
                    equip(n, 1); status = "Equipment sent to " + n + ".";
                });
            }
            case 2 -> { // AI
                cyc(left, y, "Difficulty", DIFFS, DIFF);
                cyc(c2, y, "Style", STYLES, STYLE);
                btn(left, y + 30, 140, 24, "Apply Difficulty", () -> {
                    send(CMD_DIFF.replace("{style}", DIFFS[DIFF[0]])); status = "Difficulty sent.";
                });
                btn(c2, y + 30, 140, 24, "Apply Style", () -> withBot(CMD_PLAYSTYLE.replace("{style}", STYLES[STYLE[0]]), "Style sent."));
                btn(left, y + 60, 290, 22, "Random Playstyle", () -> { send(CMD_RANDOMSTYLE); status = "Random style sent."; });
            }
            case 3 -> { // CONTROL
                btn(left, y, 140, 22, "Follow Me", () -> withBot(CMD_FOLLOW, "Follow sent."));
                btn(c2, y, 140, 22, "Attack", () -> withBot(CMD_ATTACK, "Attack sent."));
                btn(left, y + 28, 140, 22, "Stop", () -> withBot(CMD_STOP, "Stop sent."));
                btn(c2, y + 28, 140, 22, "Hide", () -> withBot(CMD_HIDE, "Hide sent."));
                btn(left, y + 56, 140, 22, "Wander", () -> withBot(CMD_WANDER, "Wander sent."));
                btn(c2, y + 56, 140, 22, "Teleport", () -> withBot(CMD_TP, "Teleport sent."));
            }
            default -> { // CUSTOM
                custom = new EditBox(font, left, y, 290, 20, Component.literal("Command"));
                custom.setMaxLength(200);
                custom.setHint(Component.literal("e.g. vexbot spawn Bob (no slash)"));
                custom.setValue(lastCustom);
                custom.setResponder(s -> lastCustom = s);
                addRenderableWidget(custom);
                btn(left, y + 30, 290, 24, "Send Command", () -> {
                    String c = custom.getValue().trim();
                    if (c.startsWith("/")) c = c.substring(1);
                    if (c.isEmpty()) { status = "Type a command first."; return; }
                    send(c); status = "Sent: /" + c;
                });
            }
        }
    }

    // ---------- helpers ----------
    private void cyc(int x, int y, String title, String[] opts, int[] st) {
        addRenderableWidget(Button.builder(Component.literal(title + ": " + opts[st[0]].toUpperCase()), b -> {
            st[0] = (st[0] + 1) % opts.length;
            b.setMessage(Component.literal(title + ": " + opts[st[0]].toUpperCase()));
        }).bounds(x, y, 140, 22).build());
    }

    private void btn(int x, int y, int w, int h, String label, Runnable r) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, w, h).build());
    }

    private String name() {
        String n = botName.getValue().trim();
        if (n.isEmpty()) { status = "Enter a bot name first."; return null; }
        return n;
    }

    private void withBot(String template, String ok) {
        String n = name(); if (n == null) return;
        send(template.replace("{bot}", n));
        status = ok;
    }

    private void send(String cmd) { VexBotGuiAddon.runCommand(cmd); }

    private void spawn() {
        String base = name(); if (base == null) return;
        int count = Integer.parseInt(COUNTS[COUNT[0]]);
        for (int i = 1; i <= count; i++) {
            // Names stay within 16 chars
            String n = count == 1 ? base : (base.length() > 13 ? base.substring(0, 13) : base) + i;
            int d = (i - 1) * 8;
            VexBotGuiAddon.runLater(CMD_SPAWN.replace("{bot}", n), d + 1);
            VexBotGuiAddon.runLater(CMD_DIFF.replace("{style}", DIFFS[DIFF[0]]), d + 11);
            equip(n, d + 16);
        }
        status = "Spawning " + count + " bot(s) named " + base + ".";
    }

    /** Vanilla /item replace (requires op). */
    private void equip(String n, int delay) {
        String a = ARMORS[ARMOR[0]];
        if (!a.equals("none")) {
            String[][] slots = {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}};
            for (String[] s : slots) {
                VexBotGuiAddon.runLater("item replace entity " + n + " armor." + s[0]
                        + " with minecraft:" + a + "_" + s[1], delay);
            }
        }
        if (!WEAPONS[WEAPON[0]].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.mainhand with minecraft:" + WEAPONS[WEAPON[0]], delay);
        }
        if (!OFFHANDS[OFFHAND[0]].equals("none")) {
            VexBotGuiAddon.runLater("item replace entity " + n + " weapon.offhand with minecraft:" + OFFHANDS[OFFHAND[0]], delay);
        }
    }

    // ---------- drawing ----------
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF111923);
        g.fill(panelX, panelY, panelX + panelW, panelY + 3, 0xFF35D0A0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(font, status, panelX + 20, panelY + 232, 0xFFB7C5D3, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
