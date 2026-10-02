package levianeer.draconis.data.campaign.intel.aicore.raids;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
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
    private static final float CHECK_DAYS = 30f;
    private static final float INITIAL_DELAY_DAYS = 90f;

    // Raid cap and cooldown settings
    private static final int MAX_ACTIVE_RAIDS = 1;
    private static final float COOLDOWN_SUCCESS_DAYS = 90f;
    private static final float COOLDOWN_FAILURE_DAYS = 180f;

    // Memory keys for persistent data
    private static final String ACTIVE_RAID_COUNT_KEY = "$draconis_activeRaidCount";
    private static final String LAST_RAID_TIMESTAMP_KEY = "$draconis_lastRaidTimestamp";
    private static final String COOLDOWN_END_TIMESTAMP_KEY = "$draconis_cooldownEndTimestamp";
    private static final String LAST_RAID_WAS_SUCCESS_KEY = "$draconis_lastRaidWasSuccess";
    private static final String SYSTEM_START_TIMESTAMP_KEY = "$draconis_raidSystemStartTimestamp";

    /**
     * {@code CampaignClockAPI.getTimestamp()} is a raw millisecond counter, 86,400,000 ms per
     * in-game day (matches vanilla's {@code BaseIntelPlugin.createIntelInfo()} msPerDay calc).
     * {@code convertToSeconds(days)} is a different unit - elapsed simulation wall-clock
     * seconds, on the order of single digits per day - not "internal time units" as this class
     * used to assume. Adding the two together made "N days from now" land on "now," so the
     * post-raid cooldown ({@link #COOLDOWN_SUCCESS_DAYS}/{@link #COOLDOWN_FAILURE_DAYS}) was
     * silently a no-op. The Office Takeover crisis has this same bug.
     */
    private static final long MS_PER_DAY = 24L * 60L * 60L * 1000L;

    // Per-faction anti-harassment keys
    private static final String LAST_RAIDED_FACTION_KEY = "$draconis_lastRaidedFactionId";
    private static final String FACTION_RAID_ATTEMPTS_KEY = "$draconis_factionRaidAttempts";
    private static final String FACTION_LAST_RAID_TIMESTAMP_KEY = "$draconis_factionLastRaidTimestamp";

    public static final float FACTION_COOLDOWN_BASE_DAYS = 60f;

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
        float days = Global.getSector().getClock().convertToDays(amount);
        checkInterval += days;

        if (checkInterval >= CHECK_DAYS) {
            checkInterval = 0f;
            checkForRaidOpportunity();
        }
    }

    private void checkForRaidOpportunity() {
        log.info("Draconis: === Checking for AI Core Raid Opportunity ===");

        long systemStartTimestamp = getSystemStartTimestamp();
        if (systemStartTimestamp == 0) {
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

        int activeRaids = getActiveRaidCount();
        log.info("Draconis: Active raids: " + activeRaids + "/" + MAX_ACTIVE_RAIDS);
        if (activeRaids >= MAX_ACTIVE_RAIDS) {
            log.info("Draconis: Raid cap reached - cannot start new raid");
            return;
        }

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

        MarketAPI target = getHighValueTarget();
        if (target == null) {
            log.info("Draconis: No high-value AI core target available - skipping raid check");
            return;
        }

        log.info("Draconis: High-value target found: " + target.getName());

        // Skip player-owned markets - colony crisis system handles those
        if (target.isPlayerOwned()) {
            log.info("Draconis: Target is player-owned - handled by colony crisis system, skipping");
            return;
        }

        MarketAPI source = DraconisAICoreRaidFactor.getDraconisSource();
        if (source == null) {
            log.info("Draconis: No Draconis source market available for raid - skipping");
            return;
        }

        log.info("Draconis: Source market: " + source.getName());

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

        Random random = new Random();
        float roll = random.nextFloat();
        log.info("Draconis: Random roll: " + roll + " (need <= 0.3)");

        if (roll > 0.3f) {
            log.info("Draconis: AI Core raid random check failed - no raid this cycle");
            return;
        }

        // The raid's listener handles AI core theft on success and cooldown management.
        log.info("Draconis: Triggering AI Core raid on " + target.getName());
        DraconisAICoreRaidFactor.createStandaloneRaid(source, target, random);

        // Listener decrements this when the raid completes (success or failure).
        incrementActiveRaidCount();

        log.info("Draconis: Raid created - listener will manage completion and cooldown");
    }

    private MarketAPI getHighValueTarget() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG)) {
                return market;
            }
        }
        return null;
    }

    private static int getActiveRaidCount() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(ACTIVE_RAID_COUNT_KEY);
    }

    private static void incrementActiveRaidCount() {
        int current = getActiveRaidCount();
        Global.getSector().getMemoryWithoutUpdate().set(ACTIVE_RAID_COUNT_KEY, current + 1);
        log.info("Draconis: Active raid count increased to " + (current + 1));
    }

    public static void decrementActiveRaidCount() {
        int current = getActiveRaidCount();
        if (current > 0) {
            Global.getSector().getMemoryWithoutUpdate().set(ACTIVE_RAID_COUNT_KEY, current - 1);
            log.info("Draconis: Active raid count decreased to " + (current - 1));
        }
    }

    private static long getLastRaidTimestamp() {
        return Global.getSector().getMemoryWithoutUpdate().getLong(LAST_RAID_TIMESTAMP_KEY);
    }

    private static long getSystemStartTimestamp() {
        return Global.getSector().getMemoryWithoutUpdate().getLong(SYSTEM_START_TIMESTAMP_KEY);
    }

    /**
     * Get the cooldown duration based on the stored success/failure flag from the last raid.
     */
    private static float getCooldownDays() {
        if (Global.getSector().getMemoryWithoutUpdate().contains(LAST_RAID_WAS_SUCCESS_KEY)) {
            boolean wasSuccess = Global.getSector().getMemoryWithoutUpdate().getBoolean(LAST_RAID_WAS_SUCCESS_KEY);
            return wasSuccess ? COOLDOWN_SUCCESS_DAYS : COOLDOWN_FAILURE_DAYS;
        }
        return COOLDOWN_SUCCESS_DAYS; // Default: treat no prior raid as a success cooldown
    }

    /**
     * Start a cooldown period after a raid completes and record per-faction tracking data.
     * @param success Whether the raid was successful
     * @param targetFactionId The faction that was raided (for anti-harassment tracking)
     */
    public static void startCooldown(boolean success, String targetFactionId) {
        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        float cooldownDays = success ? COOLDOWN_SUCCESS_DAYS : COOLDOWN_FAILURE_DAYS;

        long cooldownEnd = currentTimestamp + (long) (cooldownDays * MS_PER_DAY);

        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_END_TIMESTAMP_KEY, cooldownEnd);
        Global.getSector().getMemoryWithoutUpdate().set(LAST_RAID_TIMESTAMP_KEY, currentTimestamp);
        Global.getSector().getMemoryWithoutUpdate().set(LAST_RAID_WAS_SUCCESS_KEY, success);

        if (targetFactionId != null) {
            recordRaidAttempt(targetFactionId, currentTimestamp);
        }

        log.info("Draconis: Raid cooldown started: " + cooldownDays + " days (" + (success ? "success" : "failure") + ")" +
            (targetFactionId != null ? " against " + targetFactionId : ""));
    }

    /**
     * Record a raid attempt against a faction for anti-harassment tracking.
     * Sets the forced-switch flag, increments the per-faction attempt counter,
     * and records the per-faction timestamp.
     */
    @SuppressWarnings("unchecked")
    private static void recordRaidAttempt(String factionId, long timestamp) {
        // Forced switch: remember the last-raided faction
        Global.getSector().getMemoryWithoutUpdate().set(LAST_RAIDED_FACTION_KEY, factionId);

        // Compounding cooldown: increment attempt count
        Map<String, Integer> attempts = (Map<String, Integer>)
            Global.getSector().getMemoryWithoutUpdate().get(FACTION_RAID_ATTEMPTS_KEY);
        if (attempts == null) {
            attempts = new HashMap<>();
        }
        int count = attempts.getOrDefault(factionId, 0) + 1;
        attempts.put(factionId, count);
        Global.getSector().getMemoryWithoutUpdate().set(FACTION_RAID_ATTEMPTS_KEY, attempts);

        Map<String, Long> timestamps = (Map<String, Long>)
            Global.getSector().getMemoryWithoutUpdate().get(FACTION_LAST_RAID_TIMESTAMP_KEY);
        if (timestamps == null) {
            timestamps = new HashMap<>();
        }
        timestamps.put(factionId, timestamp);
        Global.getSector().getMemoryWithoutUpdate().set(FACTION_LAST_RAID_TIMESTAMP_KEY, timestamps);

        float cooldownDays = FACTION_COOLDOWN_BASE_DAYS * count;
        log.info("Draconis: Recorded raid attempt #" + count + " against " + factionId +
            " - faction cooldown: " + cooldownDays + " days");
    }

    /**
     * Get the faction ID of the last raid target (for forced switching).
     */
    public static String getLastRaidedFactionId() {
        return (String) Global.getSector().getMemoryWithoutUpdate().get(LAST_RAIDED_FACTION_KEY);
    }

    @SuppressWarnings("unchecked")
    public static int getFactionRaidAttempts(String factionId) {
        Map<String, Integer> attempts = (Map<String, Integer>)
            Global.getSector().getMemoryWithoutUpdate().get(FACTION_RAID_ATTEMPTS_KEY);
        if (attempts == null) return 0;
        return attempts.getOrDefault(factionId, 0);
    }

    @SuppressWarnings("unchecked")
    public static long getFactionLastRaidTimestamp(String factionId) {
        Map<String, Long> timestamps = (Map<String, Long>)
            Global.getSector().getMemoryWithoutUpdate().get(FACTION_LAST_RAID_TIMESTAMP_KEY);
        if (timestamps == null) return 0;
        return timestamps.getOrDefault(factionId, 0L);
    }

    /**
     * Check if a faction is on its compounding cooldown.
     */
    public static boolean isFactionOnCooldown(String factionId) {
        int attempts = getFactionRaidAttempts(factionId);
        if (attempts == 0) return false;

        long lastTimestamp = getFactionLastRaidTimestamp(factionId);
        if (lastTimestamp == 0) return false;

        float daysSince = Global.getSector().getClock().getElapsedDaysSince(lastTimestamp);
        float cooldownDays = FACTION_COOLDOWN_BASE_DAYS * attempts;
        return daysSince < cooldownDays;
    }

    /**
     * Clear the cooldown (for debugging or special events)
     */
    public static void clearCooldown() {
        Global.getSector().getMemoryWithoutUpdate().unset(COOLDOWN_END_TIMESTAMP_KEY);
        log.info("Draconis: Raid cooldown cleared");
    }
}
