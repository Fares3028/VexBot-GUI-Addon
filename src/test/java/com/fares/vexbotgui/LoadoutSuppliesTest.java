package com.fares.vexbotgui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoadoutSuppliesTest {
    @Test void bowGetsArrows() {
        assertEquals(new LoadoutSupplies.Supply("arrow", 64),
                LoadoutSupplies.forLoadout("none", "bow").get(0));
    }

    @Test void crossbowGetsArrowsOnly() {
        assertEquals(1, LoadoutSupplies.forLoadout("none", "crossbow").size());
        assertEquals("arrow", LoadoutSupplies.forLoadout("none", "crossbow").get(0).item());
    }

    @Test void elytraGetsFireworkRockets() {
        assertEquals(new LoadoutSupplies.Supply("firework_rocket", 64),
                LoadoutSupplies.forLoadout("elytra", "none").get(0));
    }

    @Test void maceGetsWindCharges() {
        assertEquals(new LoadoutSupplies.Supply("wind_charge", 32),
                LoadoutSupplies.forLoadout("none", "mace").get(0));
    }

    @Test void combinedLoadoutGetsAllRequiredSupplies() {
        var supplies = LoadoutSupplies.forLoadout("elytra", "mace");
        assertTrue(supplies.contains(new LoadoutSupplies.Supply("firework_rocket", 64)));
        assertTrue(supplies.contains(new LoadoutSupplies.Supply("wind_charge", 32)));
    }

    @Test void meleeSwordNeedsNoAmmunition() {
        assertTrue(LoadoutSupplies.forLoadout("iron", "iron_sword").isEmpty());
    }
}
