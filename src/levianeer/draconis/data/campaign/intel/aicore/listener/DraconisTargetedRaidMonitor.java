package levianeer.draconis.data.campaign.intel.aicore.listener;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Monitors raids against the single marked high-value target
 * When raid succeeds, steals cores and clears the target marking
 */
public class DraconisTargetedRaidMonitor implements EveryFrameScript {

    private final Set<String> processedRaids = new HashSet<>();

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
        List<IntelInfoPlugin> allIntel = Global.getSector().getIntelManager().getIntel();

        for (IntelInfoPlugin intel : allIntel) {
            if (!(intel instanceof GenericRaidFGI)) continue;

            GenericRaidFGI raid = (GenericRaidFGI) intel;
            String raidId = generateRaidId(raid);

            // Skip if already processed
            if (processedRaids.contains(raidId)) continue;

            // Only process Draconis raids
            if (!isDraconisRaid(raid)) continue;

            // Check if raid succeeded
            if (raid.isSucceeded()) {
                Global.getLogger(this.getClass()).info(
                        "Draconis raid succeeded - checking if targeting high-value AI core market"
                );

                handleSuccessfulRaid(raid);
                processedRaids.add(raidId);
            }

            // Mark as processed if ended/failed
            if (raid.isEnded() || raid.isFailed()) {
                processedRaids.add(raidId);
            }
        }

        // Cleanup old raid IDs periodically
        if (processedRaids.size() > 100) {
            processedRaids.clear();
        }
    }

    private String generateRaidId(GenericRaidFGI raid) {
        return Integer.toString(raid.hashCode());
    }

    private boolean isDraconisRaid(GenericRaidFGI raid) {
        try {
            if (raid.getFactionForUIColors() != null) {
                String factionId = raid.getFactionForUIColors().getId();
                if (DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId)) {
                    return true;
                }
            }

            if (raid.getParams() != null && raid.getParams().factionId != null) {
                String factionId = raid.getParams().factionId;
                if (DRACONIS.equals(factionId) || FORTYSECOND.equals(factionId)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("Error checking raid faction", e);
        }

        return false;
    }

    private void handleSuccessfulRaid(GenericRaidFGI raid) {
        List<MarketAPI> targets = getRaidTargets(raid);

        if (targets == null || targets.isEmpty()) {
            return;
        }

        for (MarketAPI target : targets) {
            if (target == null) continue;

            // Check if this market is marked as high-value target
            boolean isHighValueTarget = target.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG
            );

            if (!isHighValueTarget) {
                // Not the high-value target, skip
                continue;
            }

            Global.getLogger(this.getClass()).info(
                    "=== RAID SUCCEEDED AGAINST HIGH-VALUE TARGET ==="
            );
            Global.getLogger(this.getClass()).info(
                    "Target: " + target.getName() + " (" + target.getFaction().getDisplayName() + ")"
            );

            // Steal AI cores
            boolean isPlayerMarket = target.isPlayerOwned();
            DraconisAICoreTheftListener.checkAndStealAICores(target, isPlayerMarket, "raid");

            // Clear the high-value target marking
            DraconisSingleTargetScanner.clearTargetAfterRaid(target);

            Global.getLogger(this.getClass()).info(
                    "High-value target flags cleared - scanner will select new target on next cycle"
            );
        }
    }

    private List<MarketAPI> getRaidTargets(GenericRaidFGI raid) {
        try {
            if (raid.getParams() != null &&
                    raid.getParams().raidParams != null &&
                    raid.getParams().raidParams.allowedTargets != null) {
                return raid.getParams().raidParams.allowedTargets;
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("Error accessing raid targets", e);
        }

        return null;
    }
}