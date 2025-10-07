package levianeer.draconis.data.campaign.intel.aicore.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Monitors all raid fleets and triggers AI core theft when Draconis raids succeed
 */
public class DraconisRaidMonitor implements EveryFrameScript {

    private static class RaidState {
        boolean wasSucceeded = false;
        boolean wasFailed = false;
        boolean wasProcessed = false;
        long timestamp;

        RaidState(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    private final Map<String, RaidState> trackedRaids = new HashMap<>();
    private float cleanupTimer = 0f;
    private static final float CLEANUP_INTERVAL = 30f; // days

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
        cleanupTimer += days;

        // Get all active intel
        List<IntelInfoPlugin> allIntel = Global.getSector().getIntelManager().getIntel();

        for (IntelInfoPlugin intel : allIntel) {
            if (intel == null) continue;

            // Only process GenericRaidFGI intel
            if (!(intel instanceof GenericRaidFGI raid)) continue;

            // Generate unique ID for this raid
            String raidId = generateRaidId(raid);

            // Get or create state tracker for this raid
            RaidState state = trackedRaids.computeIfAbsent(
                    raidId,
                    k -> new RaidState(System.currentTimeMillis())
            );

            // Skip if already processed
            if (state.wasProcessed) continue;

            // Check current raid status
            boolean currentSucceeded = raid.isSucceeded();
            boolean currentFailed = raid.isFailed();

            // Detect success transition
            if (currentSucceeded && !state.wasSucceeded) {
                state.wasSucceeded = true;

                Global.getLogger(this.getClass()).info(
                        "Raid succeeded: " + raidId + " - checking if Draconis"
                );

                // Check if this is a Draconis raid
                if (isDraconisRaid(raid)) {
                    handleSuccessfulDraconisRaid(raid);
                    state.wasProcessed = true;
                }
            }

            // Detect failure transition (for logging)
            if (currentFailed && !state.wasFailed) {
                state.wasFailed = true;
                Global.getLogger(this.getClass()).info(
                        "Raid failed: " + raidId
                );
                state.wasProcessed = true;
            }

            // Mark as processed if raid has ended
            if (intel.isEnded() || intel.isEnding()) {
                state.wasProcessed = true;
            }
        }

        // Periodic cleanup of old raid states
        if (cleanupTimer >= CLEANUP_INTERVAL) {
            cleanupTimer = 0f;
            cleanupOldRaids(allIntel);
        }
    }

    /**
     * Generates a unique ID for a raid based on hashCode and faction
     */
    private String generateRaidId(GenericRaidFGI raid) {
        String factionId = "unknown";
        try {
            if (raid.getFactionForUIColors() != null) {
                factionId = raid.getFactionForUIColors().getId();
            }
        } catch (Exception e) {
            // Fallback if getFactionForUIColors() fails
        }

        // Use object hashCode as unique identifier
        return raid.hashCode() + "_" + factionId;
    }

    /**
     * Checks if this is a Draconis or 42nd Fleet raid
     */
    private boolean isDraconisRaid(GenericRaidFGI raid) {
        try {
            // Check via faction for UI colors (most reliable)
            if (raid.getFactionForUIColors() != null) {
                String factionId = raid.getFactionForUIColors().getId();
                if (DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId)) {
                    return true;
                }
            }

            // Try accessing params (may be null for some raid types)
            if (raid.getParams() != null && raid.getParams().factionId != null) {
                String factionId = raid.getParams().factionId;
                if (DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn(
                    "Error checking raid faction: " + e.getMessage()
            );
        }

        return false;
    }

    /**
     * Handles a successful Draconis raid by stealing AI cores from targets
     */
    private void handleSuccessfulDraconisRaid(GenericRaidFGI raid) {
        Global.getLogger(this.getClass()).info(
                "Draconis raid succeeded - initiating AI core theft"
        );

        // Try to get raid targets
        List<MarketAPI> targets = getRaidTargets(raid);

        if (targets == null || targets.isEmpty()) {
            Global.getLogger(this.getClass()).warn(
                    "Could not access raid targets - no AI cores will be stolen"
            );
            return;
        }

        // Process each target
        for (MarketAPI target : targets) {
            if (target == null) continue;

            boolean isPlayerTarget = target.isPlayerOwned();
            String actionType = determineActionType(target);

            Global.getLogger(this.getClass()).info(
                    "Stealing AI cores from " + target.getName() +
                            " (" + target.getFaction().getDisplayName() + ")"
            );

            DraconisAICoreTheftListener.checkAndStealAICores(
                    target, isPlayerTarget, actionType
            );
        }
    }

    /**
     * Gets the list of targets for a raid
     */
    private List<MarketAPI> getRaidTargets(GenericRaidFGI raid) {
        try {
            if (raid.getParams() != null &&
                    raid.getParams().raidParams != null &&
                    raid.getParams().raidParams.allowedTargets != null) {
                return raid.getParams().raidParams.allowedTargets;
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn(
                    "Error accessing raid targets: " + e.getMessage()
            );
        }

        return null;
    }

    /**
     * Determines if this was a raid or bombardment based on market conditions
     */
    private String determineActionType(MarketAPI market) {
        if (market.hasCondition("ground_defenses_destroyed") ||
                market.hasCondition("bombarded")) {
            return "bombardment";
        }

        float stability = market.getStability().getModifiedValue();
        if (stability < 3f) {
            return "bombardment";
        }

        return "raid";
    }

    /**
     * Cleans up tracking data for raids that are no longer active
     */
    private void cleanupOldRaids(List<IntelInfoPlugin> allIntel) {
        // Build set of active raid IDs
        Map<String, Boolean> activeRaidIds = new HashMap<>();
        for (IntelInfoPlugin intel : allIntel) {
            if (intel instanceof GenericRaidFGI raid) {
                activeRaidIds.put(generateRaidId(raid), true);
            }
        }

        // Remove raid states that are no longer active
        int sizeBefore = trackedRaids.size();
        trackedRaids.entrySet().removeIf(entry ->
                !activeRaidIds.containsKey(entry.getKey()) && entry.getValue().wasProcessed
        );

        int removed = sizeBefore - trackedRaids.size();
        if (removed > 0) {
            Global.getLogger(this.getClass()).info(
                    "Cleaned up " + removed + " old raid states. " +
                            "Remaining tracked raids: " + trackedRaids.size()
            );
        }
    }
}