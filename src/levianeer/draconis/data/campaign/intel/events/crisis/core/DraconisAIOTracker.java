package levianeer.draconis.data.campaign.intel.events.crisis.core;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.econ.conditions.DraconManager;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisAIOAICoreFactor;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisAIOBaselineFactor;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisAIORelationsFactor;
import levianeer.draconis.data.campaign.intel.events.crisis.deal.DraconisAIOPaymentDealIntel;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisAIOOneTimeFactor;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisFleetHostileActivityFactor;
import levianeer.draconis.data.campaign.intel.events.crisis.reward.DraconisArmamentsBonus;
import levianeer.draconis.data.campaign.intel.events.crisis.util.DraconisAIODisruptionIntel;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * The Alliance Intelligence Office Tracker - the primary mechanic of the DDA colony crisis.
 * <p>
 * A separate BaseEventIntel (like TriTachyonCommerceRaiding) visible to the player after triggering the crisis.
 * Progress 0–100, ticking monthly based on AI core usage, armaments production, and DDA relations.
 * Commission pauses the tracker. Defeating the expedition at 100 resets it to 0 and grants a reward.
 * Combat against shadow fleets does not slow the tracker.
 * <p>
 * Phases (internal - player sees the number, not the thresholds):
 *   0–34  WATCHING        - colony debuffs only, sabotage, no fleets
 *   35–64 ACTIVE_MEASURES - shadow fleets spawn and scale, debuffs worsen
 *   65–99 LIABILITY       - stronger shadow fleets, debuffs at peak
 *   100   INVASION        - DraconisPunitiveExpedition fires, end of the crisis
 */
public class DraconisAIOTracker extends BaseEventIntel {

    private static final Logger log = Global.getLogger(DraconisAIOTracker.class);

    public static final String KEY = "$dda_aio_ref";
    public static final String DEBUFF_MOD_ID = "dda_aio_watch";
    public static final int PROGRESS_MAX = 100;
    public static final String CRISIS_PERMANENTLY_ENDED_KEY = "$dda_crisis_ended_permanently";

    public enum Stage {
        WATCHING,
        ACTIVE_MEASURES,
        LIABILITY,
        INVASION,
    }

    // Tracks prior suppression state to detect transitions (commission / payment deal)
    private boolean wasSuppressed = false;

    // Monthly tick accumulator
    private float daysSinceTick = 0f;
    private static final float TICK_INTERVAL = 30f;

    // Disruption cooldown - nextDisruptionCooldown is drawn randomly after each disruption.
    // starts at 0 so the first qualifying tick fires immediately.
    private float daysSinceDisruption = 0f;
    private float nextDisruptionCooldown = 0f;

    // Total days since the tracker was first created - never reset on soft restarts
    private float totalDaysWatched = 0f;

    // Tracks whether the invasion has been fired for the current cycle (resets on defeat)
    private boolean invasionFired = false;

    // Sector memory key for the persistent defeat counter (never reset)
    private static final String DEFEAT_COUNT_KEY = "$dda_expeditions_defeated";

    // Hook for future AIO offer event chain
    private boolean aioOfferEventFired = false;

    // ==================== Static helpers ====================

    public static DraconisAIOTracker get() {
        Object o = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        if (o instanceof DraconisAIOTracker t && !t.isEnded()) return t;
        // readResolve() is not called by XStream for non-Serializable classes (BaseEventIntel
        // does not implement Serializable). Fall back to the intel manager and restore the key.
        IntelInfoPlugin found = Global.getSector().getIntelManager().getFirstIntel(DraconisAIOTracker.class);
        if (found instanceof DraconisAIOTracker t && !t.isEnded()) {
            Global.getSector().getMemoryWithoutUpdate().set(KEY, t);
            return t;
        }
        return null;
    }

    public static void createIfNecessary() {
        if (get() != null) return;
        new DraconisAIOTracker();
    }

    // ==================== Construction ====================

    public DraconisAIOTracker() {
        super();
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
        setup();
        Global.getSector().getIntelManager().addIntel(this, true);
        log.info("DDA: AIO Tracker created - timestamp=" + getPlayerVisibleTimestamp()
                + " isPlayerVisible=" + isPlayerVisible()
                + " isHidden=" + isHidden()
                + " isEnded=" + isEnded());
    }

