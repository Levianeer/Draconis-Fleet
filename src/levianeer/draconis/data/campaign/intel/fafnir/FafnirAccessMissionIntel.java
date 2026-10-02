package levianeer.draconis.data.campaign.intel.fafnir;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Set;


/**
 * Intel entry tracking an active Fafnir access mission (TT Courier or Ring-Port Contractor path).
 * <p>
 * Created when the player accepts a Fafnir bar event. Completed - and the credit reward paid -
 * when the corresponding post-entry delivery dialog is resolved:
 * <ul>
 *   <li>TT Courier: rules.csv "# Kori Arrival" section, via {@code XLII_BeginFafnirMission complete}
 *       (unconditional on either option)</li>
 *   <li>Ring-Port: rules.csv "# Ring-Port Delivery" section, via {@code XLII_BeginFafnirMission complete}
 *       (deliver option only)</li>
 * </ul>
 * Brute force and ungated paths produce no intel entry.
 */
public class FafnirAccessMissionIntel extends BaseIntelPlugin {

    private final String path;
    private boolean completed = false;
    private long completionTime = -1;

    /**
     * Not serialised: forces {@link #syncObjectiveMarker} to run once per game load, which is what
     * puts the objective marker onto saves whose mission was accepted before markers existed.
     */
    private transient boolean markerSynced = false;

    // =========================================================================
    // Construction / static accessor
    // =========================================================================

    public FafnirAccessMissionIntel(String path) {
        this.path = path;
        Global.getSector().getIntelManager().addIntel(this, false);
        Global.getSector().addScript(this);
        setImportant(true);
        syncObjectiveMarker();
    }

    /**
     * Returns the active (non-ended) instance, or null if none exists.
     * Safe to call any time after the sector is initialised.
     */
    public static FafnirAccessMissionIntel get() {
        IntelInfoPlugin found = Global.getSector().getIntelManager()
                .getFirstIntel(FafnirAccessMissionIntel.class);
        if (found instanceof FafnirAccessMissionIntel f && !f.isEnded()) return f;
        return null;
    }

    // =========================================================================
    // Completion
    // =========================================================================

    /**
     * Pay the credit reward and mark this mission complete.
     * Safe to call multiple times; only the first call pays credits.
     */
    public void complete() {
        if (completed) return;
        completed = true;
        completionTime = Global.getSector().getClock().getTimestamp();
        float reward = FafnirAccessStrings.PATH_TT_COURIER.equals(path)
                ? FafnirAccessStrings.REWARD_TT
                : FafnirAccessStrings.REWARD_RP;
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(reward);
        sendUpdateIfPlayerHasIntel(null, false);
        clearObjectiveMarker();
        endAfterDelay();
    }

    /**
     * Runs {@link #syncObjectiveMarker} once per game load - see {@link #markerSynced}.
     * Cheap: one memory read, then a no-op for the rest of the session.
     */
    @Override
    protected void advanceImpl(float amount) {
        if (markerSynced) return;
        markerSynced = true;
        if (!completed) syncObjectiveMarker();
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        return 30f;
    }

