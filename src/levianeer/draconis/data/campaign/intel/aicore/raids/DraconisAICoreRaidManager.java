package levianeer.draconis.data.campaign.intel.aicore.raids;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import org.apache.log4j.Logger;

import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Manages independent AI Core raids against NPC factions
 * Player markets are handled by the colony crisis system
 * Triggers Shadow Fleet raids on non-player markets with AI cores every 30 days
 */
public class DraconisAICoreRaidManager implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisAICoreRaidManager.class);

    private float checkInterval = 0f;
    private static final float CHECK_DAYS = 30f;  // Check for raid opportunities every 30 days
    private static final float INITIAL_DELAY_DAYS = 90f;  // Minimum 90 days before first raid can trigger

    // Raid cap and cooldown settings
    private static final int MAX_ACTIVE_RAIDS = 1;  // Maximum number of simultaneous raids
    private static final float COOLDOWN_SUCCESS_DAYS = 90f;  // Cooldown after successful raid
    private static final float COOLDOWN_FAILURE_DAYS = 180f;  // Longer cooldown after failed raid

    // Memory keys for persistent data
    private static final String ACTIVE_RAID_COUNT_KEY = "$draconis_activeRaidCount";
    private static final String LAST_RAID_TIMESTAMP_KEY = "$draconis_lastRaidTimestamp";
    private static final String COOLDOWN_END_TIMESTAMP_KEY = "$draconis_cooldownEndTimestamp";
    private static final String SYSTEM_START_TIMESTAMP_KEY = "$draconis_raidSystemStartTimestamp";

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
        // Check periodically if we should trigger a raid (independent system)
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
        log.info("Draconis: === Checking for AI Core Raid Opportunity ===");

        // Check if initial delay has passed
        long systemStartTimestamp = getSystemStartTimestamp();
        if (systemStartTimestamp == 0) {
            // First time - record the start timestamp
            long currentTimestamp = Global.getSector().getClock().getTimestamp();
            Global.getSector().getMemoryWithoutUpdate().set(SYSTEM_START_TIMESTAMP_KEY, currentTimestamp);
            log.info("Draconis: AI Core raid system initialized - 90 day delay started");
            return;
        }

        float daysSinceSystemStart = Global.getSector().getClock().getElapsedDaysSince(systemStartTimestamp);
        if (daysSinceSystemStart < INITIAL_DELAY_DAYS) {
            float daysRemaining = INITIAL_DELAY_DAYS - daysSinceSystemStart;
            log.info("Draconis: AI Core raid system on initial delay - " + String.format("%.1f", daysRemaining) + " days remaining");
            return;
        }

        // Check raid cap
        int activeRaids = getActiveRaidCount();
        log.info("Draconis: Active raids: " + activeRaids + "/" + MAX_ACTIVE_RAIDS);
        if (activeRaids >= MAX_ACTIVE_RAIDS) {
            log.info("Draconis: Raid cap reached - cannot start new raid");
            return;
        }

        // Check cooldown using the last raid timestamp
        long lastRaidTimestamp = getLastRaidTimestamp();
        if (lastRaidTimestamp > 0) {
            float daysSinceLastRaid = Global.getSector().getClock().getElapsedDaysSince(lastRaidTimestamp);
            float cooldownDays = getCooldownDays();
            if (daysSinceLastRaid < cooldownDays) {
                float daysRemaining = cooldownDays - daysSinceLastRaid;
                log.info("Draconis: Raid on cooldown - " + String.format("%.1f", daysRemaining) + " days remaining");
                return;
            }
        }

        // Check if there's a high-value target
        MarketAPI target = getHighValueTarget();
        if (target == null) {
            log.info("Draconis: No high-value AI core target available - skipping raid check");
            return;
        }

        log.info("Draconis: High-value target found: " + target.getName());

        // Skip player-owned markets - those are handled by the colony crisis system
        if (target.isPlayerOwned()) {
            log.info("Draconis: Target is player-owned - handled by colony crisis system, skipping");
            return;
        }

        // Get Draconis source market
        MarketAPI source = DraconisAICoreRaidFactor.getDraconisSource();
        if (source == null) {
            log.info("Draconis: No Draconis source market available for raid - skipping");
            return;
        }

        log.info("Draconis: Source market: " + source.getName());

        // Check if Draconis is hostile enough to the target faction (rep <= 0)
        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI targetFaction = target.getFaction();
        float rep = draconisFaction.getRelationship(targetFaction.getId());

        if (rep > -0.75f) {
            log.info("Draconis: Skipping AI core raid - not hostile to " +
                targetFaction.getDisplayName() + " (rep: " + String.format("%.2f", rep) + ", need <= -0.75)");
            return;
        }

        log.info("Draconis: Reputation check passed: " +
            String.format("%.2f", rep) + " (hostile to " + targetFaction.getDisplayName() + ")");

        // Random chance to trigger raid (30% per check)
        Random random = new Random();
        float roll = random.nextFloat();
        log.info("Draconis: Random roll: " + roll + " (need <= 0.5)");

        if (roll > 0.3f) {
            log.info("Draconis: AI Core raid random check failed - no raid this cycle");
            return;
        }

        // Create standalone raid with listener
        // Listener will handle AI core theft on success and cooldown management
        log.info("Draconis: Triggering AI Core raid on " + target.getName());
        DraconisAICoreRaidFactor.createStandaloneRaid(source, target, random);

        // Increment active raid count
        // Listener will decrement when raid completes (success or failure)
        incrementActiveRaidCount();

        log.info("Draconis: Raid created - listener will manage completion and cooldown");
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
        log.info("Draconis: Active raid count increased to " + (current + 1));
    }

    /**
     * Decrement the active raid counter
     */
    public static void decrementActiveRaidCount() {
        int current = getActiveRaidCount();
        if (current > 0) {
            Global.getSector().getMemoryWithoutUpdate().set(ACTIVE_RAID_COUNT_KEY, current - 1);
            log.info("Draconis: Active raid count decreased to " + (current - 1));
        }
    }

    /**
     * Get the timestamp when the current cooldown ends
     */
    private static long getCooldownEndTimestamp() {
        return Global.getSector().getMemoryWithoutUpdate().getLong(COOLDOWN_END_TIMESTAMP_KEY);
    }

    /**
     * Get the timestamp of the last raid
     */
    private static long getLastRaidTimestamp() {
        return Global.getSector().getMemoryWithoutUpdate().getLong(LAST_RAID_TIMESTAMP_KEY);
    }

    /**
     * Get the timestamp when the raid system started
     */
    private static long getSystemStartTimestamp() {
        return Global.getSector().getMemoryWithoutUpdate().getLong(SYSTEM_START_TIMESTAMP_KEY);
    }

    /**
     * Get the cooldown duration from the stored cooldown end timestamp
     */
    private static float getCooldownDays() {
        // Check if the last raid was a success or failure by looking at the stored cooldown end
        // We can calculate this from the difference between cooldown end and last raid timestamp
        long lastRaid = getLastRaidTimestamp();
        long cooldownEnd = getCooldownEndTimestamp();

        if (lastRaid <= 0 || cooldownEnd <= 0) {
            return COOLDOWN_SUCCESS_DAYS; // Default to success cooldown
        }

        // Calculate the cooldown that was set
        float cooldownSeconds = cooldownEnd - lastRaid;
        float cooldownDays = Global.getSector().getClock().convertToDays(cooldownSeconds);

        // Return the appropriate value based on which constant it's closer to
        if (Math.abs(cooldownDays - COOLDOWN_SUCCESS_DAYS) < Math.abs(cooldownDays - COOLDOWN_FAILURE_DAYS)) {
            return COOLDOWN_SUCCESS_DAYS;
        } else {
            return COOLDOWN_FAILURE_DAYS;
        }
    }

    /**
     * Start a cooldown period after a raid completes
     * @param success Whether the raid was successful
     */
    public static void startCooldown(boolean success) {
        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        float cooldownDays = success ? COOLDOWN_SUCCESS_DAYS : COOLDOWN_FAILURE_DAYS;

        // Convert days to seconds, then to timestamp units
        // The timestamp uses internal time units that convertToSeconds() handles
        float cooldownSeconds = Global.getSector().getClock().convertToSeconds(cooldownDays);
        long cooldownEnd = currentTimestamp + (long)cooldownSeconds;

        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_END_TIMESTAMP_KEY, cooldownEnd);
        Global.getSector().getMemoryWithoutUpdate().set(LAST_RAID_TIMESTAMP_KEY, currentTimestamp);

        log.info("Draconis: Raid cooldown started: " + cooldownDays + " days (" + (success ? "success" : "failure") + ")");
    }

    /**
     * Clear the cooldown (for debugging or special events)
     */
    public static void clearCooldown() {
        Global.getSector().getMemoryWithoutUpdate().unset(COOLDOWN_END_TIMESTAMP_KEY);
        log.info("Draconis: Raid cooldown cleared");
    }
}
