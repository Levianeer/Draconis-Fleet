package levianeer.draconis.data.campaign.intel.blind_eye;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import levianeer.draconis.data.campaign.characters.XLII_PersonEliasKorrin;
import levianeer.draconis.data.campaign.events.XLII_RingPortAssault;
import levianeer.draconis.data.campaign.intel.sigma_octantis.XLII_SigmaOctantisWatchdog;

import java.awt.Color;
import java.util.Set;

/**
 * Quest tracker for the Blind Eye / Sigma Octantis questline.
 * <p>
 * Created via {@code BeginMission BlindEyeQuestMission} in rules.csv when the player
 * finishes reading gate log fragment 4 ({@code XLII_gateLog4}). Because the mission is
 * created mid-questline, {@link #create} checks existing global flags to determine the
 * correct starting stage via {@link #determineStartingStage}.
 * <p>
 * Stage transitions use {@code setStageOnGlobalFlag} (from=null) - the confirmed-working
 * pattern from Domain Phase Lab. The FIND_NANOFORGE -> DELIVER_NANOFORGE transition uses
 * a cargo check in {@link #updateInteractionDataImpl}.
 * <p>
 * Rep gates (0.25 / 0.50 / 0.75) are shown as conditional text within the relevant
 * stage description rather than as separate stages.
 */
public class BlindEyeQuestMission extends HubMissionWithBarEvent {

    // Rep thresholds - must match XLII_CheckAdmiralRep params used in rules.csv.
    private static final float REP_GATE_LOGS      = 0.25f;
    private static final float REP_GATE_NOTE      = 0.50f;
    private static final float REP_GATE_NANOFORGE = 0.75f;

    public enum Stage {
        /** Find the Fafnir Gate; ask Admiral August about the gate logs (rep gate 0.25). */
        EXPLORE_GATE,
        /** Dock at any Draconis market to receive the note (rep gate 0.50). */
        RECEIVE_NOTE,
        /** Travel to Ring-Port with transponder off; accept the mission from Korrin. */
        SPEAK_WITH_KORRIN,
        /** Korrin has been contacted; gather marines and return to Ring-Port to begin the operation. */
        PREPARE_FOR_RAID,
        /** Assault and seize Ring-Port Station. */
        ASSAULT_RING_PORT,
        /** (Optional) Speak with Korrin at Ring-Port post-assault. */
        DEBRIEF_KORRIN,
        /** Return to Fleet Admiral August for debrief. */
        RETURN_TO_AUGUST,
        /** Ask Fleet Admiral August about another matter (rep gate 0.75). */
        ASK_ANOTHER_MATTER,
        /** Locate a Pristine Nanoforge. */
        FIND_NANOFORGE,
        /** Return to Fleet Admiral August with the Pristine Nanoforge. */
        DELIVER_NANOFORGE,
        /** Sigma Octantis uplink received. Quest complete. */
        COMPLETED,
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$XLII_blindEye_missionRef")) return false;

        setStartingStage(determineStartingStage());
        setSuccessStage(Stage.COMPLETED);
        setNoAbandon();
        setStoryMission();
        setImportant(true);

        setStageOnGlobalFlag(Stage.RECEIVE_NOTE,       "$XLII_logConversationComplete");
        setStageOnGlobalFlag(Stage.SPEAK_WITH_KORRIN,  "$XLII_blindEyeNoteReceived");
        setStageOnGlobalFlag(Stage.PREPARE_FOR_RAID,   "$XLII_transponderVerified");
        setStageOnGlobalFlag(Stage.ASSAULT_RING_PORT,  "$XLII_blindEyeMissionActive");
        setStageOnGlobalFlag(Stage.DEBRIEF_KORRIN,     XLII_RingPortAssault.MEM_TAKEN);
        setStageOnGlobalFlag(Stage.RETURN_TO_AUGUST,   "$XLII_blindEyeVictoryAcked");
        setStageOnGlobalFlag(Stage.ASK_ANOTHER_MATTER, "$XLII_blindEyeComplete");
        setStageOnGlobalFlag(Stage.FIND_NANOFORGE,     "$XLII_nanoforgeQuestOffered");
        setStageOnGlobalFlag(Stage.COMPLETED,          XLII_SigmaOctantisWatchdog.NANOFORGE_QUEST_FLAG);
        setStageOnGlobalFlag(Stage.COMPLETED,          "$XLII_ringPortTakenExternally");

