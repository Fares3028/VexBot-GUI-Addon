package com.fares.vexbotgui;

import java.util.ArrayList;
import java.util.List;

/** Inventory supplies required for the selected weapon/armor combination. */
final class LoadoutSupplies {
    record Supply(String item, int count) {}
    private LoadoutSupplies() {}

    static List<Supply> forLoadout(String armor, String weapon) {
        List<Supply> out = new ArrayList<>();
        if ("bow".equals(weapon)) out.add(new Supply("arrow", 64));
        if ("crossbow".equals(weapon)) out.add(new Supply("arrow", 64));
        if ("elytra".equals(armor)) out.add(new Supply("firework_rocket", 64));
        if ("mace".equals(weapon)) out.add(new Supply("wind_charge", 32));
        return List.copyOf(out);
    }
}
