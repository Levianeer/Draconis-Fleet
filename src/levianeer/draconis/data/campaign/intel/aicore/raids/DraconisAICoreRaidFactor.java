package levianeer.draconis.data.campaign.intel.aicore.raids;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction.FGRaidType;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import org.apache.log4j.Logger;

import java.util.Random;
import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Independent AI Core raid system (separate from colony crisis)
 * Handles creation and configuration of Shadow Fleet raids targeting AI cores
 * AI core theft occurs via finish() override in DraconisAICoreRaidIntel
 */
public class DraconisAICoreRaidFactor {
    private static final Logger log = Global.getLogger(DraconisAICoreRaidFactor.class);

    /**
     * Get a Draconis market to use as the raid source
     */
    public static MarketAPI getDraconisSource() {
        // Try Kori first (main military hub)
        MarketAPI source = Global.getSector().getEconomy().getMarket("kori_market");

        // Fallback to Vorium
        if (source == null) {
            source = Global.getSector().getEconomy().getMarket("vorium_market");
        }

        // Final fallback - any Draconis market with military capability
        if (source == null) {
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                if (!market.getFactionId().equals(DRACONIS)) continue;
                Industry b = market.getIndustry("XLII_HighCommand");
                if (b == null) b = market.getIndustry(Industries.HIGHCOMMAND);
                if (b == null) b = market.getIndustry(Industries.MILITARYBASE);
                if (b != null && b.isFunctional() && !b.isDisrupted()) {
                    source = market;
                    break;
                }
            }
        }

        if (source == null || source.hasCondition(Conditions.DECIVILIZED) ||
                !source.getFactionId().equals(DRACONIS)) {
            return null;
        }

        return source;
    }

    /**
     * Create a standalone AI core raid (for NPC faction targets)
     * AI core theft is handled by finish() override in DraconisAICoreRaidIntel
     */
    public static void createStandaloneRaid(MarketAPI source, MarketAPI target, Random random) {
        createRaid(source, target, random);
    }

    /**
     * Internal method to create AI Core raids
     */
    private static boolean createRaid(MarketAPI source, MarketAPI target, Random random) {
        if (source == null || target == null) {
            log.warn("Draconis: Cannot start AI core raid - source or target is null");
            return false;
        }

        log.info("Draconis: === STARTING AI CORE RAID ===");
        log.info("Draconis: Source: " + source.getName());
        log.info("Draconis: Target: " + target.getName() + " (" + target.getFactionId() + ")");

        GenericRaidFGI.GenericRaidParams params = new GenericRaidFGI.GenericRaidParams(
                new Random(random.nextLong()), true);

        params.factionId = DRACONIS;
        params.source = source;

        // Use same timing as hostile activity expeditions
        float prepDaysMin = Global.getSettings().getFloat("draconisExpeditionPrepDaysMin");
        float prepDaysVariance = Global.getSettings().getFloat("draconisExpeditionPrepDaysVariance");
        float payloadDaysMin = Global.getSettings().getFloat("draconisExpeditionPayloadDaysMin");
        float payloadDaysVariance = Global.getSettings().getFloat("draconisExpeditionPayloadDaysVariance");

        params.prepDays = prepDaysMin + random.nextFloat() * prepDaysVariance;
        params.payloadDays = payloadDaysMin + payloadDaysVariance * random.nextFloat();

        // Raid target and behavior configuration
        params.raidParams.where = target.getStarSystem();
        params.raidParams.type = FGRaidType.SEQUENTIAL;  // Sequential like Luddic Path/Diktat
        params.raidParams.tryToCaptureObjectives = false;  // Don't capture objectives
        params.raidParams.allowedTargets.add(target);
        params.raidParams.allowNonHostileTargets = true;  // Match base game pattern
        params.raidParams.setBombardment(BombardType.TACTICAL);  // Tactical bombardment for covert ops

        // Additional raid behavior settings (match base game defaults)
        params.raidParams.doNotGetSidetracked = true;  // Stay focused on target

        params.style = FleetStyle.QUALITY;
        params.makeFleetsHostile = false;  // Use normal faction relations

        // Build fleet composition - two Shadow Fleet battlegroups of ~500 FP each
        int fleet1Size = 450 + random.nextInt(100);  // 450-550 FP
        int fleet2Size = 450 + random.nextInt(100);  // 450-550 FP
        int fleet3Size = 450 + random.nextInt(100);  // 450-550 FP

        params.fleetSizes.add(fleet1Size);
        params.fleetSizes.add(fleet2Size);
        params.fleetSizes.add(fleet3Size);

        log.info("Draconis: Fleet count: 3");
        log.info("Draconis: Fleet 1 size: " + fleet1Size);
        log.info("Draconis: Fleet 2 size: " + fleet2Size);
        log.info("Draconis: Fleet 3 size: " + fleet3Size);

        // Create the raid intel
        // AI core theft is handled by the finish() override in DraconisAICoreRaidIntel
        DraconisAICoreRaidIntel raid = new DraconisAICoreRaidIntel(params, target);
        Global.getSector().getIntelManager().addIntel(raid);

        log.info("Draconis: AI Core Raid created and added to intel!");
        log.info("Draconis: AI core theft will be handled by finish() override on completion");

        return true;
    }

    /**
     * Mark a target market with a raid failure cooldown
     * Prevents the same target from being selected again for a specified number of days
     */
    public static void setTargetFailureCooldown(MarketAPI target, float cooldownDays) {
        if (target == null) return;

        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        target.getMemoryWithoutUpdate().set("$draconis_targetRaidFailureTimestamp", currentTimestamp);

        log.info(
            "Draconis: Applied " + cooldownDays + "-day raid failure cooldown to " + target.getName()
        );
    }
}