    protected void setup() {
        factors.clear();
        stages.clear();

        setMaxProgress(PROGRESS_MAX);

        // Phases are unlabelled to the player - they see the number, not the thresholds.
        // Stages exist for internal tracking and progress-bar markers only.
        // WATCHING gets a SMALL icon so addStageDescriptionText() is actually called for it.
        addStage(Stage.WATCHING, 0, false, StageIconSize.SMALL);
        addStage(Stage.ACTIVE_MEASURES, 35, true, StageIconSize.SMALL);
        addStage(Stage.LIABILITY, 65, true, StageIconSize.SMALL);
        addStage(Stage.INVASION, 100, true, StageIconSize.LARGE);

        // Monthly display factors - pure display; actual advancement is in doMonthlyTick().
        // Relations multiplier is shown first so the player sees the overall modifier before
        // the AI core contribution it applies to.
        factors.add(new DraconisAIORelationsFactor());
        factors.add(new DraconisAIOAICoreFactor());
    }

    // ==================== Lifecycle ====================

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().getMemoryWithoutUpdate().unset(KEY);
        clearAllMarketDebuffs();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (isEnding() || isEnded()) return;

        // Detect transitions into suppression (commission or active payment deal).
        // Clear debuffs and Priority Target immediately rather than waiting for the next tick.
        boolean suppressed = isCommissioned() || isPaymentActive();
        if (suppressed && !wasSuppressed) {
            clearAllMarketDebuffs();
            clearHighValueTargetFromPlayerMarkets();
            log.info("DDA: Tracker suppressed - cleared market debuffs and Priority Target condition");
        }
        wasSuppressed = suppressed;

        float days = Global.getSector().getClock().convertToDays(amount);
        daysSinceTick += days;
        daysSinceDisruption += days;
        totalDaysWatched += days;

        if (daysSinceTick >= TICK_INTERVAL) {
            daysSinceTick -= TICK_INTERVAL;
            doMonthlyTick();
        }

