package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.HashMap;
import java.util.Map;

public class XLII_FortySecond extends BaseHullMod {

    private static final Map combatMag = new HashMap();
    static {
        combatMag.put(HullSize.FRIGATE, 250f);
        combatMag.put(HullSize.DESTROYER, 500f);
        combatMag.put(HullSize.CRUISER, 750f);
        combatMag.put(HullSize.CAPITAL_SHIP, 1000f);
    }

    public static float PROFILE_MULT = 0.8f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSightRadiusMod().modifyFlat(id, combatMag.size());
        stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + ((Float) combatMag.get(HullSize.FRIGATE)).intValue();
        if (index == 1) return "" + ((Float) combatMag.get(HullSize.DESTROYER)).intValue();
        if (index == 2) return "" + ((Float) combatMag.get(HullSize.CRUISER)).intValue();
        if (index == 3) return "" + ((Float) combatMag.get(HullSize.CAPITAL_SHIP)).intValue();
        if (index == 4) return Math.round((1f - PROFILE_MULT) * 100f) + "%";
        return null;
    }
}