package levianeer.draconis.data.campaign.intel.events.crisis.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseStoryPointActionDelegate;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StoryPointActionDelegate;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Warning intel for a pending AIO disruption - either an AI-core industry (recurring, on its own
 * cooldown) or a defense station (one-time, ahead of the final punitive expedition). Created instead
 * of disrupting the target immediately - gives the player a number of days to either pay for a
 * security team (chance-based) or spend a story point (guaranteed) to stop it before it happens. If
 * the player does neither before the timer runs out, the operation resolves exactly like the old
 * instant-fire behavior: the target is disrupted and a DraconisAIODisruptionIntel is posted as the
 * aftermath report.
 */
public class DraconisAIOImpendingDisruptionIntel extends BaseIntelPlugin {

    private static final Logger log = Global.getLogger(DraconisAIOImpendingDisruptionIntel.class);

    public static final Object BUTTON_SECURITY_TEAM = "dda_aio_impending_security_team";
    public static final Object BUTTON_STORY_POINT = "dda_aio_impending_story_point";

    private enum Kind { INDUSTRY, STATION }

    private final Kind kind;
    private final MarketAPI market;
    private final String targetId;
    private final String targetNameSnapshot;
    private final float warningDays;
    private final long creationDate;
    private final int progressAtCreation;
    private final float explicitDisruptionDays;

    private DraconisAIOImpendingDisruptionIntel(Kind kind, MarketAPI market, Industry target, float warningDays,
                                                 int progressAtCreation, float explicitDisruptionDays) {
        this.kind = kind;
        this.market = market;
        this.targetId = target.getId();
        this.targetNameSnapshot = target.getCurrentName();
        this.warningDays = warningDays;
        this.progressAtCreation = progressAtCreation;
        this.explicitDisruptionDays = explicitDisruptionDays;
        this.creationDate = Global.getSector().getClock().getTimestamp();
        Global.getSector().getIntelManager().addIntel(this, false); // false = show notification popup
        Global.getSector().addScript(this);
    }

    /**
     * The recurring AI-core industry case. If not stopped, the eventual disruption duration scales
     * with crisis progress at the time the warning fired (progress=25 -> min duration, 100 -> max).
     */
    public static DraconisAIOImpendingDisruptionIntel forIndustry(MarketAPI market, Industry target,
                                                                   float warningDays, int progressAtCreation) {
        return new DraconisAIOImpendingDisruptionIntel(Kind.INDUSTRY, market, target, warningDays, progressAtCreation, 0f);
    }

    /**
     * The one-time pre-invasion defense station case. The disruption duration (if not stopped) is
     * supplied explicitly by the caller, which sizes it to still cover the expedition's arrival.
     */
    public static DraconisAIOImpendingDisruptionIntel forStation(MarketAPI market, Industry station,
                                                                  float warningDays, float disruptionDaysIfNotStopped) {
        return new DraconisAIOImpendingDisruptionIntel(Kind.STATION, market, station, warningDays, 0, disruptionDaysIfNotStopped);
    }

    /**
     * Returns the first pending impending-disruption intel of either kind, if any. The recurring
     * industry case is single-flight by construction (see DraconisAIOTracker's cooldown check); the
     * one-time station case can in principle overlap it, in which case only one of the two is
     * returned here - acceptable since the station case only ever fires once, at the final invasion.
     */
    public static DraconisAIOImpendingDisruptionIntel get() {
        return (DraconisAIOImpendingDisruptionIntel) Global.getSector().getIntelManager()
                .getFirstIntel(DraconisAIOImpendingDisruptionIntel.class);
    }

