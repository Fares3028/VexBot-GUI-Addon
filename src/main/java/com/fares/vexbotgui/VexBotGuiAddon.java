package com.fares.vexbotgui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class VexBotGuiAddon implements ClientModInitializer {
    private static KeyMapping openMenu, quickSpawn;

    private record Scheduled(int[] ticks, Runnable task) {}
    private static final List<Scheduled> queue = new ArrayList<>();

    /** Waits until a bot shows up in the player list (or the timeout runs out). */
    private static final class Waiter {
        final String name; int left; final Runnable ok, timeout;
        Waiter(String name, int left, Runnable ok, Runnable timeout) {
            this.name = name; this.left = left; this.ok = ok; this.timeout = timeout;
        }
    }
    private static final List<Waiter> waiters = new ArrayList<>();

    /** Server replies seen shortly after we sent a command (newest last). */
    static final List<String> LOG = new ArrayList<>();
    private static long logAdded = 0;
    private static int captureTicks = 0;

    @Override
    public void onInitializeClient() {
        Config.load();

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vexbot_gui.open", GLFW.GLFW_KEY_B, KeyMapping.Category.MISC));
        quickSpawn = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vexbot_gui.quickspawn", GLFW.GLFW_KEY_N, KeyMapping.Category.MISC));

        // Never carry pending commands, waiters or old replies into another server/world.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && captureTicks > 0) {
                LOG.add(message.getString().replace('\n', ' '));
                logAdded++;
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
            if (!connected()) { queue.clear(); waiters.clear(); return; }
            // copies, because tasks may schedule new tasks while running
            for (Scheduled s : new ArrayList<>(queue)) {
                if (--s.ticks()[0] <= 0) {
                    queue.remove(s);
                    s.task().run();
                }
            }
            for (Waiter w : new ArrayList<>(waiters)) {
                if (botOnline(w.name)) {
                    waiters.remove(w);
                    w.ok.run();
                } else if (--w.left <= 0) {
                    waiters.remove(w);
                    w.timeout.run();
                }
            }
        });
    }

    static void reset() {
        queue.clear();
        waiters.clear();
        LOG.clear();
        captureTicks = 0;
        Actions.resetConfirm();
    }

    static boolean connected() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.player != null;
    }

    /** @return false when there is no server connection (nothing is sent). */
    static boolean runCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (!connected()) return false;
        captureTicks = 100; // record replies for the next 5 seconds
        client.getConnection().sendCommand(Validate.cleanCommand(command));
        return true;
    }

    /** Run a command after N ticks (20 ticks = 1 second). */
    static void runLater(String command, int delayTicks) {
        runLaterTask(() -> runCommand(command), delayTicks);
    }

    static void runLaterTask(Runnable task, int delayTicks) {
        queue.add(new Scheduled(new int[]{Math.max(1, delayTicks)}, task));
    }

    static boolean botOnline(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(name) != null;
    }

    static void whenBotReady(String name, int timeoutTicks, Runnable ok, Runnable timeout) {
        waiters.add(new Waiter(name, timeoutTicks, ok, timeout));
    }

    static long logMark() { return logAdded; }

    /** Server replies received after the given mark. */
    static List<String> logSince(long mark) {
        int n = (int) Math.min(LOG.size(), logAdded - mark);
        return new ArrayList<>(LOG.subList(LOG.size() - n, LOG.size()));
    }

    static void clearLog() { LOG.clear(); }

    static String lastLog() { return LOG.isEmpty() ? "" : LOG.get(LOG.size() - 1); }
}
