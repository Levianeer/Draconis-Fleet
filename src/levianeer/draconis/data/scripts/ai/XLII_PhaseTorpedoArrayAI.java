package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * AI for the Phase Torpedo Array ship system.
 * Each activation just adds one torpedo to the stack - the stack releases
 * itself automatically once its timer runs out - so the AI's only job is to
 * keep pressing while a target is in range and stop once the stack is full.
 * canBeActivated() already covers ammo (stack full), cooldown, and mid-charge.
 */
public class XLII_PhaseTorpedoArrayAI implements ShipSystemAIScript {

    // Matches XLII_PhaseTorpedoArrayStats.SYSTEM_RANGE
    private static final float RANGE = 1200f;

    private ShipAPI ship;
    private ShipSystemAPI system;

    // Fast recheck so the stack tops up quickly once a target shows up.
    private final IntervalUtil tracker = new IntervalUtil(0.2f, 0.3f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        if (!system.canBeActivated() || !AIUtils.canUseSystemThisFrame(ship)) return;

        if (hasTargetInRange()) {
            ship.useSystem();
        }
    }

    private boolean hasTargetInRange() {
        ShipAPI shipTarget = ship.getShipTarget();
        if (shipTarget != null && shipTarget.isAlive()
                && MathUtils.isWithinRange(ship.getLocation(), shipTarget.getLocation(), RANGE)) {
            return true;
        }
        return !AIUtils.getNearbyEnemies(ship, RANGE).isEmpty();
    }
}
