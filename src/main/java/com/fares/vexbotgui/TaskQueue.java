package com.fares.vexbotgui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tick-based delayed tasks. A task that throws never stops the others (and never crashes the game):
 * the error goes to the handler. clear() also stops tasks that are due in the same tick.
 * Pure Java, unit-testable.
 */
final class TaskQueue {
    private static final class Item {
        int ticks;
        final Runnable task;
        Item(int ticks, Runnable task) { this.ticks = ticks; this.task = task; }
    }

    private final List<Item> items = new ArrayList<>();

    void add(Runnable task, int delayTicks) { items.add(new Item(Math.max(1, delayTicks), task)); }

    void tick(Consumer<RuntimeException> onError) {
        if (items.isEmpty()) return;
        for (Item i : new ArrayList<>(items)) {
            if (!items.contains(i)) continue;          // removed by clear() from an earlier task in this tick
            if (--i.ticks <= 0) {
                items.remove(i);
                try {
                    i.task.run();
                } catch (RuntimeException e) {
                    onError.accept(e);
                }
            }
        }
    }

    void clear() { items.clear(); }

    int size() { return items.size(); }
}
