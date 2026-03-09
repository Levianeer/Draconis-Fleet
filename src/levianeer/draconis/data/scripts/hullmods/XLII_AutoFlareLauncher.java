package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.shipsystems.XLII_DelayedFlareShot;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Hullmod: Auto Flare Launcher
 * <p>
 * Monitors incoming enemy missiles each frame. When a missile enters range and
 * the cooldown has expired, fires a flare burst from all SYSTEM weapon slots
 * using XLII_DelayedFlareShot — the same mechanism as Evasion Protocol.
 * <p>
 * Requires the hull to have at least one SYSTEM-type weapon slot.
 * Disabled while overloaded or venting.
 */
public class XLII_AutoFlareLauncher extends BaseHullMod {

    private static final Logger log = Global.getLogger(XLII_AutoFlareLauncher.class);

    // ==================== TUNING ====================

    /** Range within which an incoming enemy missile triggers a flare burst. */
    static final float DETECTION_RANGE = 900f;

    /** Minimum time between bursts, in seconds. */
    static final float COOLDOWN = 12f;

    /** Minimum number of incoming enemy missiles required to trigger a burst. */
    private static final int MIN_MISSILES = 1;

    // ==================== PER-COMBAT STATE ====================

    /** Remaining cooldown time, keyed by ship ID. Absent or <= 0 means ready. */
    private static final Map<String, Float> cooldownTimers = new HashMap<>();

    /**
     * Cached per-ship flag: true if the hull has at least one SYSTEM weapon slot.
     * Evaluated once per ship per combat and reused every frame.
     */
    private static final Map<String, Boolean> hasSystemSlotCache = new HashMap<>();

    private static CombatEngineAPI lastEngine;

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine) {
            lastEngine = engine;
            cooldownTimers.clear();
            hasSystemSlotCache.clear();
        }
    }

    // ==================== COMBAT LOOP ====================

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        checkClearState();

        if (ship == null || !ship.isAlive()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // Flares can't fire while the ship is disabled
        if (ship.getFluxTracker().isOverloaded() || ship.getFluxTracker().isVenting()) return;

        String shipId = ship.getId();

        // Check (and cache) whether this hull has any SYSTEM slots
        Boolean cached = hasSystemSlotCache.get(shipId);
        if (cached == null) {
            cached = false;
            for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
                if (slot.isSystemSlot()) {
                    cached = true;
                    break;
                }
            }
            hasSystemSlotCache.put(shipId, cached);
        }
        if (!cached) return;

        // Tick down cooldown — skip threat scan until ready
        float remaining = cooldownTimers.getOrDefault(shipId, 0f);
        if (remaining > 0f) {
            cooldownTimers.put(shipId, remaining - amount);
            return;
        }

        boolean isFighter = ship.getHullSize() == HullSize.FIGHTER;
        float detectionRange = isFighter ? DETECTION_RANGE * 0.5f : DETECTION_RANGE;
        float cooldown = isFighter ? COOLDOWN * 0.5f : COOLDOWN;

        // Scan for incoming enemy missiles
        List<MissileAPI> nearby = CombatUtils.getMissilesWithinRange(ship.getLocation(), detectionRange);
        int incomingCount = 0;
        for (MissileAPI missile : nearby) {
            if (missile.isFading()) continue;
            ShipAPI source = missile.getSource();
            if (source == null || source.getOwner() == ship.getOwner()) continue;
            ++incomingCount;
            break;
        }

        if (incomingCount < MIN_MISSILES) return;

        // Schedule burst — identical to EvasionProtocol's activation logic
        int burstSize = XLII_DelayedFlareShot.getBurstSize();
        for (int i = 0; i < burstSize; i++) {
            engine.addPlugin(new XLII_DelayedFlareShot(
                    ship,
                    i * XLII_DelayedFlareShot.BURST_DELAY,
                    i,
                    burstSize));
        }
        cooldownTimers.put(shipId, cooldown);

        log.debug("Draconis: Auto Flare Launcher - Burst triggered for "
                + ship.getHullSpec().getHullName()
                + " (" + incomingCount + "+ incoming missiles detected)");
    }

    // ==================== TOOLTIP ====================

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize,
                                          ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara(
                "Automatically deploys countermeasures from built-in launch ports whenever a hostile missile"
                        + " is detected within %s range.",
                opad, h,
                String.valueOf(Math.round(DETECTION_RANGE)));

        tooltip.addPara(
                "Flares are fired in a burst and cannot be re-triggered for %s seconds."
                        + " Disabled while overloaded or venting.",
                opad, h,
                String.valueOf(Math.round(COOLDOWN)));

        tooltip.addPara(
                "Fighters have a reduced detection range of %s but a shorter %s second cooldown.",
                opad, h,
                String.valueOf(Math.round(DETECTION_RANGE * 0.5f)),
                String.valueOf(Math.round(COOLDOWN * 0.5f)));
    }
}