package data.shipsystems.scripts.ai;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;

public class fsdf_ECM_SuiteAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipSystemAPI system;

    private IntervalUtil tracker = new IntervalUtil(1f, 1f);

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.system = system;
    }

    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0) return;
            if (system.isOutOfAmmo()) return;
            if (system.isActive()) return;

            // Check for missiles within range
            if (missilesWithinRange(700f)) {
                ship.useSystem();
            }
        }
    }

    private boolean missilesWithinRange(float range) {
        for (MissileAPI missile : engine.getMissiles()) {
            float distance = Vector2f.sub(missile.getLocation(), ship.getLocation(), null).length();
            if (distance < range) {
                return true;
            }
        }
        return false;
    }
}