    private float getSetting(String key, float defaultValue) {
        try {
            return Global.getSettings().getFloat(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private float getDaysRemaining() {
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(creationDate);
        return Math.max(0f, warningDays - elapsed);
    }

    private Industry getTarget() {
        return market.getIndustry(targetId);
    }

    /** Scales with what the targeted industry/station itself costs to build - a bigger investment is worth more to protect. */
    private int getSecurityTeamCost() {
        Industry target = getTarget();
        float buildCost = target != null ? target.getBuildCost() : 0f;
        float fraction = getSetting("draconisAIOSecurityTeamCostFraction", 0.15f);
        return Math.round(fraction * buildCost);
    }

    // ==================== Lifecycle ====================

    @Override
    public String getName() {
        return targetNameSnapshot + AIOStrings.INTEL_NAME_IMPENDING_SUFFIX;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_security_codes");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(DRACONIS);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_COLONIES);
        tags.add(DRACONIS);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return market.getPrimaryEntity();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    /** Drives the countdown via timestamp comparison, same pattern as DraconisAIODisruptionIntel. */
    @Override
    public boolean shouldRemoveIntel() {
        if (isEnded()) return true;
        if (getDaysRemaining() <= 0f) {
            resolve(false);
            return true;
        }
        return false;
    }

    @Override
    public String getSortString() {
        return "Colonies";
    }

    // ==================== Resolution ====================

    /**
     * @param stopped whether the operation was prevented (security team success or story point spend).
     */
    private void resolve(boolean stopped) {
        if (isEnded()) return;

        if (stopped) {
            endImmediately();
            return;
        }

        Industry target = getTarget();
        if (target == null || target.isDisrupted()) {
            // Target was removed, demolished, or otherwise already disrupted since the warning fired.
            log.info("DDA: Impending disruption at " + market.getName() + " resolved with no valid target");
            endImmediately();
            return;
        }

        float days;
        if (kind == Kind.STATION) {
            days = explicitDisruptionDays;
        } else {
            float minD = getSetting("draconisAIODisruptionDaysMin", 12f);
            float maxD = getSetting("draconisAIODisruptionDaysMax", 60f);
            float t = Math.max(0f, Math.min(1f, (progressAtCreation - 25f) / 75f));
            days = minD + t * (maxD - minD);
        }

        target.setDisrupted(days);
        log.info("DDA: Disrupted " + target.getCurrentName() + " at " + market.getName()
                + " for " + Math.round(days) + " days (impending operation not stopped)");

        new DraconisAIODisruptionIntel(market, target.getCurrentName(), Math.round(days));
        endImmediately();
    }

    private void resolveSecurityTeamOutcome(IntelUIAPI ui) {
        int cost = getSecurityTeamCost();
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo.getCredits().get() < cost) {
            Global.getSector().getCampaignUI().addMessage(
                    AIOStrings.IMPENDING_SECURITY_INSUFFICIENT_FUNDS, Misc.getNegativeHighlightColor());
            return;
        }

        cargo.getCredits().subtract(cost);
        float chance = getSetting("draconisAIOSecurityTeamSuccessChance", 0.7f);
        boolean success = Math.random() < chance;

        if (success) {
            Global.getSector().getCampaignUI().addMessage(
                    String.format(AIOStrings.IMPENDING_STOPPED_SECURITY_FMT, market.getName()),
                    Misc.getPositiveHighlightColor());
        } else {
            Global.getSector().getCampaignUI().addMessage(
                    String.format(AIOStrings.IMPENDING_FAILED_SECURITY_FMT, market.getName()),
                    Misc.getNegativeHighlightColor());
        }

        resolve(success);
        ui.recreateIntelUI();
    }

    private void resolveStoryPointOutcome(IntelUIAPI ui) {
        Global.getSector().getCampaignUI().addMessage(
                String.format(AIOStrings.IMPENDING_STOPPED_STORYPOINT_FMT, market.getName()),
                Misc.getPositiveHighlightColor());
        resolve(true);
        ui.recreateIntelUI();
    }

    // ==================== Panel ====================

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                    Color tc, float initPad) {
        Color bad = Misc.getNegativeHighlightColor();
        info.addPara(AIOStrings.DISRUPTION_BULLET_FMT, initPad, tc, bad, market.getName());
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        float opad = 10f;

        FactionAPI dda = Global.getSector().getFaction(DRACONIS);
        if (dda != null) {
            info.addImage(dda.getLogo(), width, 128, opad);
        }

        String para1Fmt = kind == Kind.STATION
                ? AIOStrings.IMPENDING_DESC_PARA1_STATION_FMT
                : AIOStrings.IMPENDING_DESC_PARA1_FMT;
        LabelAPI para = info.addPara(para1Fmt, opad, h, targetNameSnapshot, market.getName());
        para.setHighlightColors(bad, h);

        info.addPara(AIOStrings.IMPENDING_DESC_PARA2, opad);

        info.addPara(AIOStrings.IMPENDING_DAYS_REMAINING_FMT, opad, h,
                String.valueOf(Math.round(getDaysRemaining())));

        addGenericButton(info, width, AIOStrings.IMPENDING_BUTTON_SECURITY, BUTTON_SECURITY_TEAM);
        addGenericButton(info, width, AIOStrings.IMPENDING_BUTTON_STORY_POINT, BUTTON_STORY_POINT);
    }

    // ==================== Button handling ====================

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (buttonId == BUTTON_SECURITY_TEAM) return true;
        return super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (buttonId == BUTTON_SECURITY_TEAM) {
            float chance = getSetting("draconisAIOSecurityTeamSuccessChance", 0.7f);
            prompt.addPara(AIOStrings.IMPENDING_SECURITY_CONFIRM_FMT, 0f,
                    Misc.getHighlightColor(), Misc.getHighlightColor(),
                    Misc.getDGSCredits(getSecurityTeamCost()), String.valueOf(Math.round(chance * 100f)));
            return;
        }
        super.createConfirmationPrompt(buttonId, prompt);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_SECURITY_TEAM) {
            resolveSecurityTeamOutcome(ui);
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }

    @Override
    public StoryPointActionDelegate getButtonStoryPointActionDelegate(Object buttonId) {
        if (buttonId == BUTTON_STORY_POINT) {
            return new BaseStoryPointActionDelegate() {
                @Override
                public void createDescription(TooltipMakerAPI info) {
                    info.setParaInsigniaLarge();
                    info.addPara(AIOStrings.IMPENDING_STORYPOINT_DESC_FMT, 0f,
                            Misc.getHighlightColor(), Misc.getHighlightColor(),
                            targetNameSnapshot, market.getName());
                }

                @Override
                public String getLogText() {
                    return String.format(AIOStrings.IMPENDING_STORYPOINT_LOG_FMT, market.getName());
                }
            };
        }
        return super.getButtonStoryPointActionDelegate(buttonId);
    }

    @Override
    public void storyActionConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_STORY_POINT) {
            resolveStoryPointOutcome(ui);
            return;
        }
        super.storyActionConfirmed(buttonId, ui);
    }
}
