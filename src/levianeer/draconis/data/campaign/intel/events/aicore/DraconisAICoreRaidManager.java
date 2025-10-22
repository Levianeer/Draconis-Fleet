package levianeer.draconis.data.campaign.intel.events.aicore;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;

import java.util.Random;

/**
 * Manages AI Core raid hostile activity factor and triggers raids
 */
public class DraconisAICoreRaidManager implements EveryFrameScript {

    private boolean factorAdded = false;
    private float checkInterval = 0f;
    private static final float CHECK_DAYS = 30f;  // Check for raid opportunities every 30 days

    // Raid cap and cooldown settings
    private static final int MAX_ACTIVE_RAIDS = 1;  // Maximum number of simultaneous raids
    private static final float COOLDOWN_SUCCESS_DAYS = 60f;  // Cooldown after successful raid
    private static final float COOLDOWN_FAILURE_DAYS = 120f;  // Longer cooldown after failed raid

    // Memory keys for persistent data
    private static final String ACTIVE_RAID_COUNT_KEY = "$draconis_activeRaidCount";
    private static final String LAST_RAID_TIMESTAMP_KEY = "$draconis_lastRaidTimestamp";
    private static final String COOLDOWN_END_TIMESTAMP_KEY = "$draconis_cooldownEndTimestamp";

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        // Try to register factor with Hostile Activity if available (optional integration)
        if (!factorAdded) {
            HostileActivityEventIntel intel = HostileActivityEventIntel.get();

            if (intel != null) {
                DraconisAICoreRaidFactor existingFactor = (DraconisAICoreRaidFactor)
                        intel.getFactorOfClass(DraconisAICoreRaidFactor.class);

                if (existingFactor == null) {
                    DraconisAICoreRaidFactor factor = new DraconisAICoreRaidFactor(intel);
                    intel.addFactor(factor);

                    Global.getLogger(this.getClass()).info("AI Core Raid factor registered with Hostile Activity");
                }
            } else {
                Global.getLogger(this.getClass()).info("Hostile Activity not yet active - raids will trigger independently");
            }
            factorAdded = true;
        }

        // Check periodically if we should trigger a raid (independent of Hostile Activity)
        float days = Global.getSector().getClock().convertToDays(amount);
        checkInterval += days;

        if (checkInterval >= CHECK_DAYS) {
            checkInterval = 0f;
            checkForRaidOpportunity();
        }
    }

    /**
     * Check if conditions are right to trigger an AI core raid
     */
    private void checkForRaidOpportunity() {
        Global.getLogger(this.getClass()).info("=== Checking for AI Core Raid Opportunity ===");

        // Check raid cap
        int activeRaids = getActiveRaidCount();
        Global.getLogger(this.getClass()).info("Active raids: " + activeRaids + "/" + MAX_ACTIVE_RAIDS);

        if (activeRaids >= MAX_ACTIVE_RAIDS) {
            Global.getLogger(this.getClass()).info("Raid cap reached - cannot start new raid");
            return;
        }

        // Check cooldown
        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        long cooldownEnd = getCooldownEndTimestamp();

        if (currentTimestamp < cooldownEnd) {
            float daysRemaining = Global.getSector().getClock().getElapsedDaysSince(cooldownEnd);
            Global.getLogger(this.getClass()).info("Raid on cooldown - " + Math.abs(daysRemaining) + " days remaining");
            return;
        }

        // Check if there's a high-value target
        MarketAPI target = getHighValueTarget();
        if (target == null) {
            Global.getLogger(this.getClass()).info("No high-value AI core target available - skipping raid check");
            return;
        }

        Global.getLogger(this.getClass()).info("High-value target found: " + target.getName());

        // Get Draconis source market
        MarketAPI source = DraconisAICoreRaidFactor.getDraconisSource();
        if (source == null) {
            Global.getLogger(this.getClass()).info("No Draconis source market available for raid - skipping");
            return;
        }

        Global.getLogger(this.getClass()).info("Source market: " + source.getName());

        // Random chance to trigger raid (30% per check)
        Random random = new Random();
        float roll = random.nextFloat();
        Global.getLogger(this.getClass()).info("Random roll: " + roll + " (need <= 0.3)");

        if (roll > 0.99f) {
            Global.getLogger(this.getClass()).info("AI Core raid random check failed - no raid this cycle");
            return;
        }

        // Try to get the Hostile Activity factor if it exists
        HostileActivityEventIntel intel = HostileActivityEventIntel.get();
        DraconisAICoreRaidFactor factor = null;

        if (intel != null) {
            factor = (DraconisAICoreRaidFactor) intel.getFactorOfClass(DraconisAICoreRaidFactor.class);
        }

        // If we have a factor (Hostile Activity integration), use it
        if (factor != null) {
            Global.getLogger(this.getClass()).info("Triggering AI Core raid via Hostile Activity on " + target.getName());
            factor.startRaid(source, target, random);
        } else {
            // Otherwise, create a standalone raid
            Global.getLogger(this.getClass()).info("Triggering standalone AI Core raid on " + target.getName());
            DraconisAICoreRaidFactor.createStandaloneRaid(source, target, random);
        }

        // Increment active raid count
        incrementActiveRaidCount();
    }

    /**
     * Find the current high-value AI core target
     */
    private MarketAPI getHighValueTarget() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG)) {
                return market;
            }
        }
        return null;
    }

    /**
     * Get the current number of active raids
     */
    private static int getActiveRaidCount() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(ACTIVE_RAID_COUNT_KEY);
    }

    /**
     * Increment the active raid counter
     */
    private static void incrementActiveRaidCount() {
        int current = getActiveRaidCount();
        Global.getSector().getMemoryWithoutUpdate().set(ACTIVE_RAID_COUNT_KEY, current + 1);
        Global.getLogger(DraconisAICoreRaidManager.class).info("Active raid count increased to " + (current + 1));
    }

    /**
     * Decrement the active raid counter
     */
    public static void decrementActiveRaidCount() {
        int current = getActiveRaidCount();
        if (current > 0) {
            Global.getSector().getMemoryWithoutUpdate().set(ACTIVE_RAID_COUNT_KEY, current - 1);
            Global.getLogger(DraconisAICoreRaidManager.class).info("Active raid count decreased to " + (current - 1));
        }
    }

    /**
     * Get the timestamp when the current cooldown ends
     */
    private static long getCooldownEndTimestamp() {
        return Global.getSector().getMemoryWithoutUpdate().getLong(COOLDOWN_END_TIMESTAMP_KEY);
    }

    /**
     * Start a cooldown period after a raid completes
     * @param success Whether the raid was successful
     */
    public static void startCooldown(boolean success) {
        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        float cooldownDays = success ? COOLDOWN_SUCCESS_DAYS : COOLDOWN_FAILURE_DAYS;
        long cooldownEnd = currentTimestamp + (long)(cooldownDays * 1000f * 60f * 60f * 24f);

        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_END_TIMESTAMP_KEY, cooldownEnd);
        Global.getSector().getMemoryWithoutUpdate().set(LAST_RAID_TIMESTAMP_KEY, currentTimestamp);

        Global.getLogger(DraconisAICoreRaidManager.class).info(
                "Raid cooldown started: " + cooldownDays + " days (" + (success ? "success" : "failure") + ")"
        );
    }

    /**
     * Clear the cooldown (for debugging or special events)
     */
    public static void clearCooldown() {
        Global.getSector().getMemoryWithoutUpdate().unset(COOLDOWN_END_TIMESTAMP_KEY);
        Global.getLogger(DraconisAICoreRaidManager.class).info("Raid cooldown cleared");
    }
}