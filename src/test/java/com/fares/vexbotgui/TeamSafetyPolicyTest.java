package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TeamSafetyPolicyTest {
    @Test void disablesAllKnownAutonomousAndRetaliatoryTargetSwitches() {
        assertEquals(List.of(
                "vexbot team Red settings autotarget off",
                "vexbot team Red settings targetplayers off",
                "vexbot team Red settings targetbots off",
                "vexbot team Red settings targetmobs off",
                "vexbot team Red settings revengemode off"
        ), TeamSafetyPolicy.commands("Red"));
    }

    @Test void rejectsInvalidTeamNames() {
        assertThrows(IllegalArgumentException.class, () -> TeamSafetyPolicy.commands("bad name"));
    }
}
