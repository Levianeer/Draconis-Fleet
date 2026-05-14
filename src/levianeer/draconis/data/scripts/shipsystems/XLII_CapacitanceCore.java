package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;

public class XLII_CapacitanceCore extends BaseShipSystemScript implements DamageDealtModifier {

    // Passive hard flux dissipation
    private static final float HARD_FLUX_DISSIPATION_FRACTION = 0.05f;

    // Visual arc settings (weapon mount decoration)
    private static final float VISUAL_ARC_RANGE = 60f;
    private static final float VISUAL_ARC_THICKNESS = 6f;
    private static final int VISUAL_ARC_BUDGET = 3;
    private static final Color VISUAL_ARC_FRINGE = new Color(100, 150, 255, 255);
    private static final Color VISUAL_ARC_CORE = new Color(255,255,255,255);

    // EMP on-hit arc settings
    private static final float EMP_ARC_RANGE = 150f;
    private static final float EMP_ARC_THICKNESS = 15f;
    private static final Color EMP_HIT_FRINGE = new Color(100, 150, 255, 255);
    private static final Color EMP_HIT_CORE = new Color(255,255,255,255);
    private static final String EMP_SOUND = "tachyon_lance_emp_impact";

    // Beam throttle
    private static final float BEAM_EMP_CHANCE_PER_FRAME = 0.1f;

    // Instance state
    private boolean listenerRegistered = false;
    private boolean active = false;
    private final IntervalUtil visualArcInterval = new IntervalUtil(0.25f, 0.75f);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        if (!listenerRegistered) {
            ship.addListener(this);
            listenerRegistered = true;
        }

        if (state == State.IN || state == State.ACTIVE || state == State.OUT) {
            active = true;
            stats.getHardFluxDissipationFraction().modifyFlat(id, HARD_FLUX_DISSIPATION_FRACTION * effectLevel);

            CombatEngineAPI engine = Global.getCombatEngine();
            if (!engine.isPaused()) {
                visualArcInterval.advance(engine.getElapsedInLastFrame());
                if (visualArcInterval.intervalElapsed()) {
                    spawnWeaponArcs(ship, engine, effectLevel);
                }
            }
        } else {
            active = false;
            stats.getHardFluxDissipationFraction().unmodify(id);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Never called - runScriptWhileIdle:true in system file.
    }

    // ==================== EMP ON HIT ====================

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (!active) return null;
        if (!(target instanceof ShipAPI targetShip)) return null;
        if (param instanceof EmpArcEntityAPI) return null;

        CombatEngineAPI engine = Global.getCombatEngine();

        if (param instanceof DamagingProjectileAPI proj) {
            float damageAmount = proj.getDamageAmount();
            float empAmount = proj.getEmpAmount();
            if (empAmount >= damageAmount) return null;

            spawnEmpArcOnHit(engine, proj.getSource(), point, targetShip, shieldHit,
                    damageAmount, EMP_ARC_THICKNESS);

        } else if (param instanceof BeamAPI beam) {
            WeaponAPI weapon = beam.getWeapon();
            if (weapon == null) return null;

            float weaponEmp = weapon.getDamage().getFluxComponent();
            float weaponDamage = weapon.getDamage().getDamage();
            if (weaponEmp >= weaponDamage) return null;

            if ((float) Math.random() > BEAM_EMP_CHANCE_PER_FRAME) return null;

            float frameDamage = beam.getDamage().getDamage();
            spawnEmpArcOnHit(engine, beam.getSource(), point, targetShip, shieldHit,
                    frameDamage, EMP_ARC_THICKNESS * 0.7f);
        }

        return null;
    }

    private void spawnEmpArcOnHit(CombatEngineAPI engine, ShipAPI source, Vector2f point,
                                  ShipAPI target, boolean shieldHit, float empAmount, float thickness) {
        if (shieldHit) {
            float pierceChance = target.getHardFluxLevel() - 0.1f;
            pierceChance *= target.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
            if ((float) Math.random() >= pierceChance) return;

            engine.spawnEmpArcPierceShields(source, point, target, target,
                    DamageType.ENERGY, 0f, empAmount,
                    EMP_ARC_RANGE, EMP_SOUND, thickness,
                    EMP_HIT_FRINGE, EMP_HIT_CORE);
        } else {
            engine.spawnEmpArc(source, point, target, target,
                    DamageType.ENERGY, 0f, empAmount,
                    EMP_ARC_RANGE, EMP_SOUND, thickness,
                    EMP_HIT_FRINGE, EMP_HIT_CORE);
        }
    }

    // ==================== VISUAL ARCS ====================

    private void spawnWeaponArcs(ShipAPI ship, CombatEngineAPI engine, float effectLevel) {
        List<WeaponAPI> weapons = ship.getAllWeapons();

        // Count valid weapons and pick random subset
        int count = 0;
        for (WeaponAPI w : weapons) {
            if (isValidWeaponMount(w)) count++;
        }
        if (count == 0) return;

        int budget = Math.min(count, VISUAL_ARC_BUDGET);
        float selectChance = (float) budget / count;
        float thickness = VISUAL_ARC_THICKNESS * effectLevel;

        int spawned = 0;
        for (WeaponAPI w : weapons) {
            if (spawned >= budget) break;
            if (!isValidWeaponMount(w)) continue;
            if ((float) Math.random() > selectChance) continue;

            float sizeMult = getSizeMult(w);
            Vector2f loc = w.getLocation();
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = VISUAL_ARC_RANGE * sizeMult * (0.4f + 0.6f * (float) Math.random());
            Vector2f end = new Vector2f(
                    loc.x + (float) Math.cos(angle) * dist,
                    loc.y + (float) Math.sin(angle) * dist
            );

            engine.spawnEmpArcVisual(loc, ship, end, ship, thickness * sizeMult, VISUAL_ARC_FRINGE, VISUAL_ARC_CORE);
            spawned++;
        }
    }

    private static float getSizeMult(WeaponAPI w) {
        return switch (w.getSize()) {
            case LARGE -> 1.25f;
            case MEDIUM -> 1.0f;
            default -> 0.75f;
        };
    }

    private static boolean isValidWeaponMount(WeaponAPI w) {
        WeaponType type = w.getType();
        return type != WeaponType.DECORATIVE
                && type != WeaponType.SYSTEM
                && type != WeaponType.LAUNCH_BAY
                && !w.getSlot().isSystemSlot()
                && !w.getSlot().isDecorative();
    }

    // ==================== STATUS ====================

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (effectLevel <= 0f) return null;
        if (index == 0) return new StatusData("Dealing bonus EMP damage", false);
        if (index == 1) return new StatusData("Flux shunt active", false);
        return null;
    }
}