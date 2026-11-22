package com.fares.vexbotgui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads VexBot's reply to "givekit list": a header "[VexBot] Saved kits:", then one line per kit
 * "<name> (<n> item(s))", then a usage hint. "[VexBot] No kits saved yet..." means an empty list.
 * (Format taken from the VexBot 3.2.5 jar.) Pure Java, unit-testable.
 */
final class KitListParser {
    record KitInfo(String name, int items) {}

    record Result(boolean recognized, boolean empty, List<KitInfo> kits) {}

    private static final Pattern ROW = Pattern.compile("^(\\S{1,48})\\s+\\((\\d+)\\s+items?(?:\\(s\\))?\\)$");

    private KitListParser() {}

    static Result parse(List<String> lines) {
        boolean header = false, empty = false;
        List<KitInfo> kits = new ArrayList<>();
        if (lines != null) {
            for (String raw : lines) {
                String l = strip(raw).trim();
                if (l.contains("No kits saved yet")) { empty = true; continue; }
                if (l.contains("Saved kits:")) { header = true; continue; }
                if (!header) continue;
                if (l.startsWith("Use /vexbot")) break;
                Matcher m = ROW.matcher(l);
                if (m.matches()) {
                    String name = m.group(1);
                    boolean dup = kits.stream().anyMatch(k -> k.name().equalsIgnoreCase(name));
                    if (!dup) kits.add(new KitInfo(name, Integer.parseInt(m.group(2))));
                }
            }
        }
        return new Result(header || empty, empty && kits.isEmpty(), kits);
    }

    /** Removes Minecraft colour codes. */
    static String strip(String s) { return s == null ? "" : s.replaceAll("\u00a7.", ""); }
}
