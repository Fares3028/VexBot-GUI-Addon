package com.fares.vexbotgui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the replies of "/vexbot team list" and of a team roster. Pure Java, unit-testable.
 * Formats (VexBot 3.2.0):  "[VexBot] Teams (N):"  then one line per team  " Name - X bot(s), Y player(s)";
 * "[VexBot] No teams yet. ..." when there are none;  "[VexBot] Team NAME roster:" then the members.
 */
final class TeamListParser {
    record TeamInfo(String name, int bots, int players) {}

    /** recognized = the reply looked like a team list; empty = the server said there are no teams. */
    record Result(boolean recognized, boolean empty, int reported, List<TeamInfo> teams) {}

    private static final Pattern CODES = Pattern.compile("\u00a7[0-9a-fk-orA-FK-OR]");
    private static final Pattern HEADER = Pattern.compile("Teams \\((\\d+)\\):");
    private static final Pattern ROW = Pattern.compile(
            "^\\s*([A-Za-z0-9_\\-]{1,24})\\s*[\u2014\u2013-]+\\s*(\\d+)\\s+bot\\(s\\),\\s*(\\d+)\\s+player\\(s\\)\\s*$");

    private TeamListParser() {}

    /**
     * True when the rows we recognised match what the header announced (or no header number was given).
     * A mismatch means the reply was cut off or in an unexpected format, so the list must not be shown as complete.
     */
    static boolean complete(Result r) {
        if (!r.recognized()) return false;
        return r.reported() < 0 || r.reported() == r.teams().size();
    }

    /** Removes Minecraft colour codes. */
    static String strip(String s) { return s == null ? "" : CODES.matcher(s).replaceAll(""); }

    private static List<String> lines(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String chunk : raw) for (String l : strip(chunk).split("\\r?\\n")) out.add(l);
        return out;
    }

    static Result parse(List<String> raw) {
        boolean header = false, none = false;
        int reported = -1;
        List<TeamInfo> teams = new ArrayList<>();
        for (String line : lines(raw)) {
            Matcher h = HEADER.matcher(line);
            if (h.find()) { header = true; reported = Integer.parseInt(h.group(1)); continue; }
            if (line.contains("No teams yet")) { none = true; continue; }
            Matcher m = ROW.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                boolean dup = teams.stream().anyMatch(t -> t.name().equalsIgnoreCase(name));
                if (!dup) teams.add(new TeamInfo(name, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
            }
        }
        boolean recognized = header || none || !teams.isEmpty();
        return new Result(recognized, none && teams.isEmpty(), reported, teams);
    }

    /** Lines after the "roster:" header (max 8), colour codes removed. Empty when no roster reply was seen. */
    static List<String> rosterLines(List<String> raw) {
        List<String> out = new ArrayList<>();
        boolean in = false;
        for (String line : lines(raw)) {
            String t = line.trim();
            if (!in) {
                if (t.contains("roster:")) in = true;
                continue;
            }
            if (t.isEmpty()) continue;
            if (t.startsWith("[VexBot]")) break;
            out.add(t);
            if (out.size() >= 8) break;
        }
        return out;
    }
}
