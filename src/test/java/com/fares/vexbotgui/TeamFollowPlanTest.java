package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TeamFollowPlanTest {
    @Test void partitionsOnlineAndMissingExpectedMembers() {
        var plan = TeamFollowPlan.partition(List.of("Nova1", "Blaze2", "Rogue3"),
                name -> name.equalsIgnoreCase("Nova1") || name.equalsIgnoreCase("Rogue3"));
        assertEquals(List.of("Nova1", "Rogue3"), plan.online());
        assertEquals(List.of("Blaze2"), plan.missing());
    }

    @Test void removesDuplicateNamesCaseInsensitively() {
        var plan = TeamFollowPlan.partition(List.of("Nova1", "nova1", "BLAZE2"), name -> true);
        assertEquals(List.of("Nova1", "BLAZE2"), plan.online());
        assertTrue(plan.missing().isEmpty());
    }

    @Test void emptyExpectedListDoesNotInventMembers() {
        var plan = TeamFollowPlan.partition(List.of(), name -> true);
        assertTrue(plan.online().isEmpty());
        assertTrue(plan.missing().isEmpty());
    }
}
