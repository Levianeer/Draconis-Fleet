package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class fsdf_EvasionProtocolAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;
    private final IntervalUtil tracker = new IntervalUtil(0.2f, 0.3f);

    private float threatRadius;

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
        this.engine = engine;

        if (ship.isFighter()) {
            threatRadius = 250f;
        } else {
            threatRadius = 500f;
        }
    }

    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        if (system == null || system.getCooldownRemaining() > 0 || system.isOutOfAmmo() || system.isActive()) {
            return;
        }

        int ammo = system.getAmmo();
        boolean isLastCharge = ammo == 1;

        boolean missileThreat = detectImmediateMissileThreat();
        boolean criticalDanger = shouldEscape();

        // Only use last charge in critical situations
        if (isLastCharge && !(missileThreat || criticalDanger)) {
            return;
        }

        if (missileThreat) {
            ship.useSystem();
            return;
        }

        if (shouldEngageTarget(target)) {
            ship.useSystem();
            return;
        }

        if (criticalDanger) {
            ship.useSystem();
        }
    }

    private boolean detectImmediateMissileThreat() {
        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.isFading() || missile.isFizzling() || missile.getOwner() == ship.getOwner()) continue;

            float dist = Vector2f.sub(missile.getLocation(), ship.getLocation(), null).length();
            if (dist <= threatRadius) return true;
        }
        return false;
    }

    private boolean shouldEngageTarget(ShipAPI target) {
        if (target == null || target.isHulk() || !target.isAlive()) return false;

        float dist = Vector2f.sub(target.getLocation(), ship.getLocation(), null).length();
        float idealEngageRange = ship.getCollisionRadius() + 600f; // Range threshold for aggressive dash
        return dist > idealEngageRange;
    }

    private boolean shouldEscape() {
        float hull = ship.getHitpoints() / ship.getMaxHitpoints();
        float flux = ship.getFluxTracker().getFluxLevel();

        // Count enemies nearby
        List<ShipAPI> enemies = engine.getShips();
        int nearbyEnemies = 0;
        for (ShipAPI other : enemies) {
            if (other.getOwner() == ship.getOwner() || other.isHulk() || !other.isAlive()) continue;
            float dist = Vector2f.sub(other.getLocation(), ship.getLocation(), null).length();
            if (dist < 800f) nearbyEnemies++;
        }

        return (hull < 0.5f || flux > 0.7f ) && nearbyEnemies > 0;
    }
}