    /**
     * Double-check expiry: primary path is the {@code endAfterDelay()} timer via {@code isEnded()};
     * fallback is a direct date comparison in case the timer gets stuck (same pattern used in
     * {@code DraconisAICoreTheftIntel}).
     */
    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        clearObjectiveMarker();
        Global.getSector().removeScript(this);
    }

    // =========================================================================
    // Objective marker
    // =========================================================================

    /**
     * Reason key for {@code MemFlags.ENTITY_MISSION_IMPORTANT}. Only one access mission can be
     * active at a time, so a constant is safe.
     */
    private static final String MARKER_REASON = "XLII_fafnirAccessMission";

    /**
     * Flags the delivery destination as a mission objective. This - not {@link #getMapLocation} -
     * is what draws the marker on the target in the campaign and system views; {@code getMapLocation}
     * only tells the intel screen where to pan. {@code BaseHubMission.makeImportant} does the same
     * thing for the Blind Eye questline.
     */
    private void syncObjectiveMarker() {
        MarketAPI market = getDestinationMarket();
        if (market == null) return;
        Misc.makeImportant(market.getMemoryWithoutUpdate(), MARKER_REASON);
    }

    /** Removes the objective marker. Safe to call when it was never set. */
    private void clearObjectiveMarker() {
        MarketAPI market = getDestinationMarket();
        if (market == null) return;
        Misc.makeUnimportant(market.getMemoryWithoutUpdate(), MARKER_REASON);
    }

    @Override
    public boolean shouldRemoveIntel() {
        if (isEnded()) return true;
        if (completed && completionTime > 0) {
            float daysSince = Global.getSector().getClock().getElapsedDaysSince(completionTime);
            if (daysSince > getBaseDaysAfterEnd()) {
                endImmediately(); // triggers notifyEnded() -> removeScript
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // BaseIntelPlugin - display
    // =========================================================================

    @Override
    public String getName() {
        return FafnirAccessStrings.PATH_TT_COURIER.equals(path)
                ? FafnirAccessStrings.INTEL_NAME_TT
                : FafnirAccessStrings.INTEL_NAME_RP;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_smuggling");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        String factionId = FafnirAccessStrings.PATH_TT_COURIER.equals(path)
                ? Factions.TRITACHYON
                : Factions.PIRATES;
        return Global.getSector().getFaction(factionId);
    }

    /** Market id of the delivery destination for each path. */
    private static final String KORI_MARKET_ID      = "kori_market";
    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    /**
     * Points the intel entry's map marker at the delivery destination - Kori on the TT Courier
     * path, Ring-Port on the contractor path. Both sit inside Fafnir, so the marker doubles as
     * a pointer at the system the player is being paid to reach.
     */
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        MarketAPI market = getDestinationMarket();
        return market != null ? market.getPrimaryEntity() : null;
    }

    private MarketAPI getDestinationMarket() {
        String marketId = FafnirAccessStrings.PATH_TT_COURIER.equals(path)
                ? KORI_MARKET_ID
                : RING_PORT_MARKET_ID;
        return Global.getSector().getEconomy().getMarket(marketId);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add(Tags.INTEL_ACCEPTED);
        tags.add(Tags.INTEL_STORY);
        tags.add(FafnirAccessStrings.PATH_TT_COURIER.equals(path) ? Factions.TRITACHYON : Factions.PIRATES);
        return tags;
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                   Color tc, float initPad) {
        Color h   = Misc.getHighlightColor();
        Color pos = Misc.getPositiveHighlightColor();

        boolean isTT     = FafnirAccessStrings.PATH_TT_COURIER.equals(path);
        float reward     = isTT ? FafnirAccessStrings.REWARD_TT : FafnirAccessStrings.REWARD_RP;
        String rewardStr = Misc.getDGSCredits((long) reward);

        if (isUpdate) {
            if (completed) {
                info.addPara(FafnirAccessStrings.INTEL_PAID_LINE, initPad, tc, pos, rewardStr);
            }
        } else if (completed) {
            info.addPara(FafnirAccessStrings.INTEL_PAID_LINE, initPad, tc, pos, rewardStr);
        } else {
            String destination = isTT
                    ? FafnirAccessStrings.INTEL_DESTINATION_TT
                    : FafnirAccessStrings.INTEL_DESTINATION_RP;
            FactionAPI dda = Global.getSector().getFaction("XLII_draconis");
            info.addPara(destination, initPad, tc, dda.getBaseUIColor(), destination);
            info.addPara(FafnirAccessStrings.INTEL_REWARD_LINE, 0f, tc, h, rewardStr);
        }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        FactionAPI faction = getFactionForUIColors();
        FactionAPI dda     = Global.getSector().getFaction("XLII_draconis");

        if (faction != null) {
            info.addImages(width, 128, opad, opad, faction.getLogo());
        }

        // Provenance line - who's actually offering this, before the player's own recap of the deal.
        // Neither contact is named (see TT_BAR_SCENE/RP_BAR_SCENE), so this names the faction/role
        // instead of a person, matching vanilla's "Contract given by X, affiliated with Y" template.
        boolean isTT = FafnirAccessStrings.PATH_TT_COURIER.equals(path);
        String factionPost   = Factions.PIRATES.equals(faction != null ? faction.getId() : "") ? "-affiliated" : "";
        String factionPrefix = (faction != null ? faction.getPersonNamePrefix() : "") + factionPost;
        String article       = faction != null ? faction.getPersonNamePrefixAOrAn() : "a";

        LabelAPI provenance = info.addPara(
                "Contract offered by " + article + " " + factionPrefix + " contact, brokered at a Fafnir bar.",
                opad, faction != null ? faction.getBaseUIColor() : Misc.getHighlightColor(), factionPrefix);
        provenance.setHighlight(factionPrefix);
        provenance.setHighlightColors(faction != null ? faction.getBaseUIColor() : Misc.getHighlightColor());

        // "You've accepted a [faction] contract to deliver [objective] to [destination],
        //  which is under [DDA] control."
        String objective     = isTT ? FafnirAccessStrings.INTEL_OBJECTIVE_TT   : FafnirAccessStrings.INTEL_OBJECTIVE_RP;
        String destination   = isTT ? FafnirAccessStrings.INTEL_DESTINATION_TT : FafnirAccessStrings.INTEL_DESTINATION_RP;
        String ddaPrefix     = dda != null ? dda.getPersonNamePrefix() : "Alliance";

        LabelAPI label = info.addPara(
                "You've accepted " + article + " " + factionPrefix
                + " contract to deliver " + objective + " to " + destination
                + ", which is under " + ddaPrefix + " control.",
                0f, faction != null ? faction.getBaseUIColor() : Misc.getHighlightColor(), factionPrefix);
        label.setHighlight(factionPrefix, ddaPrefix);
        label.setHighlightColors(
                faction != null ? faction.getBaseUIColor() : Misc.getHighlightColor(),
                dda     != null ? dda.getBaseUIColor()     : Misc.getHighlightColor());

        // Indented bullets: destination + reward (active) or payment received (completed)
        addBulletPoints(info, ListInfoMode.IN_DESC);

        if (completed) {
            info.addPara(FafnirAccessStrings.INTEL_DELIVERY_CONFIRMED, opad);
        } else {
            String instruction = isTT
                    ? FafnirAccessStrings.INTEL_INSTRUCTION_TT
                    : FafnirAccessStrings.INTEL_INSTRUCTION_RP;
            info.addPara(instruction, opad);
        }

        addDeleteButton(info, width, FafnirAccessStrings.INTEL_DELETE_BUTTON);
    }

    /**
     * Deleting an uncompleted contract forfeits it: {@link #get()} skips ended intel, so the
     * delivery dialogs find nothing to complete and pay out. {@code notifyEnded()} clears the
     * objective marker either way.
     */
    @Override
    protected void createDeleteConfirmationPrompt(TooltipMakerAPI prompt) {
        prompt.addPara(completed
                        ? FafnirAccessStrings.INTEL_DELETE_CONFIRM_DONE
                        : FafnirAccessStrings.INTEL_DELETE_CONFIRM_ACTIVE,
                Misc.getTextColor(), 0f);
    }
}