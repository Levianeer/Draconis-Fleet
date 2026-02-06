package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.*;

public class XLII_SystemBurnOnHitEffect extends BaseCombatLayeredRenderingPlugin implements OnHitEffectPlugin {

    // Static constants for DOT configuration
    public static final int NUM_TICKS = 7; // Each tick is on average .9 seconds
    public static final float DOT_DAMAGE_MULT = 2.0f; // % of weapon damage as DOT
    public static final String STATUS_KEY = "XLII_system_burn"; // Unique key for the status effect
    private static final String STATUS_REGISTERED_KEY = "XLII_burn_status_registered";

    // Static registry for tracking ignite instances per ship
    private static final Map<ShipAPI, List<IgniteInstance>> activeIgnites = new HashMap<>();
    private static final Object igniteMapLock = new Object();

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (shieldHit || projectile.isFading() || !(target instanceof ShipAPI ship)) {
            return;
        }

        // Calculate DOT damage based on weapon's base damage
        WeaponAPI weapon = projectile.getWeapon();
        float totalDotDamage = (weapon != null) ? weapon.getDamage().getDamage() * DOT_DAMAGE_MULT : 500f;
        float dps = totalDotDamage / NUM_TICKS; // Calculate DPS for this instance

        // Calculate offset from ship center
        Vector2f offset = Vector2f.sub(point, ship.getLocation(), new Vector2f());
        offset = Misc.rotateAroundOrigin(offset, -ship.getFacing());

        // Create new ignite instance
        IgniteInstance newIgnite = new IgniteInstance(projectile, ship, offset, totalDotDamage, dps);

        // Add the visual effect to the engine
        CombatEntityAPI entity = engine.addLayeredRenderingPlugin(newIgnite);
        entity.getLocation().set(point);

        // Register this ignite instance
        registerIgnite(ship, newIgnite);

