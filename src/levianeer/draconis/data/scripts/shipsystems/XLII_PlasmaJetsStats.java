package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class XLII_PlasmaJetsStats extends BaseShipSystemScript {

    public static float SPEED_BONUS = 125f;
    public static float TURN_BONUS = 20f;

    private final Color color = new Color(235, 135, 65, 191);

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS);
            stats.getAcceleration().modifyPercent(id, SPEED_BONUS * 3f * effectLevel);
            stats.getDeceleration().modifyPercent(id, SPEED_BONUS * 3f * effectLevel);
            stats.getTurnAcceleration().modifyFlat(id, TURN_BONUS * effectLevel);
            stats.getTurnAcceleration().modifyPercent(id, TURN_BONUS * 5f * effectLevel);
            stats.getMaxTurnRate().modifyFlat(id, 15f);
            stats.getMaxTurnRate().modifyPercent(id, 100f);
        }
        if (stats.getEntity() instanceof ShipAPI ship) {
            ship.getEngineController().fadeToOtherColor(this, color, new Color(0,0,0,0), effectLevel, 0.67f);
            ship.getEngineController().extendFlame(this, 2f * effectLevel, 0f * effectLevel, 0f * effectLevel);
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("improved maneuverability", false);
        } else if (index == 1) {
            return new StatusData("+" + (int)SPEED_BONUS + " top speed", false);
        }
        return null;
    }
}