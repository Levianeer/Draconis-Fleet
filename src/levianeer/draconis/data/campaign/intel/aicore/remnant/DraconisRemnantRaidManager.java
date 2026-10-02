package levianeer.draconis.data.campaign.intel.aicore.remnant;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import levianeer.draconis.data.campaign.intel.aicore.util.DraconisAICoreStockpile;
import org.apache.log4j.Logger;

import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Manages Draconis raids on Remnant stations for AI core acquisition
 * Sends task forces to engage Remnant defenses and recover technology
 */
public class DraconisRemnantRaidManager implements EveryFrameScript {
    private static final Logger log = Global.getLogger(DraconisRemnantRaidManager.class);

    private static final float CHECK_INTERVAL = 45f;
    private static final float RAID_CHANCE = 0.5f;

    private float daysSinceLastCheck = 0f;

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
        daysSinceLastCheck += days;

        if (daysSinceLastCheck < CHECK_INTERVAL) return;
        daysSinceLastCheck = 0f;

        considerRemnantRaid();
    }

    private void considerRemnantRaid() {
        if (hasActiveRaidFleet()) {
            log.info(
                "Draconis: Active raid fleet already exists - skipping raid check"
            );
            return;
        }

        StarSystemAPI target = DraconisRemnantTargetScanner.getCurrentTarget();

        if (target == null) {
            log.info(
                "Draconis: No Remnant target available - skipping raid check"
            );
            return;
        }

        log.info(
            "Draconis: Remnant target found: " + target.getName()
        );

        Random random = new Random();
        if (random.nextFloat() > RAID_CHANCE) {
            log.info(
                "Draconis: Raid check failed (chance: " + (RAID_CHANCE * 100) + "%)"
            );
            return;
        }

        com.fs.starfarer.api.campaign.econ.MarketAPI source = getDraconisSource();
        if (source == null) {
            log.info(
                "Draconis: No Draconis source market available for raid"
            );
            return;
        }

        log.info("Draconis: Source market: " + source.getName());

        spawnRemnantRaidFleet(source, target);
    }

    /**
     * Checks all star systems and hyperspace, not just the current location.
     */
    private boolean hasActiveRaidFleet() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CampaignFleetAPI fleet : system.getFleets()) {
                if (fleet.getMemoryWithoutUpdate().getBoolean("$draconis_remnantRaid")) {
                    return true;
                }
            }
        }

        com.fs.starfarer.api.campaign.LocationAPI hyperspace = Global.getSector().getHyperspace();
        for (CampaignFleetAPI fleet : hyperspace.getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean("$draconis_remnantRaid")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Prefer larger markets with military capability.
     */
    private com.fs.starfarer.api.campaign.econ.MarketAPI getDraconisSource() {
        com.fs.starfarer.api.campaign.econ.MarketAPI bestSource = null;
        int bestScore = 0;

        for (com.fs.starfarer.api.campaign.econ.MarketAPI market :
             Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;

            int score = market.getSize();

            if (market.hasIndustry("militarybase") ||
                market.hasIndustry("highcommand") ||
                market.hasIndustry("XLII_highcommand")) {
                score += 3;
            }

            if (score > bestScore) {
                bestScore = score;
                bestSource = market;
            }
        }

        return bestSource;
    }

    private void spawnRemnantRaidFleet(com.fs.starfarer.api.campaign.econ.MarketAPI source,
                                       StarSystemAPI target) {
        log.info("Draconis: ========================================");
        log.info("Draconis: === SPAWNING REMNANT RAID FLEET ===");
        log.info("Draconis: Source: " + source.getName());
        log.info("Draconis: Target: " + target.getName());

        float priority = target.getMemoryWithoutUpdate().getFloat(
            DraconisRemnantTargetScanner.TARGET_PRIORITY_FLAG
        );

        float fleetPoints = Math.max(200f, Math.min(500f, 160f + priority * 2));

        // Freighter/tanker FP: small support flotilla, just enough to carry cores and supplies back.
        float freighterFP = 30f;
        float tankerFP = 20f;

        log.info("Draconis: Fleet composition: " + (int)fleetPoints + " FP combat, " +
                 (int)freighterFP + " FP freighter, " + (int)tankerFP + " FP tanker");

        FleetParamsV3 params = new FleetParamsV3(
            source,                          // Source market
            null,                            // Location (will be set after creation)
            DRACONIS,                        // Faction
            null,                            // Route (none, this is a raid)
            FleetTypes.TASK_FORCE,           // Fleet type
            fleetPoints,                     // Combat FP
            freighterFP,                     // Freighter FP (for cargo capacity)
            tankerFP,                        // Tanker FP (for fuel reserves)
            0f,                              // Transport FP
            0f,                              // Liner FP
            0f,                              // Utility FP
            0f                               // Quality bonus
        );

        params.qualityMod = source.getShipQualityFactor();

        // Bonus officers and quality for this dangerous mission.
        params.officerNumberMult = 1.5f;
        params.officerLevelBonus = 5;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null) {
            log.error(
                "Draconis: Failed to create Remnant raid fleet!"
            );
            return;
        }

        // Configure fleet
        fleet.setName("Expeditionary Strike Group");
        fleet.setNoFactionInName(true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set("$draconis_remnantRaid", true);
        fleet.getMemoryWithoutUpdate().set("$draconis_raidTarget", target);

        // Add behavior script to manage combat engagement based on assignment
        Global.getSector().addScript(new RemnantRaidFleetBehavior(fleet, source, target));

        fleet.addAbility(Abilities.SUSTAINED_BURN);
        fleet.addAbility(Abilities.EMERGENCY_BURN);
        fleet.addAbility(Abilities.SENSOR_BURST);

        SectorEntityToken sourceEntity = source.getPrimaryEntity();
        fleet.setLocation(sourceEntity.getLocation().x, sourceEntity.getLocation().y);
        sourceEntity.getContainingLocation().addEntity(fleet);

        fleet.clearAssignments();

        // Use long duration - fleets should keep trying to reach target
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION,
            target.getCenter(),
            1000f, // Take as long as needed
            "traveling to " + target.getName()
        );

        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.PATROL_SYSTEM,
            target.getCenter(),
            14f, // Patrol for 14 days
            "hunting Remnant forces in " + target.getName()
        );

        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION,
            sourceEntity,
            1000f, // Take as long as needed
            "returning to " + source.getName()
        );

        // Cores are delivered during this final phase, not the previous one.
        fleet.addAssignment(
            com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
            sourceEntity,
            1000f,
            "standing down"
        );

        log.info("Draconis: Fleet spawned successfully");
        log.info("Draconis: Fleet strength: " + fleet.getFleetPoints() + " FP");
        log.info("Draconis: Officers: " + fleet.getCommander().getStats().getOfficerNumber().getModifiedInt());
        log.info("Draconis: ========================================");

        if (shouldPlayerKnowAboutRaid(source, target)) {
            Global.getSector().getCampaignUI().addMessage(
                "Draconis Alliance forces have launched an expedition to engage Remnant installations.");
        }
    }

    private boolean shouldPlayerKnowAboutRaid(com.fs.starfarer.api.campaign.econ.MarketAPI source,
                                               StarSystemAPI target) {
        SectorEntityToken playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return false;

        StarSystemAPI playerSystem = playerFleet.getStarSystem();
        if (playerSystem == null) return false;

        return playerSystem == source.getStarSystem() || playerSystem == target;
    }

    /**
     * Manages fleet behavior during different phases of the raid
     * Makes fleet passive during transit, aggressive during patrol
     * Handles AI core acquisition and delivery
     * <p>
     * <b>Bug found while auditing other raid/fleet-action code for the same class of issue the
     * Office Takeover crisis's invasion fleet had (see {@code XLII_LongsightBastionIntel}'s
     * travel-reissue fix): {@code isDone()} used to treat a null {@code getCurrentAssignment()} as
     * "this fleet is done, stop managing it."</b> Under normal operation that's only ever true right
     * after the final {@code GO_TO_LOCATION_AND_DESPAWN} assignment completes and the fleet is
     * removed - but the whole raid arc is a single one-shot queue of four assignments issued once at
     * spawn (see {@code spawnRemnantRaidFleet()}), with nothing re-establishing a cleared queue. If
     * combat (or anything else) wiped the fleet's assignment queue mid-route - the fleet is still
     * alive, just temporarily orderless - this script would immediately give up on it: stop toggling
     * {@code FLEET_IGNORES_OTHER_FLEETS}/{@code MEMORY_KEY_ALLOW_LONG_PURSUIT}, never resume travel,
     * never patrol, never return to deliver cores. Exactly the same "gets distracted and never
     * resumes" failure mode, just manifesting as an orphaned fleet with no navigator instead of one
     * peacefully parked in the wrong stance. Fixed by tracking an explicit {@link Phase} (rather than
     * the raw last-seen {@code FleetAssignment}, which is ambiguous - {@code GO_TO_LOCATION} is used
     * for both the outbound and return legs) and re-issuing whatever assignment that phase implies
     * whenever the current assignment reads null but the fleet is still alive, matching the same
     * one-check-per-frame idiom vanilla's own {@code BaseAssignmentAI.advance()} uses
     * ({@code if (fleet.getCurrentAssignment() == null) pickNext();}).
     */
    private static class RemnantRaidFleetBehavior implements EveryFrameScript {
        private static final Logger log = Global.getLogger(RemnantRaidFleetBehavior.class);

        private enum Phase { TRAVELING_TO_TARGET, PATROLLING, RETURNING, DESPAWNING }

        private final CampaignFleetAPI fleet;
        private final com.fs.starfarer.api.campaign.econ.MarketAPI source;
        private final StarSystemAPI target;
        private FleetAssignment lastAssignment = null;
        private Phase phase = Phase.TRAVELING_TO_TARGET;
        private boolean coresAcquired = false;
        private float patrolTimeElapsed = 0f;

        public RemnantRaidFleetBehavior(CampaignFleetAPI fleet,
                                         com.fs.starfarer.api.campaign.econ.MarketAPI source,
                                         StarSystemAPI target) {
            this.fleet = fleet;
            this.source = source;
            this.target = target;
        }

        @Override
        public boolean isDone() {
            // Clean up only once the fleet is actually gone - a temporarily orderless-but-alive
            // fleet (assignment queue cleared by combat, etc.) must still be managed; see this
            // class's own doc for why checking getCurrentAssignment() == null here was a real bug.
            return fleet == null || !fleet.isAlive();
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            if (fleet == null || !fleet.isAlive()) return;

            com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
            if (assignment == null) {
                reissueForCurrentPhase();
                return;
            }

            FleetAssignment assignmentType = assignment.getAssignment();

            if (lastAssignment != assignmentType) {
                handleAssignmentChange(lastAssignment, assignmentType);
                lastAssignment = assignmentType;
            }

            if (assignmentType == FleetAssignment.GO_TO_LOCATION) {
                if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                }
            }
            else if (assignmentType == FleetAssignment.PATROL_SYSTEM) {
                float days = Global.getSector().getClock().convertToDays(amount);
                patrolTimeElapsed += days;

                if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
                    fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
                }

                // Cores are acquired on transition to the return phase below, not during patrol itself.
            }
            else if (assignmentType == FleetAssignment.GO_TO_LOCATION_AND_DESPAWN) {
                if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                    fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT);
                }

                // coresAcquired guards against delivering more than once.
                if (coresAcquired && hasAICoresInCargo()) {
                    deliverAICores();
                }
            }
            else {
                if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                    fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT);
                }
            }
        }

        private void handleAssignmentChange(FleetAssignment from, FleetAssignment to) {
            if (to == FleetAssignment.PATROL_SYSTEM) {
                phase = Phase.PATROLLING;
                log.info(
                    "Draconis: Raid fleet entering patrol phase in target system"
                );
            } else if (from == FleetAssignment.PATROL_SYSTEM && to == FleetAssignment.GO_TO_LOCATION) {
                phase = Phase.RETURNING;
                if (!coresAcquired) {
                    log.info(
                        "Draconis: Raid fleet survived patrol and is returning - acquiring AI cores"
                    );
                    acquireAICores();
                }
                log.info(
                    "Draconis: Raid fleet transitioning to return journey. Cores acquired: " + coresAcquired
                );
            } else if (to == FleetAssignment.GO_TO_LOCATION_AND_DESPAWN) {
                phase = Phase.DESPAWNING;
            }
        }

        /**
         * Re-issues whatever assignment {@link #phase} implies - called when
         * {@code getCurrentAssignment()} reads null on a fleet that's still alive, i.e. something
         * cleared its queue out from under it. See this class's own doc for the bug this closes.
         */
        private void reissueForCurrentPhase() {
            SectorEntityToken sourceEntity = source.getPrimaryEntity();
            if (sourceEntity == null) return;

            switch (phase) {
                case TRAVELING_TO_TARGET:
                    fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target.getCenter(), 1000f,
                            "traveling to " + target.getName());
                    break;
                case PATROLLING:
                    fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, target.getCenter(), 14f,
                            "hunting Remnant forces in " + target.getName());
                    break;
                case RETURNING:
                    fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, sourceEntity, 1000f,
                            "returning to " + source.getName());
                    break;
                case DESPAWNING:
                    fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, sourceEntity, 1000f,
                            "standing down");
                    break;
            }
        }

        private void acquireAICores() {
            CargoAPI cargo = fleet.getCargo();

            // Longer patrol = more cores, representing more battles won.
            int alphaCount = 0;
            int betaCount = 0;
            int gammaCount = 0;

            // Base rewards on patrol time (5 days minimum, rewards scale up to 30+ days)
            if (patrolTimeElapsed >= 5f) {
                gammaCount = 1 + (int)(patrolTimeElapsed / 10f); // 1-4 gamma cores
            }
            if (patrolTimeElapsed >= 15f) {
                betaCount = 1 + (int)((patrolTimeElapsed - 15f) / 15f); // 1-2 beta cores
            }
            if (patrolTimeElapsed >= 30f) {
                alphaCount = 1; // 1 alpha core for long patrols
            }

            if (alphaCount > 0) {
                cargo.addCommodity(Commodities.ALPHA_CORE, alphaCount);
            }
            if (betaCount > 0) {
                cargo.addCommodity(Commodities.BETA_CORE, betaCount);
            }
            if (gammaCount > 0) {
                cargo.addCommodity(Commodities.GAMMA_CORE, gammaCount);
            }

            // Cores are also added as ExtraSalvage (vanilla's special-cargo convention) so they're
            // guaranteed lootable if the player defeats this fleet instead of letting it return.
            CargoAPI extraSalvage = Global.getFactory().createCargo(true);

            if (alphaCount > 0) {
                extraSalvage.addCommodity(Commodities.ALPHA_CORE, alphaCount);
            }
            if (betaCount > 0) {
                extraSalvage.addCommodity(Commodities.BETA_CORE, betaCount);
            }
            if (gammaCount > 0) {
                extraSalvage.addCommodity(Commodities.GAMMA_CORE, gammaCount);
            }

            com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial.addExtraSalvage(
                extraSalvage, fleet.getMemoryWithoutUpdate(), -1
            );

            int totalCores = alphaCount + betaCount + gammaCount;
            log.info(
                "Draconis: Fleet acquired " + totalCores + " AI cores (" + alphaCount + " Alpha, " +
                betaCount + " Beta, " + gammaCount + " Gamma) - added to extra salvage"
            );

            coresAcquired = true;
            fleet.getMemoryWithoutUpdate().set("$draconis_coresAcquired", true);

            if (shouldPlayerSeeFleet()) {
                Global.getSector().getCampaignUI().addMessage(
                    "Draconis expedition fleet has secured AI cores from Remnant forces (" +
                    totalCores + " core" + (totalCores > 1 ? "s" : "") + ")",
                    com.fs.starfarer.api.util.Misc.getHighlightColor()
                );
            }
        }

        /**
         * Check if fleet still has AI cores in cargo
         */
        private boolean hasAICoresInCargo() {
            CargoAPI cargo = fleet.getCargo();
            return cargo.getCommodityQuantity(Commodities.ALPHA_CORE) > 0 ||
                   cargo.getCommodityQuantity(Commodities.BETA_CORE) > 0 ||
                   cargo.getCommodityQuantity(Commodities.GAMMA_CORE) > 0;
        }

        private void deliverAICores() {
            CargoAPI cargo = fleet.getCargo();

            int alphaDelivered = (int) cargo.getCommodityQuantity(Commodities.ALPHA_CORE);
            int betaDelivered = (int) cargo.getCommodityQuantity(Commodities.BETA_CORE);
            int gammaDelivered = (int) cargo.getCommodityQuantity(Commodities.GAMMA_CORE);

            if (alphaDelivered == 0 && betaDelivered == 0 && gammaDelivered == 0) {
                log.warn(
                    "Draconis: Fleet marked as having cores but none found in cargo - possibly stolen by player/pirates"
                );
                return;
            }

            cargo.removeCommodity(Commodities.ALPHA_CORE, alphaDelivered);
            cargo.removeCommodity(Commodities.BETA_CORE, betaDelivered);
            cargo.removeCommodity(Commodities.GAMMA_CORE, gammaDelivered);

            int totalCores = alphaDelivered + betaDelivered + gammaDelivered;

            log.info(
                "Draconis: === AI CORES DELIVERED === Alpha: " + alphaDelivered + ", Beta: " +
                betaDelivered + ", Gamma: " + gammaDelivered + " (Total: " + totalCores + ")"
            );

            if (alphaDelivered > 0) DraconisAICoreStockpile.add(Commodities.ALPHA_CORE, alphaDelivered);
            if (betaDelivered  > 0) DraconisAICoreStockpile.add(Commodities.BETA_CORE,  betaDelivered);
            if (gammaDelivered > 0) DraconisAICoreStockpile.add(Commodities.GAMMA_CORE, gammaDelivered);

            log.info("Draconis: Added " + totalCores + " delivered core(s) to stockpile - attempting installation");
            DraconisAICoreStockpile.tryInstallStockpiledCores();

            if (shouldPlayerSeeFleet()) {
                Global.getSector().getCampaignUI().addMessage(
                    "Draconis expedition fleet has successfully delivered " + totalCores +
                    " AI core" + (totalCores > 1 ? "s" : "") + " to Alliance command",
                    com.fs.starfarer.api.util.Misc.getPositiveHighlightColor()
                );
            }

            fleet.getMemoryWithoutUpdate().set("$draconis_coresDelivered", true);
        }

        private boolean shouldPlayerSeeFleet() {
            SectorEntityToken player = Global.getSector().getPlayerFleet();
            if (player == null || fleet == null) return false;

            if (player.getContainingLocation() != fleet.getContainingLocation()) {
                return false;
            }

            float distance = com.fs.starfarer.api.util.Misc.getDistance(
                player.getLocation(), fleet.getLocation()
            );

            // Within sensor range (roughly)
            return distance < 5000f;
        }
    }
}