package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;

public class XLII_OverclockCore extends BaseLogisticsHullMod {

    public static float DAMAGE_MISSILES_PERCENT = 100f;
    public static float DAMAGE_FIGHTERS_PERCENT = 100f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {

        stats.getDamageToMissiles().modifyPercent(id, DAMAGE_MISSILES_PERCENT);
        stats.getDamageToFighters().modifyPercent(id, DAMAGE_FIGHTERS_PERCENT);
        stats.getBeamWeaponTurnRateBonus().modifyMult(id, 2f);
        stats.getAutofireAimAccuracy().modifyFlat(id, 1f);

        stats.getEngineDamageTakenMult().modifyMult(id, 0f);

        stats.getDynamic().getMod(Stats.PD_IGNORES_FLARES).modifyFlat(id, 1f);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        super.applyEffectsAfterShipCreation(ship, id);
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + (int) DAMAGE_MISSILES_PERCENT + "%";
        return null;
    }
}