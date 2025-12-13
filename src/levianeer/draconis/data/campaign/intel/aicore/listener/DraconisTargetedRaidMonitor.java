package levianeer.draconis.data.campaign.intel.aicore.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import levianeer.draconis.data.campaign.intel.aicore.raids.DraconisAICoreRaidIntel;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Monitors Draconis raids and invasions
 * Steals AI cores from any successfully raided/invaded market
 */
public class DraconisTargetedRaidMonitor implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisTargetedRaidMonitor.class);

    private final Set<String> processedEvents = new HashSet<>();
    private float checkInterval = 0f;
    private static final float CHECK_FREQUENCY = 7f; // Check every 7 days

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

        // Don't check every single frame
        if (checkInterval < CHECK_FREQUENCY) return;
        checkInterval = 0f;

        // Create a copy of the intel list to avoid ConcurrentModificationException
        List<IntelInfoPlugin> intelList = new ArrayList<>(Global.getSector().getIntelManager().getIntel());

        for (IntelInfoPlugin intel : intelList) {
            // Check for Draconis AI Core raids
            if (intel instanceof DraconisAICoreRaidIntel) {
                checkAICoreRaid((DraconisAICoreRaidIntel) intel);
            }
            // Check for Nexerelin raids
            else if (intel instanceof NexRaidIntel) {
                checkRaid((NexRaidIntel) intel);
            }
            // Check for invasions
            else if (intel instanceof InvasionIntel) {
                checkInvasion((InvasionIntel) intel);
            }
        }

        // Cleanup old IDs periodically
        if (processedEvents.size() > 200) {
            log.info("Draconis: Clearing processed events cache");
            processedEvents.clear();
        }
    }

    private void checkAICoreRaid(DraconisAICoreRaidIntel raid) {
        MarketAPI target = raid.getTarget();
        if (target == null) return;

        // Generate ID based on market and timestamp
        String eventId = generateStableEventId(target, "aicore_raid");

        // Check if raid is complete FIRST, before checking if we've processed it
        // This fixes the timing issue where we'd mark it processed before it completes
        boolean isComplete = raid.isEnded() || raid.isSucceeded() || raid.isFailed();

        if (!isComplete) {
            // Raid still in progress - don't process yet
            // Remove from processed set if it was added prematurely
            processedEvents.remove(eventId);
            return;
        }

        // Raid is complete - check if we've already processed this completion
        if (!processedEvents.add(eventId)) {
            // Already processed this specific raid completion, skip
            return;
        }

        log.info("Draconis: Checking AI Core raid on " + target.getName() +
                " | Succeeded: " + raid.isSucceeded() +
                " | Failed: " + raid.isFailed() +
                " | Ended: " + raid.isEnded() +
                " | EventID: " + eventId
        );

        // NOTE: DraconisAICoreRaidIntel now handles its own AI core theft
        // This monitor only needs to log completion for tracking purposes

        if (raid.isSucceeded() || raid.isEnded()) {
            log.info("Draconis: ==========================================");
            log.info("Draconis: === DRACONIS AI CORE RAID COMPLETED ===");
            log.info("Draconis: Status: " + (raid.isSucceeded() ? "SUCCEEDED" : "ENDED (treating as success)"));
            log.info("Draconis: Raid target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")");
            log.info("Draconis: AI core theft already handled by raid intel - monitor is just tracking completion");
            log.info("Draconis: ==========================================");

            // Don't call handleSuccessfulAction - raid intel already stole cores
            // Just clear the target flags if needed
            boolean wasHighValueTarget = target.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG
            );

            if (wasHighValueTarget) {
                // Flags should already be cleared by raid intel, but double-check
                log.info("Draconis: High-value target flags should already be cleared by raid intel");
            }
        } else if (raid.isFailed()) {
            log.info("Draconis: AI Core raid on " + target.getName() + " FAILED - no cores stolen");
            // Clear target condition even on failure
            clearTargetConditionAfterRaid(target);
        }

        // Keep event in processed set - raid has been handled
    }

    private void checkRaid(NexRaidIntel raid) {
        // Only process Draconis raids
        if (!isDraconisRaid(raid)) return;

        MarketAPI target = raid.getTarget();
        if (target == null) return;

        // Generate ID based on market and timestamp
        String eventId = generateStableEventId(target, "raid");

        // Check if raid is complete FIRST, before checking if we've processed it
        boolean isComplete = raid.isEnded() || raid.isSucceeded() || raid.isFailed();

        if (!isComplete) {
            // Raid still in progress - don't process yet
            processedEvents.remove(eventId);
            return;
        }

        // Raid is complete - check if we've already processed this completion
        if (!processedEvents.add(eventId)) {
            // Already processed this specific raid completion, skip
            return;
        }

        // DETAILED LOGGING: Log raid state for debugging
        log.info("Draconis: Checking raid on " + target.getName() +
                " | Succeeded: " + raid.isSucceeded() +
                " | Failed: " + raid.isFailed() +
                " | Ended: " + raid.isEnded() +
                " | EventID: " + eventId
        );

        // Process the completed raid
        // IMPORTANT: Check isSucceeded() FIRST, even if isFailed() is also true
        // Nexerelin sometimes sets both flags, but success should take priority
        if (raid.isSucceeded()) {
            log.info("Draconis: ==========================================");
            log.info("Draconis: === DRACONIS RAID SUCCEEDED ===");
            log.info("Draconis: Event ID: " + eventId);
            log.info("Draconis: Raid target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")");
            log.info("Draconis: Raid faction: " + (raid.getFaction() != null ? raid.getFaction().getDisplayName() : "unknown"));

            handleSuccessfulAction(target, "raid");
        } else if (raid.isFailed()) {
            log.info("Draconis: Draconis raid on " + target.getName() + " FAILED - no cores stolen");
            log.info("Draconis:   Reason check: isEnded=" + raid.isEnded() +
                    ", isFailed=" + raid.isFailed() +
                    ", isSucceeded=" + raid.isSucceeded()
            );
        } else if (raid.isEnded()) {
            log.info("Draconis: Draconis raid on " + target.getName() + " ENDED (no explicit success/fail)");
            log.info("Draconis:   Checking if target still has AI cores to determine actual success...");

            // WORKAROUND: If raid ended without explicit success/fail,
            // check if target was marked as high-value and attempt theft anyway
            boolean wasHighValueTarget = target.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG
            );

            if (wasHighValueTarget) {
                log.info("Draconis:   High-value target - attempting theft as raid likely completed objectives");
                handleSuccessfulAction(target, "raid");
            }
        }

        // Keep event in processed set - raid has been handled
    }

    private void checkInvasion(InvasionIntel invasion) {
        if (!isDraconisInvasion(invasion)) return;

        MarketAPI target = invasion.getTarget();
        if (target == null) return;

        String eventId = generateStableEventId(target, "invasion");

        // Check if invasion is complete FIRST, before checking if we've processed it
        boolean isComplete = invasion.isEnded() || invasion.isSucceeded() || invasion.isFailed();

        if (!isComplete) {
            // Invasion still in progress - don't process yet
            processedEvents.remove(eventId);
            return;
        }

        // Invasion is complete - check if we've already processed this completion
        if (!processedEvents.add(eventId)) {
            // Already processed this specific invasion completion, skip
            return;
        }

        // Process the completed invasion
        if (invasion.isSucceeded()) {
            log.info("Draconis: ==========================================");
            log.info("Draconis: === DRACONIS INVASION SUCCEEDED ===");
            log.info("Draconis: Event ID: " + eventId);
            log.info("Draconis: Invasion target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")");
            log.info("Draconis: Invasion faction: " + (invasion.getFaction() != null ? invasion.getFaction().getDisplayName() : "unknown"));

            handleSuccessfulAction(target, "invasion");
        } else if (invasion.isFailed()) {
            log.info("Draconis: Draconis invasion of " + target.getName() + " failed - no cores stolen");
        }

        // Keep event in processed set - invasion has been handled
    }

    private void handleSuccessfulAction(MarketAPI target, String actionType) {
        if (target == null) return;

        boolean isPlayerMarket = target.isPlayerOwned();
        boolean wasHighValueTarget = target.getMemoryWithoutUpdate().getBoolean(
                DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG
        );

        if (wasHighValueTarget) {
            log.info("Draconis: >>> TARGET WAS MARKED AS HIGH-VALUE - PRIORITY THEFT <<<");
        }

        log.info("Draconis: Attempting AI core theft from " + target.getName());

        // Steal AI cores
        DraconisAICoreTheftListener.checkAndStealAICores(
                target, isPlayerMarket, actionType
        );

        // Clear high-value target flags if applicable
        if (wasHighValueTarget) {
            DraconisSingleTargetScanner.clearTargetAfterRaid(target);
            log.info("Draconis: Cleared high-value target flags - scanner will select new target");
        }

        log.info("Draconis: ==========================================");
    }

    /**
     * Generates a stable event ID based on market and current day
     * This ensures the same raid isn't processed multiple times even if there are
     * multiple intel objects for it
     */
    private String generateStableEventId(MarketAPI target, String actionType) {
        long currentDay = Global.getSector().getClock().getDay();
        // Use market ID + day + action type as the unique identifier
        // This means each market can only have one successful raid/invasion processed per day
        return target.getId() + "_" + actionType + "_" + currentDay;
    }

    private boolean isDraconisRaid(NexRaidIntel raid) {
        try {
            if (raid.getFaction() != null) {
                String factionId = raid.getFaction().getId();
                return DRACONIS.equals(factionId);
            }
        } catch (Exception e) {
            log.warn("Draconis: Error checking raid faction", e);
        }
        return false;
    }

    private boolean isDraconisInvasion(InvasionIntel invasion) {
        try {
            if (invasion.getFaction() != null) {
                String factionId = invasion.getFaction().getId();
                return DRACONIS.equals(factionId);
            }
        } catch (Exception e) {
            log.warn("Draconis: Error checking invasion faction", e);
        }
        return false;
    }

    /**
     * Clear high-value target condition after a raid (regardless of success/failure)
     * Gives the colony breathing room before being targeted again
     */
    private void clearTargetConditionAfterRaid(MarketAPI market) {
        boolean wasHighValueTarget = market.getMemoryWithoutUpdate().getBoolean(
                DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG
        );

        if (wasHighValueTarget) {
            DraconisSingleTargetScanner.clearTargetAfterRaid(market);
            log.info("Draconis: Cleared high-value target condition from " + market.getName() + " after raid");
        }
    }
}