        return true;
    }

    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    @Override
    protected void updateInteractionDataImpl() {
        if (currentStage == Stage.FIND_NANOFORGE && hasNanoforgeInCargo()) {
            setCurrentStage(Stage.DELIVER_NANOFORGE, null, null);
        }

        // External Ring-Port capture: advance to RETURN_TO_AUGUST before debrief
        if (currentStage != Stage.COMPLETED
                && currentStage != Stage.RETURN_TO_AUGUST
                && isRingPortTakenExternally()) {
            setCurrentStage(Stage.RETURN_TO_AUGUST, null, null);
            XLII_PersonEliasKorrin.hideIfExternalCapture();
        }
    }

    /**
     * Checks existing global flags in reverse quest order to find the most-advanced
     * completed state. Called once during {@link #create} to handle mid-questline saves.
     */
    private Stage determineStartingStage() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        if (g.getBoolean("$XLII_ringPortTakenExternally")) return Stage.COMPLETED;
        if (g.getBoolean(XLII_SigmaOctantisWatchdog.NANOFORGE_QUEST_FLAG)) return Stage.COMPLETED;
        if (g.getBoolean("$XLII_nanoforgeQuestOffered"))
            return hasNanoforgeInCargo() ? Stage.DELIVER_NANOFORGE : Stage.FIND_NANOFORGE;
        if (g.getBoolean("$XLII_blindEyeComplete"))       return Stage.ASK_ANOTHER_MATTER;
        if (g.getBoolean("$XLII_blindEyeVictoryAcked"))   return Stage.RETURN_TO_AUGUST;
        if (g.getBoolean(XLII_RingPortAssault.MEM_TAKEN)) return Stage.DEBRIEF_KORRIN;
        if (isRingPortTakenExternally())                  return Stage.RETURN_TO_AUGUST;
        if (g.getBoolean("$XLII_blindEyeMissionActive"))  return Stage.ASSAULT_RING_PORT;
        if (g.getBoolean("$XLII_transponderVerified"))    return Stage.PREPARE_FOR_RAID;
        if (g.getBoolean("$XLII_blindEyeNoteReceived"))   return Stage.SPEAK_WITH_KORRIN;
        if (g.getBoolean("$XLII_logConversationComplete")) return Stage.RECEIVE_NOTE;
        return Stage.EXPLORE_GATE;
    }

    @Override
    public String getBaseName() {
        return "Blind Eye";
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
        if (startingStage != null) {
            return "";
        }
        return super.getPostfixForState();
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == null) return false;
        float rep = getAdmiralRep();
        switch ((Stage) currentStage) {
            case EXPLORE_GATE:
                if (rep < REP_GATE_LOGS)
                    info.addPara("The Admiral is not yet ready to discuss it", tc, pad);
                else
                    info.addPara("Ask Admiral August about the Fafnir Gate logs", tc, pad);
                return true;
            case RECEIVE_NOTE:
                if (rep < REP_GATE_NOTE)
                    info.addPara("Requires Friendly standing with Admiral August", tc, pad);
                else
                    info.addPara("Wait to be contacted", tc, pad);
                return true;
            case SPEAK_WITH_KORRIN:
                info.addPara("Find Korrin at Ring-Port Station", tc, pad);
                return true;
            case PREPARE_FOR_RAID:
                info.addPara("Prepare for the assault on Ring-Port", tc, pad);
                return true;
            case ASSAULT_RING_PORT:
                info.addPara("Assault Ring-Port Station", tc, pad);
                return true;
            case DEBRIEF_KORRIN:
                info.addPara("Speak with Korrin (optional)", tc, pad);
                return true;
            case RETURN_TO_AUGUST:
                info.addPara("Return to Fleet Admiral August", tc, pad);
                return true;
            case ASK_ANOTHER_MATTER:
                if (rep < REP_GATE_NANOFORGE)
                    info.addPara("The Admiral is not yet ready to raise it", tc, pad);
                else
                    info.addPara("Speak with Fleet Admiral August", tc, pad);
                return true;
            case FIND_NANOFORGE:
                info.addPara("Find a Pristine Nanoforge", tc, pad);
                return true;
            case DELIVER_NANOFORGE:
                info.addPara("Return to Fleet Admiral August", tc, pad);
                return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage == null) return;
        float rep = getAdmiralRep();
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        switch ((Stage) currentStage) {
            case EXPLORE_GATE:
                if (rep < REP_GATE_LOGS) {
                    info.addPara("Admiral August keeps detailed records on the Fafnir Gate salvage findings. He does not discuss them with those he hasn't learned to trust.", opad);
                } else {
                    info.addPara("Ask Fleet Admiral August about the logs recovered from the Fafnir Gate. He has restricted access to them. They are not available to civilians.", opad);
                }
                break;
            case RECEIVE_NOTE:
                if (rep < REP_GATE_NOTE) {
                    info.addPara("August will not authorize the next step until your standing with the Alliance is better established.", opad);
                    info.addPara("Requires %s standing.", 5f, h, "Friendly");
                } else {
                    info.addPara("Keep your ear to the ground. When the Admiral is ready, someone will make contact.", opad);
                }
                break;
            case SPEAK_WITH_KORRIN:
                info.addPara("Travel to Ring-Port Station. Approach with your %s.", opad, h, "transponder off");
                info.addPara("Ask for Korrin.", 5f);
                break;
            case PREPARE_FOR_RAID:
                info.addPara("You have spoken with Korrin. The operation is clear.", opad);
                info.addPara("Gather sufficient marines and return to Ring-Port Station when you are ready to begin.", 5f);
                break;
            case ASSAULT_RING_PORT:
                info.addPara("Ring-Port's occupants have held that station for three decades. Fleet Admiral August requires that to end.", opad);
                info.addPara("Dock at Ring-Port Station and begin the operation.", 5f);
                break;
            case DEBRIEF_KORRIN:
                info.addPara("Ring-Port is secured - off the books, as agreed.", opad);
                info.addPara("Korrin remains at the station. There is a conversation to be had, if you are inclined. Either way, the Admiral is waiting.", 5f);
                break;
            case RETURN_TO_AUGUST:
                if (isRingPortTakenExternally()) {
                    info.addPara("Ring-Port Station has changed hands. Fleet Admiral August should be informed.", opad);
                } else {
                    info.addPara("The operation is complete. Report to Fleet Admiral August.", opad);
                }
                break;
            case ASK_ANOTHER_MATTER:
                if (rep < REP_GATE_NANOFORGE) {
                    info.addPara("August has something further to discuss. He will raise it when he's ready. Keep doing what you're doing.", opad);
                } else {
                    info.addPara("Return to Fleet Admiral August. He has another matter to raise.", opad);
                }
                break;
            case FIND_NANOFORGE:
                info.addPara("The Alliance requires a %s - Domain-era, undamaged. August will not ask how you acquire it.", opad, h, "Pristine Nanoforge");
                break;
            case DELIVER_NANOFORGE:
                info.addPara("You have a %s. Return to Fleet Admiral August to complete the exchange.", opad, h, "Pristine Nanoforge");
                break;
        }
    }

    private float getAdmiralRep() {
        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson("XLII_fleet_admiral_emil");
        return admiral != null ? admiral.getRelToPlayer().getRel() : 0f;
    }

    private boolean hasNanoforgeInCargo() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null && fleet.getCargo().getCommodityQuantity("pristine_nanoforge") > 0;
    }

    private boolean isRingPortTakenExternally() {
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (market == null) return false;
        if (Factions.PIRATES.equals(market.getFactionId())) return false;
        return !Global.getSector().getMemoryWithoutUpdate().getBoolean(XLII_RingPortAssault.MEM_TAKEN);
    }
}
