package levianeer.draconis.data.scripts.ai;

import java.util.ArrayList;
import java.util.Iterator;
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

    private static final float MAX_RANGE = 1500f;
    private static final float AOE_RADIUS = 250f;
    private static final int MIN_MISSILES_TO_FIRE = 2;

    private static final float REACTION_DELAY_MIN = 0.2f;
    private static final float REACTION_DELAY_MAX = 0.4f;

    // How long another ship's claimed strike point keeps nearby missiles off-limits to this AI -
    // covers the reaction delay plus the system's charge up/active/down time, so two ships don't
    // waste activations jamming the same missiles.
    private static final float CLAIM_DURATION = 1.5f;

    // Shared across all ships running this AI so they can see each other's claims.
    private static final List<Claim> claims = new ArrayList<Claim>();

    private static final class Claim {
        final CombatEngineAPI engine;
        final ShipAPI claimant;
        final Vector2f point;
        final float expiresAt;

        Claim(CombatEngineAPI engine, ShipAPI claimant, Vector2f point, float expiresAt) {
            this.engine = engine;
            this.claimant = claimant;
            this.point = point;
            this.expiresAt = expiresAt;
        }
    }

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

        purgeExpiredClaims();

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;
        if (!AIUtils.canUseSystemThisFrame(ship)) return;

        Vector2f strikePoint = findBestStrikePoint();
        if (strikePoint == null) return;

        claims.add(new Claim(engine, ship, strikePoint, engine.getTotalElapsedTime(false) + CLAIM_DURATION));

        flags.setFlag(AIFlags.SYSTEM_TARGET_COORDS, 1f, strikePoint);
        activationPending = true;
        reactionTimer = REACTION_DELAY_MIN + (REACTION_DELAY_MAX - REACTION_DELAY_MIN) * (float) Math.random();
    }

    private void purgeExpiredClaims() {
        float now = engine.getTotalElapsedTime(false);
        Iterator<Claim> it = claims.iterator();
        while (it.hasNext()) {
            Claim c = it.next();
            // Claims from a previous battle (different engine instance) are always stale.
            if (c.engine != engine || c.expiresAt <= now) {
                it.remove();
            }
        }
    }

    private boolean isClaimedByOther(Vector2f loc) {
        for (Claim c : claims) {
            if (c.engine != engine) continue;
            if (c.claimant == ship) continue;
            if (Misc.getDistance(loc, c.point) <= AOE_RADIUS) return true;
        }
        return false;
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
        // Must check for the enemy owner specifically, not just "not mine" - a friendly missile's
        // source ship can die mid-flight and become a neutral (owner 100) hulk, which would
        // otherwise get misread as hostile here.
        int enemyOwner = ship.getOwner() == 0 ? 1 : 0;
        if (missile.getSource().getOwner() != enemyOwner) return false;
        // Already disabled (fizzling/unguided) missiles aren't worth jamming again.
        if (missile.isFading() || missile.isFizzling() || missile.didDamage()) return false;
        if (missile.isFlare()) return false;
        if (isClaimedByOther(missile.getLocation())) return false;
        return true;
    }
}
