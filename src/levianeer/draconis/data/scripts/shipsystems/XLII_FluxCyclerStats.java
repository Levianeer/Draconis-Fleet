package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lwjgl.util.vector.Vector2f;

public class XLII_FluxCyclerStats extends BaseShipSystemScript implements DamageTakenModifier {

    // ==================== TUNING PARAMETERS ====================

    // Damage reduction applied to the shield during the active window.
    private static final float DAMAGE_MULT = 0.9f;

    // Raw incoming damage absorbed by the shield needed for the maximum weapon buff.
    // Public: XLII_FluxCyclerAI reads this to judge when enough buff has been banked.
    public static final float MAX_RAW_DAMAGE = 4000f;

    // Maximum weapon damage percent bonus at full buff.
    private static final float MAX_DAMAGE_BOOST = 50f;

    // Maximum fire rate mult bonus at full buff.
    private static final float MAX_ROF_BONUS = 1f;

    // Seconds the weapon buff takes to decay to zero after the shield drops.
    // Tracked independently of the system's own cooldown field.
    // Public: XLII_FluxCyclerAI reads this to size its reactivation gate.
    public static final float DECAY_DURATION = 6f;

    // ==================== INSTANCE STATE ====================

    private boolean listenerRegistered = false;
    private boolean isTracking = false;
    private float accumulatedDamage = 0f;
    private float buffDamage = 0f;
    private float decayTimeRemaining = 0f;
    private float currentBuffLevel = 0f; // cached for getStatusData()

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        if (!listenerRegistered) {
            ship.addListener(this);
            listenerRegistered = true;
        }

        boolean holdingShield = (state == State.IN || state == State.ACTIVE || state == State.OUT);

        if (holdingShield) {
            if (!isTracking) {
                accumulatedDamage = 0f;
                isTracking = true;
            }
            // KEEP_SHIELDS_ON decays and must be refreshed every frame the system wants the
            // shield up - a one-time set at activation isn't enough to stop the ship's own
            // shield AI from lowering shields (and forcing this SHIELD_MOD off) mid-cycle.
            ship.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON, 1f);
            applyFortressShield(stats, id, effectLevel);
        } else { // IDLE or COOLDOWN
            if (isTracking) {
                // Bank whatever was accumulated even if the shield was forced down (e.g. an
                // overload) and the system jumped straight from ACTIVE to IDLE without ever
                // passing through OUT - otherwise the buff is silently lost instead of banked.
                buffDamage = accumulatedDamage;
                decayTimeRemaining = (buffDamage > 0f) ? DECAY_DURATION : 0f;
                isTracking = false;
            }
            stats.getShieldDamageTakenMult().unmodify(id);
            stats.getShieldUpkeepMult().unmodify(id);
            applyDecayingBuff(stats, id);
        }
    }

    private void applyFortressShield(MutableShipStatsAPI stats, String id, float effectLevel) {
        stats.getShieldDamageTakenMult().modifyMult(id, 1f - DAMAGE_MULT * effectLevel);
        stats.getShieldUpkeepMult().modifyMult(id, 0f);
    }

    private void applyDecayingBuff(MutableShipStatsAPI stats, String id) {

        float mult = 1f + MAX_ROF_BONUS * currentBuffLevel;

        String buffId = id + "_buff";
        if (buffDamage <= 0f) {
            clearBuff(stats, buffId);
            currentBuffLevel = 0f;
            return;
        }

        decayTimeRemaining = Math.max(0f, decayTimeRemaining - Global.getCombatEngine().getElapsedInLastFrame());
        float decayFraction = decayTimeRemaining / DECAY_DURATION;
        currentBuffLevel = Math.min(1f, buffDamage / MAX_RAW_DAMAGE) * decayFraction;

        if (currentBuffLevel <= 0f) {
            clearBuff(stats, buffId);
            return;
        }

        stats.getBallisticWeaponDamageMult().modifyPercent(buffId, MAX_DAMAGE_BOOST * currentBuffLevel);
        stats.getEnergyWeaponDamageMult().modifyPercent(buffId, MAX_DAMAGE_BOOST * currentBuffLevel);

        stats.getBallisticRoFMult().modifyMult(buffId, mult);
        stats.getEnergyRoFMult().modifyMult(buffId, mult);
    }

    private void clearBuff(MutableShipStatsAPI stats, String buffId) {
        stats.getBallisticWeaponDamageMult().unmodify(buffId);
        stats.getEnergyWeaponDamageMult().unmodify(buffId);

        stats.getBallisticRoFMult().unmodify(buffId);
        stats.getEnergyRoFMult().unmodify(buffId);
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (isTracking && shieldHit) {
            accumulatedDamage += damage.getDamage();
        }
        return null;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Never called - runScriptWhileIdle:true in XLII_flux_cycler.system.
    }

    @Override
    public String getDisplayNameOverride(State state, float effectLevel) {
        if (state == State.IN || state == State.ACTIVE || state == State.OUT) {
            return "flux cycler - cycling";
        }
        if ((state == State.IDLE || state == State.COOLDOWN) && decayTimeRemaining > 0f) {
            return currentBuffLevel > 0f ? "flux cycler - surging" : "flux cycler - cooling";
        }
        return null;
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        float mult = 1f + MAX_ROF_BONUS * currentBuffLevel;
        float bonusPercent = (int) ((mult - 1f) * 100f);

        if (state == State.IN || state == State.ACTIVE || state == State.OUT) {
            if (index == 0) return new StatusData("cycler active", false);
        }
        if ((state == State.IDLE || state == State.COOLDOWN) && decayTimeRemaining > 0f && currentBuffLevel > 0f) {
            if (index == 0) return new StatusData(String.format("+%.0f%% weapon damage", MAX_DAMAGE_BOOST * currentBuffLevel), false);
            if (index == 1) return new StatusData("+" + (int) bonusPercent + "%" + " weapon rate of fire", false);
        }
        return null;
    }
}