package levianeer.draconis.data.campaign.intel.aicore.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Monitors Draconis raids and invasions
 * Steals AI cores from any successfully raided/invaded market
 */
public class DraconisTargetedRaidMonitor implements EveryFrameScript {

    private final Set<String> processedEvents = new HashSet<>();
    private float checkInterval = 0f;
    private static final float CHECK_FREQUENCY = 0.1f; // Check every 0.1 days

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
            if (intel instanceof levianeer.draconis.data.campaign.intel.events.aicore.DraconisAICoreRaidIntel) {
                checkAICoreRaid((levianeer.draconis.data.campaign.intel.events.aicore.DraconisAICoreRaidIntel) intel);
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
            Global.getLogger(this.getClass()).info("Clearing processed events cache");
            processedEvents.clear();
        }
    }

    private void checkAICoreRaid(levianeer.draconis.data.campaign.intel.events.aicore.DraconisAICoreRaidIntel raid) {
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

        Global.getLogger(this.getClass()).info(
                "Checking AI Core raid on " + target.getName() +
                " | Succeeded: " + raid.isSucceeded() +
                " | Failed: " + raid.isFailed() +
                " | Ended: " + raid.isEnded() +
                " | EventID: " + eventId
        );

        // NOTE: DraconisAICoreRaidIntel now handles its own AI core theft
        // This monitor only needs to log completion for tracking purposes

        if (raid.isSucceeded() || raid.isEnded()) {
            Global.getLogger(this.getClass()).info(
                    "=========================================="
            );
            Global.getLogger(this.getClass()).info(
                    "=== DRACONIS AI CORE RAID COMPLETED ==="
            );
            Global.getLogger(this.getClass()).info(
                    "Status: " + (raid.isSucceeded() ? "SUCCEEDED" : "ENDED (treating as success)"));
            Global.getLogger(this.getClass()).info(
                    "Raid target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")"
            );
            Global.getLogger(this.getClass()).info(
                    "AI core theft already handled by raid intel - monitor is just tracking completion"
            );
            Global.getLogger(this.getClass()).info(
                    "=========================================="
            );

            // Don't call handleSuccessfulAction - raid intel already stole cores
            // Just clear the target flags if needed
            boolean wasHighValueTarget = target.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG
            );

            if (wasHighValueTarget) {
                // Flags should already be cleared by raid intel, but double-check
                Global.getLogger(this.getClass()).info(
                        "High-value target flags should already be cleared by raid intel"
                );
            }
        } else if (raid.isFailed()) {
            Global.getLogger(this.getClass()).info(
                    "Draconis AI Core raid on " + target.getName() + " FAILED - no cores stolen"
            );
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
        Global.getLogger(this.getClass()).info(
                "Checking raid on " + target.getName() +
                " | Succeeded: " + raid.isSucceeded() +
                " | Failed: " + raid.isFailed() +
                " | Ended: " + raid.isEnded() +
                " | EventID: " + eventId
        );

        // Process the completed raid
        // IMPORTANT: Check isSucceeded() FIRST, even if isFailed() is also true
        // Nexerelin sometimes sets both flags, but success should take priority
        if (raid.isSucceeded()) {
            Global.getLogger(this.getClass()).info(
                    "=========================================="
            );
            Global.getLogger(this.getClass()).info(
                    "=== DRACONIS RAID SUCCEEDED ==="
            );
            Global.getLogger(this.getClass()).info(
                    "Event ID: " + eventId
            );
            Global.getLogger(this.getClass()).info(
                    "Raid target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")"
            );
            Global.getLogger(this.getClass()).info(
                    "Raid faction: " + (raid.getFaction() != null ? raid.getFaction().getDisplayName() : "unknown")
            );

            handleSuccessfulAction(target, "raid");
        } else if (raid.isFailed()) {
            Global.getLogger(this.getClass()).info(
                    "Draconis raid on " + target.getName() + " FAILED - no cores stolen"
            );
            Global.getLogger(this.getClass()).info(
                    "  Reason check: isEnded=" + raid.isEnded() +
                    ", isFailed=" + raid.isFailed() +
                    ", isSucceeded=" + raid.isSucceeded()
            );
        } else if (raid.isEnded()) {
            Global.getLogger(this.getClass()).info(
                    "Draconis raid on " + target.getName() + " ENDED (no explicit success/fail)"
            );
            Global.getLogger(this.getClass()).info(
                    "  Checking if target still has AI cores to determine actual success..."
            );

            // WORKAROUND: If raid ended without explicit success/fail,
            // check if target was marked as high-value and attempt theft anyway
            boolean wasHighValueTarget = target.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG
            );

            if (wasHighValueTarget) {
                Global.getLogger(this.getClass()).info(
                        "  High-value target - attempting theft as raid likely completed objectives"
                );
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
            Global.getLogger(this.getClass()).info(
                    "=========================================="
            );
            Global.getLogger(this.getClass()).info(
                    "=== DRACONIS INVASION SUCCEEDED ==="
            );
            Global.getLogger(this.getClass()).info(
                    "Event ID: " + eventId
            );
            Global.getLogger(this.getClass()).info(
                    "Invasion target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")"
            );
            Global.getLogger(this.getClass()).info(
                    "Invasion faction: " + (invasion.getFaction() != null ? invasion.getFaction().getDisplayName() : "unknown")
            );

            handleSuccessfulAction(target, "invasion");
        } else if (invasion.isFailed()) {
            Global.getLogger(this.getClass()).info(
                    "Draconis invasion of " + target.getName() + " failed - no cores stolen"
            );
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
            Global.getLogger(this.getClass()).info(
                    ">>> TARGET WAS MARKED AS HIGH-VALUE - PRIORITY THEFT <<<"
            );
        }

        Global.getLogger(this.getClass()).info(
                "Attempting AI core theft from " + target.getName()
        );

        // Steal AI cores
        DraconisAICoreTheftListener.checkAndStealAICores(
                target, isPlayerMarket, actionType
        );

        // Clear high-value target flags if applicable
        if (wasHighValueTarget) {
            DraconisSingleTargetScanner.clearTargetAfterRaid(target);
            Global.getLogger(this.getClass()).info(
                    "Cleared high-value target flags - scanner will select new target"
            );
        }

        Global.getLogger(this.getClass()).info(
                "=========================================="
        );
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
                return DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId);
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("Error checking raid faction", e);
        }
        return false;
    }

    private boolean isDraconisInvasion(InvasionIntel invasion) {
        try {
            if (invasion.getFaction() != null) {
                String factionId = invasion.getFaction().getId();
                return DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId);
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("Error checking invasion faction", e);
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
            Global.getLogger(this.getClass()).info(
                    "Cleared high-value target condition from " + market.getName() + " after raid"
            );
        }
    }
}