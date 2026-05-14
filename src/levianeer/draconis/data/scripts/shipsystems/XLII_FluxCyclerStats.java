package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lwjgl.util.vector.Vector2f;

public class XLII_FluxCyclerStats extends BaseShipSystemScript implements DamageTakenModifier {

    // ==================== TUNING PARAMETERS ====================

    // Damage reduction applied to the shield during the active window.
    private static final float DAMAGE_MULT = 0.9f;

    // Raw incoming damage absorbed by the shield needed for the maximum weapon buff.
    private static final float MAX_RAW_DAMAGE = 1000f;

    // Maximum weapon damage percent bonus at full buff.
    private static final float MAX_DAMAGE_BOOST = 50f;

    // Maximum fire rate mult bonus at full buff.
    private static final float MAX_ROF_BONUS = 1f;

    // ==================== INSTANCE STATE ====================

    private boolean listenerRegistered = false;
    private boolean isTracking = false;
    private float accumulatedDamage = 0f;
    private float buffDamage = 0f;
    private float currentBuffLevel = 0f; // cached for getStatusData()

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        if (!listenerRegistered) {
            ship.addListener(this);
            listenerRegistered = true;
        }

        if (state == State.IN || state == State.ACTIVE) {
            if (!isTracking) {
                accumulatedDamage = 0f;
                isTracking = true;
            }
            applyFortressShield(stats, id, effectLevel);
        } else if (state == State.OUT) {
            if (isTracking) {
                buffDamage = accumulatedDamage;
                isTracking = false;
            }
            applyFortressShield(stats, id, effectLevel);
        } else { // IDLE or COOLDOWN
            isTracking = false;
            stats.getShieldDamageTakenMult().unmodify(id);
            stats.getShieldUpkeepMult().unmodify(id);
            applyDecayingBuff(stats, id, ship);
        }
    }

    private void applyFortressShield(MutableShipStatsAPI stats, String id, float effectLevel) {
        stats.getShieldDamageTakenMult().modifyMult(id, 1f - DAMAGE_MULT * effectLevel);
        stats.getShieldUpkeepMult().modifyMult(id, 0f);
    }

    private void applyDecayingBuff(MutableShipStatsAPI stats, String id, ShipAPI ship) {

        float mult = 1f + MAX_ROF_BONUS * currentBuffLevel;

        String buffId = id + "_buff";
        if (buffDamage <= 0f) {
            clearBuff(stats, buffId);
            currentBuffLevel = 0f;
            return;
        }

        float cooldown = ship.getSystem().getCooldown();
        float remaining = ship.getSystem().getCooldownRemaining();
        float decayFraction = (cooldown > 0f) ? (remaining / cooldown) : 0f;
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
        if (state == State.COOLDOWN) {
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
        if (state == State.COOLDOWN && currentBuffLevel > 0f) {
            if (index == 0) return new StatusData(String.format("+%.0f%% weapon damage", MAX_DAMAGE_BOOST * currentBuffLevel), false);
            if (index == 1) return new StatusData("+" + (int) bonusPercent + "%" + " weapon rate of fire", false);
        }
        return null;
    }
}