        // Register status plugin if not already registered and player is affected
        if (ship == engine.getPlayerShip() && !engine.getCustomData().containsKey(STATUS_REGISTERED_KEY)) {
            engine.addPlugin(new SystemBurnStatusPlugin());
            engine.getCustomData().put(STATUS_REGISTERED_KEY, Boolean.TRUE);
        }
    }

    // Register a new ignite instance
    private void registerIgnite(ShipAPI ship, IgniteInstance ignite) {
        synchronized (igniteMapLock) {
            activeIgnites.computeIfAbsent(ship, k -> new ArrayList<>()).add(ignite);
        }
    }

    // Clean up expired ignite
    public static void unregisterIgnite(ShipAPI ship, IgniteInstance ignite) {
        synchronized (igniteMapLock) {
            List<IgniteInstance> ignites = activeIgnites.get(ship);
            if (ignites != null) {
                ignites.remove(ignite);
                if (ignites.isEmpty()) {
                    activeIgnites.remove(ship);
                }
            }
        }
    }

    // Get the currently active (highest DPS) ignite for damage dealing
    public static IgniteInstance getActiveIgnite(ShipAPI ship) {
        synchronized (igniteMapLock) {
            List<IgniteInstance> ignites = activeIgnites.get(ship);
            if (ignites == null || ignites.isEmpty()) return null;

            // Clean up expired ignites first - use safe iteration to avoid concurrent modification
            List<IgniteInstance> toRemove = new ArrayList<>();
            for (IgniteInstance ignite : new ArrayList<>(ignites)) {
                if (ignite == null || ignite.isExpired()) {
                    toRemove.add(ignite);
                }
            }
            ignites.removeAll(toRemove);

            if (ignites.isEmpty()) {
                activeIgnites.remove(ship);
                return null;
            }

            // Return the highest DPS ignite that's still active
            IgniteInstance best = null;
            for (IgniteInstance ignite : new ArrayList<>(ignites)) {
                if (ignite != null && !ignite.isExpired() && ignite.ticks < NUM_TICKS) {
                    if (best == null || ignite.dps > best.dps) {
                        best = ignite;
                    }
                }
            }
            return best;
        }
    }

    // Get status info for display
    public static DOTStatusInfo getStatusInfo(ShipAPI ship) {
        synchronized (igniteMapLock) {
            List<IgniteInstance> ignites = activeIgnites.get(ship);
            if (ignites == null || ignites.isEmpty()) return null;

            // Clean up expired ignites safely
            List<IgniteInstance> toRemove = new ArrayList<>();
            for (IgniteInstance ignite : new ArrayList<>(ignites)) {
                if (ignite == null || ignite.isExpired()) {
                    toRemove.add(ignite);
                }
            }
            ignites.removeAll(toRemove);

            if (ignites.isEmpty()) {
                activeIgnites.remove(ship);
                return null;
            }

            IgniteInstance activeIgnite = getActiveIgnite(ship);
            if (activeIgnite == null) return null;

            float totalRemainingDamage = 0f;
            for (IgniteInstance ignite : new ArrayList<>(ignites)) {
                if (ignite != null && !ignite.isExpired() && ignite.ticks < NUM_TICKS) {
                    int ticksLeft = NUM_TICKS - ignite.ticks;
                    totalRemainingDamage += ignite.totalDamage * ticksLeft / (float) NUM_TICKS;
                }
            }

            int ticksLeft = NUM_TICKS - activeIgnite.ticks;
            return new DOTStatusInfo(ignites.size(), activeIgnite.dps, totalRemainingDamage, ticksLeft);
        }
    }

    // Helper class for status info
        public record DOTStatusInfo(int totalInstances, float activeDPS, float totalRemainingDamage, int ticksRemaining) {
    }

    // Particle data class (copied from base game)
    public static class ParticleData {
        public SpriteAPI sprite;
        public Vector2f offset = new Vector2f();
        public Vector2f vel;
        public float scale;
        public float scaleIncreaseRate;
        public float turnDir;
        public float angle;

        public float maxDur;
        public FaderUtil fader;
        public float elapsed = 0f;
        public float baseSize;

        public Color color = new Color(255, 100, 50, 35); // Orange/red for burn effect

        public ParticleData(float baseSize, float maxDur, float endSizeMult) {
            sprite = Global.getSettings().getSprite("systemMap", "icon_black_hole_well");
            float i = Misc.random.nextInt(4);
            float j = Misc.random.nextInt(4);
            sprite.setTexWidth(0.25f);
            sprite.setTexHeight(0.25f);
            sprite.setTexX(i * 0.25f);
            sprite.setTexY(j * 0.25f);
            sprite.setAdditiveBlend();

            angle = (float) Math.random() * 360f;

            this.maxDur = maxDur;
            scaleIncreaseRate = endSizeMult / maxDur;
            if (endSizeMult < 1f) {
                scaleIncreaseRate = -1f * endSizeMult;
            }
            scale = 1f;

            this.baseSize = baseSize;
            turnDir = Math.signum((float) Math.random() - 0.5f) * 20f * (float) Math.random();

            float driftDir = (float) Math.random() * 360f;
            vel = Misc.getUnitVectorAtDegreeAngle(driftDir);
            vel.scale(0.25f * baseSize / maxDur * (1f + (float) Math.random()));

            fader = new FaderUtil(0f, 0.5f, 0.5f);
            fader.forceOut();
            fader.fadeIn();
        }

        public void advance(float amount) {
            scale += scaleIncreaseRate * amount;

            offset.x += vel.x * amount;
            offset.y += vel.y * amount;

            angle += turnDir * amount;

            elapsed += amount;
            if (maxDur - elapsed <= fader.getDurationOut() + 0.1f) {
                fader.fadeOut();
            }
            fader.advance(amount);
        }
    }

    // Individual ignite instance - follows base game pattern
    public static class IgniteInstance extends BaseCombatLayeredRenderingPlugin {

        protected final List<ParticleData> particles = new ArrayList<>();
        protected final DamagingProjectileAPI proj;
        protected final ShipAPI target;
        protected final Vector2f offset;
        protected final float totalDamage;
        protected final float dps;
        protected int ticks = 0;
        protected IntervalUtil interval;
        protected FaderUtil fader = new FaderUtil(1f, 0.5f, 0.5f);

        // For damage dealing - only the active ignite deals damage
        private static final IntervalUtil globalDamageInterval = new IntervalUtil(0.8f, 1.0f);

        // Pre-define layers enum to avoid recreation
        private static final EnumSet<CombatEngineLayers> LAYERS = EnumSet.of(CombatEngineLayers.BELOW_INDICATORS_LAYER);

        public IgniteInstance(DamagingProjectileAPI projectile, ShipAPI target, Vector2f offset, float totalDamage, float dps) {
            this.proj = projectile;
            this.target = target;
            this.offset = new Vector2f(offset);
            this.totalDamage = totalDamage;
            this.dps = dps;
            this.interval = new IntervalUtil(0.8f, 1.0f);
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return LAYERS;
        }

        public float getRenderRadius() {
            return 500f;
        }

        @Override
        public void init(CombatEntityAPI entity) {
            super.init(entity);
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) return;

            // Update entity location to follow target (like base game)
            Vector2f loc = new Vector2f(offset);
            loc = Misc.rotateAroundOrigin(loc, target.getFacing());
            Vector2f.add(target.getLocation(), loc, loc);
            entity.getLocation().set(loc);

            // Update particles
            List<ParticleData> toRemove = new ArrayList<>();
            for (ParticleData p : particles) {
                p.advance(amount);
                if (p.elapsed >= p.maxDur) {
                    toRemove.add(p);
                }
            }
            particles.removeAll(toRemove);

            // Handle fading and sound
            boolean shouldEnd = ticks >= NUM_TICKS || !target.isAlive() || !Global.getCombatEngine().isEntityInPlay(target);
            float volume = 1f;
            if (shouldEnd) {
                fader.fadeOut();
                volume = fader.getBrightness();
            }
            fader.advance(amount);

            // Play sound (all ignites play sound, but volume varies)
            IgniteInstance activeIgnite = getActiveIgnite(target);
            float soundVolume = (this == activeIgnite) ? volume : volume * 0.3f; // Active ignite is louder
            Global.getSoundPlayer().playLoop("disintegrator_loop", target, 1f, soundVolume, loc, target.getVelocity());

            // Only the active ignite deals damage (POE-style)
            if (this == activeIgnite) {
                globalDamageInterval.advance(amount);
                if (globalDamageInterval.intervalElapsed() && ticks < NUM_TICKS) {
                    dealDamage();
                    ticks++;

                    // Add particles when dealing damage
                    int numParticles = 3;
                    for (int i = 0; i < numParticles; i++) {
                        addParticle();
                    }
                }
            } else {
                // Non-active ignites still advance their tick counter for duration tracking
                // but don't deal damage
                interval.advance(amount);
                if (interval.intervalElapsed() && ticks < NUM_TICKS) {
                    ticks++;
                    // Add fewer particles for visual feedback
                    addParticle();
                }
            }
        }

        protected void addParticle() {
            if (particles.size() < 20) { // Prevent excessive particle buildup
                ParticleData p = new ParticleData(25f, 3f + (float) Math.random() * 2f, 2f);
                particles.add(p);
                p.offset = Misc.getPointWithinRadius(p.offset, 15f);
            }
        }

        protected void dealDamage() {
            CombatEngineAPI engine = Global.getCombatEngine();

            float damagePerTick = totalDamage / (float) NUM_TICKS;
            Vector2f point = new Vector2f(entity.getLocation());

            // Apply damage
            engine.applyDamage(
                    target,                     // target
                    point,                      // point of impact
                    damagePerTick,              // damage amount
                    DamageType.ENERGY,          // damage type
                    0f,                         // emp damage
                    true,                       // bypass shields
                    false,                      // deal soft flux damage
                    proj.getSource(),           // source
                    false                       // ignore armor
            );
        }

        @Override
        public boolean isExpired() {
            boolean expired = particles.isEmpty() &&
                    (ticks >= NUM_TICKS || !target.isAlive() || !Global.getCombatEngine().isEntityInPlay(target));

            if (expired) {
                unregisterIgnite(target, this);
            }

            return expired;
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            if (particles.isEmpty()) return; // Early exit if no particles

            float x = entity.getLocation().x;
            float y = entity.getLocation().y;
            float b = viewport.getAlphaMult();

            // Different blend mode for burn effect
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);

            for (ParticleData p : particles) {
                float size = p.baseSize * p.scale;
                Vector2f loc = new Vector2f(x + p.offset.x, y + p.offset.y);

                p.sprite.setAngle(p.angle);
                p.sprite.setSize(size, size);
                p.sprite.setAlphaMult(b * p.fader.getBrightness());
                p.sprite.setColor(p.color);
                p.sprite.renderAtCenter(loc.x, loc.y);
            }

            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        }
    }

    // Status plugin for UI display
    public static class SystemBurnStatusPlugin implements EveryFrameCombatPlugin {

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            ShipAPI playerShip = engine.getPlayerShip();
            if (playerShip == null || !playerShip.isAlive()) return;

            DOTStatusInfo info = getStatusInfo(playerShip);

            if (info != null && info.totalInstances > 0) {
                String title = info.totalInstances > 1 ?
                        "System Burn (" + info.totalInstances + " stacks)" :
                        "System Burn";

                String description = String.format("DPS: %.0f | Remaining: %.0f",
                        info.activeDPS, Math.max(1, Math.ceil(info.totalRemainingDamage)));

                engine.maintainStatusForPlayerShip(
                        STATUS_KEY,
                        "graphics/icons/tactical/reality_disrupted.png",
                        title,
                        description,
                        true
                );
            }
        }

        @Override
        public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
            // do nothing
        }

        @Override
        public void renderInWorldCoords(ViewportAPI viewport) {
            // do nothing
        }

        @Override
        public void renderInUICoords(ViewportAPI viewport) {
            // do nothing
        }

        @Override
        @Deprecated
        public void init(CombatEngineAPI engine) {
            // do nothing
        }
    }
}