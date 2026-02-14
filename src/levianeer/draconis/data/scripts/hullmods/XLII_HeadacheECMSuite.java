package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;
import java.util.List;

public class XLII_HeadacheECMSuite extends BaseHullMod {

    // AoE radius = collision radius * this multiplier (same pattern as FortySecond)
    private static final Map<HullSize, Float> ecmRange = new HashMap<>();
    static {
        ecmRange.put(HullSize.FRIGATE, 5f);
        ecmRange.put(HullSize.DESTROYER, 4f);
        ecmRange.put(HullSize.CRUISER, 3f);
        ecmRange.put(HullSize.CAPITAL_SHIP, 2f);
    }

    public static final float SPEED_MULT = 0.5f;            // Missiles and fighters move at 50% speed
    public static final float MISSILE_HP_DRAIN = 20f;       // HP/s drain on missiles in field
    public static final float FIGHTER_DAMAGE_MULT = 1.5f;   // 50% more damage taken by fighters

    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f;
    private static final Color RING_COLOR = new Color(90, 255, 195, 10);

    // Track affected fighters per ship for proper cleanup
    private static final Map<ShipAPI, Set<ShipAPI>> affectedFighters = new HashMap<>();

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) {
            cleanupShip(ship);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        float effectRange = ship.getCollisionRadius() * ecmRange.get(ship.getHullSize());

        // ECM offline during overload, venting, or phase
        boolean ecmDisabled = ship.getFluxTracker().isOverloaded() || ship.getFluxTracker().isVenting() || ship.isPhased();
        if (ecmDisabled) {
            cleanupFighters(ship);
            return;
        }

        // --- Ring visual (drawn once per frame, no lifecycle) ---
        SpriteAPI ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring2");
        float spriteSize = effectRange * 2f * SPRITE_ALIGNMENT_SCALE;
        MagicRender.singleframe(
                ringSprite,
                ship.getLocation(),
                new Vector2f(spriteSize, spriteSize),
                0f,
                RING_COLOR,
                true
        );

        float effectRangeSq = effectRange * effectRange;
        Vector2f shipLocation = ship.getLocation();
        int owner = ship.getOwner();

        // --- Missile effects ---
        List<MissileAPI> nearbyMissiles = CombatUtils.getMissilesWithinRange(shipLocation, effectRange);
        for (MissileAPI missile : nearbyMissiles) {
            if (missile.isFading()) continue;

            ShipAPI source = missile.getSource();
            if (source == null || source.getOwner() == owner) continue;

            float distSq = MathUtils.getDistanceSquared(shipLocation, missile.getLocation());
            if (distSq > effectRangeSq) continue;

            // Slow: cap velocity to 50% of max speed
            Vector2f vel = missile.getVelocity();
            float currentSpeedSq = vel.x * vel.x + vel.y * vel.y;
            float cappedSpeed = missile.getMaxSpeed() * SPEED_MULT;
            float cappedSpeedSq = cappedSpeed * cappedSpeed;
            if (currentSpeedSq > cappedSpeedSq && currentSpeedSq > 0f) {
                float scale = cappedSpeed / (float) Math.sqrt(currentSpeedSq);
                vel.x *= scale;
                vel.y *= scale;
            }

            // HP drain
            engine.applyDamage(
                    missile,
                    missile.getLocation(),
                    MISSILE_HP_DRAIN * amount,
                    DamageType.ENERGY,
                    0f,
                    true,  // does not bypass shields (irrelevant for missiles)
                    false, // not soft flux
                    ship
            );
        }

        // --- Fighter effects ---
        String modId = "XLII_headache_ecm_" + ship.getId();
        Set<ShipAPI> currentlyAffected = affectedFighters.computeIfAbsent(ship, k -> new HashSet<>());
        Set<ShipAPI> stillInRange = new HashSet<>();

        List<ShipAPI> nearbyShips = CombatUtils.getShipsWithinRange(shipLocation, effectRange);
        for (ShipAPI target : nearbyShips) {
            if (!target.isFighter()) continue;
            if (target.getOwner() == owner) continue;
            if (target.isHulk()) continue;

            float distSq = MathUtils.getDistanceSquared(shipLocation, target.getLocation());
            if (distSq > effectRangeSq) continue;

            stillInRange.add(target);

            // Slow: 50% max speed
            target.getMutableStats().getMaxSpeed().modifyMult(modId, SPEED_MULT);

            // Increased damage taken: +50%
            target.getMutableStats().getHullDamageTakenMult().modifyMult(modId, FIGHTER_DAMAGE_MULT);
            target.getMutableStats().getArmorDamageTakenMult().modifyMult(modId, FIGHTER_DAMAGE_MULT);
            target.getMutableStats().getShieldDamageTakenMult().modifyMult(modId, FIGHTER_DAMAGE_MULT);
        }

        // Remove debuffs from fighters that left range
        for (ShipAPI fighter : currentlyAffected) {
            if (!stillInRange.contains(fighter) && fighter != null) {
                removeFighterDebuff(fighter, modId);
            }
        }

        currentlyAffected.clear();
        currentlyAffected.addAll(stillInRange);
    }

    private void removeFighterDebuff(ShipAPI fighter, String modId) {
        fighter.getMutableStats().getMaxSpeed().unmodify(modId);
        fighter.getMutableStats().getHullDamageTakenMult().unmodify(modId);
        fighter.getMutableStats().getArmorDamageTakenMult().unmodify(modId);
        fighter.getMutableStats().getShieldDamageTakenMult().unmodify(modId);
    }

    private void cleanupFighters(ShipAPI ship) {
        Set<ShipAPI> fighters = affectedFighters.get(ship);
        if (fighters == null) return;

        String modId = "XLII_headache_ecm_" + ship.getId();
        for (ShipAPI fighter : fighters) {
            if (fighter != null) {
                removeFighterDebuff(fighter, modId);
            }
        }
        fighters.clear();
    }

    private void cleanupShip(ShipAPI ship) {
        cleanupFighters(ship);
        affectedFighters.remove(ship);
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Projects an electronic countermeasure field that disrupts enemy missiles and fighters within range.", opad);

        tooltip.addPara("Enemy missiles within the field are slowed to %s of their maximum speed and suffer %s damage per second.",
                opad, h,
                Math.round(SPEED_MULT * 100f) + "%",
                Math.round(MISSILE_HP_DRAIN) + "");

        tooltip.addPara("Enemy fighters within the field are slowed to %s of their maximum speed and take %s more damage from all sources.",
                opad, h,
                Math.round(SPEED_MULT * 100f) + "%",
                Math.round((FIGHTER_DAMAGE_MULT - 1f) * 100f) + "%");

        tooltip.addPara("Disabled while the ship is overloaded or venting.", opad);
    }
}