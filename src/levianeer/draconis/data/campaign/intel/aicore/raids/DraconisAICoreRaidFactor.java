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
     * Create a standalone AI core raid for NPC faction targets.
     */
    public static void createStandaloneRaid(MarketAPI source, MarketAPI target, Random random) {
        createRaid(source, target, random);
    }

    private static boolean createRaid(MarketAPI source, MarketAPI target, Random random) {
        if (source == null || target == null) {
            log.warn("Draconis: Cannot start AI core raid - source or target is null");
            return false;
        }

        log.info("Draconis: === STARTING AI CORE RAID ===");
        log.info("Draconis: Source: " + source.getName());
        log.info("Draconis: Target: " + target.getName() + " (" + target.getFactionId() + ")");

        GenericRaidFGI.GenericRaidParams params = new GenericRaidFGI.GenericRaidParams(
                new Random(random.nextLong()), false);

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
        params.raidParams.tryToCaptureObjectives = false;
        params.raidParams.allowedTargets.add(target);
        params.raidParams.allowNonHostileTargets = true;  // Match base game pattern
        params.raidParams.setBombardment(BombardType.TACTICAL);  // Tactical bombardment for covert ops

        params.raidParams.doNotGetSidetracked = true;

        params.style = FleetStyle.QUALITY;
        params.makeFleetsHostile = false;  // Use normal faction relations

        // Build fleet composition - three Shadow Fleet battlegroups of ~500 FP each
        int fleet1Size = 400 + random.nextInt(200);  // 400-600 FP
        int fleet2Size = 400 + random.nextInt(200);  // 400-600 FP
        int fleet3Size = 400 + random.nextInt(200);  // 400-600 FP

        params.fleetSizes.add(fleet1Size);
        params.fleetSizes.add(fleet2Size);
        params.fleetSizes.add(fleet3Size);

        log.info("Draconis: Fleet count: 3");
        log.info("Draconis: Fleet 1 size: " + fleet1Size);
        log.info("Draconis: Fleet 2 size: " + fleet2Size);
        log.info("Draconis: Fleet 3 size: " + fleet3Size);

        DraconisAICoreRaidIntel raid = new DraconisAICoreRaidIntel(params, target);
        Global.getSector().getIntelManager().addIntel(raid);

        log.info("Draconis: AI Core Raid created and added to intel!");
        log.info("Draconis: AI core theft will be handled by finish() override on completion");

        return true;
    }

    private static final String TARGET_FAILURE_COOLDOWN_END_KEY = "$draconis_targetRaidFailureCooldownEnd";

    /**
     * Bug fix: {@code CampaignClockAPI.getTimestamp()} is a raw millisecond counter, 86,400,000 ms
     * per in-game day (confirmed against vanilla's decompiled {@code BaseIntelPlugin.createIntelInfo()},
     * which computes {@code msPerDay = 60L*1000L*60L*24L}). {@code convertToSeconds(days)} is a
     * different unit - elapsed simulation wall-clock seconds, on the order of single digits per day.
     * The previous code here added the two together, so "N days from now" landed on "now": the
     * cooldown was a no-op and failed targets could be re-picked almost immediately. (This method is
     * also called by the Office Takeover crisis.)
     */
    private static final long MS_PER_DAY = 24L * 60L * 60L * 1000L;

    /**
     * Mark a target market with a raid failure cooldown.
     * Prevents the same target from being selected again for {@code cooldownDays} days.
     */
    public static void setTargetFailureCooldown(MarketAPI target, float cooldownDays) {
        if (target == null) return;

        long currentTimestamp = Global.getSector().getClock().getTimestamp();
        long cooldownEnd = currentTimestamp + (long) (cooldownDays * MS_PER_DAY);

        target.getMemoryWithoutUpdate().set(TARGET_FAILURE_COOLDOWN_END_KEY, cooldownEnd);

        log.info(
            "Draconis: Applied " + cooldownDays + "-day raid failure cooldown to " + target.getName()
        );
    }

    /**
     * Returns true if the given market is still within a raid failure cooldown window.
     */
    public static boolean isTargetOnFailureCooldown(MarketAPI target) {
        if (target == null) return false;
        if (!target.getMemoryWithoutUpdate().contains(TARGET_FAILURE_COOLDOWN_END_KEY)) return false;

        long cooldownEnd = target.getMemoryWithoutUpdate().getLong(TARGET_FAILURE_COOLDOWN_END_KEY);
        return Global.getSector().getClock().getTimestamp() < cooldownEnd;
    }
}
