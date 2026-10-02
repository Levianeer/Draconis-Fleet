package levianeer.draconis.data.campaign.intel.longsight;

import com.fs.starfarer.api.Global;
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
import levianeer.draconis.data.campaign.events.XLII_BastionDestructionMonitor;
import levianeer.draconis.data.campaign.events.XLII_KoriStrike;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeSystem;

import java.awt.Color;
import java.util.Set;

/**
 * Quest tracker for the Longsight contact, decision hub, and ending - picks up exactly where
 * {@link ShapesOfOldMission} leaves off (the Kori archive infiltration) and runs through
 * Burn the Machine's conclusion at Ladon.
 * <p>
 * Originally this class also covered the referral, Monroe's vetting, the nanoforge exchange, and
 * the sector tour; that's now split into three sequential trackers ({@link OfficeReferralMission},
 * {@link ShapesOfOldMission}, this class) so only one story-important intel entry is ever active at
 * a time. See {@code work/outline/main-story-endings.md}'s "0b" step 3d.
 * <p>
 * Created via {@code BeginMission LongsightQuestMission} from {@code XLII_kori_longsight_warning_dock}
 * - the {@code MarketPostDock} row that fires automatically the moment the player docks at Kori with
 * Elias's lead in hand, setting {@code $global.XLII_koriInvestigationRaised}, which also closes out
 * {@link ShapesOfOldMission}. The archive dig itself doesn't happen until much later - it was folded
 * into the merged Burn the Machine raid at {@code KORI_STRIKE} below (see {@code XLII_KoriStrike}/
 * {@code XLII_ResolveBurnRaid}) - so this handoff reflects Elias having a lead in hand, not a
 * completed operation. Every gate past that point is still a global flag read by rules.csv or Java;
 * this is a tracker, not new logic.
 * <p>
 * See {@link OfficeReferralMission} and {@link ShapesOfOldMission} for the arcs this continues
 * from, and the comment above this class's {@code create()} for why stage transitions use
 * {@code setStageOnCustomCondition} rather than {@code setStageOnGlobalFlag}.
 */
public class LongsightQuestMission extends HubMissionWithBarEvent {

    public enum Stage {
        /** Longsight intercepts the moment you dock at Kori with Elias's lead in hand - no waiting on a channel, and no meeting with August first. */
        LONGSIGHT_CONTACT,
        /** Longsight's warning is over and the choice is made - walk away, or go through with it. What follows is between you and Kori now, not August. */
        CONFRONT_AUGUST,
        /** Declared intent. Travel to Kori and launch the strike when ready - no rush, no going back. */
        KORI_STRIKE,
        /** Kori is settled. One loose end remains at Ladon. */
        BASTION,
        /** Ladon is gone. There is nothing further to do here. */
        COMPLETED,
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$XLII_longsightQuest_missionRef")) return false;

        setStartingStage(determineStartingStage());
        setSuccessStage(Stage.COMPLETED);
        setNoAbandon();
        setStoryMission();
        setImportant(true);

        // setStageOnCustomCondition + GlobalBooleanChecker, not setStageOnGlobalFlag - see
        // BlindEyeQuestMission.create() for why setStageOnGlobalFlag silently unsets every flag
        // it watches the instant this mission reaches Stage.COMPLETED. These flags are permanent
        // player-facing state read directly by other rules.csv content and Java, not
        // mission-scoped working state, and must never be auto-unset.
        setStageOnCustomCondition(Stage.CONFRONT_AUGUST,      new GlobalBooleanChecker(XLII_LongsightContactDialog.CONTACT_DONE_FLAG));
        setStageOnCustomCondition(Stage.KORI_STRIKE,          new GlobalBooleanChecker("$XLII_burnTheMachineDeclared"));
        setStageOnCustomCondition(Stage.BASTION,              new GlobalBooleanChecker(XLII_KoriStrike.MEM_COMPLETE));
        setStageOnCustomCondition(Stage.COMPLETED,            new GlobalBooleanChecker(XLII_BastionDestructionMonitor.FINALE_FLAG));
        // Alternate terminal paths - the player can also end this quest by refusing the uplink
        // outright (Status Quo+, walking away at Longsight's own warning - resolves directly in
        // XLII_LongsightContactDialog, no August involved) or by consciously taking it (Office
        // Takeover). Office Takeover moved (see .claude/systems/uplink-to-god-endgame-redesign.md)
        // from a post-raid rules.csv choice (XLII_interception_cave, deleted) to
        // XLII_KoriStrike's pre-raid August confrontation (showAugustInterceptCave()) - the raid is
        // now the point of no return, so standing down must happen before it, not after.
        setStageOnCustomCondition(Stage.COMPLETED,            new GlobalBooleanChecker("$XLII_longsightUplinkGranted"));
        setStageOnCustomCondition(Stage.COMPLETED,            new GlobalBooleanChecker("$XLII_longsightUplinkRefused"));
        // Fourth terminal path (added in the endgame redesign, see
        // .claude/systems/uplink-to-god-endgame-redesign.md): the player commits to destroying
        // Longsight and loses the fleet fight against the Forty-Second. Distinct from the BASTION ->
        // COMPLETED win path (XLII_KoriStrike.MEM_COMPLETE / FINALE_FLAG above) - this branch never
        // reaches finalizeStrike(), so neither of those flags is ever set on it.
        setStageOnCustomCondition(Stage.COMPLETED,            new GlobalBooleanChecker(XLII_KoriStrike.MEM_FAILED));