        // Invasion check: also evaluated between ticks in case progress was set externally
        if (getProgress() >= 100 && !invasionFired) {
            invasionFired = true;
            fireInvasion();
        }
    }

    // ==================== Monthly tick ====================

    private void doMonthlyTick() {
        // Commission pauses the tracker and all effects
        if (isCommissioned()) {
            log.debug("DDA: AIO tracker paused (commissioned)");
            return;
        }

        // Active payment deal suppresses the tick
        if (isPaymentActive()) {
            log.debug("DDA: AIO tick suppressed by active payment deal");
            return;
        }

        float increment = calculateMonthlyIncrement();
        int newProgress = Math.min(100, getProgress() + Math.round(increment));
        setProgress(newProgress);
        log.debug("DDA: AIO tick - +" + increment + " -> " + newProgress);

        // Apply scaled market debuffs to all player colonies
        applyAllMarketDebuffs(newProgress);

        // Disrupt a single random AI-core industry at 25+ (on a random cooldown)
        if (newProgress >= 1 && daysSinceDisruption >= nextDisruptionCooldown) {
            daysSinceDisruption = 0f;
            float minCd = getSetting("draconisAIODisruptionCooldownMin", 20f);
            float maxCd = getSetting("draconisAIODisruptionCooldownMax", 50f);
            nextDisruptionCooldown = minCd + (float)(Math.random() * (maxCd - minCd));
            disruptAICoreIndustries(newProgress);
        }
    }

    /**
     * Calculates the monthly increment.
     * Formula: max(baseFloor, aiCoreContrib * relationsMultiplier)
     * The baseFloor is intentionally unaffected - the tracker never fully stalls.
     */
    private float calculateMonthlyIncrement() {
        float aiCoreRate = getSetting("draconisAIOAICoreRate", 0.4f);
        float relationsMaxReduction = getSetting("draconisAIORelationsMaxReduction", 0.7f);
        float baseFloor = getSetting("draconisAIOBaseFloor", 0.5f);

        float raw = computeAICoreContrib(aiCoreRate)
                * computeRelationsMultiplier(relationsMaxReduction);

        return Math.max(baseFloor, raw);
    }

    public int getExpeditionDefeats() {
        return (int) Global.getSector().getMemoryWithoutUpdate().getFloat(DEFEAT_COUNT_KEY);
    }

    private void incrementExpeditionDefeats() {
        Global.getSector().getMemoryWithoutUpdate()
                .set(DEFEAT_COUNT_KEY, getExpeditionDefeats() + 1);
    }

    // ==================== Market debuffs ====================

    private void applyAllMarketDebuffs(int trackerValue) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;
            applyMarketDebuffs(market, trackerValue);
        }
    }

    private void applyMarketDebuffs(MarketAPI market, int trackerValue) {
        float maxAccess = getSetting("draconisAIOMaxAccessibilityPenalty", 0.15f);
        float maxStab = getSetting("draconisAIOMaxStabilityPenalty", 2f);

        // Accessibility: linear 0->100 maps to 0->maxAccess (e.g. 50 pts = 25%, 100 pts = 50%)
        float accessPenalty = trackerValue * maxAccess / 100f;

        // Stability: scales 15->25 to full penalty, capped at max after 25
        float stabT = Math.max(0f, Math.min((trackerValue - 15f) / 10f, 1f));
        float stabPenalty = stabT * maxStab;

        market.getAccessibilityMod().modifyFlat(DEBUFF_MOD_ID, -accessPenalty, "AIO Scrutiny");
        if (stabPenalty > 0f) {
            market.getStability().modifyFlat(DEBUFF_MOD_ID, -stabPenalty, "AIO Scrutiny");
        } else {
            market.getStability().unmodify(DEBUFF_MOD_ID);
        }
    }

    private void clearAllMarketDebuffs() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;
            market.getAccessibilityMod().unmodify(DEBUFF_MOD_ID);
            market.getStability().unmodify(DEBUFF_MOD_ID);
        }
    }

    private void clearHighValueTargetFromPlayerMarkets() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;
            if (!market.getMemoryWithoutUpdate().getBoolean(DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG)) continue;
            market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.HIGH_VALUE_TARGET_FLAG);
            market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);
            market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_ALPHA_COUNT_FLAG);
            market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_BETA_COUNT_FLAG);
            market.getMemoryWithoutUpdate().unset(DraconisSingleTargetScanner.TARGET_GAMMA_COUNT_FLAG);
            market.removeCondition("draconis_high_value_target");
            log.info("DDA: Removed Priority Target condition from " + market.getName() + " (tracker suppressed)");
        }
    }

    // ==================== Disruption ====================

    /**
     * Disrupts a single randomly chosen AI-core industry. Duration scales with crisis progress:
     * at progress=25 the minimum disruption time is used; at 100 the maximum is used.
     * Sends an intel update notification with the industry and market name.
     */
    private void disruptAICoreIndustries(int progress) {
        // Collect all eligible (undisrupted, AI-core-slotted) industries
        List<Industry> eligible = new ArrayList<>();
        List<MarketAPI> ownerMarket = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;
            for (Industry industry : market.getIndustries()) {
                if (industry == null || industry.isDisrupted()) continue;
                if (Industries.POPULATION.equals(industry.getId())) continue;
                String coreId = industry.getAICoreId();
                if (coreId == null || coreId.isEmpty()) continue;
                eligible.add(industry);
                ownerMarket.add(market);
            }
        }

        if (eligible.isEmpty()) return;

        // Pick one at random
        int idx = (int) (Math.random() * eligible.size());
        Industry target = eligible.get(idx);
        MarketAPI market = ownerMarket.get(idx);

        // Duration scales from min at progress=25 to max at progress=100
        float minD = getSetting("draconisAIODisruptionDaysMin", 10f);
        float maxD = getSetting("draconisAIODisruptionDaysMax", 60f);
        float t = Math.max(0f, Math.min(1f, (progress - 25f) / 75f));
        float days = minD + t * (maxD - minD);

        target.setDisrupted(days);
        log.info("DDA: Disrupted " + target.getCurrentName() + " at " + market.getName()
                + " for " + Math.round(days) + " days (progress=" + progress + ")");

        // Constructor self-registers via addIntel + addScript
        new DraconisAIODisruptionIntel(market, target.getCurrentName(), Math.round(days));
    }

    // ==================== Invasion ====================

    private void fireInvasion() {
        if (DraconisPunitiveExpedition.get() != null) {
            log.info("DDA: Invasion already active, skipping");
            return;
        }

        MarketAPI source = DraconisFleetHostileActivityFactor.getDraconisHomeworld();
        MarketAPI target = findInvasionTarget();

        if (source == null || target == null) {
            log.warn("DDA: Cannot fire invasion - source=" + source + " target=" + target);
            invasionFired = false; // allow retry on next advance
            return;
        }

        HostileActivityEventIntel haeIntel = HostileActivityEventIntel.get();
        if (haeIntel == null) {
            log.warn("DDA: No HostileActivityEventIntel, cannot fire invasion");
            invasionFired = false;
            return;
        }

        DraconisFleetHostileActivityFactor factor = (DraconisFleetHostileActivityFactor)
                haeIntel.getFactorOfClass(DraconisFleetHostileActivityFactor.class);

        if (factor == null) {
            log.warn("DDA: Factor not found, cannot fire invasion");
            invasionFired = false;
            return;
        }

        int defeats = getExpeditionDefeats();
        int diffMin, diffMax;
        switch (defeats) {
            case 0:
                diffMin = (int) getSetting("draconisExpeditionDiffMin1", 60f);
                diffMax = (int) getSetting("draconisExpeditionDiffMax1", 80f);
                break;
            case 1:
                diffMin = (int) getSetting("draconisExpeditionDiffMin2", 75f);
                diffMax = (int) getSetting("draconisExpeditionDiffMax2", 95f);
                break;
            default:
                diffMin = (int) getSetting("draconisExpeditionDiffMin3", 90f);
                diffMax = (int) getSetting("draconisExpeditionDiffMax3", 110f);
                break;
        }

        log.info("DDA: Firing expedition #" + (defeats + 1) + " from " + source.getName()
                + " to " + target.getName() + " (diff " + diffMin + "-" + diffMax + ")");
        boolean started = factor.startAttack(source, target, target.getStarSystem(), new Random(), diffMin, diffMax);
        if (!started) {
            log.warn("DDA: startAttack returned false");
            invasionFired = false;
        } else {
            // Pre-invasion disruption only fires on the final (3rd) raid.
            if (defeats >= 2) {
                // Read the actual raid target from the expedition object (single source of truth).
                DraconisPunitiveExpedition expedition = DraconisPunitiveExpedition.get();
                if (expedition != null && expedition.getParams() != null) {
                    List<MarketAPI> raidTargets = expedition.getParams().raidParams.allowedTargets;
                    if (!raidTargets.isEmpty()) {
                        disruptDefensesForInvasion(raidTargets.get(0));
                    }
                }
            }
        }
    }

    /**
     * Disrupts the highest-tier military station at the invasion target market.
     * Duration covers the maximum possible fleet travel time (prep + payload max + buffer)
     * so the station is still down when the expedition arrives.
     */
    private void disruptDefensesForInvasion(MarketAPI target) {
        String[] stationIds = {
                Industries.STARFORTRESS,
                Industries.STARFORTRESS_MID,
                Industries.STARFORTRESS_HIGH,
                Industries.BATTLESTATION,
                Industries.BATTLESTATION_MID,
                Industries.BATTLESTATION_HIGH,
                Industries.ORBITALSTATION,
                Industries.ORBITALSTATION_MID,
                Industries.ORBITALSTATION_HIGH,
        };

        Industry station = null;
        for (String id : stationIds) {
            Industry ind = target.getIndustry(id);
            if (ind != null) {
                station = ind;
                break;
            }
        }

        if (station == null) {
            log.info("DDA: No defense station at " + target.getName() + " to disrupt pre-invasion");
            return;
        }

        // Use the actual rolled prep+payload values from the expedition that was just created.
        // Falls back to worst-case estimate if expedition is somehow unavailable.
        float duration;
        DraconisPunitiveExpedition expedition = DraconisPunitiveExpedition.get();
        if (expedition != null && expedition.getParams() != null) {
            duration = expedition.getParams().prepDays + expedition.getParams().payloadDays + 35f;
        } else {
            float prepMin    = getSetting("draconisExpeditionPrepDaysMin", 14f);
            float prepVar    = getSetting("draconisExpeditionPrepDaysVariance", 14f);
            float payloadMin = getSetting("draconisExpeditionPayloadDaysMin", 27f);
            float payloadVar = getSetting("draconisExpeditionPayloadDaysVariance", 7f);
            duration         = prepMin + prepVar + payloadMin + payloadVar + 35f;
        }

        station.setDisrupted(duration);
        log.info("DDA: Pre-invasion disruption - " + station.getCurrentName()
                + " at " + target.getName() + " for " + Math.round(duration) + " days");

        // Constructor self-registers via addIntel + addScript
        new DraconisAIODisruptionIntel(target, station.getCurrentName(), Math.round(duration));
    }

    private MarketAPI findInvasionTarget() {
        // Target the largest player market
        MarketAPI bestBySize = null;
        int bestSize = 0;

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;
            if (market.getStarSystem() == null) continue;

            if (market.getSize() > bestSize) {
                bestSize = market.getSize();
                bestBySize = market;
            }
        }

        return bestBySize;
    }

    // ==================== Expedition defeat callback ====================

    /**
     * Called by DraconisPunitiveExpedition when the player defeats the invasion at tracker = 100.
     * First and second defeats reset the tracker to a floor and slow the climb.
     * Third defeat ends the crisis entirely and grants the armaments export bonus.
     */
    public void onExpeditionDefeated() {
        int defeats = getExpeditionDefeats();
        log.info("DDA: Expedition defeated (defeat #" + (defeats + 1) + ")");

        sendUpdateIfPlayerHasIntel(new DefeatNotificationParam(defeats), false);

        if (defeats >= 2) {
            endCrisis();
            return;
        }

        float resetFloor = (defeats == 0)
                ? getSetting("draconisExpeditionReset1", 50f)
                : getSetting("draconisExpeditionReset2", 65f);
        setProgress(Math.round(resetFloor));

        invasionFired = false;
        daysSinceTick = 0f;
        daysSinceDisruption = 0f;
        nextDisruptionCooldown = 0f;

        // Reapply (not clear) debuffs at the new floor - player stays under pressure
        applyAllMarketDebuffs(getProgress());

        incrementExpeditionDefeats();
    }

    /**
     * Called when the punitive expedition succeeds (Draconis wins).
     * Resets the tracker so the crisis can be re-triggered via HAE.
     * Unlike endCrisis(), does NOT set CRISIS_PERMANENTLY_ENDED_KEY.
     */
    public void resetCrisisAfterSuccessfulInvasion() {
        log.info("DDA: Invasion succeeded - resetting crisis for potential HAE re-trigger");
        invasionFired = false;
        daysSinceTick = 0f;
        daysSinceDisruption = 0f;
        nextDisruptionCooldown = 0f;
        // Clear defeat counter so the next cycle starts at stage 1
        Global.getSector().getMemoryWithoutUpdate().unset(DEFEAT_COUNT_KEY);
        // notifyEnded() (via endImmediately) clears market debuffs and unsets KEY.
        // CRISIS_PERMANENTLY_ENDED_KEY is intentionally NOT set.
        endImmediately();
    }

    /**
     * Ends the crisis permanently after the third expedition defeat.
     * Grants the armaments export bonus then removes this intel.
     */
    private void endCrisis() {
        log.info("DDA: Crisis ended - third expedition defeated");
        DraconisArmamentsBonus.grantBonus(true);
        DraconisAIOPaymentDealIntel activeDeal = DraconisAIOPaymentDealIntel.get();
        if (activeDeal != null) activeDeal.endImmediately();
        invasionFired = false;
        daysSinceTick = 0f;
        daysSinceDisruption = 0f;
        nextDisruptionCooldown = 0f;
        // Permanently close the DDA crisis pathway in the HAE system.
        Global.getSector().getMemoryWithoutUpdate().set(CRISIS_PERMANENTLY_ENDED_KEY, true);
        // notifyEnded() (called by endImmediately) handles clearAllMarketDebuffs() and KEY unset.
        // Shadow fleets naturally stop: getEffectMagnitude() returns 0 when tracker is null.
        endImmediately();
    }

    // ==================== Defeat notification ====================

    /** Parameter passed to sendUpdateIfPlayerHasIntel to drive defeat notification text. */
    public static class DefeatNotificationParam {
        public final int defeatsBeforeThis;
        public DefeatNotificationParam(int defeats) { this.defeatsBeforeThis = defeats; }
    }

    // ==================== Helpers ====================

    /** Months the DDA has been building the player's file. Persists through soft restarts; resets only when the tracker is destroyed. */
    public int getMonthsWatched() {
        return Math.max(1, Math.round(totalDaysWatched / 30f));
    }

    public boolean isCommissioned() {
        return DRACONIS.equals(Misc.getCommissionFactionId());
    }

    public boolean isAIOOfferEventFired() {
        return aioOfferEventFired;
    }

    public void setAIOOfferEventFired(boolean fired) {
        this.aioOfferEventFired = fired;
    }

    // ==================== Periodic payment API ====================

    public boolean isPaymentActive() {
        return DraconisAIOPaymentDealIntel.get() != null;
    }

    private float getSetting(String key, float defaultValue) {
        try {
            return Global.getSettings().getFloat(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== One-time factor API ====================

    /**
     * Applies an immediate progress delta and adds a visible entry to the "Recent one-time
     * factors" panel on the AIO Tracker intel. Use for any external event that should be
     * legible to the player (combat, donations, quest beats).
     *
     * @param points      progress delta (positive = escalation, negative = reduction)
     * @param desc        short label shown in the factors table
     * @param tooltipText longer hover explanation, or null
     * @param dialog      unused - kept for API compatibility with old callers
     */
    public void addOneTimeFactor(int points, String desc, String tooltipText, InteractionDialogAPI dialog) {
        int newProgress = Math.min(100, Math.max(0, getProgress() + points));
        setProgress(newProgress);
        log.info("DDA: One-time factor \"" + desc + "\": " + (points >= 0 ? "+" : "") + points
                + " -> " + newProgress);

        factors.add(new DraconisAIOOneTimeFactor(points, desc, tooltipText));
        sendUpdateIfPlayerHasIntel(this, false);
    }

    public void addOneTimeFactor(int points, String desc, String tooltipText) {
        addOneTimeFactor(points, desc, tooltipText, null);
    }

    public void addOneTimeFactor(int points, String desc) {
        addOneTimeFactor(points, desc, null, null);
    }

    // ==================== Static computation helpers (used by display factors) ====================

    public static float computeAICoreContrib(float aiCoreRate) {
        float total = 0f;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;
            if (market.getAdmin().getAICoreId() != null) total += 3f * aiCoreRate;
            for (Industry industry : market.getIndustries()) {
                if (industry == null) continue;
                String coreId = industry.getAICoreId();
                if (coreId == null || coreId.isEmpty()) continue;
                float weight;
                if (Commodities.ALPHA_CORE.equals(coreId)) weight = 3f;
                else if (Commodities.BETA_CORE.equals(coreId)) weight = 2f;
                else weight = 1f;
                total += weight * aiCoreRate;
            }
        }
        return total;
    }

    public static float computeRelationsMultiplier(float relationsMaxReduction) {
        float relation = Global.getSector().getPlayerFaction().getRelationship(DRACONIS);
        float clamped = Math.max(-1f, Math.min(1f, relation));
        return 1f - clamped * relationsMaxReduction;
    }

    // ==================== Progress display overrides ====================

    @Override
    public boolean isEventProgressANegativeThingForThePlayer() {
        return true; // high progress = invasion = bad for player
    }

    /**
     * Returns the projected monthly progress for the bar tooltip.
     * Overrides the default (which would sum factor getProgress() values) to use the
     * accurate float formula with floor clamping. Actual advancement is done in doMonthlyTick().
     */
    @Override
    public int getMonthlyProgress() {
        if (isCommissioned()) return 0;
        if (isPaymentActive()) return 0;
        return Math.round(calculateMonthlyIncrement());
    }

    /**
     * Prevents BaseEventIntel's economy tick from advancing progress.
     * Advancement is handled exclusively by doMonthlyTick() called from advance().
     */
    @Override
    public void reportEconomyTick(int iterIndex) {
        // intentional no-op - progress is controlled by doMonthlyTick()
    }

    // ==================== Icon configuration ====================

    private static final String ICON_INTEL_LIST      = "XLII_aio_stage";
    private static final String ICON_WATCHING        = "XLII_hostile_activity_1";
    private static final String ICON_ACTIVE_MEASURES = "XLII_hostile_activity_2";
    private static final String ICON_LIABILITY       = "XLII_hostile_activity_3";
    private static final String ICON_INVASION        = "XLII_hostile_activity_4";

    // ==================== UI overrides ====================

    @Override
    protected String getStageIconImpl(Object stageId) {
        EventStageData esd = getDataFor(stageId);
        if (esd == null) return null;
        String key;
        if (esd.id == Stage.WATCHING)             key = ICON_WATCHING;
        else if (esd.id == Stage.ACTIVE_MEASURES) key = ICON_ACTIVE_MEASURES;
        else if (esd.id == Stage.LIABILITY)        key = ICON_LIABILITY;
        else                                       key = ICON_INVASION;
        return Global.getSettings().getSpriteName("events", key);
    }

    @Override
    public String getName() {
        int defeats = getExpeditionDefeats();
        String phase = defeats == 0 ? AIOStrings.INTEL_NAME_AIO_PHASE_1
                     : defeats == 1 ? AIOStrings.INTEL_NAME_AIO_PHASE_2
                     :                AIOStrings.INTEL_NAME_AIO_PHASE_3;
        return AIOStrings.INTEL_NAME_AIO_ASSESSMENT + phase;
    }

    public FactionAPI getFaction() {
        return Global.getSector().getFaction(DRACONIS);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("events", ICON_INTEL_LIST);
    }

    @Override
    public java.util.Set<String> getIntelTags(SectorMapAPI map) {
        java.util.Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_COLONIES);
        tags.add(DRACONIS);
        return tags;
    }

    @Override
    public boolean canMakeVisibleToPlayer(boolean playerInRelayRange) {
        return true; // Always immediately visible; no comm relay required
    }

    @Override
    public void addStageDescriptionText(TooltipMakerAPI info, float width, Object stageId) {
        // isStageActive() can behave unexpectedly for the threshold-0 WATCHING stage,
        // so we use explicit progress-range checks instead.
        int p = getProgress();
        boolean active =
                (stageId == Stage.WATCHING        && p <  35) ||
                (stageId == Stage.ACTIVE_MEASURES && p >= 35 && p < 65) ||
                (stageId == Stage.LIABILITY       && p >= 65 && p < 100) ||
                (stageId == Stage.INVASION        && p >= 100);
        if (!active) return;

        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();
        FactionAPI f = getFaction();
        Color fc = f != null ? f.getBaseUIColor() : h;

        if (stageId == Stage.WATCHING) {
            info.addPara(AIOStrings.WATCHING_PARA1, opad);
            info.addPara(AIOStrings.WATCHING_PARA2_FMT, opad, n, AIOStrings.WATCHING_PARA2_HIGHLIGHT);
            float maxAccess = getSetting("draconisAIOMaxAccessibilityPenalty", 0.15f);
            float maxStab = getSetting("draconisAIOMaxStabilityPenalty", 2f);

            String stringAccess = String.valueOf(Math.round(maxAccess * 100f));

            LabelAPI debuffLabel = info.addPara(
                    AIOStrings.WATCHING_PARA3_FMT,
                    opad, n,
                    "accessibility", stringAccess,
                    "stability", String.valueOf(Math.round(maxStab)), "periodic disruptions");
            debuffLabel.setHighlight(
                    "accessibility", stringAccess + "%",
                    "stability", String.valueOf(Math.round(maxStab)), "periodic disruptions");
            debuffLabel.setHighlightColors(n, n, n, n);
        } else if (stageId == Stage.ACTIVE_MEASURES) {
            info.addPara(AIOStrings.ACTIVE_PARA1_FMT, opad, fc, AIOStrings.ACTIVE_PARA1_HIGHLIGHT);
            info.addPara(AIOStrings.ACTIVE_PARA2_FMT, opad, n, AIOStrings.ACTIVE_PARA2_HIGHLIGHT);
        } else if (stageId == Stage.LIABILITY) {
            info.addPara(AIOStrings.LIABILITY_PARA1_FMT, opad, n, AIOStrings.LIABILITY_PARA1_HIGHLIGHT);
            info.addPara(AIOStrings.LIABILITY_PARA2_FMT, opad, n, AIOStrings.LIABILITY_PARA2_HIGHLIGHT);
        } else if (stageId == Stage.INVASION) {
            int defeats = getExpeditionDefeats();
            info.addPara(AIOStrings.INVASION_PARA1_FMT, opad, fc, AIOStrings.INVASION_PARA1_HIGHLIGHT);
            if (defeats >= 2) {
                LabelAPI label = info.addPara(
                        AIOStrings.INVASION_FINAL_FMT,
                        opad, h, "final", "end", "heavy armaments");
                label.setHighlight("final", "end", "heavy armaments");
                label.setHighlightColors(fc, Misc.getPositiveHighlightColor(), Misc.getPositiveHighlightColor());
            } else {
                int resetFloor = Math.round(getSetting(
                        defeats == 0 ? "draconisExpeditionReset1" : "draconisExpeditionReset2",
                        defeats == 0 ? 50f : 65f));
                String ordinal = defeats == 0 ? "first" : "second";
                String remaining = defeats == 0 ? AIOStrings.INVASION_REMAINING_TWO : AIOStrings.INVASION_REMAINING_ONE;
                LabelAPI label = info.addPara(
                        AIOStrings.INVASION_NONFINAL_FMT,
                        opad, h, ordinal, String.valueOf(resetFloor));
                label.setHighlight(ordinal, String.valueOf(resetFloor));
                label.setHighlightColors(fc, Misc.getPositiveHighlightColor());
                info.addPara(AIOStrings.INVASION_REMAINING_FMT, opad, n, remaining);
            }
        }
    }

    @Override
    public TooltipCreator getStageTooltipImpl(Object stageId) {
        final EventStageData esd = getDataFor(stageId);
        if (esd == null) return null;

        if (esd.id != Stage.ACTIVE_MEASURES && esd.id != Stage.LIABILITY && esd.id != Stage.INVASION) {
            return null;
        }

        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                FactionAPI f = getFaction();
                Color fc = f != null ? f.getBaseUIColor() : Misc.getHighlightColor();
                Color n = Misc.getNegativeHighlightColor();

                if (esd.id == Stage.ACTIVE_MEASURES) {
                    tooltip.addTitle(AIOStrings.TOOLTIP_TITLE_ACTIVE_MEASURES);
                    tooltip.addPara(AIOStrings.ACTIVE_PARA1_FMT, opad, fc, AIOStrings.ACTIVE_PARA1_HIGHLIGHT);
                } else if (esd.id == Stage.LIABILITY) {
                    tooltip.addTitle(AIOStrings.TOOLTIP_TITLE_LIABILITY);
                    tooltip.addPara(AIOStrings.LIABILITY_PARA1_FMT, opad, n, AIOStrings.LIABILITY_PARA1_HIGHLIGHT);
                } else {
                    tooltip.addTitle(AIOStrings.TOOLTIP_TITLE_INVASION);
                    tooltip.addPara(AIOStrings.INVASION_PARA1_FMT, opad, fc, AIOStrings.INVASION_PARA1_HIGHLIGHT);
                }

                esd.addProgressReq(tooltip, opad);
            }
        };
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                    Color tc, float initPad) {
        FactionAPI f = getFaction();
        Color fc = f != null ? f.getBaseUIColor() : Misc.getHighlightColor();
        int progress = getProgress();

        if (isUpdate && getListInfoParam() instanceof DefeatNotificationParam dnp) {
            Color pos = Misc.getPositiveHighlightColor();
            Color neg = Misc.getNegativeHighlightColor();
            switch (dnp.defeatsBeforeThis) {
                case 0: {
                    info.addPara(AIOStrings.DEFEAT1_PARA1, tc, initPad);
                    int floor1 = Math.round(getSetting("draconisExpeditionReset1", 50f));
                    LabelAPI l = info.addPara(AIOStrings.DEFEAT_RESET_FMT,
                            0f, pos, AIOStrings.DEFEAT_RESET_KEYWORD, String.valueOf(floor1),
                            AIOStrings.DEFEAT1_RATE_REDUCED, AIOStrings.INVASION_REMAINING_TWO);
                    l.setHighlight(AIOStrings.DEFEAT_RESET_KEYWORD, String.valueOf(floor1),
                            AIOStrings.DEFEAT1_RATE_REDUCED, AIOStrings.INVASION_REMAINING_TWO);
                    l.setHighlightColors(pos, pos, pos, neg);
                    break;
                }
                case 1: {
                    info.addPara(AIOStrings.DEFEAT2_PARA1, tc, initPad);
                    int floor2 = Math.round(getSetting("draconisExpeditionReset2", 65f));
                    LabelAPI l = info.addPara(AIOStrings.DEFEAT_RESET_FMT,
                            0f, pos, AIOStrings.DEFEAT_RESET_KEYWORD, String.valueOf(floor2),
                            AIOStrings.DEFEAT2_RATE_REDUCED, AIOStrings.INVASION_REMAINING_ONE);
                    l.setHighlight(AIOStrings.DEFEAT_RESET_KEYWORD, String.valueOf(floor2),
                            AIOStrings.DEFEAT2_RATE_REDUCED, AIOStrings.INVASION_REMAINING_ONE);
                    l.setHighlightColors(pos, pos, pos, neg);
                    break;
                }
                default: {
                    info.addPara(AIOStrings.DEFEAT3_PARA1, tc, initPad);
                    LabelAPI l = info.addPara(AIOStrings.DEFEAT3_PARA2_FMT,
                            0f, pos, AIOStrings.DEFEAT3_OVER, AIOStrings.DEFEAT3_GRANTED);
                    l.setHighlight(AIOStrings.DEFEAT3_OVER, AIOStrings.DEFEAT3_GRANTED);
                    l.setHighlightColors(pos, pos);
                    break;
                }
            }
            return;
        }

        if (isUpdate && getListInfoParam() instanceof EventStageData esd) {
            if (esd.id == Stage.ACTIVE_MEASURES) {
                info.addPara(AIOStrings.BULLET_ACTIVE_DEPLOYED_FMT, initPad, tc, fc, AIOStrings.BULLET_ACTIVE_HIGHLIGHT);
                return;
            } else if (esd.id == Stage.LIABILITY) {
                info.addPara(AIOStrings.BULLET_LIABILITY_FMT, initPad, tc, Misc.getNegativeHighlightColor(), AIOStrings.BULLET_LIABILITY_HIGHLIGHT);
                return;
            } else if (esd.id == Stage.INVASION) {
                info.addPara(AIOStrings.BULLET_INVASION_FMT,
                        initPad, tc, fc, AIOStrings.BULLET_INVASION_HIGHLIGHT, String.valueOf(getExpeditionDefeats() + 1));
                return;
            }
        }

        if (isCommissioned()) {
            info.addPara(AIOStrings.BULLET_PAUSED_FMT, initPad, tc,
                    Misc.getPositiveHighlightColor(), AIOStrings.BULLET_PAUSED_HIGHLIGHT);
        } else if (isPaymentActive()) {
            info.addPara(AIOStrings.BULLET_SUPPRESSED_FMT, initPad, tc,
                    Misc.getPositiveHighlightColor(), AIOStrings.BULLET_SUPPRESSED_HIGHLIGHT);
        } else if (progress < 34) {
            info.addPara(AIOStrings.BULLET_WATCHING_PASSIVE, tc, initPad);
        } else if (progress < 67) {
            info.addPara(AIOStrings.BULLET_ACTIVE_FLEETS_FMT, initPad, tc, fc, AIOStrings.BULLET_ACTIVE_HIGHLIGHT);
        } else if (progress < 100) {
            info.addPara(AIOStrings.BULLET_LIABILITY_FMT, initPad, tc, Misc.getNegativeHighlightColor(), AIOStrings.BULLET_LIABILITY_HIGHLIGHT);
        } else {
            info.addPara(AIOStrings.BULLET_INVASION_FMT,
                    initPad, tc, fc, AIOStrings.BULLET_INVASION_HIGHLIGHT, String.valueOf(getExpeditionDefeats() + 1));
        }
    }
}
