package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * Generic OnFireEffect/EveryFrameWeaponEffectPlugin that mimics an APDS-style discarding sabot:
 * on every shot, two decorative petal sprites split off to the left and right of the barrel and tumble away.
 * Purely visual - no collision, no damage, no change to the fired shot itself. Works for any non-beam weapon
 */
public class XLII_SabotDiscardEffect implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    // Petal speed as a fraction of the shot's own muzzle speed
    private static final float PETAL_SPEED_FRACTION = 0.7f;
    private static final float PETAL_SPEED_FRACTION_VARIANCE = 0.1f; // +/-

    // How far each petal diverges from the shot's firing line, in degrees, at SPREAD_REFERENCE_SPEED.
    // Scaled by the weapon's own (nominal) projectile speed - not the shot's actual velocity, so ship
    // movement doesn't affect it. Faster weapons pull this tighter (inversely proportional to speed),
    // slower ones widen it - clamped to [MIN_SPREAD_ANGLE, MAX_SPREAD_ANGLE].
    private static final float SPREAD_ANGLE = 15f;
    private static final float SPREAD_ANGLE_VARIANCE = 8f; // +/- degrees
    private static final float SPREAD_REFERENCE_SPEED = 700f; // su/s at which SPREAD_ANGLE applies as-is
    private static final float MIN_SPREAD_ANGLE = 10f;
    private static final float MAX_SPREAD_ANGLE = 25f;

    // How long a petal takes to fully fade out (seconds)
    private static final float LIFETIME = 2.5f;
    private static final float LIFETIME_VARIANCE = 0.5f; // +/- seconds

    // Fraction of petal speed retained after one full second of drag (heavy, since petals are light and draggy)
    // Yes I know this is in space, shush
    private static final float DRAG_RETENTION_PER_SEC = 0.1f;

    // Tumble rate as the petals peel away from the bore line, degrees/sec
    private static final float SPIN_RATE = 220f;
    private static final float SPIN_RATE_VARIANCE = 60f; // +/- degrees/sec

    private static final String LEFT_SPRITE = "XLII_sabot_empty_left";
    private static final String RIGHT_SPRITE = "XLII_sabot_empty_right";

    // -------------------------------------------------------------------------

    // BALLISTIC_AS_BEAM projectiles (e.g. XLII_montante) don't have their velocity/location set right
    // away - same quirk vanilla's CryoblasterEffect works around. Waits for it to settle before giving up on a shot.
    private static final float SETTLE_TIMEOUT = 0.1f;

    /**
     * A single tumbling, decelerating, fading petal.
     */
    private static class PetalParticle {
        final SpriteAPI sprite;
        final Vector2f position;
        final Vector2f velocity;
        final float spinRate;
        float angle;
        final FaderUtil fader;

        PetalParticle(SpriteAPI sprite, Vector2f position, Vector2f velocity, float angle, float spinRate, float lifetime) {
            this.sprite = sprite;
            this.position = position;
            this.velocity = velocity;
            this.angle = angle;
            this.spinRate = spinRate;

            fader = new FaderUtil(1f, 0.01f, lifetime);
            fader.fadeOut();
        }

        void advance(float amount) {
            float dragMult = (float) Math.pow(DRAG_RETENTION_PER_SEC, amount);
            velocity.scale(dragMult);

            position.x += velocity.x * amount;
            position.y += velocity.y * amount;
            angle += spinRate * amount;

            fader.advance(amount);
        }

        boolean isExpired() {
            return fader.getBrightness() <= 0.01f;
        }
    }

    /**
     * Renders a pair of petals as they fly apart from their spawn point.
     */
    private static class PetalPairRenderPlugin extends BaseCombatLayeredRenderingPlugin {
        private final List<PetalParticle> particles;

        PetalPairRenderPlugin(List<PetalParticle> particles) {
            this.particles = particles;
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) return;

            List<PetalParticle> toRemove = new ArrayList<>();
            for (PetalParticle p : particles) {
                p.advance(amount);
                if (p.isExpired()) toRemove.add(p);
            }
            particles.removeAll(toRemove);
        }

        @Override
        public boolean isExpired() {
            return particles.isEmpty();
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            for (PetalParticle p : particles) {
                p.sprite.setAngle(p.angle - 90f);
                p.sprite.setAlphaMult(p.fader.getBrightness());
                p.sprite.renderAtCenter(p.position.x, p.position.y);
            }
        }

        @Override
        public float getRenderRadius() {
            return 200f;
        }

        private final EnumSet<CombatEngineLayers> layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return layers;
        }
    }

    // -------------------------------------------------------------------------

    private final List<DamagingProjectileAPI> pendingSpawns = new ArrayList<>();

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (weapon == null || weapon.isBeam()) return;
        if (projectile == null) return;

        // Deferred to advance() - see SETTLE_TIMEOUT above for why.
        pendingSpawns.add(projectile);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isInFastTimeAdvance() || pendingSpawns.isEmpty()) return;

        Iterator<DamagingProjectileAPI> it = pendingSpawns.iterator();
        while (it.hasNext()) {
            DamagingProjectileAPI proj = it.next();
            if (!engine.isInPlay(proj)) {
                it.remove();
                continue;
            }

            Vector2f vel = proj.getVelocity();
            float speed = vel != null ? vel.length() : 0f;
            if (speed <= 0f) {
                if (proj.getElapsed() < SETTLE_TIMEOUT) continue; // still waiting for it to settle
                it.remove(); // never got a valid velocity - give up on this shot
                continue;
            }

            it.remove();
            float weaponSpeed = weapon != null ? weapon.getProjectileSpeed() : speed;
            spawnPetalPair(engine, proj.getLocation(), proj.getFacing(), speed, weaponSpeed);
        }
    }

    private void spawnPetalPair(CombatEngineAPI engine, Vector2f origin, float facing, float shotSpeed, float weaponSpeed) {
        if (engine == null || origin == null) return;

        float spreadAngle = computeSpreadAngle(weaponSpeed);
        float leftAngle = facing + jitter(spreadAngle, SPREAD_ANGLE_VARIANCE);
        float rightAngle = facing - jitter(spreadAngle, SPREAD_ANGLE_VARIANCE);

        Vector2f leftVel = Misc.getUnitVectorAtDegreeAngle(leftAngle);
        leftVel.scale(shotSpeed * jitter(PETAL_SPEED_FRACTION, PETAL_SPEED_FRACTION_VARIANCE));
        Vector2f rightVel = Misc.getUnitVectorAtDegreeAngle(rightAngle);
        rightVel.scale(shotSpeed * jitter(PETAL_SPEED_FRACTION, PETAL_SPEED_FRACTION_VARIANCE));

        List<PetalParticle> particles = new ArrayList<>();
        particles.add(new PetalParticle(Global.getSettings().getSprite("fx", LEFT_SPRITE),
                new Vector2f(origin), leftVel, leftAngle,
                jitter(SPIN_RATE, SPIN_RATE_VARIANCE), jitter(LIFETIME, LIFETIME_VARIANCE)));
        particles.add(new PetalParticle(Global.getSettings().getSprite("fx", RIGHT_SPRITE),
                new Vector2f(origin), rightVel, rightAngle,
                jitter(-SPIN_RATE, SPIN_RATE_VARIANCE), jitter(LIFETIME, LIFETIME_VARIANCE)));

        PetalPairRenderPlugin plugin = new PetalPairRenderPlugin(particles);
        CombatEntityAPI entity = engine.addLayeredRenderingPlugin(plugin);
        entity.getLocation().set(origin);
    }

    /**
     * Returns base +/- a random amount up to variance.
     */
    private static float jitter(float base, float variance) {
        return base + (float) (Math.random() * 2f - 1f) * variance;
    }

    /**
     * See SPREAD_ANGLE above for the scaling rationale.
     */
    private static float computeSpreadAngle(float shotSpeed) {
        float scaled = SPREAD_ANGLE * (SPREAD_REFERENCE_SPEED / shotSpeed);
        return Math.max(MIN_SPREAD_ANGLE, Math.min(MAX_SPREAD_ANGLE, scaled));
    }
}
