package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TeamRosterVerifierTest {
    @Test void confirmsEveryExpectedMemberCaseInsensitively() {
        assertTrue(TeamRosterVerifier.missing(List.of("Bots: Nova1, Blaze2"), List.of("nova1", "BLAZE2")).isEmpty());
    }

    @Test void reportsMissingMembersInsteadOfAssumingTeamIsComplete() {
        assertEquals(List.of("Rogue3"), TeamRosterVerifier.missing(
                List.of("Bots: Nova1, Blaze2"), List.of("Nova1", "Blaze2", "Rogue3")));
    }

    @Test void emptyRosterDoesNotConfirmAnyExpectedMember() {
        assertEquals(List.of("Nova1", "Blaze2"), TeamRosterVerifier.missing(List.of(), List.of("Nova1", "Blaze2")));
    }
}
