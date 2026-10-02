package levianeer.draconis.data.campaign.intel.longsight;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission.GlobalBooleanChecker;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.companion.KorrinCompanion;
import levianeer.draconis.data.campaign.events.XLII_KoriInfiltration;
import levianeer.draconis.data.campaign.events.XLII_SectorTourListener;

import java.awt.Color;
import java.util.Set;

/**
 * Quest tracker for the sector tour and Kori archive infiltration - the bridge stretch between
 * {@link OfficeReferralMission}'s nanoforge delivery and {@link LongsightQuestMission}'s
 * Longsight contact and decision hub. Covers the four vanilla endgame/eldritch site visits with
 * Elias Korrin aboard (Remnants, Omega, the Threat, the Shrouded Dweller) and the closing Kori
 * archive dig.
 * <p>
 * Split from what used to be a single stage ({@code LongsightQuestMission.Stage.LONGSIGHT_CONTACT})
 * inside the old combined tracker. The tour itself has no new combat content; every site is a
 * pre-existing vanilla encounter, detected by
 * {@link levianeer.draconis.data.campaign.events.XLII_SectorTourListener} counting up
 * {@code $global.XLII_tourStopsComplete}. All new content is Elias's reaction (rules.csv,
 * {@code korrin_topics.csv}) plus the archive infiltration itself
 * ({@link XLII_KoriInfiltration}).
 * <p>
 * Created via {@code BeginMission ShapesOfOldMission} from {@code XLII_nanoforge_deliver_success} -
 * the same row that sets {@code $global.XLII_nanoforgeQuestComplete}, which is also
 * {@code OfficeReferralMission}'s own completion flag. Ends on
 * {@code $global.XLII_koriInvestigationRaised}, set by the same rules.csv row
 * ({@code XLII_kori_longsight_warning_dock}, a {@code MarketPostDock} row) that creates
 * {@code LongsightQuestMission} - so only one of these three trackers is ever the active,
 * story-important intel entry at a time.
 * <p>
 * The archive dig itself is no longer a standalone event that finishes before this handoff - it's
 * folded into the merged Burn the Machine raid ({@code XLII_KoriStrike}/{@code
 * XLII_ResolveBurnRaid}), which now runs at the endgame, after Longsight's own contact scene.
 * This tracker ends when Elias's lead is in hand and the player docks at Kori (see
 * {@code Stage.ARCHIVE} and {@code XLII_kori_longsight_warning_dock} in rules.csv), not when the
 * dig itself finishes.
 * <p>
 * Stage transitions use {@code setStageOnCustomCondition} rather than
 * {@code setStageOnGlobalFlag} - see {@code BlindEyeQuestMission.create()} for why
 * {@code setStageOnGlobalFlag} silently unsets every flag it watches the instant this mission
 * reaches {@code Stage.COMPLETED}, which would break every rules.csv gate downstream of these
 * permanent, player-facing flags.
 */
public class ShapesOfOldMission extends HubMissionWithBarEvent {

    public enum Stage {
        /** Visit the four sites with Elias aboard, in any order. */
        TOUR,
        /** Elias has offered the Kori archive dig. Dock at Kori to act on it - Longsight intercepts the moment you do, before August ever gets a say. */
        ARCHIVE,
        /** Raised with August. What follows is Longsight's. */
        COMPLETED,
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$XLII_shapesOfOld_missionRef")) return false;

        setStartingStage(determineStartingStage());
        setSuccessStage(Stage.COMPLETED);
        setNoAbandon();
        setStoryMission();
        setImportant(true);

        setStageOnCustomCondition(Stage.ARCHIVE,   new GlobalBooleanChecker("$XLII_koriInfiltrationOffered"));
        setStageOnCustomCondition(Stage.COMPLETED, new GlobalBooleanChecker("$XLII_koriInvestigationRaised"));

        return true;
    }