        addStageMarkers();

        return true;
    }

    private static final String ADMIRAL_ID = "XLII_fleet_admiral_emil";

    /** {@code LONGSIGHT_CONTACT} is deliberately unmarked - Longsight's contact comes to the player rather than the other way around. */
    private void addStageMarkers() {
        PersonAPI august = Global.getSector().getImportantPeople().getPerson(ADMIRAL_ID);
        if (august != null) {
            makeImportant(august, null,
                    Stage.CONFRONT_AUGUST,
                    Stage.KORI_STRIKE);
        }

        StarSystemAPI officeSystem = Global.getSector().getStarSystem(XLII_OfficeSystem.SYSTEM_ID);
        if (officeSystem != null) {
            SectorEntityToken bastion = officeSystem.getEntityById(XLII_OfficeSystem.BASTION_ID);
            if (bastion != null) {
                makeImportant(bastion, null, Stage.BASTION);
            }
        }
    }

    /**
     * Checks existing global flags in reverse quest order to find the most-advanced completed
     * state. Called once during {@link #create} to handle mid-questline saves.
     */
    private Stage determineStartingStage() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        if (g.getBoolean(XLII_BastionDestructionMonitor.FINALE_FLAG))       return Stage.COMPLETED;
        if (g.getBoolean("$XLII_longsightUplinkGranted"))                   return Stage.COMPLETED;
        if (g.getBoolean("$XLII_longsightUplinkRefused"))                   return Stage.COMPLETED;
        if (g.getBoolean(XLII_KoriStrike.MEM_FAILED))                       return Stage.COMPLETED;
        if (g.getBoolean(XLII_KoriStrike.MEM_COMPLETE))                    return Stage.BASTION;
        if (g.getBoolean("$XLII_burnTheMachineDeclared"))                  return Stage.KORI_STRIKE;
        if (g.getBoolean(XLII_LongsightContactDialog.CONTACT_DONE_FLAG)) return Stage.CONFRONT_AUGUST;
        return Stage.LONGSIGHT_CONTACT;
    }

    @Override
    public String getBaseName() {
        return "Uplink to God";
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
            "Longsight has made contact. What becomes of it - and of the Alliance's "
                    + "arrangement with the Office - is down to you now.";

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
            case LONGSIGHT_CONTACT:
                info.addPara("Hear Longsight out", tc, pad);
                return true;
            case CONFRONT_AUGUST:
                info.addPara("The choice is made - see it through", tc, pad);
                return true;
            case KORI_STRIKE:
                info.addPara("Travel to Kori and launch the strike when ready", tc, pad);
                return true;
            case BASTION:
                info.addPara("Travel to Ladon and finish it", tc, pad);
                return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage == null) return;
        float opad = 10f;
        switch ((Stage) currentStage) {
            case LONGSIGHT_CONTACT:
                info.addPara("Whatever Elias pulled from Kori's archive, the plan to raise it with "
                        + "Fleet Admiral August first didn't survive docking - Longsight has already "
                        + "made contact, unprompted, the moment you arrived.",
                        opad);
                break;
            case CONFRONT_AUGUST:
                info.addPara("You have seen what it actually is, firsthand, and already decided where you land. August was never part of that conversation - what remains is yours to finish.", opad);
                break;
            case KORI_STRIKE:
                info.addPara("August will not move against Longsight. That leaves it to you.", opad);
                info.addPara("There is no rush here - gather what you need, then launch the strike on Kori when ready.", 5f);
                break;
            case BASTION:
                info.addPara("Kori is settled. Ladon's garrison went hostile the moment it was - the Office does not leave a black site standing for someone it no longer trusts to know where it is.", opad);
                break;
        }
    }
}
