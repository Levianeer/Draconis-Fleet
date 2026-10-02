package levianeer.draconis.data.campaign.intel.longsight.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.EventProgressBarAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.ids.Factions;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sector-wide status panel for the Office Takeover crisis - the single always-visible intel entry
 * summarizing {@link XLII_LongsightCrisisManager}'s state, in the same {@code BaseEventIntel} shape
 * {@code DraconisAIOTracker} uses for the AIO colony crisis (progress bar + monthly factors), rather
 * than the per-Bastion detail {@link XLII_LongsightBastionIntel} already provides.
 * <p>
 * <b>Two stages, not a one-way arc.</b> {@code Stage.START} (progress 0) exists purely so
 * {@code BaseEventIntel.setProgress()} always has a non-null "last active stage" to compare against
 * (the same convention {@code DraconisAIOTracker} uses for its own WATCHING stage) - its icon is
 * never actually drawn ({@code progress <= 0} is skipped by the base class). {@code Stage.BASTION}
 * (progress 100) is the only stage that renders: a fixed icon and description at the bar's far end,
 * marking what happens once it fills. Its own {@code sendIntelUpdateOnReaching} is turned off in the
 * constructor - the manager's spawn check is probabilistic (see {@link #advanceImpl}'s own doc), so
 * reaching 100 here does not reliably mean a Bastion actually appeared, and a notification firing
 * every single cycle regardless would be misleading.
 * <p>
 * <b>Layout override.</b> {@link #createLargeDescription} is fully overridden rather than just
 * hooking {@code afterStageDescriptions()} - the base class hardcodes a second, right-hand column
 * for "Recent one-time factors" with no seam to repurpose its heading/content, and this crisis has
 * no one-time factors at all. The override keeps the base class's bar/stage/monthly-factors
 * machinery as-is and substitutes a "Colonies Captured" column in that same right-hand slot.
 * <p>
 * Created by {@link XLII_LongsightCrisisManager#createIfNecessary()} and ended by that same
 * manager's {@code resolveCrisis()} - this class owns no lifecycle of its own beyond that.
 */
public class XLII_LongsightCrisisTrackerIntel extends BaseEventIntel {

    private static final Logger log = Global.getLogger(XLII_LongsightCrisisTrackerIntel.class);

    private enum Stage {
        START,
        BASTION,
    }

    private static final String BASTION_ICON = "XLII_bastion_stage";

    /** Placeholder - not final copy, same as the rest of this package's Stage 8 prose. */
    private static final String INTRO_PARA =
            "The Alliance Intelligence Office has moved past merely watching - it's now actively "
            + "trying to seize the sector. Hidden forward operating bases, scattered through the "
            + "Sector, are coordinating invasions against independent and rival colonies alike, "
            + "capturing or destroying them outright. Destroying a base doesn't end this - only "
            + "slows the Office down while it rebuilds.";

    /** Placeholder - not final copy. */
    private static final String BASTION_STAGE_DESC =
            "Once this fills, the Office attempts to stand up another base somewhere in the "
            + "Sector - though it may hold off if it already has as many active as it wants to risk "
            + "at once.";

    /** Dev-only, same convention as {@code DraconisAIOTracker}'s own in-panel dev button. */
    private static final Object BUTTON_DEV_FORCE_SPAWN = "longsight_crisis_dev_force_spawn";

    public static XLII_LongsightCrisisTrackerIntel get() {
        IntelInfoPlugin found = Global.getSector().getIntelManager()
                .getFirstIntel(XLII_LongsightCrisisTrackerIntel.class);
        if (found instanceof XLII_LongsightCrisisTrackerIntel t && !t.isEnded()) return t;
        return null;
    }

    /** Safe to call any time - a no-op if an entry already exists. */
    public static void createIfNecessary() {
        if (get() != null) return;
        new XLII_LongsightCrisisTrackerIntel();
    }

    public XLII_LongsightCrisisTrackerIntel() {
        super();

        addStage(Stage.START, 0, false, StageIconSize.SMALL);
        addStage(Stage.BASTION, 100, false, StageIconSize.LARGE);
        EventStageData bastionStage = getDataFor(Stage.BASTION);
        if (bastionStage != null) bastionStage.sendIntelUpdateOnReaching = false;

        factors.add(new XLII_LongsightBaseRateFactor());
        factors.add(new XLII_LongsightDestroyedBastionFactor());

        Global.getSector().getIntelManager().addIntel(this, true);
        log.info("Draconis: Longsight Crisis Tracker intel created");
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
        if (manager == null) {
            endImmediately();
            return;
        }

        // A percentage of the manager's own spawn-check interval, not a raw day count - that
        // IntervalUtil's real duration is currently a sub-day testing placeholder (see
        // XLII_LongsightCrisisManager.PLACEHOLDER_BASE_INTERVAL_DAYS's own doc), so showing whole
        // days of progress would round to a near-binary 0/1 bar. A 0-100 percentage stays a smooth,
        // meaningful bar regardless of how that interval is eventually tuned.
        IntervalUtil tracker = manager.getTracker();
        float duration = tracker.getIntervalDuration();
        float frac = duration > 0f ? tracker.getElapsed() / duration : 0f;
        frac = Math.max(0f, Math.min(1f, frac));
        setMaxProgress(100);
        setProgress(Math.round(frac * 100f));
    }

    @Override
    public boolean isEventProgressANegativeThingForThePlayer() {
        return true;
    }

    /**
     * Hidden until the crisis-wide reveal fires - same shared flag and reasoning
     * {@code XLII_LongsightBastionIntel.isHidden()} already reads. Without this, the crisis panel
     * would appear in the intel list the moment the manager is created (silently, per the
     * suppressed-popup {@code addIntel(this, true)} call above) - well before the reveal scene
     * that's supposed to be the player's first real confirmation anything is wrong.
     */
    @Override
    public boolean isHidden() {
        if (super.isHidden()) return true;
        return !XLII_LongsightCrisisManager.isRevealed();
    }

    /** Always immediately visible once revealed - no comm relay required, same override
     *  {@code DraconisAIOTracker} uses for the AIO colony crisis. */
    @Override
    public boolean canMakeVisibleToPlayer(boolean playerInRelayRange) {
        return true;
    }

    @Override
    public TooltipCreator getBarTooltip() {
        return new TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 450;
            }
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Progress toward the Office's next attempt to build a new "
                        + "base somewhere in the Sector.", 0f);
            }
        };
    }

    /**
     * Never delegates to {@code super.getStageIconImpl()} - its default ("stage_unknown" under
     * "events") isn't actually registered in this mod's sprite catalog and crashes on texture
     * lookup. That default would otherwise be hit for {@code Stage.START}:
     * {@code addStageDescriptionWithImage()} resolves every stage's icon unconditionally (the
     * "was any text actually added" check only gates whether the built block gets inserted into
     * the panel, not whether the icon texture gets looked up first), so START's icon is resolved
     * even though its block is never shown.
     */
    @Override
    protected String getStageIconImpl(Object stageId) {
        if (stageId == Stage.BASTION) {
            return Global.getSettings().getSpriteName("events", BASTION_ICON);
        }
        return Global.getSettings().getSpriteName("intel", "XLII_security_codes");
    }

    /** Hover tooltip on the Bastion stage icon itself - see {@link #getStageTooltip} for why this
     *  is the actual override point, not {@code getStageTooltipImpl} directly. */
    @Override
    public TooltipCreator getStageTooltipImpl(Object stageId) {
        if (stageId != Stage.BASTION) return null;
        return new TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 350;
            }
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(BASTION_STAGE_DESC, 0f);
            }
        };
    }

    /**
     * Full override - see this class's own doc for why {@code afterStageDescriptions()} alone
     * isn't enough to move the colonies list into the right-hand column. Structurally identical to
     * {@code BaseEventIntel.createLargeDescription()} up through the monthly-factors table; the
     * "Recent one-time factors" column that would normally follow is replaced with
     * {@link #addCapturedColoniesList}.
     */
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float opad = 10f;

        TooltipMakerAPI main = panel.createUIElement(width, height, true);

        main.setTitleOrbitronVeryLarge();
        main.addTitle(getName(), Misc.getBasePlayerColor());

        main.addPara(INTRO_PARA, opad);

        EventProgressBarAPI bar = main.addEventProgressBar(this, 100f);
        TooltipCreator barTC = getBarTooltip();
        if (barTC != null) {
            main.addTooltipToPrevious(barTC, TooltipLocation.BELOW, false);
        }

        for (EventStageData curr : stages) {
            if (curr.progress <= 0) continue; // no icon for the START bookkeeping stage
            EventStageDisplayData data = createDisplayData(curr.id);
            UIComponentAPI marker = main.addEventStageMarker(data);
            float xOff = bar.getXCoordinateForProgress(curr.progress) - bar.getPosition().getX();
            marker.getPosition().aboveLeft(bar, data.downLineLength).setXAlignOffset(xOff - data.size / 2f - 1);

            TooltipCreator tc = getStageTooltip(curr.id);
            if (tc != null) {
                main.addTooltipTo(tc, marker, TooltipLocation.LEFT, false);
            }
        }

        {
            UIComponentAPI marker = main.addEventProgressMarker(this);
            float xOff = bar.getXCoordinateForProgress(progress) - bar.getPosition().getX();
            marker.getPosition().belowLeft(bar, -getBarProgressIndicatorHeight() * 0.5f - 2)
                        .setXAlignOffset(xOff - getBarProgressIndicatorWidth() / 2 - 1);
        }

        main.addSpacer(opad);

        float barW = getBarWidth();

        if (Global.getSettings().isDevMode()) {
            addGenericButton(main, barW, new Color(122, 122, 122, 255), new Color(40, 40, 40, 255),
                    ">> (dev) force next base spawn", BUTTON_DEV_FORCE_SPAWN);
            main.addSpacer(opad);
        }

        float factorWidth = (barW - opad) / 2f;

        FactionAPI faction = getFactionForUIColors();
        Color c = faction.getBaseUIColor();
        Color bg = faction.getDarkUIColor();

        TooltipMakerAPI mFac = main.beginSubTooltip(factorWidth);
        mFac.addSectionHeading("Monthly factors", c, bg, Alignment.MID, opad).getPosition().setXAlignOffset(0);

        float strW = 40f;
        float rh = 20f;
        mFac.beginTable2(faction, rh, false, false,
                "Monthly factors", factorWidth - strW - 3,
                "Progress", strW
                );

        for (EventFactor factor : factors) {
            if (!factor.shouldShow(this)) continue;
            String desc = factor.getDesc(this);
            if (desc != null) {
                mFac.addRowWithGlow(Alignment.LMID, factor.getDescColor(this), desc,
                                    Alignment.RMID, factor.getProgressColor(this), factor.getProgressStr(this));
                TooltipCreator t = factor.getMainRowTooltip(this);
                if (t != null) {
                    mFac.addTooltipToAddedRow(t, TooltipLocation.RIGHT, false);
                }
            }
            factor.addExtraRows(mFac, this);
        }

        mFac.addTable("None", -1, opad);
        mFac.getPrev().getPosition().setXAlignOffset(-5);
        main.endSubTooltip();

        TooltipMakerAPI cFac = main.beginSubTooltip(factorWidth);
        cFac.addSectionHeading("Colonies Captured", c, bg, Alignment.MID, opad).getPosition().setXAlignOffset(0);
        addCapturedColoniesList(cFac, opad);
        cFac.addSectionHeading("Colonies Destroyed", c, bg, Alignment.MID, opad).getPosition().setXAlignOffset(0);
        addDestroyedColoniesList(cFac, opad);
        main.endSubTooltip();

        float factorHeight = Math.max(mFac.getHeightSoFar(), cFac.getHeightSoFar());
        mFac.setHeightSoFar(factorHeight);
        cFac.setHeightSoFar(factorHeight);

        main.addCustom(mFac, opad * 2f);
        main.addCustomDoNotSetPosition(cFac).getPosition().rightOfTop(mFac, opad);

        panel.addUIElement(main).inTL(0, 0);
    }

    private void addCapturedColoniesList(TooltipMakerAPI info, float opad) {
        List<MarketAPI> captured = getCapturedMarkets();
        if (captured.isEmpty()) {
            info.addPara("None yet.", opad);
            return;
        }

        float pad = opad;
        for (MarketAPI market : captured) {
            addCapturedMarketRow(info, market, pad);
            pad = 3f;
        }
    }

    /**
     * Same shape as {@code BaseIntelPlugin.addMarketToList()}, but names
     * {@link XLII_LongsightCrisisManager#PREVIOUS_OWNER_FLAG} instead of the market's current
     * faction - every market here is currently Draconis-owned by definition (see
     * {@link #getCapturedMarkets}), so showing the current faction would just print "Draconis" on
     * every row and tell the player nothing about what was actually lost.
     */
    private void addCapturedMarketRow(TooltipMakerAPI info, MarketAPI market, float pad) {
        String previousOwnerId = market.getMemoryWithoutUpdate().getString(
                XLII_LongsightCrisisManager.PREVIOUS_OWNER_FLAG);
        FactionAPI previousOwner = previousOwnerId != null
                ? Global.getSector().getFaction(previousOwnerId) : null;
        String ownerName = previousOwner != null ? previousOwner.getDisplayName() : "an unknown faction";
        Color ownerColor = previousOwner != null ? previousOwner.getBaseUIColor() : Misc.getGrayColor();

        String indent = INDENT;
        if (info.getBulletedListPrefix() != null) indent = "";

        LabelAPI label = info.addPara(indent + market.getName() + " (size %s, formerly %s)",
                pad, Misc.getTextColor(), ownerColor,
                "" + (int) market.getSize(),
                ownerName);
        label.setHighlight("" + (int) market.getSize(), ownerName);
        label.setHighlightColors(Misc.getHighlightColor(), ownerColor);
    }

    /**
     * Currently-held only - a market tagged {@link XLII_LongsightCrisisManager#CAPTURED_FLAG} that
     * has since been retaken by anyone else no longer passes the Draconis-ownership check, so it
     * drops off this list on its own with no separate liberation bookkeeping needed.
     */
    private List<MarketAPI> getCapturedMarkets() {
        List<MarketAPI> result = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.DRACONIS.equals(market.getFactionId())) continue;
            if (!market.getMemoryWithoutUpdate().getBoolean(XLII_LongsightCrisisManager.CAPTURED_FLAG)) continue;
            result.add(market);
        }
        return result;
    }

    /**
     * Unlike {@link #addCapturedColoniesList}, this reads
     * {@link XLII_LongsightCrisisManager#getDestroyedMarkets()}'s plain snapshot records instead of
     * scanning live markets - a destroyed market's own {@code MarketAPI} is gone from the economy
     * entirely (see that method's own doc), so there is no live object left to list.
     */
    private void addDestroyedColoniesList(TooltipMakerAPI info, float opad) {
        XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
        List<XLII_LongsightCrisisManager.DestroyedMarketRecord> destroyed =
                manager != null ? manager.getDestroyedMarkets() : List.of();
        if (destroyed.isEmpty()) {
            info.addPara("None yet.", opad);
            return;
        }

        float pad = opad;
        for (XLII_LongsightCrisisManager.DestroyedMarketRecord record : destroyed) {
            addDestroyedMarketRow(info, record, pad);
            pad = 3f;
        }
    }

    private void addDestroyedMarketRow(TooltipMakerAPI info,
                                        XLII_LongsightCrisisManager.DestroyedMarketRecord record, float pad) {
        FactionAPI previousOwner = record.previousOwnerId != null
                ? Global.getSector().getFaction(record.previousOwnerId) : null;
        String ownerName = previousOwner != null ? previousOwner.getDisplayName() : "an unknown faction";
        Color ownerColor = previousOwner != null ? previousOwner.getBaseUIColor() : Misc.getGrayColor();

        String indent = INDENT;
        if (info.getBulletedListPrefix() != null) indent = "";

        LabelAPI label = info.addPara(indent + record.name + " (formerly %s)",
                pad, Misc.getTextColor(), ownerColor, ownerName);
        label.setHighlight(ownerName);
        label.setHighlightColors(ownerColor);
    }

    /** Dev-only ({@code BUTTON_DEV_FORCE_SPAWN} only ever appears in dev mode - see
     *  {@link #createLargeDescription}). Forces the manager's own spawn-check interval to elapse
     *  immediately, so the next {@code advance()} it runs attempts a real spawn check right away
     *  instead of waiting out the interval - same idea as {@code DraconisAIOTracker}'s own
     *  "(dev) advance crisis" button, just forcing the timer rather than the progress value, since
     *  that's what this crisis's bar actually tracks (see {@link #advanceImpl}). */
    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_DEV_FORCE_SPAWN) {
            XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
            if (manager != null) manager.getTracker().forceIntervalElapsed();
            ui.updateUIForItem(this);
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }

    /** Intel-list summary line(s) - captured/destroyed colony counts once there's one of either
     *  to report. The active-Bastion count stays in {@link #getName()} only, not duplicated here. */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                    Color tc, float initPad) {
        XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
        if (manager == null) return;

        int captured = getCapturedMarkets().size();
        if (captured > 0) {
            String capturedStr = captured + (captured == 1 ? " colony" : " colonies");
            info.addPara("%s captured so far", initPad, tc,
                    Misc.getNegativeHighlightColor(), capturedStr);
        }

        int destroyed = manager.getDestroyedMarkets().size();
        if (destroyed > 0) {
            String destroyedStr = destroyed + (destroyed == 1 ? " colony" : " colonies");
            info.addPara("%s destroyed so far", initPad, tc,
                    Misc.getNegativeHighlightColor(), destroyedStr);
        }
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_security_codes");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(Factions.INTELLIGENCE_OFFICE);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Factions.INTELLIGENCE_OFFICE);
        tags.add(Tags.INTEL_MILITARY);
        return tags;
    }

    @Override
    public String getName() {
        XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
        int active = manager != null ? manager.getActiveCount() : 0;
        String bastionStr = active + (active == 1 ? " Base" : " Bases");
        return "Office Takeover Crisis - " + bastionStr + " Active";
    }
}
