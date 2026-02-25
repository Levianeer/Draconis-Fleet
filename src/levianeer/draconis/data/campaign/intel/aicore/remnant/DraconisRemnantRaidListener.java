package levianeer.draconis.data.campaign.intel.aicore.remnant;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICorePriorityManager;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICoreStockpile;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Monitors Draconis Remnant raid fleets
 * Periodically generates AI cores when raid fleets are in Remnant systems
 * Simulates successful engagements without needing to track individual battles
 */
public class DraconisRemnantRaidListener implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisRemnantRaidListener.class);

    private static final float CHECK_INTERVAL = 14f; // Check every 14 days for core recovery
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
        log.info("Draconis: ========================================");
        log.info("Draconis: === DRACONIS REMNANT RAID - CORE RECOVERY ===");
        log.info("Draconis: Fleet: " + fleet.getName());
        log.info("Draconis: System: " + system.getName());
        log.info("Draconis: Fleet strength: " + fleet.getFleetPoints() + " FP");

        // Mark recovery time
        long currentDay = Global.getSector().getClock().getDay();
        fleet.getMemoryWithoutUpdate().set("$lastCoreRecoveryDay", currentDay);

        // Generate cores based on fleet strength (represents successful battles over time)
        List<String> recoveredCores = generateCoresFromRaid(fleet);

        log.info("Draconis: Cores recovered: " + recoveredCores.size());

        if (!recoveredCores.isEmpty()) {
            installRecoveredCores(recoveredCores);

            // Mark system as raided
            DraconisRemnantTargetScanner.markSystemAsRaided(system);
            log.info("Draconis: Marked " + system.getName() + " as raided");
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

        log.info("Draconis: ========================================");
    }

    /**
     * Generate AI cores recovered from raid operations
     * Based on fleet strength (stronger fleets = more successful raids)
     */
    private List<String> generateCoresFromRaid(CampaignFleetAPI fleet) {
        List<String> cores = new ArrayList<>();

        int fleetStrength = fleet.getFleetPoints();

        // Alpha cores: rare, only from strong fleets
        if (fleetStrength > 150 && Math.random() < 0.25) {
            cores.add(Commodities.ALPHA_CORE);
        }

        // Beta cores: uncommon
        int betaRolls = Math.max(1, fleetStrength / 100);
        for (int i = 0; i < betaRolls; i++) {
            if (Math.random() < 0.3) {
                cores.add(Commodities.BETA_CORE);
            }
        }

        // Gamma cores: common
        int gammaRolls = Math.max(1, fleetStrength / 60);
        for (int i = 0; i < gammaRolls; i++) {
            if (Math.random() < 0.45) {
                cores.add(Commodities.GAMMA_CORE);
            }
        }

        // Minimum guarantee: at least 1 gamma core
        if (cores.isEmpty()) {
            cores.add(Commodities.GAMMA_CORE);
        }

        return cores;
    }

    /**
     * Route recovered cores through the canonical stockpile.
     * DraconisAICoreStockpile.tryInstallStockpiledCores() handles priority sorting,
     * displaced-core redistribution, and CME safety.
     * Any cores that can't be installed right now remain in the stockpile and will be
     * retried daily by DraconisAICoreDonationListener.advance().
     */
    private void installRecoveredCores(List<String> cores) {
        if (cores.isEmpty()) return;
        for (String coreId : cores) {
            DraconisAICoreStockpile.add(coreId, 1);
        }
        log.info("Draconis: Added " + cores.size() + " recovered core(s) to stockpile — attempting installation");
        DraconisAICoreStockpile.tryInstallStockpiledCores();
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