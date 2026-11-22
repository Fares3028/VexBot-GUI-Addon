package com.fares.vexbotgui;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded console log: what we sent (OUT), what the server answered (IN) and our own notes (INFO).
 * mark()/repliesSince() let a caller read only the replies that arrived after a point in time,
 * even after old entries were trimmed. Pure Java, unit-testable.
 */
final class LogBuffer {
    enum Dir { OUT, IN, INFO }

    record Entry(String time, Dir dir, String text, boolean error) {}

    private final int max;
    private final List<Entry> entries = new ArrayList<>();
    private long added = 0;

    LogBuffer(int max) { this.max = Math.max(1, max); }

    void add(String time, Dir dir, String text) {
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        add(time, dir, clean, dir == Dir.IN && ReplyClassifier.isError(clean));
    }

    void add(String time, Dir dir, String text, boolean error) {
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        entries.add(new Entry(time, dir, clean, error));
        added++;
        while (entries.size() > max) entries.remove(0);
    }

    int size() { return entries.size(); }

    Entry get(int i) { return entries.get(i); }

    void clear() { entries.clear(); }

    /** Position to pass to repliesSince() later. */
    long mark() { return added; }

    /** Texts of the server replies (IN) added after the mark. Entries that were already trimmed are gone. */
    List<String> repliesSince(long mark) {
        List<String> out = new ArrayList<>();
        long n = Math.min(entries.size(), added - mark);
        for (int i = (int) (entries.size() - Math.max(0, n)); i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.dir() == Dir.IN) out.add(e.text());
        }
        return out;
    }

    /** Latest server reply text, or "". */
    String lastReply() {
        for (int i = entries.size() - 1; i >= 0; i--) if (entries.get(i).dir() == Dir.IN) return entries.get(i).text();
        return "";
    }
}
