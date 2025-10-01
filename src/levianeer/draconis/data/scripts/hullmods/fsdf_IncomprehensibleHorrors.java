package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class fsdf_IncomprehensibleHorrors extends BaseHullMod {

    private static final float COOLDOWN_REDUCTION = -50f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSystemCooldownBonus().modifyPercent(id, COOLDOWN_REDUCTION);
    }
}