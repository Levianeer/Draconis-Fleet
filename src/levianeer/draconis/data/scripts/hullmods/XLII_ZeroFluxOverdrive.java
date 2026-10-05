package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class XLII_ZeroFluxOverdrive extends BaseHullMod {

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getZeroFluxSpeedBoost().modifyMult(id, 2f);
    }
}
