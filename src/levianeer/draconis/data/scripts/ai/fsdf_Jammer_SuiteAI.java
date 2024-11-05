package levianeer.draconis.data.scripts.ai;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;

public class fsdf_Jammer_SuiteAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipSystemAPI system;

    private IntervalUtil tracker = new IntervalUtil(1f, 1f);

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.system = system;
    }

    public void advance(float amount, Vector2f shipDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0) return;
            if (system.isOutOfAmmo()) return;
            if (system.isActive()) return;

            // Check if the ship is under attack by an enemy
            if (isUnderAttack()) {
                ship.useSystem();
            }
        }
    }

    private boolean isUnderAttack() {
        for (ShipAPI enemy : engine.getShips()) {
            if (enemy.getOwner() == ship.getOwner() || !enemy.isAlive()) {
                continue; // Skip own or dead ships
            }

            float distance = Vector2f.sub(enemy.getLocation(), ship.getLocation(), null).length();
            float effectiveRange = 2000f + enemy.getCollisionRadius(); // Adjusted range based on enemy ship size

            if (distance < effectiveRange) {
                return true;
            }
        }
        return false;
    }
}