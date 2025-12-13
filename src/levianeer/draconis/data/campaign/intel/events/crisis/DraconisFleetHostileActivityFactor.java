package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.HAERandomEventData;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.Stage;
import com.fs.starfarer.api.impl.campaign.intel.events.TriTachyonStandardActivityCause.CompetitorData;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction.FGRaidType;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel.FGIEventListener;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.CountingMap;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.Random;

import levianeer.draconis.data.campaign.ids.FleetTypes;
import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

public class DraconisFleetHostileActivityFactor extends BaseHostileActivityFactor
        implements FGIEventListener {

    public static String DEFEATED_DRACONIS_ATTACK = "$defeatedDraconisAttack";
    public static String RAIDER_FLEET = "$draconisRaider";

    public static boolean meetsResetConditions() {
        return isCommissioned() && hasHighReputation();
    }

    public static boolean isCommissioned() {
        String commissionFaction = Misc.getCommissionFactionId();
        return DRACONIS.equals(commissionFaction);
    }

    public static boolean hasHighReputation() {
        // Check if player has high enough reputation
        float requiredRep = Global.getSettings().getFloat("draconisReputationThreshold");
        return Global.getSector().getPlayerFaction().getRelationship(DRACONIS) >= requiredRep;
    }

    public static boolean isPlayerDefeatedDraconisAttack() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getBoolean(DEFEATED_DRACONIS_ATTACK);
    }

    public static void setPlayerDefeatedDraconisAttack() {
        Global.getSector().getPlayerMemoryWithoutUpdate().set(DEFEATED_DRACONIS_ATTACK, true);
    }

    public DraconisFleetHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
    }

    public String getProgressStr(BaseEventIntel intel) {
        return "";
    }

    public String getDesc(BaseEventIntel intel) {
        return "Draconis Alliance";
    }

    public String getNameForThreatList(boolean first) {
        return "Draconis Alliance";
    }

    public Color getDescColor(BaseEventIntel intel) {
        if (getProgress(intel) <= 0) {
            return Misc.getGrayColor();
        }
        return Global.getSector().getFaction(DRACONIS).getBaseUIColor();
    }

    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                tooltip.addPara("You've attracted the hostile attention of the Draconis Alliance.", 0f);

                tooltip.addPara("Draconis strike fleets have been detected in your space, "
                        + "targeting facilities that compete with their heavy armaments production.", opad);
            }
        };
    }

    public boolean shouldShow(BaseEventIntel intel) {
        return getProgress(intel) > 0;
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (!checkFactionExists(DRACONIS, true)) {
            return 0;
        }
        // Stop showing progress if BOTH commissioned AND high reputation
        if (meetsResetConditions() || isPlayerDefeatedDraconisAttack()) {
            return 0;
        }
        return super.getProgress(intel);
    }

    @Override
    public int getMaxNumFleets(StarSystemAPI system) {
        return Global.getSettings().getInt("draconisMaxFleets");
    }

    // Create Shadow Fleets
    public CampaignFleetAPI createFleet(StarSystemAPI system, Random random) {
        float f = intel.getMarketPresenceFactor(system);

        int difficultyBase = Global.getSettings().getInt("draconisPatrolDifficultyBase");
        float difficultyMult = Global.getSettings().getFloat("draconisPatrolDifficultyMult");

        int difficulty = difficultyBase + Math.round(f * difficultyMult);

        FleetCreatorMission m = new FleetCreatorMission(random);
        m.beginFleet();

        Vector2f loc = system.getLocation();

        m.createQualityFleet(difficulty, DRACONIS, loc);

        if (difficulty <= 5) {
            m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_1);
        } else if (difficulty <= 7) {
            m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_2);
        } else {
            m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_3);
        }

        m.triggerSetFleetType(FleetTypes.SHADOW_FLEET);

        m.triggerSetPirateFleet();
        m.triggerMakeHostile();
        m.triggerMakeNonHostileToFaction(DRACONIS);
        m.triggerMakeNoRepImpact();
        m.triggerFleetAllowLongPursuit();
        m.triggerMakeHostileToAllTradeFleets();
        m.triggerMakeEveryoneJoinBattleAgainst();

        m.triggerSetFleetFlag(RAIDER_FLEET);

        m.triggerFleetMakeFaster(true, 0, true);

        return m.createFleet();
    }

    public void addBulletPointForEvent(HostileActivityEventIntel intel, EventStageData stage, TooltipMakerAPI info,
                                       ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        Color c = Global.getSector().getFaction(DRACONIS).getBaseUIColor();
        info.addPara("Impending Draconis Alliance strike", initPad, tc, c, "Draconis Alliance");
    }

    public void addBulletPointForEventReset(HostileActivityEventIntel intel, EventStageData stage, TooltipMakerAPI info,
                                            ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        info.addPara("Draconis Alliance attack averted", tc, initPad);
    }

    public void addStageDescriptionForEvent(HostileActivityEventIntel intel, EventStageData stage, TooltipMakerAPI info) {
        float small;
        float opad = 10f;

        small = 8f;

        Global.getSector().getFaction(DRACONIS).getBaseUIColor();
        Color c;

        Misc.getHighlightColor();
        info.addPara("Intelligence suggests that the Draconis Alliance is preparing a coordinated strike to "
                        + "eliminate your heavy armaments production facilities.",
                small, Misc.getNegativeHighlightColor(), "eliminate");

        LabelAPI label = info.addPara("If the attack is defeated, the Draconis Alliance will likely reconsider "
                        + "their aggressive stance, and your ability to produce and export heavy armaments will be enhanced.",
                opad);
        label.setHighlight("Draconis Alliance",
                "heavy armaments", "enhanced");
        label.setHighlightColors(
                Global.getSector().getFaction(DRACONIS).getBaseUIColor(),
                Misc.getPositiveHighlightColor(),
                Misc.getPositiveHighlightColor());

        c = Global.getSector().getFaction(DRACONIS).getBaseUIColor();
        stage.beginResetReqList(info, true, "crisis", opad);
        info.addPara("You obtain a %s with the Draconis Alliance %s reach %s reputation", 0f, c,
                "commission", "and", "Cooperative");
        info.addPara("The Draconis Alliance's primary production facilities are %s", 0f, c, "significantly disrupted");
        stage.endResetReqList(info, false, "crisis", -1, -1);

        addBorder(info, Global.getSector().getFaction(DRACONIS).getBaseUIColor());
    }

    public String getEventStageIcon(HostileActivityEventIntel intel, EventStageData stage) {
        return Global.getSector().getFaction(DRACONIS).getCrest();
    }

    public TooltipCreator getStageTooltipImpl(final HostileActivityEventIntel intel, final EventStageData stage) {
        if (stage.id == Stage.HA_EVENT) {
            return getDefaultEventTooltip("Draconis Alliance strike", intel, stage);
        }
        return null;
    }

    public static MarketAPI getDraconisHomeworld() {
        // Try Kori first (main military hub)
        MarketAPI homeworld = Global.getSector().getEconomy().getMarket("kori_market");

        // Fallback to Vorium
        if (homeworld == null) {
            homeworld = Global.getSector().getEconomy().getMarket("vorium_market");
        }

        // Final fallback - any Draconis market with military capability
        if (homeworld == null) {
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                if (!market.getFactionId().equals(DRACONIS)) continue;
                Industry b = market.getIndustry("XLII_HighCommand");
                if (b == null) b = market.getIndustry(Industries.HIGHCOMMAND);
                if (b == null) b = market.getIndustry(Industries.MILITARYBASE);
                if (b != null && b.isFunctional() && !b.isDisrupted()) {
                    homeworld = market;
                    break;
                }
            }
        }

        if (homeworld == null || homeworld.hasCondition(Conditions.DECIVILIZED) ||
                !homeworld.getFactionId().equals(DRACONIS)) {
            return null;
        }

        Industry b = homeworld.getIndustry(Industries.MILITARYBASE);
        if (b == null) b = homeworld.getIndustry(Industries.HIGHCOMMAND);

        return homeworld;
    }

    public float getEventFrequency(HostileActivityEventIntel intel, EventStageData stage) {
        if (stage.id == Stage.HA_EVENT) {
            Global.getLogger(this.getClass()).info("=== Checking Event Frequency ===");

            if (isPlayerDefeatedDraconisAttack() || getDraconisHomeworld() == null) {
                Global.getLogger(this.getClass()).info("Event blocked: defeated=" + isPlayerDefeatedDraconisAttack() +
                        ", homeworld=" + (getDraconisHomeworld() != null));
                return 0f;
            }

            if (meetsResetConditions()) {
                Global.getLogger(this.getClass()).info("Event blocked: reset conditions met (commission + high rep)");
                return 0f;
            }

            if (DraconisPunitiveExpedition.get() != null) {
                Global.getLogger(this.getClass()).info("Event blocked: expedition already active");
                return 0f;
            }

            MarketAPI target = findExpeditionTarget();
            MarketAPI source = getDraconisHomeworld();

            Global.getLogger(this.getClass()).info("Target: " + (target != null ? target.getName() : "NULL"));
            Global.getLogger(this.getClass()).info("Source: " + (source != null ? source.getName() : "NULL"));

            if (target != null && source != null) {
                float freq = Global.getSettings().getFloat("draconisExpeditionEventFrequency");
                Global.getLogger(this.getClass()).info("Event CAN fire! Frequency: " + freq);
                return freq;
            }

            Global.getLogger(this.getClass()).info("Event blocked: missing target or source");
        }
        return 0;
    }

    public void rollEvent(HostileActivityEventIntel intel, EventStageData stage) {
        HAERandomEventData data = new HAERandomEventData(this, stage);
        stage.rollData = data;
        intel.sendUpdateIfPlayerHasIntel(data, false);
    }

    public boolean fireEvent(HostileActivityEventIntel intel, EventStageData stage) {
        MarketAPI target = findExpeditionTarget();
        MarketAPI source = getDraconisHomeworld();

        if (source == null || target == null) {
            return false;
        }

        stage.rollData = null;
        return startAttack(source, target, target.getStarSystem(), getRandomizedStageRandom(3));
    }

    public static MarketAPI findExpeditionTarget() {
        List<CompetitorData> data = DraconisFleetStandardActivityCause.computePlayerCompetitionData();

        if (data.isEmpty()) {
            // Fallback: just find any player market with size 4+
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                if (Factions.PLAYER.equals(market.getFactionId()) && market.getSize() >= 4) {
                    return market;
                }
            }
            return null;
        }

        CountingMap<MarketAPI> counts = new CountingMap<>();

        for (CompetitorData curr : data) {
            for (MarketAPI market : curr.competitorProducers) {
                StarSystemAPI system = market.getStarSystem();
                if (system == null) continue;
                CommodityOnMarketAPI com = market.getCommodityData(curr.commodityId);
                if (com == null) continue;
                int weight = com.getMaxSupply();
                counts.add(market, weight);
            }
        }

        return counts.getLargest();
    }

    public void reportFGIAborted(FleetGroupIntel intel) {
        setPlayerDefeatedDraconisAttack();

        // Grant production bonus - FROM DEFEAT, so it's permanent
        DraconisArmamentsBonus.grantBonus(true);
    }

    public void notifyEventEnding() {
        notifyFactorRemoved();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        // If reset conditions newly met, grant the bonus and reset everything
        if (meetsResetConditions() && !isPlayerDefeatedDraconisAttack()) {

            // Grant the same bonus as defeating the expedition, but mark as from commission
            // (not from defeat) so it can be removed if commission is lost
            setPlayerDefeatedDraconisAttack();
            DraconisArmamentsBonus.grantBonus(false); // false = from commission, not defeat

            // Reset crisis progress to 0
            if (intel != null) {
                intel.setProgress(0);
            }
        }

        // Check if player lost commission and should lose the bonus
        // Only remove if the bonus wasn't from defeating the expedition
        if (!DraconisArmamentsBonus.isBonusFromDefeat() &&
                !isCommissioned() &&
                Global.getSector().getMemoryWithoutUpdate().contains(DraconisArmamentsBonus.KEY)) {

            // Player lost commission and bonus was from commission, not from defeating expedition
            DraconisArmamentsBonus.removeBonus();

            // Also reset the defeated flag so the crisis can start again
            Global.getSector().getPlayerMemoryWithoutUpdate().unset(DEFEATED_DRACONIS_ATTACK);
        }

        // Cancel planned attacks if reset conditions met or homeworld gone
        if (intel != null) {
            EventStageData stage = intel.getDataFor(Stage.HA_EVENT);
            if (stage != null && stage.rollData instanceof HAERandomEventData &&
                    ((HAERandomEventData)stage.rollData).factor == this) {

                if (meetsResetConditions() || getDraconisHomeworld() == null) {
                    intel.resetHA_EVENT();
                }
            }
        }
    }

    public boolean startAttack(MarketAPI source, MarketAPI target, StarSystemAPI system, Random random) {
        Global.getLogger(this.getClass()).info("=== STARTING ATTACK ===");
        Global.getLogger(this.getClass()).info("Source: " + source.getName() + " (" + source.getFactionId() + ")");
        Global.getLogger(this.getClass()).info("Target: " + target.getName() + " (" + target.getFactionId() + ")");
        Global.getLogger(this.getClass()).info("System: " + system.getName());

        GenericRaidFGI.GenericRaidParams params = new GenericRaidFGI.GenericRaidParams(new Random(random.nextLong()), true);

        params.makeFleetsHostile = true;
        params.factionId = DRACONIS;
        params.source = source;

        float prepDaysMin = Global.getSettings().getFloat("draconisExpeditionPrepDaysMin");
        float prepDaysVariance = Global.getSettings().getFloat("draconisExpeditionPrepDaysVariance");
        float payloadDaysMin = Global.getSettings().getFloat("draconisExpeditionPayloadDaysMin");
        float payloadDaysVariance = Global.getSettings().getFloat("draconisExpeditionPayloadDaysVariance");

        params.prepDays = prepDaysMin + random.nextFloat() * prepDaysVariance;
        params.payloadDays = payloadDaysMin + payloadDaysVariance * random.nextFloat();

        params.raidParams.where = system;
        // Use default FGRaidType.CONCURRENT (don't override to SEQUENTIAL - causes raid failures)
        params.raidParams.tryToCaptureObjectives = false;
        params.raidParams.allowedTargets.add(target);
        params.raidParams.allowNonHostileTargets = true;
        params.raidParams.setBombardment(BombardType.TACTICAL);
        // doNotGetSidetracked defaults to true, no need to set it

        params.style = FleetStyle.QUALITY;

        float fleetSizeMult = source.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
        float f = intel.getMarketPresenceFactor(system);

        float difficultyBase = Global.getSettings().getFloat("draconisExpeditionDifficultyBase");
        float difficultyMinFactor = Global.getSettings().getFloat("draconisExpeditionDifficultyMinFactor");
        float difficultyMaxFactor = Global.getSettings().getFloat("draconisExpeditionDifficultyMaxFactor");

        float totalDifficulty = fleetSizeMult * difficultyBase * (difficultyMinFactor + difficultyMaxFactor * f);

        int minDifficulty = Global.getSettings().getInt("draconisExpeditionMinTotalDifficulty");
        int maxDifficulty = Global.getSettings().getInt("draconisExpeditionMaxTotalDifficulty");

        if (totalDifficulty < minDifficulty) {
            return false;
        }
        if (totalDifficulty > maxDifficulty) {
            totalDifficulty = maxDifficulty;
        }

        // Send 1 massive SMOD_3 quality fleet (stealth operation - single coordinated strike)
        // Fleet size scales proportionally with totalDifficulty (like Remnant Ordo fleets)
        // Scales from ~90-110 FP at minimum difficulty to ~450-550 FP at maximum difficulty
        int fleetSize = (int)(totalDifficulty * 9) + random.nextInt(Math.max(1, (int)(totalDifficulty * 2)));

        params.fleetSizes.add(fleetSize);

        Global.getLogger(this.getClass()).info("Total difficulty: " + totalDifficulty);
        Global.getLogger(this.getClass()).info("Fleet count: " + params.fleetSizes.size());
        Global.getLogger(this.getClass()).info("Fleet sizes: " + params.fleetSizes);

        DraconisPunitiveExpedition expedition = new DraconisPunitiveExpedition(params);
        expedition.setListener(this);
        Global.getSector().getIntelManager().addIntel(expedition);

        Global.getLogger(this.getClass()).info("Expedition created and added to intel!");

        return true;
    }
}