package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

public class fsdf_ECM_SuiteAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;
    private final IntervalUtil tracker = new IntervalUtil(0.2f, 0.3f); // Quick checks

    private float threatRadius; // dynamic per ship size

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.engine = engine;

        // Set threat radius depending on ship size
        if (ship.isFighter()) {
            threatRadius = 500f;
        } else {
            threatRadius = 1000f;
        }
    }

    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0 || system.isOutOfAmmo() || system.isActive()) {
                return;
            }

            if (detectImmediateThreat()) {
                ship.useSystem();
            }
        }
    }

    private boolean detectImmediateThreat() {
        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.isFading() || missile.isFizzling()) {
                continue;
            }
            if (missile.getOwner() == ship.getOwner()) {
                continue;
            }

            // Check distance to self only
            if (isMissileThreatening(missile)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMissileThreatening(MissileAPI missile) {
        float distance = Vector2f.sub(missile.getLocation(), ship.getLocation(), null).length();
        return distance <= threatRadius;
    }
}