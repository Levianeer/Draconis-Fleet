package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class XLII_ImprovedSystems extends BaseHullMod {

    private static final float SYSTEM_CHARGE_INCREASE = 2f;
    private static final float SYSTEM_COOLDOWN_BONUS = 100f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSystemUsesBonus().modifyFlat(id, SYSTEM_CHARGE_INCREASE);
        stats.getSystemCooldownBonus().modifyPercent(id, SYSTEM_COOLDOWN_BONUS);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + SYSTEM_CHARGE_INCREASE + "×";
        if (index == 1) return "" + (int) SYSTEM_COOLDOWN_BONUS + "%";
        return null;
    }
}