package levianeer.draconis.data.campaign.intel.aicore.remnant;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

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
     * Reuses the existing theft system infrastructure
     */
    private void installRecoveredCores(List<String> cores) {
        // Find available Draconis industries
        List<com.fs.starfarer.api.campaign.econ.Industry> availableIndustries =
            new ArrayList<>();

        for (com.fs.starfarer.api.campaign.econ.MarketAPI market :
             Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            for (com.fs.starfarer.api.campaign.econ.Industry industry : market.getIndustries()) {
                if (industry.getAICoreId() != null && !industry.getAICoreId().isEmpty()) continue;
                if (!industry.isFunctional()) continue;

                availableIndustries.add(industry);
            }
        }

        if (availableIndustries.isEmpty()) {
            Global.getLogger(this.getClass()).warn(
                "No available industries for recovered cores!"
            );
            return;
        }

        // Install cores using priority system from theft listener
        int installed = 0;
        for (String coreId : cores) {
            if (availableIndustries.isEmpty()) break;

            // Pick best industry for this core type
            com.fs.starfarer.api.campaign.econ.Industry target =
                pickBestIndustry(availableIndustries, coreId);

            if (target != null) {
                target.setAICoreId(coreId);
                availableIndustries.remove(target);
                installed++;

                Global.getLogger(this.getClass()).info(
                    "Installed " + coreId + " on " + target.getCurrentName() +
                    " at " + target.getMarket().getName()
                );
            }
        }

        Global.getLogger(this.getClass()).info(
            "Installed " + installed + " of " + cores.size() + " recovered cores"
        );
    }

    /**
     * Pick best industry for a core (simplified version of theft system)
     */
    private com.fs.starfarer.api.campaign.econ.Industry pickBestIndustry(
        List<com.fs.starfarer.api.campaign.econ.Industry> industries, String coreId) {

        if (industries.isEmpty()) return null;

        // Just pick the first suitable one for now
        // TODO: Could use WeightedRandomPicker like in theft system
        return industries.get(0);
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