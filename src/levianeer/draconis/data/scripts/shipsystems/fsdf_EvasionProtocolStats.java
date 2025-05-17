package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class fsdf_EvasionProtocolStats extends BaseShipSystemScript {

    private static final float SPEED = 200f;
    private static final float DELTA = 2000f; // Acceleration and Deceleration

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, SPEED * effectLevel);
            stats.getAcceleration().modifyFlat(id, DELTA * effectLevel);
            stats.getDeceleration().modifyFlat(id, DELTA * effectLevel);
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        switch (index) {
            case 0: return new StatusData("increased RCS power", false);
            case 1: return new StatusData("deploying countermeasures", false);
            default: return null;
        }
    }
}