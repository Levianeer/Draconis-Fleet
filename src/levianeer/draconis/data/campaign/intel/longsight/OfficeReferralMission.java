package levianeer.draconis.data.campaign.intel.longsight;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission.GlobalBooleanChecker;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.blind_eye.XLII_OfficeContactMonitor;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeSystem;

import java.awt.Color;
import java.util.Set;

/**
 * Quest tracker picking up exactly where Blind Eye leaves off: August's referral to the
 * Office, Monroe's vetting, and the nanoforge exchange. Ends at delivery -
 * {@link ShapesOfOldMission} picks up the sector tour and Kori archive infiltration from
 * there, and {@link LongsightQuestMission} picks up the Longsight contact, decision hub,
 * and ending after that.
 * <p>
 * Split from a single tracker that used to span the whole stretch from referral to ending, so
 * only one story-important intel entry is ever active at a time instead of two overlapping
 * trackers narrating the same content.
 * <p>
 * Created via {@code BeginMission OfficeReferralMission} from
 * {@code XLII_nanoforge_discuss_eligible}, replacing that beat's old direct
 * {@code $XLII_officeReferralPending} set. Every gate past that point is still a global flag
 * read by rules.csv or Java; this is a tracker, not new logic.
 * <p>
 * Stage transitions use {@code setStageOnCustomCondition} rather than
 * {@code setStageOnGlobalFlag} - see {@code BlindEyeQuestMission.create()} for why
 * {@code setStageOnGlobalFlag} silently unsets every flag it watches the instant this mission
 * reaches {@code Stage.COMPLETED}, which would break every rules.csv gate downstream of these
 * permanent, player-facing flags.
 */
public class OfficeReferralMission extends HubMissionWithBarEvent {

    public enum Stage {
        /** August has deferred to the Office. Await contact, then speak with Director Monroe. */
        MEET_THE_DIRECTOR,
        /** Monroe's vetting is done. Report back to Fleet Admiral August at Kori. */
        RETURN_FROM_DIRECTOR,
        /** Locate a Pristine Nanoforge. */
        FIND_NANOFORGE,
        /** Return to Fleet Admiral August with the Pristine Nanoforge. */
        DELIVER_NANOFORGE,
        /** Forge delivered. What follows is Shapes of Old's. */
        COMPLETED,
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$XLII_officeReferral_missionRef")) return false;

        setStartingStage(determineStartingStage());
        setSuccessStage(Stage.COMPLETED);
        setNoAbandon();
        setStoryMission();
        setImportant(true);

        setStageOnCustomCondition(Stage.RETURN_FROM_DIRECTOR, new GlobalBooleanChecker("$XLII_officeVettingComplete"));
        setStageOnCustomCondition(Stage.FIND_NANOFORGE,       new GlobalBooleanChecker("$XLII_nanoforgeQuestOffered"));
        setStageOnCustomCondition(Stage.COMPLETED,            new GlobalBooleanChecker("$XLII_nanoforgeQuestComplete"));

        addStageMarkers();

        return true;
    }

    private static final String ADMIRAL_ID = "XLII_fleet_admiral_emil";

    /**
     * August only - the Bastion's marker is registered separately, once the Office actually
     * delivers Ladon's coordinates (see {@link #syncBastionMarker}). {@code FIND_NANOFORGE} is
     * deliberately unmarked - there is no fixed location for a salvage hunt.
     */
    private void addStageMarkers() {
        PersonAPI august = Global.getSector().getImportantPeople().getPerson(ADMIRAL_ID);
        if (august != null) {
            makeImportant(august, null,
                    Stage.RETURN_FROM_DIRECTOR,
                    Stage.DELIVER_NANOFORGE);
        }
    }

