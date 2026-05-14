package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.util.HashMap;
import java.util.Map;

public class XLII_AmmoSelectorStats extends BaseShipSystemScript {

    public static final int KINETIC = 0;
    public static final int HE = 1;
    public static final int FRAG = 2;

    private static final float KINETIC_ROF_BONUS   =  30f;
    private static final float KINETIC_SPEED_BONUS =  25f;
    private static final float HE_ROF_PENALTY      = -20f;
    private static final float HE_SPEED_PENALTY    = -10f;
    private static final float FRAG_ROF_PENALTY    = -10f;
    private static final float FRAG_SPEED_PENALTY  = -15f;

    private static final String[] MODE_NAMES = { "KINETIC", "HE", "FRAG" };
    private static final String[] MODE_DESC  = {
        "high velocity - fast firing",
        "EMP heavy explosive rounds",
        "area of effect flak"
    };

    /** Mode per ship ID - read by XLII_AutomatWeaponEffect. */
    private static final Map<String, Integer> MODE = new HashMap<>();

    public static int getMode(String shipId) {
        return MODE.getOrDefault(shipId, KINETIC);
    }

    // -------------------------------------------------------------------------

    private int     currentMode = KINETIC;
    private State   prevState   = State.IDLE;
    private final Object STATUSKEY = new Object();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI ship)) return;

        // Cycle mode on the first frame the system transitions into charge-up.
        if (state == State.IN && (prevState == State.IDLE || prevState == State.COOLDOWN)) {
            currentMode = (currentMode + 1) % 3;
            MODE.put(ship.getId(), currentMode);
        }
        prevState = state;

        applyModeStats(stats, id);

        if (ship == Global.getCombatEngine().getPlayerShip()) {
            Global.getCombatEngine().maintainStatusForPlayerShip(
                    STATUSKEY,
                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                    "Ammo: " + MODE_NAMES[currentMode],
                    MODE_DESC[currentMode],
                    false
            );
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBallisticRoFMult().unmodify(id);
        stats.getBallisticProjectileSpeedMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Ammo: " + MODE_NAMES[currentMode] + " - " + MODE_DESC[currentMode], false);
        }
        return null;
    }

    // -------------------------------------------------------------------------

    private void applyModeStats(MutableShipStatsAPI stats, String id) {
        stats.getBallisticRoFMult().unmodify(id);
        stats.getBallisticProjectileSpeedMult().unmodify(id);

        switch (currentMode) {
            case KINETIC:
                stats.getBallisticRoFMult().modifyPercent(id, KINETIC_ROF_BONUS);
                stats.getBallisticProjectileSpeedMult().modifyPercent(id, KINETIC_SPEED_BONUS);
                break;
            case HE:
                stats.getBallisticRoFMult().modifyPercent(id, HE_ROF_PENALTY);
                stats.getBallisticProjectileSpeedMult().modifyPercent(id, HE_SPEED_PENALTY);
                break;
            case FRAG:
                stats.getBallisticRoFMult().modifyPercent(id, FRAG_ROF_PENALTY);
                stats.getBallisticProjectileSpeedMult().modifyPercent(id, FRAG_SPEED_PENALTY);
                break;
        }
    }
}
