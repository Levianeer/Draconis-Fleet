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

        // Try to add to set first
        if (!processedEvents.add(eventId)) {
            // Already processed, skip silently
            return;
        }

        Global.getLogger(this.getClass()).info(
                "Checking AI Core raid on " + target.getName() +
                " | Succeeded: " + raid.isSucceeded() +
                " | Failed: " + raid.isFailed() +
                " | Ended: " + raid.isEnded() +
                " | EventID: " + eventId
        );

        // Check if raid is complete
        if (raid.isEnded() || raid.isSucceeded() || raid.isFailed()) {
            if (raid.isSucceeded()) {
                Global.getLogger(this.getClass()).info(
                        "=========================================="
                );
                Global.getLogger(this.getClass()).info(
                        "=== DRACONIS AI CORE RAID SUCCEEDED ==="
                );
                Global.getLogger(this.getClass()).info(
                        "Raid target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")"
                );

                handleSuccessfulAction(target, "ai_core_raid");
            } else if (raid.isFailed()) {
                Global.getLogger(this.getClass()).info(
                        "Draconis AI Core raid on " + target.getName() + " FAILED - no cores stolen"
                );
                // Clear target condition even on failure
                clearTargetConditionAfterRaid(target);
            } else if (raid.isEnded()) {
                Global.getLogger(this.getClass()).info(
                        "Draconis AI Core raid on " + target.getName() + " ENDED - attempting theft anyway"
                );
                handleSuccessfulAction(target, "ai_core_raid");
            }
        } else {
            // Raid still in progress - remove from set so we can check again
            processedEvents.remove(eventId);
        }
    }

    private void checkRaid(NexRaidIntel raid) {
        // Only process Draconis raids
        if (!isDraconisRaid(raid)) return;

        MarketAPI target = raid.getTarget();
        if (target == null) return;

        // Generate ID based on market and timestamp
        String eventId = generateStableEventId(target, "raid");

        // MORE DEFENSIVE: Try to add to set first, if already there, skip entirely
        // This is atomic with HashSet - if it returns false, means it was already there
        if (!processedEvents.add(eventId)) {
            // Already processed, skip silently
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

        // Check if raid is complete (succeeded or failed)
        if (raid.isEnded() || raid.isSucceeded() || raid.isFailed()) {
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
            // Keep event in processed set - raid is finished either way
        } else {
            // Raid still in progress - remove from set so we can check again
            processedEvents.remove(eventId);
        }
    }

    private void checkInvasion(InvasionIntel invasion) {
        if (!isDraconisInvasion(invasion)) return;

        MarketAPI target = invasion.getTarget();
        if (target == null) return;

        String eventId = generateStableEventId(target, "invasion");

        // MORE DEFENSIVE: Try to add first
        if (!processedEvents.add(eventId)) {
            // Already processed
            return;
        }

        // Check if invasion is complete (succeeded or failed)
        if (invasion.isEnded() || invasion.isSucceeded() || invasion.isFailed()) {
            // Only process if succeeded
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
            // Keep event in processed set - invasion is finished either way
        } else {
            // Invasion still in progress - remove from set so we can check again
            processedEvents.remove(eventId);
        }
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