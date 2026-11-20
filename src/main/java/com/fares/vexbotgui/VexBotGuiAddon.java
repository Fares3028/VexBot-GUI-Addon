package com.fares.vexbotgui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class VexBotGuiAddon implements ClientModInitializer {
    private static KeyMapping openMenu;

    private record Scheduled(int[] ticks, String command) {}
    private static final List<Scheduled> queue = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vexbot_gui.open", GLFW.GLFW_KEY_B, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.setScreen(new VexBotScreen());
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
            client.getConnection().sendCommand(command);
        }
    }

    /** Run a command after N ticks (20 ticks = 1 second). */
    static void runLater(String command, int delayTicks) {
        queue.add(new Scheduled(new int[]{delayTicks}, command));
    }
}
