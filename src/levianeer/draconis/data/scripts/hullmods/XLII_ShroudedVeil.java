package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.impl.combat.NegativeExplosionVisual;
import com.fs.starfarer.api.impl.combat.NegativeExplosionVisual.NEParams;
import com.fs.starfarer.api.impl.combat.RiftCascadeMineExplosion;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.combat.dweller.DwellerShroud;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class XLII_ShroudedVeil extends BaseHullMod {

    private static final float MAX_DODGE = 0.5f;  // dodge chance at 0 flux
    private static final float MIN_DODGE = 0.1f;  // dodge chance floor, reached at FLOOR_FLUX_LEVEL and beyond
    private static final float FLOOR_FLUX_LEVEL = 0.5f;  // flux level (0-1) at which dodge chance bottoms out
    private static final float ACTIVE_ALPHA = 0.5f;  // sprite opacity at full shroud strength (0 flux)
    private static final float ALPHA_RAMP   = 0.3f;  // seconds to fade opacity in/out
    private static final float DODGE_FX_COOLDOWN = 0.5f; // minimum gap between overt dodge FX, so sustained beam fire doesn't spam it
    private static final float DODGE_FX_RADIUS = 40f;

    // Reuse the real Dweller shroud's own colors rather than inventing new ones -
    // same palette DwellerShroud/ShroudedMantleHullmod/ShroudedLensHullmod use.
    private static final Color SHROUD_JITTER_COLOR = Misc.scaleAlpha(DwellerShroud.SHROUD_GLOW_COLOR, 0.06f); // subtle ambient shimmer
    private static final Color DODGE_FLASH_COLOR   = DwellerShroud.SHROUD_OVERLOAD_FRINGE_COLOR;              // overt burst on an actual dodge

    private static CombatEngineAPI lastEngine_ShroudedVeil;
    private static final Map<ShipAPI, VeilState> states = new HashMap<>();

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_ShroudedVeil) {
            lastEngine_ShroudedVeil = engine;
            states.clear();
        }
    }

    // Linear falloff from MAX_DODGE at 0 flux to MIN_DODGE at FLOOR_FLUX_LEVEL, floored beyond that.
    private static float dodgeChanceForFluxLevel(float fluxLevel) {
        float t = Math.min(fluxLevel, FLOOR_FLUX_LEVEL) / FLOOR_FLUX_LEVEL;
        return MAX_DODGE - (MAX_DODGE - MIN_DODGE) * t;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        checkClearState();

        if (ship == null || !ship.isAlive()) {
            states.remove(ship);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        VeilState state = states.get(ship);
        if (state == null) {
            state = new VeilState();
            ship.addListener(state);
            states.put(ship, state);
        }

        // Explicitly incompatible with Safety Overrides (see isApplicableToShip) - forced inert here too
        // as a runtime safety net in case a ship ends up with both anyway (old save, console commands).
        // Also shuts off entirely while overloaded - a helpless ship doesn't get to keep dodging.
        float dodgeChance = (ship.getFluxTracker().isOverloaded()
                || ship.getVariant().hasHullMod(HullMods.SAFETYOVERRIDES))
                ? 0f
                : dodgeChanceForFluxLevel(ship.getFluxTracker().getFluxLevel());
        state.dodgeChance = dodgeChance;

        // Shroud visual strength: 1 at 0 flux (max dodge), 0 at/past FLOOR_FLUX_LEVEL (min dodge).
        float strength = (dodgeChance - MIN_DODGE) / (MAX_DODGE - MIN_DODGE);
        strength = Math.max(0f, Math.min(1f, strength));

        float target = 1f - strength * (1f - ACTIVE_ALPHA);
        if (target < state.alpha) {
            state.alpha = Math.max(target, state.alpha - amount / ALPHA_RAMP);
        } else {
            state.alpha = Math.min(target, state.alpha + amount / ALPHA_RAMP);
        }
        ship.setExtraAlphaMult(state.alpha);

        if (strength > 0f) {
            ship.setJitter(this, SHROUD_JITTER_COLOR, 0.35f * strength, 2, 0f, 3f * strength);
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return !ship.getVariant().hasHullMod(HullMods.SAFETYOVERRIDES);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getVariant().hasHullMod(HullMods.SAFETYOVERRIDES)) {
            return "Incompatible with Safety Overrides";
        }
        return null;
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara(
                "Salvaged plating that never settles into a single, fixed shape. Up to %s of incoming hits " +
                        "simply fail to connect at zero flux, falling off as flux rises until it bottoms out " +
                        "at %s once flux reaches %s or higher.",
                opad, h,
                Math.round(MAX_DODGE * 100f) + "%",
                Math.round(MIN_DODGE * 100f) + "%",
                Math.round(FLOOR_FLUX_LEVEL * 100f) + "%");

        tooltip.addPara(
                "The shroud collapses entirely the moment the ship overloads, leaving it as exposed as any other hull.",
                opad);

        tooltip.addPara(
                "Incompatible with Safety Overrides.",
                new Color(255, 100, 0), opad);
    }

    static class VeilState implements DamageTakenModifier {
        float alpha = 1f;
        float dodgeChance = 0f;
        float lastFxTime = -999f;

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target,
                                         DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (Math.random() >= dodgeChance) return null;

            damage.getModifier().modifyMult("XLII_shroudedVeilDodge", 0f);

            float now = Global.getCombatEngine().getTotalElapsedTime(false);
            if (now - lastFxTime > DODGE_FX_COOLDOWN) {
                lastFxTime = now;

                // Same rift-burst primitive the vanilla Abyssal Glare effect uses (RiftCascadeMineExplosion ->
                // NegativeExplosionVisual), spawned here as a pure visual with no backing explosion/damage.
                NEParams p = RiftCascadeMineExplosion.createStandardRiftParams(DODGE_FLASH_COLOR, DODGE_FX_RADIUS);
                p.numRiftsToSpawn = 1;
                p.withNegativeParticles = false;
                CombatEntityAPI rift = Global.getCombatEngine().addLayeredRenderingPlugin(new NegativeExplosionVisual(p));
                rift.getLocation().set(point);
            }

            return "XLII_shroudedVeilDodge";
        }
    }
}
