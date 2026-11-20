package com.fares.vexbotgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VexBotScreen extends Screen {
    // Remembered between openings
    private static String lastName = "";
    private static int diffIdx = 1;
    private static int armorIdx = 0;

    private static final String[] DIFFS = {"easy", "medium", "hard"};
    private static final String[] ARMORS = {"none", "leather", "chainmail", "iron", "diamond", "netherite"};

    private EditBox botName;
    private Button diffBtn, armorBtn;
    private String status = "Choose a name and configure your bot.";
    private final int panelW = 330, panelH = 270;
    private int panelX, panelY;

    public VexBotScreen() { super(Component.literal("VexBot Control")); }

    @Override
    protected void init() {
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int left = panelX + 20;
        int y = panelY + 80;

        botName = new EditBox(font, left, y, 290, 20, Component.literal("Bot name"));
        botName.setMaxLength(16);
        botName.setHint(Component.literal("e.g. TrainingBot"));
        botName.setValue(lastName);
        addRenderableWidget(botName);

        diffBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            diffIdx = (diffIdx + 1) % DIFFS.length; refreshLabels();
        }).bounds(left, y + 30, 140, 22).build());

        armorBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            armorIdx = (armorIdx + 1) % ARMORS.length; refreshLabels();
        }).bounds(left + 150, y + 30, 140, 22).build());

        addRenderableWidget(Button.builder(Component.literal("Spawn Bot"), b -> spawn())
                .bounds(left, y + 62, 140, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Random Gear"), b -> {
            send("vexbot randomgear"); status = "Random gear sent.";
        }).bounds(left + 150, y + 62, 140, 24).build());

        addRenderableWidget(Button.builder(Component.literal("Kill Bot"), b -> {
            String n = botName.getValue().trim();
            if (n.isEmpty()) { status = "Enter the bot name to kill."; return; }
            send("vexbot kill " + n); status = "Kill sent for " + n + ".";
        }).bounds(left, y + 92, 140, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + 150, y + 92, 140, 22).build());

        refreshLabels();
    }

    private void refreshLabels() {
        diffBtn.setMessage(Component.literal("Difficulty: " + DIFFS[diffIdx].toUpperCase()));
        armorBtn.setMessage(Component.literal("Armor: " + ARMORS[armorIdx].toUpperCase()));
    }

    private void send(String cmd) { VexBotGuiAddon.runCommand(cmd); }

    private void spawn() {
        String name = botName.getValue().trim();
        if (name.isEmpty()) { status = "Enter a bot name first."; return; }
        lastName = name;

        send("vexbot spawn " + name);
        VexBotGuiAddon.runLater("vexbot difficulty preset " + DIFFS[diffIdx], 10);

        // Armor uses vanilla /item replace (needs op), after the bot has spawned.
        String a = ARMORS[armorIdx];
        if (!a.equals("none")) {
            String[][] slots = {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}};
            for (String[] s : slots) {
                VexBotGuiAddon.runLater("item replace entity " + name + " armor." + s[0]
                        + " with minecraft:" + a + "_" + s[1], 15);
            }
        }
        status = "Spawn sent for " + name + ".";
    }

    // Draw the panel as part of the background so it isn't blurred and
    // the background is not rendered twice (that crashes on 1.21+).
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF111923);
        g.fill(panelX, panelY, panelX + panelW, panelY + 3, 0xFF35D0A0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(font, "VEXBOT", panelX + 20, panelY + 18, 0xFFFFFFFF, false);
        g.drawString(font, "CONTROL PANEL", panelX + 20, panelY + 36, 0xFF75DDBB, false);
        g.drawString(font, "BOT CONFIGURATION", panelX + 20, panelY + 63, 0xFFB7C5D3, false);
        g.drawString(font, status, panelX + 20, panelY + 250, 0xFFB7C5D3, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
