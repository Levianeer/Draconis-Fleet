package levianeer.draconis.data.campaign.intel.events.crisis.factors;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.impl.campaign.intel.events.HegemonyAICoresActivityCause;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.HAERandomEventData;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.Stage;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel.FGIEventListener;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.econ.conditions.DraconManager;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreFleetInflater;
import levianeer.draconis.data.campaign.ids.FleetTypes;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisPunitiveExpedition;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Registers Draconis as an active colony crisis faction and drives shadow fleet spawning.
 * In the new AIO Tracker architecture, this factor's role is significantly reduced:
 *   - Progress is a fixed nominal value (AIO Tracker is the real mechanic)
 *   - getEffectMagnitude() reads from AIO Tracker to scale shadow fleet spawn rate
 *   - Events (fireEvent/getEventFrequency) are disabled - AIO Tracker fires the invasion at 100
 *   - startAttack() is kept and called by DraconisAIOTracker when tracker reaches 100
 */
public class DraconisFleetHostileActivityFactor extends BaseHostileActivityFactor
        implements FGIEventListener {

    private static final Logger log = Global.getLogger(DraconisFleetHostileActivityFactor.class);

    public static String RAIDER_FLEET = "$draconisRaider";

    // Nominal progress value returned to HostileActivityEventIntel - keeps DDA visible
    // in the colony crisis bar without driving any HAE events.
    private static final int NOMINAL_PROGRESS = 100;

    public static boolean isCommissioned() {
        String commissionFaction = Misc.getCommissionFactionId();
        return DRACONIS.equals(commissionFaction);
    }

    public DraconisFleetHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
    }

    public String getProgressStr(BaseEventIntel intel) {
        return "";
    }

    public String getDesc(BaseEventIntel intel) {
        return AIOStrings.FACTOR_DDA_NAME;
    }

    public String getNameForThreatList(boolean first) {
        return AIOStrings.FACTOR_DDA_NAME;
    }

    public Color getDescColor(BaseEventIntel intel) {
        if (getProgress(intel) <= 0) {
            return Misc.getGrayColor();
        }
        FactionAPI f = Global.getSector().getFaction(DRACONIS);
        return f != null ? f.getBaseUIColor() : Misc.getGrayColor();
    }

    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                tooltip.addPara(AIOStrings.HAE_MAIN_TIP_PARA1, 0f);
                tooltip.addPara(AIOStrings.HAE_MAIN_TIP_PARA2, opad);
            }
        };
    }

    private static boolean isCrisisPermanentlyEnded() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(DraconisAIOTracker.CRISIS_PERMANENTLY_ENDED_KEY);
    }

    public boolean shouldShow(BaseEventIntel intel) {
        if (getProgress(intel) <= 0) return false;
        return computeHAEAICoreProgress() > 0;
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (!playerMeetsColonyThreshold()) return 0;
        if (isCrisisPermanentlyEnded()) return 0;
        if (!checkFactionExists(DRACONIS, true)) return 0;
        // Once the AIO Tracker is running it owns the crisis - remove DDA from the HAE bar.
        // Shadow fleet spawning (getEffectMagnitude / getSpawnFrequency) is unaffected by this.
        if (DraconisAIOTracker.get() != null) return 0;
        // Return nominal progress so DDA has non-zero weight in HAE's event picker before
        // the tracker is created. rollRandomizedStage weights as frequency × (progress/total × 0.9 + 0.1).
        return NOMINAL_PROGRESS;
    }

    @Override
    public int getMaxNumFleets(StarSystemAPI system) {
        return Global.getSettings().getInt("draconisMaxFleets");
    }

    @Override
    public float getSpawnFrequency(StarSystemAPI system) {
        float baseMagnitude = getEffectMagnitude(system);
        if (baseMagnitude <= 0) return 0f;
        float multiplier = Global.getSettings().getFloat("draconisSpawnFrequencyMult");
        return baseMagnitude * multiplier;
    }

    /**
     * Shadow fleet spawn magnitude, derived from the AIO Tracker value.
     * 0 before tracker reaches 34; scales linearly from 34->100 to 0->maxMagnitude.
     */
    @Override
    public float getEffectMagnitude(StarSystemAPI system) {
        if (!playerMeetsColonyThreshold()) return 0f;
        if (isCrisisPermanentlyEnded()) return 0f;
        DraconisAIOTracker tracker = DraconisAIOTracker.get();
        if (tracker == null) return 0f;
        if (tracker.isCommissioned() || tracker.isPaymentActive()) return 0f;

        int trackerValue = tracker.getProgress();
        if (trackerValue < 34) return 0f;

        float maxMag = getSetting("draconisAIOMaxFleetMagnitude", 4.0f);
        float t = (trackerValue - 34f) / (100f - 34f);
        return t * maxMag;
    }

    // Shadow Fleet creation - unchanged
    public CampaignFleetAPI createFleet(StarSystemAPI system, Random random) {
        float f = intel.getMarketPresenceFactor(system);

        int difficultyBase = Global.getSettings().getInt("draconisPatrolDifficultyBase");
        float difficultyMult = Global.getSettings().getFloat("draconisPatrolDifficultyMult");

        // Scale difficulty with both market presence AND AIO tracker value
        DraconisAIOTracker tracker = DraconisAIOTracker.get();
        float trackerScale = 1f;
        if (tracker != null) {
            int tv = tracker.getProgress();
            if (tv >= 67) trackerScale = 1.5f;      // LIABILITY: meaningfully stronger
            else if (tv >= 34) trackerScale = 1f;   // ACTIVE_MEASURES: normal
        }

        int difficulty = Math.round((difficultyBase + f * difficultyMult) * trackerScale);

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

        CampaignFleetAPI fleet = m.createFleet();

        if (fleet == null) {
            log.error("Draconis: Shadow Fleet creation failed (null) in " + system.getName());
        } else {
            fleet.addScript(new DraconisAICoreFleetInflater.DeferredInflateScript(fleet, true));
        }

        return fleet;
    }

    // ==================== HAE event system ====================
    // Used only to create the AIO Tracker when HAE selects DDA for the first time.
    // After tracker exists, no further HAE events are needed.

    public float getEventFrequency(HostileActivityEventIntel intel, EventStageData stage) {
        if (!playerMeetsColonyThreshold()) return 0f;
        if (isCrisisPermanentlyEnded()) return 0f;
        if (stage.id != Stage.HA_EVENT) return 0f;
        if (DraconisAIOTracker.get() != null) return 0f; // tracker already running
        // Homeworld check removed: tracker can exist without a homeworld.
        // The invasion path (fireInvasion in DraconisAIOTracker) handles null homeworld gracefully.
        try {
            return Global.getSettings().getFloat("draconisExpeditionEventFrequency");
        } catch (Exception e) {
            return 5.0f;
        }
    }

    public void rollEvent(HostileActivityEventIntel intel, EventStageData stage) {
        log.info("DDA: rollEvent called - DDA selected by HAE");
        DraconisAIOTracker.createIfNecessary();
        HAERandomEventData data = new HAERandomEventData(this, stage);
        stage.rollData = data;
        intel.sendUpdateIfPlayerHasIntel(data, false);
    }

    public boolean fireEvent(HostileActivityEventIntel intel, EventStageData stage) {
        stage.rollData = null;
        DraconisAIOTracker.createIfNecessary();
        boolean created = DraconisAIOTracker.get() != null;
        log.info("DDA: fireEvent called - tracker " + (created ? "created" : "null"));
        return created;
    }

    // ==================== FGIEventListener ====================

    public void reportFGIAborted(FleetGroupIntel intel) {
        // Expedition was aborted by the system (not player defeat - that's handled in DraconisPunitiveExpedition)
        DraconManager.restoreRaidSavedLevel();
    }

    public void notifyEventEnding() {
        notifyFactorRemoved();
    }

    // ==================== advance() - clear stale roll data if homeworld lost ====================

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (intel == null) return;
        EventStageData stage = intel.getDataFor(Stage.HA_EVENT);
        if (stage != null && stage.rollData instanceof HAERandomEventData
                && ((HAERandomEventData) stage.rollData).factor == this) {
            if (getDraconisHomeworld() == null) {
                intel.resetHA_EVENT();
            }
        }
    }

    // ==================== Punitive expedition creation (called by DraconisAIOTracker) ====================

    public boolean startAttack(MarketAPI source, MarketAPI target, StarSystemAPI system,
                               Random random, int minDifficulty, int maxDifficulty) {
        log.info("Draconis: Starting punitive expedition from " + source.getName()
                + " targeting " + target.getName() + " in " + system.getName());

        DraconManager.saveAndForceLevel(1);

        GenericRaidFGI.GenericRaidParams params = new GenericRaidFGI.GenericRaidParams(
                new Random(random.nextLong()), true);

        params.factionId = FORTYSECOND;
        params.source = source;

        float prepDaysMin = Global.getSettings().getFloat("draconisExpeditionPrepDaysMin");
        float prepDaysVariance = Global.getSettings().getFloat("draconisExpeditionPrepDaysVariance");
        float payloadDaysMin = Global.getSettings().getFloat("draconisExpeditionPayloadDaysMin");
        float payloadDaysVariance = Global.getSettings().getFloat("draconisExpeditionPayloadDaysVariance");

        params.prepDays = prepDaysMin + random.nextFloat() * prepDaysVariance;
        params.payloadDays = payloadDaysMin + payloadDaysVariance * random.nextFloat();

        params.raidParams.where = system;
        params.raidParams.tryToCaptureObjectives = false;
        params.raidParams.allowedTargets.add(target);
        params.raidParams.allowNonHostileTargets = true;
        params.raidParams.raidApproachText = "moving to bombard";
        params.raidParams.raidActionText = "bombarding";
        params.raidParams.raidsPerColony = 1;

        params.style = FleetStyle.QUALITY;

        float fleetSizeMult = source.getStats().getDynamic()
                .getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(1f);

        float f = intel.getMarketPresenceFactor(system);

        float difficultyBase = Global.getSettings().getFloat("draconisExpeditionDifficultyBase");
        float difficultyMinFactor = Global.getSettings().getFloat("draconisExpeditionDifficultyMinFactor");
        float difficultyMaxFactor = Global.getSettings().getFloat("draconisExpeditionDifficultyMaxFactor");

        float totalDifficulty = fleetSizeMult * difficultyBase
                * (difficultyMinFactor + difficultyMaxFactor * f);

        totalDifficulty = Math.max(totalDifficulty, minDifficulty);  // floor: always fires
        totalDifficulty = Math.min(totalDifficulty, maxDifficulty);  // ceiling: cap strength

        // Split into multiple elite fleets
        while (totalDifficulty > 0) {
            int fleetDiff = Math.min(10, (int) totalDifficulty);
            if (fleetDiff < 6) fleetDiff = 6;
            params.fleetSizes.add(fleetDiff);
            totalDifficulty -= fleetDiff;
        }

        DraconisPunitiveExpedition expedition = new DraconisPunitiveExpedition(params);
        expedition.setListener(this);
        Global.getSector().getIntelManager().addIntel(expedition);

        return true;
    }

    // ==================== HAE extra rows (sub-causes in monthly factors table) ====================

    /**
     * Adds AI core and arms production sub-rows under the "Draconis Alliance" monthly factor row,
     * mirroring how Tri-Tachyon shows "Competing exports" - giving the player a quantified
     * sense of what is driving the AIO assessment forward each month.
     * Numbers are in AIO Tracker points per month.
     */
    @Override
    public void addExtraRows(TooltipMakerAPI info, BaseEventIntel intel) {
        int aiCore = computeHAEAICoreProgress();

        Color high = Misc.getHighlightColor();
        Color white = Misc.getTextColor();

        if (aiCore > 0) {
            info.addRowWithGlow(Alignment.LMID, white, AIOStrings.HAE_EXTRA_ROW_AI_CORE,
                    Alignment.RMID, high, "+" + aiCore);
            info.addTooltipToAddedRow(new BaseFactorTooltip() {
                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    Color h = Misc.getHighlightColor();
                    tooltip.addPara(AIOStrings.HAE_AI_CORE_TIP_PARA, 0f, h, "AI cores");
                }
            }, TooltipLocation.RIGHT, false);
        }
    }

    // ==================== HAE UI methods ====================

    public void addBulletPointForEvent(HostileActivityEventIntel intel, EventStageData stage,
                                       TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                       Color tc, float initPad) {
        Color c = Global.getSector().getFaction(DRACONIS).getBaseUIColor();
        info.addPara(AIOStrings.HAE_BULLET_STRIKE, initPad, tc, c, AIOStrings.HAE_BULLET_STRIKE_HIGHLIGHT);
    }

    public void addBulletPointForEventReset(HostileActivityEventIntel intel, EventStageData stage,
                                            TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                            Color tc, float initPad) {
        info.addPara(AIOStrings.HAE_BULLET_AVERTED, tc, initPad);
    }

    public void addStageDescriptionForEvent(HostileActivityEventIntel intel, EventStageData stage,
                                            TooltipMakerAPI info) {
        float opad = 10f;
        info.addPara(AIOStrings.HAE_STAGE_DESC, opad);
    }

    public String getEventStageIcon(HostileActivityEventIntel intel, EventStageData stage) {
        return Global.getSector().getFaction(DRACONIS).getCrest();
    }

    public TooltipCreator getStageTooltipImpl(final HostileActivityEventIntel intel,
                                               final EventStageData stage) {
        if (stage.id == Stage.HA_EVENT) {
            return getDefaultEventTooltip(AIOStrings.HAE_EVENT_TOOLTIP_TITLE, intel, stage);
        }
        return null;
    }

    // ==================== Homeworld lookup (used by AIO Tracker) ====================

    public static MarketAPI getDraconisHomeworld() {
        MarketAPI homeworld = Global.getSector().getEconomy().getMarket("kori_market");

        if (homeworld == null) {
            homeworld = Global.getSector().getEconomy().getMarket("vorium_market");
        }

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

        if (homeworld == null || homeworld.hasCondition(Conditions.DECIVILIZED)
                || !homeworld.getFactionId().equals(DRACONIS)) {
            return null;
        }

        Industry b = homeworld.getIndustry(Industries.MILITARYBASE);
        if (b == null) b = homeworld.getIndustry(Industries.HIGHCOMMAND);

        return homeworld;
    }

    // ==================== Helpers ====================

    /**
     * Computes the AI core contribution for the HAE extra row using the exact Hegemony formula:
     * hegemony point weights per core tier, size-3-or-smaller colonies ignored, same DR loop.
     * No relations multiplier - matches what Hegemony shows on the HAE bar.
     * This doesn't actually *need* the colony threshold, however it's not worth removing.
     */
    private static int computeHAEAICoreProgress() {
        float admin = 10f;
        float alpha = 4f;
        float beta  = 2f;
        float gamma = 1f;
        float unit  = 10f;
        float mult  = 0.75f;

        float total = 0f;
        for (StarSystemAPI system : Misc.getPlayerSystems(false)) {
            for (MarketAPI market : Misc.getMarketsInLocation(system, Factions.PLAYER)) {
                if (market.getAdmin().getAICoreId() != null) total += admin;
                for (Industry ind : market.getIndustries()) {
                    String core = ind.getAICoreId();
                    if (Commodities.ALPHA_CORE.equals(core)) total += alpha;
                    else if (Commodities.BETA_CORE.equals(core)) total += beta;
                    else if (Commodities.GAMMA_CORE.equals(core)) total += gamma;
                }
            }
        }

        float rem = total;
        float adjusted = 0f;
        while (rem > unit) {
            adjusted += unit;
            rem -= unit;
            rem *= mult;
        }
        adjusted += rem;

        int progress = Math.round(adjusted);
        if (total > 0 && progress < 1) progress = 1;
        return progress;
    }

    private static boolean playerMeetsColonyThreshold() {
        int count = 0;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!"player".equals(market.getFactionId())) continue;
            if (market.getSize() >= 4) return true;
            count++;
            if (count >= 2) return true;
        }
        return false;
    }

    private float getSetting(String key, float defaultValue) {
        try {
            return Global.getSettings().getFloat(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
