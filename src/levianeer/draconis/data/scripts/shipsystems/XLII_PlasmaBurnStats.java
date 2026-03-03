package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

import java.awt.*;

public class XLII_PlasmaBurnStats extends BaseShipSystemScript {

    private final Color engineGlowColor = new Color(235, 135, 65, 191);

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, 150f * effectLevel);
            stats.getAcceleration().modifyFlat(id, 150f * effectLevel);
        }

        if (!(stats.getEntity() instanceof ShipAPI ship)) return;

        ship.getEngineController().fadeToOtherColor(this, engineGlowColor, new Color(0, 0, 0, 0), effectLevel, 0.67f);
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
            return new StatusData("increased engine power", false);
        }
        return null;
    }
}
