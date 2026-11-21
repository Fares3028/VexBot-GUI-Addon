package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TeamListParserTest {
    @Test void parsesOneMessageWithManyTeams() {
        String msg = "[VexBot] Teams (\u00a7f2\u00a7r):\n \u00a7eRed\u00a77 \u2014 \u00a7f3 bot(s), 0 player(s)\n \u00a7eBlue_2\u00a77 \u2014 1 bot(s), 2 player(s)";
        TeamListParser.Result r = TeamListParser.parse(List.of(msg));
        assertTrue(r.recognized());
        assertFalse(r.empty());
        assertEquals(2, r.reported());
        assertEquals(2, r.teams().size());
        assertEquals(new TeamListParser.TeamInfo("Red", 3, 0), r.teams().get(0));
        assertEquals(new TeamListParser.TeamInfo("Blue_2", 1, 2), r.teams().get(1));
    }

    @Test void parsesOneMessagePerLine() {
        TeamListParser.Result r = TeamListParser.parse(List.of("[VexBot] Teams (1):", " Alpha \u2014 4 bot(s), 0 player(s)"));
        assertEquals(1, r.teams().size());
        assertEquals("Alpha", r.teams().get(0).name());
    }

    @Test void noTeamsIsRecognizedAsEmpty() {
        TeamListParser.Result r = TeamListParser.parse(List.of("[VexBot] No teams yet. Create one with /vexbot team create <name>."));
        assertTrue(r.recognized());
        assertTrue(r.empty());
        assertTrue(r.teams().isEmpty());
    }

    @Test void unrelatedChatIsNotARecognizedList() {
        TeamListParser.Result r = TeamListParser.parse(List.of("<Steve> hello there", "Unknown or incomplete command"));
        assertFalse(r.recognized());
        assertTrue(r.teams().isEmpty());
        assertFalse(TeamListParser.parse(List.of()).recognized());
    }

    @Test void rosterLinesFollowTheHeader() {
        List<String> lines = TeamListParser.rosterLines(List.of(
                "[VexBot] Team \u00a7eRed\u00a7r roster:\n Bots: \u00a7fA1\u00a77, \u00a7fA2\n Players: (none)"));
        assertEquals(List.of("Bots: A1, A2", "Players: (none)"), lines);
        assertTrue(TeamListParser.rosterLines(List.of("something else")).isEmpty());
    }

    @Test void rosterStopsAtTheNextVexBotMessage() {
        List<String> lines = TeamListParser.rosterLines(List.of("[VexBot] Team X roster:", "Bots: a", "[VexBot] other", "ignored"));
        assertEquals(List.of("Bots: a"), lines);
    }

    @Test void completeWhenTheRowsMatchTheHeader() {
        TeamListParser.Result r = TeamListParser.parse(List.of("[VexBot] Teams (2):", " Red - 3 bot(s), 0 player(s)", " Blue - 1 bot(s), 1 player(s)"));
        assertTrue(TeamListParser.complete(r));
    }

    @Test void incompleteWhenARowWasNotRecognised() {
        TeamListParser.Result r = TeamListParser.parse(List.of("[VexBot] Teams (3):", " Red - 3 bot(s), 0 player(s)", " ??? garbled row"));
        assertTrue(r.recognized());
        assertFalse(TeamListParser.complete(r));
    }

    @Test void emptyListIsCompleteAndNotRecognisedIsNot() {
        assertTrue(TeamListParser.complete(TeamListParser.parse(List.of("[VexBot] No teams yet. Use teamcreate"))));
        assertFalse(TeamListParser.complete(TeamListParser.parse(List.of("hello world"))));
    }

    @Test void duplicateRowsAreCountedOnce() {
        TeamListParser.Result r = TeamListParser.parse(List.of("[VexBot] Teams (1):", " Red - 3 bot(s), 0 player(s)", " red - 3 bot(s), 0 player(s)"));
        assertEquals(1, r.teams().size());
    }
}
