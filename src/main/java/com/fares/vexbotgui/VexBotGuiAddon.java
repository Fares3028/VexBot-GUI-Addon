package com.fares.vexbotgui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class VexBotGuiAddon implements ClientModInitializer {
    private static KeyMapping openMenu, quickSpawn;

    private record Scheduled(int[] ticks, String command) {}
    private static final List<Scheduled> queue = new ArrayList<>();

    /** Server replies seen shortly after we sent a command (newest last). */
    static final List<String> LOG = new ArrayList<>();
    private static int captureTicks = 0;

    @Override
    public void onInitializeClient() {
        Config.load();

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vexbot_gui.open", GLFW.GLFW_KEY_B, KeyMapping.Category.MISC));
        quickSpawn = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vexbot_gui.quickspawn", GLFW.GLFW_KEY_N, KeyMapping.Category.MISC));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && captureTicks > 0) {
                LOG.add(message.getString().replace('\n', ' '));
                while (LOG.size() > 40) LOG.remove(0);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureTicks > 0) captureTicks--;
            while (openMenu.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.setScreen(new VexBotScreen());
                }
            }
            while (quickSpawn.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    Actions.quickSpawn();
                }
            }
            Iterator<Scheduled> it = queue.iterator();
            while (it.hasNext()) {
                Scheduled s = it.next();
                if (--s.ticks()[0] <= 0) {
                    runCommand(s.command());
                    it.remove();
                }
            }
        });
    }

    static void runCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            captureTicks = 100; // record replies for the next 5 seconds
            client.getConnection().sendCommand(command);
        }
    }

    /** Run a command after N ticks (20 ticks = 1 second). */
    static void runLater(String command, int delayTicks) {
        queue.add(new Scheduled(new int[]{delayTicks}, command));
    }

    static String lastLog() { return LOG.isEmpty() ? "" : LOG.get(LOG.size() - 1); }
}
