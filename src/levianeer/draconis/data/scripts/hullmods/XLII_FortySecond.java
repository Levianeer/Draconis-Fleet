package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class XLII_FortySecond extends BaseHullMod {

    public static final float ECM_BONUS = 1f;
    public static float PROFILE_MULT = 0.85f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(id, ECM_BONUS);
        stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return ((Float) ECM_BONUS).intValue() + "%";
        if (index == 1) return Math.round((1f - PROFILE_MULT) * 100f) + "%";
        return null;
    }
}