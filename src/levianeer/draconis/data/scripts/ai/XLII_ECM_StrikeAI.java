package levianeer.draconis.data.scripts.ai;

import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.combat.AIUtils;

public class XLII_ECM_StrikeAI implements ShipSystemAIScript {

    private static final float MAX_RANGE = 4000f;
    private static final float AOE_RADIUS = 500f;
    private static final int MIN_MISSILES_TO_FIRE = 2;

    private static final float REACTION_DELAY_MIN = 0.2f;
    private static final float REACTION_DELAY_MAX = 0.4f;

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;

    private final IntervalUtil tracker = new IntervalUtil(0.2f, 0.3f);
    private boolean activationPending = false;
    private float reactionTimer = 0f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        if (activationPending) {
            reactionTimer -= amount;
            if (reactionTimer <= 0f) {
                activationPending = false;
                if (AIUtils.canUseSystemThisFrame(ship)) {
                    ship.useSystem();
                }
            }
            return;
        }

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;
        if (!AIUtils.canUseSystemThisFrame(ship)) return;

        Vector2f strikePoint = findBestStrikePoint();
        if (strikePoint == null) return;

        flags.setFlag(AIFlags.SYSTEM_TARGET_COORDS, 1f, strikePoint);
        activationPending = true;
        reactionTimer = REACTION_DELAY_MIN + (REACTION_DELAY_MAX - REACTION_DELAY_MIN) * (float) Math.random();
    }

    // Picks the enemy missile with the most other enemy missiles clustered within AOE_RADIUS of it,
    // so the jamming burst catches as many missiles as possible in one activation.
    private Vector2f findBestStrikePoint() {
        List<MissileAPI> missiles = engine.getMissiles();

        Vector2f bestPoint = null;
        int bestCount = 0;

        for (MissileAPI missile : missiles) {
            if (!isThreatening(missile)) continue;
            if (Misc.getDistance(ship.getLocation(), missile.getLocation()) > MAX_RANGE) continue;

            int count = 0;
            for (MissileAPI other : missiles) {
                if (!isThreatening(other)) continue;
                if (Misc.getDistance(missile.getLocation(), other.getLocation()) <= AOE_RADIUS) {
                    count++;
                }
            }

            if (count > bestCount) {
                bestCount = count;
                bestPoint = missile.getLocation();
            }
        }

        if (bestCount < MIN_MISSILES_TO_FIRE) return null;
        return new Vector2f(bestPoint);
    }

    private boolean isThreatening(MissileAPI missile) {
        if (missile.getSource() == null) return false;
        if (missile.getSource().getOwner() == ship.getOwner()) return false;
        if (missile.isFading() || missile.didDamage()) return false;
        if (missile.isFlare()) return false;
        return true;
    }
}
