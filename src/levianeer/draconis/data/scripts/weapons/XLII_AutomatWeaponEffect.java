package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import levianeer.draconis.data.scripts.shipsystems.XLII_AmmoSelectorStats;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paired weapon script for XLII_automat (Automat Cannon).
 * <p>
 * Registered as onFireEffect in XLII_automat.wpn and onHitEffect in XLII_automat_shot.proj.
 * Reads the current ammo mode from XLII_AmmoSelectorStats and:
 * <p>
 *   onFire:   sets damage type; begins flight tracking for HE and FRAG projectiles
 *   onHit:    applies HE impact effects; FRAG splits into submunitions on any direct hit
 *   advance:  HE - detonates at max range if missed
 *             FRAG - proximity fuze vs fighters; timed fuze splits into submunitions mid-flight
 */
public class XLII_AutomatWeaponEffect implements OnFireEffectPlugin, OnHitEffectPlugin, EveryFrameWeaponEffectPlugin {

    // HE mode
    private static final float HE_EMP_AMOUNT       = 300f;
    private static final float HE_PIERCE_BASE      = -0.1f;
    private static final float HE_PIERCE_FLUX_MULT = 0.75f;
    private static final Color HE_ARC_CORE   = new Color(35, 105, 155, 255);
    private static final Color HE_ARC_FRINGE = new Color(255, 255, 255, 255);
    private static final String HE_SOUND     = "mine_explosion";
    private static final String HE_ARC_SOUND = "shock_repeater_emp_impact";

    // FRAG mode
    private static final int   FRAG_SUBMUNITIONS      = 8;
    private static final float FRAG_FUZE_FRACTION     = 0.70f; // fire at 70% of weapon range
    private static final float FRAG_PROXIMITY_RADIUS  = 200f;  // proximity fuze vs fighters (su)
    private static final String FRAG_SUB_WEAPON_ID    = "XLII_automat_frag_sub";

    private static final Color  WHITE = Color.WHITE;
    private static final Vector2f ZERO = new Vector2f();

    // -------------------------------------------------------------------------
    // Static flight-tracking maps - shared between onFireEffect and onHitEffect instances
    //   HE_TRACK  : projectile -> float[2]{lastX, lastY}
    //   FRAG_TRACK: projectile -> fuze timer remaining (seconds)
    // -------------------------------------------------------------------------

    private static final Map<DamagingProjectileAPI, float[]> HE_TRACK   = new LinkedHashMap<>();
    private static final Map<DamagingProjectileAPI, Float>   FRAG_TRACK = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // OnFireEffectPlugin
    // -------------------------------------------------------------------------

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (weapon.getShip() == null) return;

        int mode = XLII_AmmoSelectorStats.getMode(weapon.getShip().getId());
        switch (mode) {
            case XLII_AmmoSelectorStats.KINETIC:
                projectile.getDamage().setType(DamageType.KINETIC);
                break;

            case XLII_AmmoSelectorStats.HE:
                projectile.getDamage().setType(DamageType.HIGH_EXPLOSIVE);
                Vector2f loc = projectile.getLocation();
                HE_TRACK.put(projectile, new float[]{loc.x, loc.y});
                break;

            case XLII_AmmoSelectorStats.FRAG:
                projectile.getDamage().setType(DamageType.FRAGMENTATION);
                float fuzeTime = weapon.getRange() * FRAG_FUZE_FRACTION / projectile.getMoveSpeed();
                FRAG_TRACK.put(projectile, fuzeTime);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // OnHitEffectPlugin
    // -------------------------------------------------------------------------

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult,
                      CombatEngineAPI engine) {
        if (point == null) return;

        // Direct hit - remove from tracking to suppress range/fuze detonation
        HE_TRACK.remove(projectile);
        boolean wasFragTracked = FRAG_TRACK.remove(projectile) != null;

        ShipAPI source = projectile.getSource();
        int mode = XLII_AmmoSelectorStats.getMode(source != null ? source.getId() : "");

        if (mode == XLII_AmmoSelectorStats.HE) {
            applyHEImpactEffects(projectile, target, point, shieldHit, engine, source);
        } else if (mode == XLII_AmmoSelectorStats.FRAG && wasFragTracked) {
            // Split on any direct hit - engine removes the projectile automatically
            spawnSubmunitions(projectile, projectile.getWeapon(), engine);
            engine.spawnExplosion(point, projectile.getVelocity(), WHITE, 40f, 0.3f);
        }
        // KINETIC: no special effects
    }