    /**
     * Both stages are deliberately unmarked - the four tour sites have no fixed location (any
     * encounter with the right faction counts, same reasoning as
     * {@code OfficeReferralMission.Stage.FIND_NANOFORGE}), and the archive dig is offered and run
     * through Elias as a companion topic aboard the fleet, not at a dockable market.
     */
    private Stage determineStartingStage() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        if (g.getBoolean("$XLII_koriInvestigationRaised")) return Stage.COMPLETED;
        if (g.getBoolean("$XLII_koriInfiltrationOffered")) return Stage.ARCHIVE;
        return Stage.TOUR;
    }

    @Override
    public String getBaseName() {
        return "Shapes of Old";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_smuggling");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add(Tags.INTEL_IMPORTANT);
        tags.add(Tags.INTEL_STORY);
        return tags;
    }

    @Override
    public String getPostfixForState() {
        if (startingStage != null) return "";
        return super.getPostfixForState();
    }

    // =========================================================================
    // Delete entry
    // =========================================================================

    private static final String QUEST_OVERVIEW =
            "Elias Korrin believes the fabrication marks on Kori's new forge didn't come from "
                    + "anywhere in the Alliance's own history. Confirming it means comparing them "
                    + "against everything else in the Sector nobody has a category for.";

    /**
     * Korrin's portrait, paired with the Neutral crest rather than Draconis' own - this is his
     * personal initiative, run off the books, not Alliance business.
     * <p>
     * {@code getPerson()} is null here: {@code BeginMission} only sets {@code personOverride} from
     * {@code dialog.getInteractionTarget().getActivePerson()}, and this mission starts from a
     * conversation whose interaction target is the player's own fleet, not Korrin (see
     * {@code KorrinIntel.openTalkDialog()}). So {@code BaseHubMission}'s own portrait+crest block
     * never fires; this substitutes for it directly, after the overview text rather than before,
     * to match how that block renders for sibling missions that do get a person override (e.g.
     * Chain of Custody: August's "given by" line and portrait also render after its leading
     * overview paragraph, since that text is added before delegating to
     * {@code super.createSmallDescription()}).
     */
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addPara(QUEST_OVERVIEW, Misc.getTextColor(), 0f);

        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin != null) {
            info.addImages(width, 128, opad, opad,
                    korrin.getPortraitSprite(),
                    Global.getSector().getFaction(Factions.NEUTRAL).getCrest());
        }

        super.createSmallDescription(info, width, height);
        addDeleteButton(info, width, "Delete entry");
    }

    @Override
    protected void createDeleteConfirmationPrompt(TooltipMakerAPI prompt) {
        prompt.addPara("Deleting this entry removes the tracker only - the questline itself is "
                        + "unaffected and can still be completed. It cannot be restored.",
                Misc.getTextColor(), 0f);
    }

    // =========================================================================
    // Player-facing guidance
    // =========================================================================

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == null) return false;
        switch ((Stage) currentStage) {
            case TOUR:
                info.addPara("Investigate the Sector's old-world sites with Elias aboard ("
                        + getTourStopsComplete() + " of 4)", tc, pad);
                return true;
            case ARCHIVE:
                info.addPara("Return to Kori", tc, pad);
                return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage == null) return;
        float opad = 10f;
        switch ((Stage) currentStage) {
            case TOUR:
                info.addPara("Elias thinks the fabrication marks on the forge point somewhere - "
                        + "Remnant salvage, the Omega, the Threat, whatever else is still out past "
                        + "the charts. Talk to him aboard the fleet about where to start.", opad);
                info.addPara(getTourStopsComplete() + " of 4 sites investigated.", 5f);
                break;
            case ARCHIVE:
                info.addPara("Elias Korrin has a way into Kori's own archive that doesn't go "
                        + "through the Office. Dock at Kori to act on it - whatever happens next "
                        + "won't wait for a word with August.", opad);
                break;
        }
    }

    private int getTourStopsComplete() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(XLII_SectorTourListener.MEM_STOPS_COMPLETE);
    }
}