    /**
     * Not serialised: forces {@link #syncBastionMarker} to run once per game load, backfilling
     * saves where the Office contact fired before this existed. Same pattern as
     * {@code FafnirAccessMissionIntel.markerSynced}.
     */
    private transient boolean bastionMarkerSynced = false;

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        updateStageFixups();
        syncBastionMarker();
    }

    @Override
    protected void updateInteractionDataImpl() {
        updateStageFixups();
        syncBastionMarker();
    }

    /** FIND_NANOFORGE -> DELIVER_NANOFORGE isn't a single global flag, so it can't use setStageOnCustomCondition. */
    private void updateStageFixups() {
        if (currentStage == Stage.FIND_NANOFORGE && hasNanoforgeInCargo()) {
            setCurrentStage(Stage.DELIVER_NANOFORGE, null, null);
        }
    }

    /**
     * Registers the Bastion as {@code MEET_THE_DIRECTOR}'s objective marker/map location, but only
     * once {@code XLII_OfficeContactMonitor.CONTACT_DONE_FLAG} fires - Ladon's location is a secret
     * the Office hands over, not something the player already knows, and registering it
     * unconditionally in {@code create()} revealed it (and lit "Show on map") before any courier
     * delivered the coordinates. {@code makeImportant(...)} feeds both the on-map pointer and
     * {@code getMapLocation()} together (see {@code .claude/systems/intel.md}'s "BeginMission
     * auto-supplies a portrait+crest" section), so gating registration itself is the only lever.
     * <p>
     * Guarded by {@link #bastionMarkerSynced} so this only registers once - calling
     * {@code makeImportant} again every frame/interaction would duplicate the entry.
     */
    private void syncBastionMarker() {
        if (bastionMarkerSynced) return;
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(XLII_OfficeContactMonitor.CONTACT_DONE_FLAG)) return;
        bastionMarkerSynced = true;

        StarSystemAPI officeSystem = Global.getSector().getStarSystem(XLII_OfficeSystem.SYSTEM_ID);
        if (officeSystem == null) return;
        SectorEntityToken bastion = officeSystem.getEntityById(XLII_OfficeSystem.BASTION_ID);
        if (bastion == null) return;
        makeImportant(bastion, null, Stage.MEET_THE_DIRECTOR);
    }

    /**
     * Checks existing global flags in reverse quest order to find the most-advanced completed
     * state. Called once during {@link #create} to handle mid-questline saves.
     */
    private Stage determineStartingStage() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        if (g.getBoolean("$XLII_nanoforgeQuestComplete"))                  return Stage.COMPLETED;
        if (g.getBoolean("$XLII_nanoforgeQuestOffered"))
            return hasNanoforgeInCargo() ? Stage.DELIVER_NANOFORGE : Stage.FIND_NANOFORGE;
        if (g.getBoolean("$XLII_officeVettingComplete"))                   return Stage.RETURN_FROM_DIRECTOR;
        return Stage.MEET_THE_DIRECTOR;
    }

    @Override
    public String getBaseName() {
        return "Chain of Custody";
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
            "Fleet Admiral August has referred you to the Alliance Intelligence Office for a "
                    + "matter he will not discuss on his own authority. What follows depends on how "
                    + "far you are willing to go once you understand what it actually is.";

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(QUEST_OVERVIEW, Misc.getTextColor(), 0f);
        super.createSmallDescription(info, width, height);
        addDeleteButton(info, width, "Delete entry");
    }

    @Override
    protected void createDeleteConfirmationPrompt(TooltipMakerAPI prompt) {
        prompt.addPara("Deleting this entry removes the tracker only - the questline itself is "
                        + "unaffected and can still be completed. It cannot be restored.",
                Misc.getTextColor(), 0f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_DELETE) {
            clearStageMarkers();
        }
        super.buttonPressConfirmed(buttonId, ui);
    }

    private void clearStageMarkers() {
        PersonAPI august = Global.getSector().getImportantPeople().getPerson(ADMIRAL_ID);
        if (august != null) makeUnimportant(august);

        StarSystemAPI officeSystem = Global.getSector().getStarSystem(XLII_OfficeSystem.SYSTEM_ID);
        if (officeSystem != null) {
            SectorEntityToken bastion = officeSystem.getEntityById(XLII_OfficeSystem.BASTION_ID);
            if (bastion != null) makeUnimportant(bastion);
        }
    }

    // =========================================================================
    // Player-facing guidance
    // =========================================================================

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == null) return false;
        switch ((Stage) currentStage) {
            case MEET_THE_DIRECTOR:
                if (Global.getSector().getMemoryWithoutUpdate().getBoolean(XLII_OfficeContactMonitor.CONTACT_DONE_FLAG))
                    info.addPara("Travel to Ladon in the " + XLII_OfficeSystem.getSystemName() + " system", tc, pad);
                else
                    info.addPara("Await contact from the Alliance Intelligence Office", tc, pad);
                return true;
            case RETURN_FROM_DIRECTOR:
                info.addPara("Report back to Fleet Admiral August at Kori", tc, pad);
                return true;
            case FIND_NANOFORGE:
                info.addPara("Find a Pristine Nanoforge", tc, pad);
                return true;
            case DELIVER_NANOFORGE:
                info.addPara("Return to Fleet Admiral August at Kori", tc, pad);
                return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage == null) return;
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        switch ((Stage) currentStage) {
            case MEET_THE_DIRECTOR:
                if (Global.getSector().getMemoryWithoutUpdate().getBoolean(XLII_OfficeContactMonitor.CONTACT_DONE_FLAG)) {
                    info.addPara("An Office courier delivered Ladon's coordinates and stood its defenses down for you. Report there and ask for the Director.", opad);
                } else {
                    info.addPara("August has referred this to the Office before it goes any further. There's no channel back - someone will find you.", opad);
                }
                break;
            case RETURN_FROM_DIRECTOR:
                info.addPara("The Office has said what it needed to. Report back to Fleet Admiral August at Kori.", opad);
                break;
            case FIND_NANOFORGE:
                info.addPara("The Alliance requires a %s - Domain-era, undamaged. August will not ask how you acquire it.", opad, h, "Pristine Nanoforge");
                break;
            case DELIVER_NANOFORGE:
                info.addPara("You have a %s. Return to Fleet Admiral August at Kori to complete the exchange.", opad, h, "Pristine Nanoforge");
                break;
        }
    }

    private boolean hasNanoforgeInCargo() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null && fleet.getCargo().getCommodityQuantity("pristine_nanoforge") > 0;
    }
}
