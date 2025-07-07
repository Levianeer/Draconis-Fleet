package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class fsdf_EvasionProtocolStats extends BaseShipSystemScript {

    private static final float VELOCITY = 200f;
    private static final float DELTA = 2000f;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, VELOCITY * effectLevel);
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
        return switch (index) {
            case 0 -> new StatusData("increased RCS power", false);
            case 1 -> new StatusData("deploying countermeasures", false);
            default -> null;
        };
    }
}