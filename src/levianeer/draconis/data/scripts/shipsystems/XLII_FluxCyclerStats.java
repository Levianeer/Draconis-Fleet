package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class XLII_FluxCyclerStats extends BaseShipSystemScript {

    // ==================== TUNING PARAMETERS ====================

    // Flux drain rate as a multiplier of the ship's current flux dissipation.
    // Net flux gain per second = dissipation × (DRAIN_MULTIPLIER - 1).
    private static final float DRAIN_MULTIPLIER = 1.5f;

    // Hard safety cutoff: force-deactivates the system when flux reaches this level.
    private static final float SAFETY_FLUX_CUTOFF = 0.5f;

    public static final float FLUX_REDUCTION = 100f;

    private static final float PROJECTILE_SPEED_BONUS = 100f;
    private static final float FIRE_RATE_BOOST = 20f;

    // Passive bonuses (when system is NOT active)
    private static final float PASSIVE_SPEED_BOOST = 10f;
    private static final float PASSIVE_ROF_PENALTY = -20f;

    private boolean needsUnapply = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        boolean active = state == State.IN || state == State.ACTIVE || state == State.OUT;

        // Blended stats — smoothly interpolate between passive and active values every frame.
        float rofBonus = PASSIVE_ROF_PENALTY + (FIRE_RATE_BOOST - PASSIVE_ROF_PENALTY) * effectLevel;
        stats.getBallisticRoFMult().modifyPercent(id, rofBonus);
        stats.getEnergyRoFMult().modifyPercent(id, rofBonus);

        // Passive-only stats — fade out as system activates
        stats.getMaxSpeed().modifyPercent(id, PASSIVE_SPEED_BOOST * (1f - effectLevel));
        stats.getZeroFluxMinimumFluxLevel().modifyFlat(id, 2f * (1f - effectLevel));

        // Active-only stats + flux drain
        if (active) {
            modify(id, stats, effectLevel, ship);
            // Refresh every frame with a short duration as a safety net in case the system
            // is interrupted — the flag will expire on its own if apply() stops being called.
            ship.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.DO_NOT_BACK_OFF, 0.5f);
            needsUnapply = true;
        } else {
            if (needsUnapply) {
                unmodify(id, stats, ship);
                ship.getAIFlags().unsetFlag(ShipwideAIFlags.AIFlags.DO_NOT_BACK_OFF);
                needsUnapply = false;
            }
        }
    }

    private void modify(String id, MutableShipStatsAPI stats, float effectLevel, ShipAPI ship) {
        // Hard safety cutoff — deactivate before applying any effects or drain.
        if (ship.getFluxTracker().getFluxLevel() >= SAFETY_FLUX_CUTOFF) {
            ship.getSystem().deactivate();
            return;
        }

        // Clamp to a small positive floor — exactly 0 seems to break weapons with chargeup times?
        float fluxMult = Math.max(0.01f, 1f - (FLUX_REDUCTION / 100f) * effectLevel);
        stats.getEnergyWeaponFluxCostMod().modifyMult(id, fluxMult);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, fluxMult);
        stats.getBeamWeaponFluxCostMult().modifyMult(id, fluxMult);

        stats.getProjectileSpeedMult().modifyPercent(id, PROJECTILE_SPEED_BONUS * effectLevel);

        // Inject flux drain: dissipation × multiplier per second.
        // Passive dissipation continues to operate, partially offsetting the drain.
        // Net effective gain = dissipation × (DRAIN_MULTIPLIER - 1) per second.
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        float amount = engine.getElapsedInLastFrame();
        float dissipation = stats.getFluxDissipation().getModifiedValue();
        float drainPerSecond = dissipation * DRAIN_MULTIPLIER * effectLevel;

        ship.getFluxTracker().increaseFlux(drainPerSecond * amount, false);
    }

    private void unmodify(String id, MutableShipStatsAPI stats, ShipAPI ship) {
        // Remove active-only effects
        stats.getEnergyWeaponFluxCostMod().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getBeamWeaponFluxCostMult().unmodify(id);
        stats.getProjectileSpeedMult().unmodify(id);

    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Never called — runScriptWhileIdle:true in XLII_flux_cycler.system.
        // Active-to-idle cleanup is handled in unmodify(), called from apply().
    }

    public String getDisplayNameOverride(State state, float effectLevel) {
        if (state == State.IDLE || state == State.COOLDOWN) {
            return "flux cycler - cooling";
        }
        else if (state == State.IN || state == State.OUT) {
            return "flux cycler - spooling";
        }
        else if (state == State.ACTIVE) {
            return "flux cycler - firing";
        }
        return null;
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.IDLE || state == State.COOLDOWN) {
            if (index == 5) {
                return new StatusData("fire rate reduced", true);
            }
            if (index == 4) {
                return new StatusData("max speed increased", false);
            }
        }
        if (effectLevel <= 0f) return null;

        if (index == 3) {
            return new StatusData("soft flux rising", true);
        }
        if (index == 2) {
        return new StatusData("weapon flux disabled", false);
        }
        if (index == 1) {
        return new StatusData("projectile speed boosted", false);
        }
        if (index == 0) {
        return new StatusData("fire rate increased", false);
        }
        return null;
    }
}