package levianeer.draconis.data.campaign.intel.events.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction.FGRaidType;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel.FGIEventListener;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;

import java.awt.*;
import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Hostile activity factor for AI Core acquisition raids
 * Spawns Shadow Fleet raids targeting markets with AI cores
 */
public class DraconisAICoreRaidFactor extends BaseHostileActivityFactor implements FGIEventListener {

    public DraconisAICoreRaidFactor(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public String getProgressStr(BaseEventIntel intel) {
        return "";
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "AI Core Acquisition Operations";
    }

    @Override
    public String getNameForThreatList(boolean first) {
        return "AI Core Raids";
    }

    @Override
    public Color getDescColor(BaseEventIntel intel) {
        if (getProgress(intel) <= 0) {
            return Misc.getGrayColor();
        }
        return Global.getSector().getFaction(FORTYSECOND).getBaseUIColor();
    }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                tooltip.addPara("Draconis Shadow Fleet operations have been detected, " +
                        "targeting facilities with AI core installations.", 0f);

                tooltip.addPara("These covert raids seek to acquire advanced AI technology " +
                        "from hostile colonies for integration into Draconis systems.", opad);
            }
        };
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        return getProgress(intel) > 0;
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (!checkFactionExists(DRACONIS, true)) {
            return 0;
        }

        // Check if there are any high-value targets
        MarketAPI target = getHighValueTarget();
        if (target == null) {
            return 0;
        }

        // Progress based on AI core value at the target
        float coreValue = target.getMemoryWithoutUpdate().getFloat(
                DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);

        // Convert core value to progress (scale appropriately)
        return Math.max(1, Math.min(100, (int)(coreValue / 10f)));
    }

    /**
     * Find the current high-value AI core target
     */
    private MarketAPI getHighValueTarget() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getMemoryWithoutUpdate().getBoolean(
                    DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG)) {
                return market;
            }
        }
        return null;
    }

    @Override
    public int getMaxNumFleets(StarSystemAPI system) {
        // Don't spawn patrol fleets - only use expedition system
        return 0;
    }

    @Override
    public CampaignFleetAPI createFleet(StarSystemAPI system, Random random) {
        // Not used - we only spawn via expeditions
        return null;
    }

    public void addBulletPointForEvent(HostileActivityEventIntel intel, EventStageData stage,
                                       TooltipMakerAPI info, ListInfoMode mode,
                                       boolean isUpdate, Color tc, float initPad) {
        Color c = Global.getSector().getFaction(FORTYSECOND).getBaseUIColor();
        info.addPara("Shadow Fleet AI core acquisition raid imminent", initPad, tc, c, "Shadow Fleet");
    }

    public void addBulletPointForEventReset(HostileActivityEventIntel intel, EventStageData stage,
                                            TooltipMakerAPI info, ListInfoMode mode,
                                            boolean isUpdate, Color tc, float initPad) {
        info.addPara("AI core raid operation cancelled", tc, initPad);
    }

    public void addStageDescriptionForEvent(HostileActivityEventIntel intel, EventStageData stage,
                                            TooltipMakerAPI info) {
        float opad = 10f;

        MarketAPI target = getHighValueTarget();
        if (target == null) {
            info.addPara("Target intelligence lost - raid cancelled.", opad);
            return;
        }

        int alphaCount = target.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_ALPHA_COUNT_FLAG);
        int betaCount = target.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_BETA_COUNT_FLAG);
        int gammaCount = target.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_GAMMA_COUNT_FLAG);

        String coreInfo = String.format("%d Alpha, %d Beta, %d Gamma", alphaCount, betaCount, gammaCount);

        info.addPara("Intelligence has confirmed AI core installations at " + target.getName() + " (" + coreInfo + "). " +
                        "A Shadow Fleet raid is being prepared to acquire these cores for Draconis use.",
                opad, Misc.getNegativeHighlightColor(), target.getName());

        LabelAPI label = info.addPara("If the raid succeeds, the stolen AI cores will be integrated into " +
                        "Draconis military and industrial infrastructure, " +
                        "significantly enhancing their technological capabilities.",
                opad);
        label.setHighlight("stolen AI cores", "enhancing");
        label.setHighlightColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor());

        addBorder(info, Global.getSector().getFaction(FORTYSECOND).getBaseUIColor());
    }

    @Override
    public String getEventStageIcon(HostileActivityEventIntel intel, EventStageData stage) {
        return Global.getSettings().getSpriteName("intel", "XLII_ai_found");
    }

    @Override
    public TooltipCreator getStageTooltipImpl(final HostileActivityEventIntel intel, final EventStageData stage) {
        if (stage.id == HostileActivityEventIntel.Stage.HA_EVENT) {
            return getDefaultEventTooltip("Shadow Fleet AI Core Raid", intel, stage);
        }
        return null;
    }

    /**
     * Calculate a simplified market presence factor for a system
     * Mimics HostileActivityEventIntel.getMarketPresenceFactor() for standalone raids
     * Higher values = more player presence = bigger raids
     */
    private static float calculateMarketPresenceFactor(StarSystemAPI system) {
        if (system == null) return 0.5f; // Default moderate presence

        float total = 0f;
        int count = 0;

        // Check all markets in the system
        for (com.fs.starfarer.api.campaign.SectorEntityToken entity : system.getAllEntities()) {
            if (entity.getMarket() == null) continue;
            MarketAPI market = entity.getMarket();

            // Player markets contribute most
            if (market.isPlayerOwned()) {
                total += market.getSize() * 2f; // Double weight for player markets
                count++;
            }
            // Friendly markets contribute some
            else if (market.getFaction().isPlayerFaction() ||
                     Global.getSector().getPlayerFaction().getRelationship(market.getFactionId()) >= 0.5f) {
                total += market.getSize() * 0.5f;
                count++;
            }
        }

        if (count == 0) return 0.25f; // Low presence if no friendly markets

        // Normalize to 0-1 range, with typical values between 0.3-0.8
        float avgSize = total / count;
        return Math.min(1.0f, avgSize / 10f);
    }

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
     * Create a standalone AI core raid (when Hostile Activity isn't active)
     */
    public static void createStandaloneRaid(MarketAPI source, MarketAPI target, Random random) {
        // Don't use a factor instance - just create the raid directly
        createRaid(source, target, random, null);
    }

    /**
     * Start a Shadow Fleet raid on the high-value target
     */
    public boolean startRaid(MarketAPI source, MarketAPI target, Random random) {
        return createRaid(source, target, random, this);
    }

    /**
     * Internal method to create AI Core raids
     * @param listener The listener to attach, or null for standalone raids
     */
    private static boolean createRaid(MarketAPI source, MarketAPI target, Random random, FGIEventListener listener) {
        if (source == null || target == null) {
            Global.getLogger(DraconisAICoreRaidFactor.class).warn("Cannot start AI core raid - source or target is null");
            return false;
        }

        Global.getLogger(DraconisAICoreRaidFactor.class).info("=== STARTING AI CORE RAID ===");
        Global.getLogger(DraconisAICoreRaidFactor.class).info("Source: " + source.getName());
        Global.getLogger(DraconisAICoreRaidFactor.class).info("Target: " + target.getName() + " (" + target.getFactionId() + ")");

        GenericRaidFGI.GenericRaidParams params = new GenericRaidFGI.GenericRaidParams(
                new Random(random.nextLong()), true);

        params.factionId = FORTYSECOND;  // Shadow Fleet faction
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
        params.makeFleetsHostile = false;  // Delayed hostility like Tri-Tachyon - made hostile on arrival

        // Build fleet composition - single MASSIVE Shadow Fleet battlegroup
        // Note: Quality/officer bonuses are controlled by FleetStyle.QUALITY and faction doctrine
        //
        // COMPENSATION STRATEGY: Since we can't set explicit quality/officer bonuses
        // like XLII_HighCommand does with FleetParamsV3, we use MASSIVE fleet size
        // combined with maxed-out faction doctrine (shipQuality:5, shipSize:5, numShips:5)
        // to force the raid system to spawn capital-heavy fleets.
        //
        // Shadow Fleet raids are 6-10x larger than standard High Command patrols
        // Base combat points: 1200-2000 FP (6-10x XLII_HighCommand's 200-300 FP)
        float baseCombat = Math.round(60f + random.nextFloat() * 40f) * 20f;

        // Scale by market combat fleet size multiplier (minimum 1.0x)
        float fleetSizeMult = source.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
        baseCombat *= Math.max(1.0f, fleetSizeMult);

        // Add ENORMOUS bonus based on target market size and defenses
        // Larger markets deserve proportionally massive raid fleets
        float targetSizeBonus = target.getSize() * 100f;  // 300-900 FP bonus (size 3-9 markets)
        baseCombat += targetSizeBonus;

        // Add OVERWHELMING bonus based on AI core value
        // High-value targets get the full Shadow Fleet armada
        int alphaCores = target.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_ALPHA_COUNT_FLAG);
        int betaCores = target.getMemoryWithoutUpdate().getInt(
                DraconisSingleTargetScanner.TARGET_BETA_COUNT_FLAG);
        float coreBonus = (alphaCores * 200f) + (betaCores * 100f);  // 200 FP per Alpha, 100 per Beta
        baseCombat += coreBonus;

        // Cap at extremely high limits - Shadow Fleet brings overwhelming force
        // Minimum 1200 FP, maximum 4000 FP (enough for 20+ capitals with doctrine bonuses)
        int fleetSize = Math.round(Math.max(1200f, Math.min(4000f, baseCombat)));
        params.fleetSizes.add(fleetSize);

        Global.getLogger(DraconisAICoreRaidFactor.class).info("Fleet count: 1 (single battlegroup)");
        Global.getLogger(DraconisAICoreRaidFactor.class).info("Fleet size: " + fleetSize);

        // Create the raid intel
        DraconisAICoreRaidIntel raid = new DraconisAICoreRaidIntel(params, target);
        if (listener != null) {
            raid.setListener(listener);
        }
        Global.getSector().getIntelManager().addIntel(raid);

        Global.getLogger(DraconisAICoreRaidFactor.class).info("AI Core Raid created and added to intel!");

        return true;
    }


    public void reportFleetGroupAboutToSpawn(FleetGroupIntel intel) {
        // Called when raid fleet is about to spawn
        Global.getLogger(this.getClass()).info("AI Core Raid fleet spawning");
    }


    public void reportFleetGroupEnded(FleetGroupIntel intel) {
        // Called when raid completes or is destroyed
        Global.getLogger(this.getClass()).info("AI Core Raid ended");

        // Determine if the raid was successful
        boolean success = false;
        MarketAPI target = null;
        if (intel instanceof DraconisAICoreRaidIntel) {
            DraconisAICoreRaidIntel raidIntel = (DraconisAICoreRaidIntel) intel;
            success = raidIntel.isSucceeded();
            target = raidIntel.getTarget();
        } else if (intel instanceof GenericRaidFGI) {
            GenericRaidFGI raidIntel = (GenericRaidFGI) intel;
            success = raidIntel.isSucceeded();
        }

        // If raid succeeded, steal AI cores from the target
        if (success && target != null) {
            Global.getLogger(this.getClass()).info("Raid succeeded - attempting AI core theft from " + target.getName());

            boolean isPlayerMarket = target.isPlayerOwned();
            levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener.checkAndStealAICores(
                target, isPlayerMarket, "ai_core_raid"
            );

            // Clear high-value target flags after successful raid
            levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner.clearTargetAfterRaid(target);
        } else if (!success) {
            Global.getLogger(this.getClass()).info("Raid failed - no AI cores will be stolen");
        }

        // Decrement active raid count
        DraconisAICoreRaidManager.decrementActiveRaidCount();

        // Start cooldown based on success/failure
        DraconisAICoreRaidManager.startCooldown(success);

        Global.getLogger(this.getClass()).info("AI Core Raid " + (success ? "succeeded" : "failed") + " - cooldown started");
    }

    @Override
    public void reportFGIAborted(FleetGroupIntel intel) {
        // Called when raid is aborted before completion
        Global.getLogger(this.getClass()).info("AI Core Raid aborted");

        // Decrement active raid count
        DraconisAICoreRaidManager.decrementActiveRaidCount();

        // Start failure cooldown (raid was aborted)
        DraconisAICoreRaidManager.startCooldown(false);
    }
}