package levianeer.draconis.data.campaign.intel.fafnir;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
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
 *   <li>TT Courier: {@code XLII_FafnirKoriArrivalDialogPlugin} (unconditional on either option)</li>
 *   <li>Ring-Port: {@code XLII_FafnirRingPortDeliveryDialogPlugin} (OPT_DELIVER only)</li>
 * </ul>
 * Brute force and ungated paths produce no intel entry.
 */
public class FafnirAccessMissionIntel extends BaseIntelPlugin {

    private final String path;
    private boolean completed = false;
    private long completionTime = -1;

    // =========================================================================
    // Construction / static accessor
    // =========================================================================

    public FafnirAccessMissionIntel(String path) {
        this.path = path;
        Global.getSector().getIntelManager().addIntel(this, false);
        Global.getSector().addScript(this);
        setImportant(true);
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
        endAfterDelay();
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
        Global.getSector().removeScript(this);
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

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MISSIONS);
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

        // Faction logo banner
        if (faction != null) {
            info.addImages(width, 128, opad, opad, faction.getLogo());
        }

        // "You've accepted a [faction] contract to deliver [objective] to [destination],
        //  which is under [DDA] control."
        boolean isTT = FafnirAccessStrings.PATH_TT_COURIER.equals(path);
        String factionPost   = Factions.PIRATES.equals(faction != null ? faction.getId() : "") ? "-affiliated" : "";
        String factionPrefix = (faction != null ? faction.getPersonNamePrefix() : "") + factionPost;
        String article       = faction != null ? faction.getPersonNamePrefixAOrAn() : "a";
        String objective     = isTT ? FafnirAccessStrings.INTEL_OBJECTIVE_TT   : FafnirAccessStrings.INTEL_OBJECTIVE_RP;
        String destination   = isTT ? FafnirAccessStrings.INTEL_DESTINATION_TT : FafnirAccessStrings.INTEL_DESTINATION_RP;
        String ddaPrefix     = dda != null ? dda.getPersonNamePrefix() : "Alliance";

        LabelAPI label = info.addPara(
                "You've accepted " + article + " " + factionPrefix
                + " contract to deliver " + objective + " to " + destination
                + ", which is under " + ddaPrefix + " control.",
                opad, faction != null ? faction.getBaseUIColor() : Misc.getHighlightColor(), factionPrefix);
        label.setHighlight(factionPrefix, ddaPrefix);
        label.setHighlightColors(
                faction != null ? faction.getBaseUIColor() : Misc.getHighlightColor(),
                dda     != null ? dda.getBaseUIColor()     : Misc.getHighlightColor());

        // Indented bullets: destination + reward (active) or payment received (completed)
        addBulletPoints(info, ListInfoMode.IN_DESC);

        // Closing instruction / status line
        if (completed) {
            info.addPara(FafnirAccessStrings.INTEL_DELIVERY_CONFIRMED, opad);
        } else {
            String instruction = isTT
                    ? FafnirAccessStrings.INTEL_INSTRUCTION_TT
                    : FafnirAccessStrings.INTEL_INSTRUCTION_RP;
            info.addPara(instruction, opad);
        }
    }
}