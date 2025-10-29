package levianeer.draconis.data.campaign.intel.aicore.remnant;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICorePriorityManager;

import java.util.ArrayList;
import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Monitors Draconis Remnant raid fleets
 * Periodically generates AI cores when raid fleets are in Remnant systems
 * Simulates successful engagements without needing to track individual battles
 */
public class DraconisRemnantRaidListener implements EveryFrameScript {

    private static final float CHECK_INTERVAL = 7f; // Check every 7 days for core recovery
    private float checkTimer = 0f;

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
        checkTimer += days;

        if (checkTimer < CHECK_INTERVAL) return;
        checkTimer = 0f;

        checkRaidFleets();
    }

    /**
     * Check all active Remnant raid fleets and generate cores periodically
     */
    private void checkRaidFleets() {
        for (CampaignFleetAPI fleet : Global.getSector().getCurrentLocation().getFleets()) {
            // Only process Draconis Remnant raid fleets
            if (!fleet.getFaction().getId().equals(DRACONIS)) continue;
            if (!fleet.getMemoryWithoutUpdate().getBoolean("$draconis_remnantRaid")) continue;

            // Check if fleet is in a Remnant system
            StarSystemAPI system = fleet.getStarSystem();
            if (system == null) continue;

            // Check if system has Remnant presence
            if (!hasRemnantPresence(system)) continue;

            // Check if we've already generated cores recently for this fleet
            long currentDay = Global.getSector().getClock().getDay();
            Long lastCoreDay = (Long) fleet.getMemoryWithoutUpdate().get("$lastCoreRecoveryDay");

            if (lastCoreDay != null && (currentDay - lastCoreDay) < 7) {
                continue; // Already recovered cores this week
            }

            // Generate cores based on fleet strength and time in system
            processRaidFleetCoreRecovery(fleet, system);
        }
    }

    /**
     * Check if a star system has Remnant presence
     */
    private boolean hasRemnantPresence(StarSystemAPI system) {
        for (com.fs.starfarer.api.campaign.SectorEntityToken entity : system.getAllEntities()) {
            if (entity.getFaction() != null &&
                Factions.REMNANTS.equals(entity.getFaction().getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Process core recovery for a raid fleet in a Remnant system
     */
    private void processRaidFleetCoreRecovery(CampaignFleetAPI fleet, StarSystemAPI system) {
        Global.getLogger(this.getClass()).info("========================================");
        Global.getLogger(this.getClass()).info("=== DRACONIS REMNANT RAID - CORE RECOVERY ===");
        Global.getLogger(this.getClass()).info("Fleet: " + fleet.getName());
        Global.getLogger(this.getClass()).info("System: " + system.getName());
        Global.getLogger(this.getClass()).info("Fleet strength: " + (int)fleet.getFleetPoints() + " FP");

        // Mark recovery time
        long currentDay = Global.getSector().getClock().getDay();
        fleet.getMemoryWithoutUpdate().set("$lastCoreRecoveryDay", currentDay);

        // Generate cores based on fleet strength (represents successful battles over time)
        List<String> recoveredCores = generateCoresFromRaid(fleet);

        Global.getLogger(this.getClass()).info("Cores recovered: " + recoveredCores.size());
        for (String core : recoveredCores) {
            Global.getLogger(this.getClass()).info("  - " + core);
        }

        if (!recoveredCores.isEmpty()) {
            installRecoveredCores(recoveredCores);

            // Mark system as raided
            DraconisRemnantTargetScanner.markSystemAsRaided(system);
            Global.getLogger(this.getClass()).info("Marked " + system.getName() + " as raided");
        }

        // Notify player if appropriate
        if (shouldNotifyPlayer(fleet)) {
            String message = "Draconis Alliance forces operating in " + system.getName() +
                           " have recovered " + recoveredCores.size() + " AI core" +
                           (recoveredCores.size() > 1 ? "s" : "") + " from Remnant installations.";

            Global.getSector().getCampaignUI().addMessage(
                message,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor()
            );
        }

        Global.getLogger(this.getClass()).info("========================================");
    }

    /**
     * Generate AI cores recovered from raid operations
     * Based on fleet strength (stronger fleets = more successful raids)
     */
    private List<String> generateCoresFromRaid(CampaignFleetAPI fleet) {
        List<String> cores = new ArrayList<>();

        int fleetStrength = (int) fleet.getFleetPoints();

        // Alpha cores: rare, only from strong fleets
        if (fleetStrength > 150 && Math.random() < 0.25) {
            cores.add(Commodities.ALPHA_CORE);
            Global.getLogger(this.getClass()).info("Generated Alpha Core (strong fleet)");
        }

        // Beta cores: uncommon
        int betaRolls = Math.max(1, fleetStrength / 100);
        for (int i = 0; i < betaRolls; i++) {
            if (Math.random() < 0.3) {
                cores.add(Commodities.BETA_CORE);
                Global.getLogger(this.getClass()).info("Generated Beta Core");
            }
        }

        // Gamma cores: common
        int gammaRolls = Math.max(1, fleetStrength / 60);
        for (int i = 0; i < gammaRolls; i++) {
            if (Math.random() < 0.45) {
                cores.add(Commodities.GAMMA_CORE);
                Global.getLogger(this.getClass()).info("Generated Gamma Core");
            }
        }

        // Minimum guarantee: at least 1 gamma core
        if (cores.isEmpty()) {
            cores.add(Commodities.GAMMA_CORE);
            Global.getLogger(this.getClass()).info("Generated minimum Gamma Core");
        }

        return cores;
    }

    /**
     * Install recovered cores on Draconis facilities
     * Supports both empty industries and core upgrades
     */
    private void installRecoveredCores(List<String> cores) {
        // Sort cores by priority (Alpha > Beta > Gamma)
        List<String> sortedCores = new ArrayList<>(cores);
        sortedCores.sort((a, b) -> {
            int priorityA = DraconisAICorePriorityManager.getCorePriority(a);
            int priorityB = DraconisAICorePriorityManager.getCorePriority(b);
            return Integer.compare(priorityB, priorityA); // Descending order
        });

        // Find available Draconis industries (empty slots)
        List<com.fs.starfarer.api.campaign.econ.Industry> availableIndustries = new ArrayList<>();

        // Find upgradeable Draconis industries (lower-tier cores)
        List<com.fs.starfarer.api.campaign.econ.Industry> upgradeableIndustries = new ArrayList<>();

        for (com.fs.starfarer.api.campaign.econ.MarketAPI market :
             Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            for (com.fs.starfarer.api.campaign.econ.Industry industry : market.getIndustries()) {
                if (!industry.isFunctional()) continue;

                String currentCore = industry.getAICoreId();

                if (currentCore == null || currentCore.isEmpty()) {
                    // Empty slot
                    availableIndustries.add(industry);
                } else if (!currentCore.equals(Commodities.ALPHA_CORE)) {
                    // Has a lower-tier core that can be upgraded
                    upgradeableIndustries.add(industry);
                }
            }
        }

        Global.getLogger(this.getClass()).info(
            "Found " + availableIndustries.size() + " empty industries and " +
            upgradeableIndustries.size() + " upgradeable industries"
        );

        if (availableIndustries.isEmpty() && upgradeableIndustries.isEmpty()) {
            Global.getLogger(this.getClass()).warn(
                "No available industries for recovered cores!"
            );
            return;
        }

        // Install cores using priority system
        int installed = 0;
        for (String coreId : sortedCores) {
            if (availableIndustries.isEmpty() && upgradeableIndustries.isEmpty()) break;

            // Pick best industry for this core type (considers both empty and upgradeable)
            com.fs.starfarer.api.campaign.econ.Industry target =
                DraconisAICorePriorityManager.pickTargetIndustryByPriority(
                    availableIndustries, upgradeableIndustries, coreId
                );

            if (target != null) {
                // Check if this is an upgrade
                String displacedCore = null;
                if (target.getAICoreId() != null && !target.getAICoreId().isEmpty()) {
                    displacedCore = target.getAICoreId();
                    Global.getLogger(this.getClass()).info(
                        "Displacing " + DraconisAICorePriorityManager.getCoreDisplayName(displacedCore) +
                        " from " + target.getCurrentName() + " with better " +
                        DraconisAICorePriorityManager.getCoreDisplayName(coreId)
                    );
                }

                target.setAICoreId(coreId);

                // Remove from appropriate list
                availableIndustries.remove(target);
                upgradeableIndustries.remove(target);

                installed++;

                Global.getLogger(this.getClass()).info(
                    "Installed " + coreId + " on " + target.getCurrentName() +
                    " at " + target.getMarket().getName()
                );

                // If we displaced a core, add it back to the queue for redistribution
                if (displacedCore != null) {
                    sortedCores.add(displacedCore);
                    Global.getLogger(this.getClass()).info(
                        "Re-queuing displaced " + DraconisAICorePriorityManager.getCoreDisplayName(displacedCore) +
                        " for installation elsewhere"
                    );
                }
            }
        }

        Global.getLogger(this.getClass()).info(
            "Installed " + installed + " of " + cores.size() + " recovered cores"
        );
    }

    /**
     * Check if player should be notified about the raid
     */
    private boolean shouldNotifyPlayer(CampaignFleetAPI draconisFleet) {
        // Always notify if player is in same system
        if (Global.getSector().getPlayerFleet() != null &&
            Global.getSector().getPlayerFleet().getContainingLocation() ==
            draconisFleet.getContainingLocation()) {
            return true;
        }

        // Random chance otherwise
        return Math.random() < 0.3;
    }
}