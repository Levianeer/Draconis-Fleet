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

public class XLII_EntropicField extends BaseHullMod {

    // AoE radius = collision radius * this multiplier (same pattern as XLII_HeadacheECMSuite),
    // bumped 50% over that base (5/4/3/2) per author request after testing.
    private static final Map<HullSize, Float> fieldRangeMult = new HashMap<>();
    static {
        fieldRangeMult.put(HullSize.FRIGATE, 7.5f);
        fieldRangeMult.put(HullSize.DESTROYER, 6f);
        fieldRangeMult.put(HullSize.CRUISER, 4.5f);
        fieldRangeMult.put(HullSize.CAPITAL_SHIP, 3f);
    }

    public static final float REPAIR_TIME_MULT = 2f; // weapon/engine repair takes this many times longer

    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f;
    // Omega's own established color (DamperFieldOmegaStats' weapon glow/jitter tint), at low alpha for a subtle ring
    private static final Color RING_COLOR = new Color(100, 165, 255, 12);

    private static final float FADE_TIME = 0.25f;

    // Track affected ships per source ship for proper cleanup
    private static final Map<ShipAPI, Set<ShipAPI>> affectedShips = new HashMap<>();
    private static final Map<ShipAPI, Float> effectLevels = new HashMap<>();
    private static SpriteAPI ringSprite = null;
    private static CombatEngineAPI lastEngine_EntropicField;

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_EntropicField) {
            lastEngine_EntropicField = engine;
            affectedShips.clear();
            effectLevels.clear();
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        checkClearState();

        if (ship == null || !ship.isAlive()) {
            cleanupShip(ship);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        Float rangeMult = fieldRangeMult.get(ship.getHullSize());
        if (rangeMult == null) return;
        float effectRange = ship.getCollisionRadius() * rangeMult;

        // Field offline during overload, venting, or phase - same convention as Headache ECM Suite
        boolean fieldDisabled = ship.getFluxTracker().isOverloaded() || ship.getFluxTracker().isVenting() || ship.isPhased();
        if (fieldDisabled) {
            effectLevels.put(ship, 0f);
            cleanupAffected(ship);
            return;
        }

        float effectRangeSq = effectRange * effectRange;
        Vector2f shipLocation = ship.getLocation();
        int owner = ship.getOwner();

        String modId = "XLII_entropicField_" + ship.getId();
        Set<ShipAPI> currentlyAffected = affectedShips.computeIfAbsent(ship, k -> new HashSet<>());
        Set<ShipAPI> stillInRange = new HashSet<>();

        List<ShipAPI> nearbyShips = CombatUtils.getShipsWithinRange(shipLocation, effectRange);
        for (ShipAPI target : nearbyShips) {
            if (target.getOwner() == owner) continue;
            if (target.isFighter() || target.isStationModule() || target.isHulk()) continue;

            float distSq = MathUtils.getDistanceSquared(shipLocation, target.getLocation());
            if (distSq > effectRangeSq) continue;

            stillInRange.add(target);

            target.getMutableStats().getCombatWeaponRepairTimeMult().modifyMult(modId, REPAIR_TIME_MULT);
            target.getMutableStats().getCombatEngineRepairTimeMult().modifyMult(modId, REPAIR_TIME_MULT);
        }

        // Remove the debuff from ships that left range
        for (ShipAPI previouslyAffected : currentlyAffected) {
            if (!stillInRange.contains(previouslyAffected) && previouslyAffected != null) {
                removeDebuff(previouslyAffected, modId);
            }
        }

        currentlyAffected.clear();
        currentlyAffected.addAll(stillInRange);

        // --- Ramp effect level, for the ring fade in/out ---
        // Shows whenever the field itself is active (we only reach this point when it isn't
        // disabled), not gated on actually having caught a qualifying enemy ship - a broadcast
        // field's radius should be visible so the ship can be positioned, not only once it's
        // already caught something.
        float currentLevel = Math.min(1f, effectLevels.getOrDefault(ship, 0f) + amount / FADE_TIME);
        effectLevels.put(ship, currentLevel);

        // --- Ring visual ---
        if (currentLevel > 0f) {
            if (ringSprite == null) ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring2");
            float spriteSize = effectRange * 2f * SPRITE_ALIGNMENT_SCALE * currentLevel;
            MagicRender.singleframe(ringSprite, ship.getLocation(), new Vector2f(spriteSize, spriteSize), 0f, RING_COLOR, true);
        }
    }

    private void removeDebuff(ShipAPI target, String modId) {
        target.getMutableStats().getCombatWeaponRepairTimeMult().unmodify(modId);
        target.getMutableStats().getCombatEngineRepairTimeMult().unmodify(modId);
    }

    private void cleanupAffected(ShipAPI ship) {
        Set<ShipAPI> ships = affectedShips.get(ship);
        if (ships == null) return;

        String modId = "XLII_entropicField_" + ship.getId();
        for (ShipAPI target : ships) {
            if (target != null) removeDebuff(target, modId);
        }
        ships.clear();
    }

    private void cleanupShip(ShipAPI ship) {
        cleanupAffected(ship);
        affectedShips.remove(ship);
        effectLevels.remove(ship);
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
                "Broadcasts a field of unresolved, contradictory information across local sensor and repair " +
                        "networks - nothing damaged inside it stays diagnosed long enough to actually get fixed.",
                opad);

        tooltip.addPara(
                "Enemy ships within the field take %s longer to repair damaged weapons and engines.",
                opad, h, Math.round((REPAIR_TIME_MULT - 1f) * 100f) + "%");

        tooltip.addPara(
                "Disabled while the ship is overloaded, venting, or phased.",
                opad);
    }
}