    // -------------------------------------------------------------------------
    // EveryFrameWeaponEffectPlugin
    // -------------------------------------------------------------------------

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) return;

        processHETracking(engine);
        processFRAGTracking(amount, engine, weapon);
    }

    // -------------------------------------------------------------------------
    // HE - range detonation
    // -------------------------------------------------------------------------

    private void processHETracking(CombatEngineAPI engine) {
        if (HE_TRACK.isEmpty()) return;

        Iterator<Map.Entry<DamagingProjectileAPI, float[]>> it = HE_TRACK.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DamagingProjectileAPI, float[]> entry = it.next();
            DamagingProjectileAPI proj = entry.getKey();
            float[] lastPos = entry.getValue();

            if (!engine.isInPlay(proj)) {
                // Left play without a direct hit (onHit removes from map before expiry if it hits)
                applyHERangeDetonation(new Vector2f(lastPos[0], lastPos[1]), proj.getSource(), engine);
                it.remove();
            } else if (proj.didDamage()) {
                it.remove(); // safety: onHit should have already removed, but clean up just in case
            } else {
                lastPos[0] = proj.getLocation().x;
                lastPos[1] = proj.getLocation().y;
            }
        }
    }

    private void applyHERangeDetonation(Vector2f point, ShipAPI source, CombatEngineAPI engine) {
        engine.spawnExplosion(point, ZERO, WHITE, 90f, 0.6f);
        engine.spawnExplosion(point, ZERO, WHITE, 60f, 0.6f);
        engine.addNegativeNebulaParticle(point, ZERO, 30f, 2f, 0f, 0f, 0.5f, WHITE);
        Global.getSoundPlayer().playSound(HE_SOUND, 1f, 1f, point, ZERO);
    }

    private void applyHEImpactEffects(DamagingProjectileAPI projectile, CombatEntityAPI target,
                                       Vector2f point, boolean shieldHit, CombatEngineAPI engine,
                                       ShipAPI source) {
        Vector2f targetVel = target.getVelocity();

        engine.spawnExplosion(point, targetVel, WHITE, 90f, 0.6f);
        engine.spawnExplosion(point, targetVel, WHITE, 60f, 0.6f);
        engine.addNegativeNebulaParticle(point, targetVel, 30f, 2f, 0f, 0f, 0.5f, WHITE);

        if (target instanceof ShipAPI empTarget) {
            boolean applyEmp = !shieldHit;
            if (shieldHit) {
                float pierceChance = empTarget.getHardFluxLevel() * HE_PIERCE_FLUX_MULT + HE_PIERCE_BASE;
                float pierceMult = empTarget.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
                applyEmp = Math.random() < (pierceChance * pierceMult);
            }
            if (applyEmp) {
                engine.spawnEmpArcPierceShields(
                        source, point, empTarget, empTarget,
                        DamageType.ENERGY,
                        projectile.getDamageAmount() * 0.5f,
                        HE_EMP_AMOUNT,
                        100000f,
                        HE_ARC_SOUND,
                        15f,
                        HE_ARC_CORE,
                        HE_ARC_FRINGE
                );
            }
        }

        Global.getSoundPlayer().playSound(HE_SOUND, 1f, 1f, point, ZERO);
    }

    // -------------------------------------------------------------------------
    // FRAG - fuze and submunition split
    // -------------------------------------------------------------------------

    private void processFRAGTracking(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (FRAG_TRACK.isEmpty()) return;

        Iterator<Map.Entry<DamagingProjectileAPI, Float>> it = FRAG_TRACK.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DamagingProjectileAPI, Float> entry = it.next();
            DamagingProjectileAPI proj = entry.getKey();

            if (!engine.isInPlay(proj) || proj.didDamage()) {
                it.remove();
                continue;
            }

            boolean proximityTriggered = isFighterNearby(proj, engine);
            float remaining = proximityTriggered ? 0f : entry.getValue() - amount;
            if (remaining <= 0f) {
                spawnSubmunitions(proj, weapon, engine);
                engine.spawnExplosion(proj.getLocation(), ZERO, WHITE, 40f, 0.3f);
                engine.removeEntity(proj);
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private boolean isFighterNearby(DamagingProjectileAPI proj, CombatEngineAPI engine) {
        ShipAPI source = proj.getSource();
        if (source == null) return false;

        Vector2f loc = proj.getLocation();
        float r2 = FRAG_PROXIMITY_RADIUS * FRAG_PROXIMITY_RADIUS;
        for (ShipAPI ship : engine.getShips()) {
            if (!ship.isAlive()) continue;
            if (ship.getOwner() == source.getOwner()) continue;
            if (ship.getHullSize() != ShipAPI.HullSize.FIGHTER) continue;
            float dx = ship.getLocation().x - loc.x;
            float dy = ship.getLocation().y - loc.y;
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    private void spawnSubmunitions(DamagingProjectileAPI proj, WeaponAPI weapon, CombatEngineAPI engine) {
        ShipAPI source = proj.getSource();
        if (source == null) return;

        Vector2f loc = proj.getLocation();
        float totalSpread = 75f;
        float halfSpread  = totalSpread / 2f;
        float angleStep   = totalSpread / (FRAG_SUBMUNITIONS - 1);
        float jitterRange = angleStep * 0.4f;

        for (int i = 0; i < FRAG_SUBMUNITIONS; i++) {
            float jitter = (float)(Math.random() - 0.5f) * jitterRange;
            float angle  = proj.getFacing() - halfSpread + i * angleStep + jitter;
            engine.spawnProjectile(source, weapon, FRAG_SUB_WEAPON_ID,
                    new Vector2f(loc), angle, proj.getVelocity());
        }
    }
}