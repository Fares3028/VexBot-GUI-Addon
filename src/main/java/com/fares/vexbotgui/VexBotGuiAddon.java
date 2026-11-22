package com.fares.vexbotgui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class VexBotGuiAddon implements ClientModInitializer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int CAPTURE_TICKS = 100; // replies are recorded for 5 s after a command we send

    private static KeyMapping openMenu, quickSpawn;

    private static final TaskQueue queue = new TaskQueue();

    /** Waits until a bot shows up in the player list (or the timeout runs out). */
    private static final class Waiter {
        final String name; int left; final Runnable ok, timeout;
        Waiter(String name, int left, Runnable ok, Runnable timeout) {
            this.name = name; this.left = left; this.ok = ok; this.timeout = timeout;
        }
    }
    private static final List<Waiter> waiters = new ArrayList<>();

    /** Collects every chat/system message for a short time (e.g. to read the reply of a list command). */
    private static final class Collector {
        final List<String> lines = new ArrayList<>();
        int left;
        final Consumer<List<String>> done;
        Collector(int left, Consumer<List<String>> done) { this.left = left; this.done = done; }
    }
    private static final List<Collector> collectors = new ArrayList<>();

    /** Console log: commands we sent, server replies seen after a send, and our own notes. */
    static final LogBuffer LOG = new LogBuffer(300);
    private static int captureTicks = 0;
    /** Result of the most recent runCommand: lets the UI tell "sent" from "nothing was sent". */
    static boolean lastSendOk = false;

    private static String now() { return LocalTime.now().format(TIME); }

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
            if (overlay) return;
            String text = message.getString();
            for (Collector c : collectors) c.lines.add(text);
            if (captureTicks > 0) LOG.add(now(), LogBuffer.Dir.IN, text);
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
                    guarded("quick spawn", Actions::quickSpawn);
                }
            }
            if (!connected()) { clearPending(); return; }

            queue.tick(e -> internalError("scheduled task", e));
            if (!collectors.isEmpty()) {
                for (Collector c : new ArrayList<>(collectors)) {
                    if (--c.left <= 0) {
                        collectors.remove(c);
                        guarded("reply collector", () -> c.done.accept(c.lines));
                    }
                }
            }
            if (!waiters.isEmpty()) {
                for (Waiter w : new ArrayList<>(waiters)) {
                    if (!waiters.contains(w)) continue;
                    if (botOnline(w.name)) {
                        waiters.remove(w);
                        guarded("bot waiter", w.ok);
                    } else if (--w.left <= 0) {
                        waiters.remove(w);
                        guarded("bot waiter timeout", w.timeout);
                    }
                }
            }
        });
    }

    /** A bug in one callback must never crash the game: report it in the console instead. */
    private static void guarded(String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            internalError(what, e);
        }
    }

    private static void internalError(String where, RuntimeException e) {
        System.err.println("[VexBot GUI] internal error in " + where + ": " + e);
        e.printStackTrace();
        LOG.add(now(), LogBuffer.Dir.INFO, "INTERNAL ERROR in " + where + ": " + e, true);
    }

    private static void clearPending() {
        queue.clear();
        waiters.clear();
        collectors.clear();
    }

    /** Forget everything that belongs to the previous server/world. */
    static void reset() {
        clearPending();
        LOG.clear();
        captureTicks = 0;
        Actions.resetSession();
    }

    static boolean connected() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.player != null;
    }

    /** @return false when nothing was sent (no server connection, or an empty command). */
    static boolean runCommand(String command) {
        String clean = Validate.cleanCommand(command);
        lastSendOk = false;
        if (clean.isEmpty()) return false;
        if (!connected()) {
            LOG.add(now(), LogBuffer.Dir.INFO, "NOT SENT (no server connection): " + clean, true);
            return false;
        }
        lastSendOk = true;
        captureTicks = CAPTURE_TICKS;
        LOG.add(now(), LogBuffer.Dir.OUT, clean);
        Minecraft.getInstance().getConnection().sendCommand(clean);
        return true;
    }

    /** Run a command after N ticks (20 ticks = 1 second). */
    static void runLater(String command, int delayTicks) {
        runLaterTask(() -> runCommand(command), delayTicks);
    }

    static void runLaterTask(Runnable task, int delayTicks) { queue.add(task, delayTicks); }

    static boolean botOnline(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(name) != null;
    }

    static void whenBotReady(String name, int timeoutTicks, Runnable ok, Runnable timeout) {
        waiters.add(new Waiter(name, timeoutTicks, ok, timeout));
    }

    /** Start listening now; after the given ticks the collected raw messages are handed to done. */
    static void collectReplies(int ticks, Consumer<List<String>> done) {
        collectors.add(new Collector(Math.max(1, ticks), done));
    }

    /** Adds a note of our own (warning or error) to the console. */
    static void note(String text, boolean error) { LOG.add(now(), LogBuffer.Dir.INFO, text, error); }

    static long logMark() { return LOG.mark(); }

    /** Server replies received after the given mark. */
    static List<String> logSince(long mark) { return LOG.repliesSince(mark); }

    static void clearLog() { LOG.clear(); }

    static String lastLog() { return LOG.lastReply(); }

    /** Text for a bug report: versions, connection, config problems and the latest console lines. */
    static String diagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("VexBot GUI ").append(modVersion("vexbot_gui"))
                .append(" | Minecraft ").append(modVersion("minecraft"))
                .append(" | Fabric Loader ").append(modVersion("fabricloader"))
                .append(" | Java ").append(System.getProperty("java.version")).append('\n');
        sb.append("Connected: ").append(connected()).append('\n');
        List<String> problems = Config.allProblems();
        sb.append("Config problems: ").append(problems.isEmpty() ? "none" : "").append('\n');
        for (String p : problems) sb.append("  - ").append(p).append('\n');
        sb.append("Pending tasks: ").append(queue.size()).append(" | waiters: ").append(waiters.size())
                .append(" | collectors: ").append(collectors.size()).append('\n');
        sb.append("Last console lines:\n");
        for (int i = Math.max(0, LOG.size() - 40); i < LOG.size(); i++) {
            LogBuffer.Entry e = LOG.get(i);
            sb.append("  [").append(e.time()).append("] ")
                    .append(e.dir() == LogBuffer.Dir.OUT ? "> " : e.dir() == LogBuffer.Dir.IN ? "< " : "! ")
                    .append(e.text()).append('\n');
        }
        return sb.toString();
    }

    private static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }
}
