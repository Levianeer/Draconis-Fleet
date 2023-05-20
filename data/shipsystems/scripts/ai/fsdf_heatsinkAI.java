package data.shipsystems.scripts.ai;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;

public class fsdf_heatsinkAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private ShipSystemAPI system;
    private IntervalUtil intervalUtil = new IntervalUtil(0.5f, 1f);
    private boolean isRetreating = false;

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        intervalUtil.advance(amount);

        if (intervalUtil.intervalElapsed()) {
            if (system.isOutOfAmmo() && ship.getFluxTracker().getFluxLevel() > 0.5f) {
                if (!isRetreating) {
                    isRetreating = true;
                    ship.useSystem();
                }
                return;
            }

            if (ship.getFluxTracker().getFluxLevel() > 0.7f && !system.isActive()) {
                ship.useSystem();
                return;
            }

            isRetreating = false;
        }
    }
}