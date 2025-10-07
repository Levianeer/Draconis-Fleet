package levianeer.draconis.data.scripts.ai;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.util.List;

public class XLII_ESW_SuiteAI implements ShipSystemAIScript {
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipSystemAPI system;
    private final IntervalUtil tracker = new IntervalUtil(1f, 1f);
    private static final float ACTIVATION_RANGE = 2400f;

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.system = system;
    }

    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0 || system.isOutOfAmmo() || system.isActive()) return;

            ShipAPI bestTarget = findBestTarget();
            if (bestTarget != null) {
                ship.useSystem();
            }
        }
    }

    private ShipAPI findBestTarget() {
        List<ShipAPI> enemies = engine.getShips();
        ShipAPI bestTarget = null;
        float bestScore = 0f;

        for (ShipAPI enemy : enemies) {
            if (enemy.getOwner() == ship.getOwner() || enemy.isHulk() || enemy.isPhased()) continue;

            // Exclude fighters and frigates
            ShipAPI.HullSize hullSize = enemy.getHullSize();
            if (hullSize == ShipAPI.HullSize.FIGHTER || hullSize == ShipAPI.HullSize.FRIGATE) continue;

            float distance = Misc.getDistance(ship.getLocation(), enemy.getLocation());
            if (distance > ACTIVATION_RANGE) continue;

            float threatLevel = enemy.getFleetMember() != null ? enemy.getFleetMember().getDeploymentPointsCost() : 10f;
            float score = (ACTIVATION_RANGE - distance) + (threatLevel * 10f);

            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }
        return bestTarget;
    }
}