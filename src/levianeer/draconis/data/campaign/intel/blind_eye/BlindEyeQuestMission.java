package levianeer.draconis.data.campaign.intel.blind_eye;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
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
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import levianeer.draconis.data.campaign.characters.XLII_PersonEliasKorrin;
import levianeer.draconis.data.campaign.events.XLII_RingPortAssault;

import java.awt.Color;
import java.util.Set;

/**
 * Quest tracker for Blind Eye - the Ring-Port capture arc only. Ends at the Kori debrief.
 * Everything past that (the Office referral, Monroe, the nanoforge exchange, and beyond) is a
 * separate tracker, {@link LongsightQuestMission}, created independently once
 * {@code $XLII_blindEyeComplete} is set. See {@code .claude/systems/blind-eye.md}.
 * <p>
 * Created via {@code BeginMission BlindEyeQuestMission} in rules.csv when the player
 * finishes reading gate log fragment 4 ({@code XLII_gateLog4}). Because the mission is
 * created mid-questline, {@link #create} checks existing global flags to determine the
 * correct starting stage via {@link #determineStartingStage}.
 * <p>
 * Stage transitions use {@code setStageOnCustomCondition} with a {@code GlobalBooleanChecker}
 * (from=null), not {@code setStageOnGlobalFlag} - see the comment above the {@code create()}
 * transition block for why {@code setStageOnGlobalFlag} is actively wrong here (it silently
 * unsets every one of these flags the moment the mission reaches {@code Stage.COMPLETED}).
 * <p>
 * Rep gates (0.25 / 0.50) are shown as conditional text within the relevant stage description
 * rather than as separate stages.
 */
public class BlindEyeQuestMission extends HubMissionWithBarEvent {

    // Rep thresholds - must match XLII_CheckAdmiralRep params used in rules.csv.
    private static final float REP_GATE_LOGS = 0.25f;
    private static final float REP_GATE_NOTE = 0.50f;

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
        /** Debriefed. Blind Eye is over - what comes next is LongsightQuestMission's. */
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

        // setStageOnGlobalFlag() (from=null) registers each flag in this mission's own
        // Abortable "changes" list with removeOnMissionOver hardcoded true
        // (BaseHubMission.connectWithGlobalFlag) - so every one of these flags gets
        // unconditionally unset the moment this mission reaches Stage.COMPLETED, because
        // endSuccess() unconditionally calls abort() (BaseHubMission.java:1333) regardless of
        // whether the mission actually succeeded. VariableSet.abort()'s early-return only
        // triggers for removeOnMissionOver=false, which setStageOnGlobalFlag never sets - so
        // completing this quest wiped these flags right when the player finished it, silently
        // reopening every gate downstream rules.csv content thought was permanently closed.
        // setStageOnCustomCondition with a GlobalBooleanChecker gets the identical
        // watch-and-transition behavior without registering anything in "changes" - these flags
        // are permanent, player-facing game
        // state read directly by other rules.csv content, not mission-scoped working state, and
        // must never be auto-unset.
        setStageOnCustomCondition(Stage.RECEIVE_NOTE,      new GlobalBooleanChecker("$XLII_logConversationComplete"));
        setStageOnCustomCondition(Stage.SPEAK_WITH_KORRIN, new GlobalBooleanChecker("$XLII_blindEyeNoteReceived"));
        setStageOnCustomCondition(Stage.PREPARE_FOR_RAID,  new GlobalBooleanChecker("$XLII_transponderVerified"));
        setStageOnCustomCondition(Stage.ASSAULT_RING_PORT, new GlobalBooleanChecker("$XLII_blindEyeMissionActive"));
        setStageOnCustomCondition(Stage.DEBRIEF_KORRIN,    new GlobalBooleanChecker(XLII_RingPortAssault.MEM_TAKEN));
        setStageOnCustomCondition(Stage.RETURN_TO_AUGUST,  new GlobalBooleanChecker("$XLII_blindEyeVictoryAcked"));
        setStageOnCustomCondition(Stage.COMPLETED,         new GlobalBooleanChecker("$XLII_blindEyeComplete"));
        setStageOnCustomCondition(Stage.COMPLETED,         new GlobalBooleanChecker("$XLII_ringPortTakenExternally"));

        addStageMarkers();

        return true;
    }

    private static final String RING_PORT_MARKET_ID = "pirateStation_market";
    private static final String ADMIRAL_ID = "XLII_fleet_admiral_emil";

    /**
     * Registers the per-stage map location for the intel entry, and the "important" indicator on
     * the target itself. Without these, {@code BaseHubMission.getMapLocation} has nothing to
     * return and the quest shows no marker at all.
     * <p>
     * {@code COMPLETED} is deliberately unmarked - there is nothing left to point at.
     * <p>
     * {@code DEBRIEF_KORRIN} marks both: Korrin at Ring-Port is optional, August is where the
     * player actually needs to go next. August is registered first, so {@code getMapLocation}
     * returns him while Ring-Port keeps its own indicator.
     */
    private void addStageMarkers() {
        PersonAPI august = Global.getSector().getImportantPeople().getPerson(ADMIRAL_ID);
        if (august != null) {
            makeImportant(august, null,
                    Stage.EXPLORE_GATE,
                    Stage.DEBRIEF_KORRIN,
                    Stage.RETURN_TO_AUGUST);
        }

        MarketAPI ringPort = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (ringPort != null) {
            makeImportant(ringPort, null,
                    Stage.SPEAK_WITH_KORRIN,
                    Stage.PREPARE_FOR_RAID,
                    Stage.ASSAULT_RING_PORT,
                    Stage.DEBRIEF_KORRIN);
        }
    }

    /**
     * How far the player has to get from Ring-Port before {@code DEBRIEF_KORRIN} counts as passed.
     * Ring-Port orbits at 12000 from Fafnir's star and Kori at 750, so anything short of staying
     * on the station clears this comfortably and there is no way to be near both.
     */
    private static final float LEFT_RING_PORT_RANGE = 2000f;

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        updateStageFixups();
    }

    /**
     * Also route the rules-driven path here, in case a future rule ever calls
     * {@code Call $XLII_blindEye_missionRef updateData}. Nothing does today.
     */
    @Override
    protected void updateInteractionDataImpl() {
        updateStageFixups();
    }

    /**
     * Stage corrections that {@code setStageOnGlobalFlag} cannot express, because their conditions
     * are not single global flags.
     * <p>
     * These must run from {@link #advanceImpl}, not {@code updateInteractionDataImpl}:
     * {@code BaseHubMission.updateInteractionData} only fires when a rule invokes
     * {@code Call $ref updateData}, and no Blind Eye rule references
     * {@code $XLII_blindEye_missionRef} at all. {@code advanceImpl} runs on the mission's own
     * tracker, so it is the hook that actually executes.
     * <p>
     * Safe against the flag-driven transitions fighting back: {@code checkStageChangesAndTriggers}
     * removes each {@code StageConnection} once it fires, so a consumed transition cannot re-assert
     * an earlier stage.
     */
    private void updateStageFixups() {
        // Korrin's post-assault conversation is optional and August's debrief only needs
        // $XLII_ringPortTaken. A player who skips Korrin would otherwise sit on DEBRIEF_KORRIN
        // until the debrief itself set $XLII_blindEyeComplete, with the marker stuck on Ring-Port.
        if (currentStage == Stage.DEBRIEF_KORRIN && hasLeftRingPort()) {
            setCurrentStage(Stage.RETURN_TO_AUGUST, null, null);
            return;
        }

        // External Ring-Port capture: advance to RETURN_TO_AUGUST before debrief
        if (currentStage != Stage.COMPLETED
                && currentStage != Stage.RETURN_TO_AUGUST
                && isRingPortTakenExternally()) {
            setCurrentStage(Stage.RETURN_TO_AUGUST, null, null);
            XLII_PersonEliasKorrin.hideIfExternalCapture();
        }
    }

    /** True once the player fleet has left Ring-Port's vicinity, or the system it sits in. */
    private boolean hasLeftRingPort() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        SectorEntityToken station =
                Global.getSector().getEntityById(XLII_RingPortAssault.STATION_ENTITY_ID);
        if (fleet == null || station == null) return false;
        if (fleet.getContainingLocation() != station.getContainingLocation()) return true;
        return Misc.getDistance(fleet.getLocation(), station.getLocation()) > LEFT_RING_PORT_RANGE;
    }

    /**
     * Checks existing global flags in reverse quest order to find the most-advanced
     * completed state. Called once during {@link #create} to handle mid-questline saves.
     */
    private Stage determineStartingStage() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        if (g.getBoolean("$XLII_ringPortTakenExternally")) return Stage.COMPLETED;
        if (g.getBoolean("$XLII_blindEyeComplete"))        return Stage.COMPLETED;
        if (g.getBoolean("$XLII_blindEyeVictoryAcked"))    return Stage.RETURN_TO_AUGUST;
        if (g.getBoolean(XLII_RingPortAssault.MEM_TAKEN))  return Stage.DEBRIEF_KORRIN;
        if (isRingPortTakenExternally())                   return Stage.RETURN_TO_AUGUST;
        if (g.getBoolean("$XLII_blindEyeMissionActive"))   return Stage.ASSAULT_RING_PORT;
        if (g.getBoolean("$XLII_transponderVerified"))     return Stage.PREPARE_FOR_RAID;
        if (g.getBoolean("$XLII_blindEyeNoteReceived"))    return Stage.SPEAK_WITH_KORRIN;
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

    // =========================================================================
    // Delete entry
    //
    // The mission is only a tracker - every gate in the questline is a global flag read by
    // rules.csv, so deleting it removes the intel entry without blocking the quest. It is
    // one-way: setGlobalReference("$XLII_blindEye_missionRef") leaves the key set, so a later
    // BeginMission will not recreate the tracker.
    // =========================================================================

    /**
     * Static, spoiler-free framing shown above the stage-specific text on every visit to
     * the entry, regardless of how far the questline has progressed.
     */
    private static final String QUEST_OVERVIEW =
            "Fleet Admiral August is using you as an off-book asset to settle Ring-Port before the "
                    + "Alliance Intelligence Office can intervene.";

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

    /**
     * {@code endImmediately()} does not run {@code BaseHubMission.abort()}, so the "important"
     * markers registered by {@link #addStageMarkers} would stay on August and Ring-Port forever.
     * Clear them before the base class ends the mission.
     */
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

        MarketAPI ringPort = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (ringPort != null) makeUnimportant(ringPort);
    }

    // =========================================================================
    // Player-facing guidance
    //
    // Every gate the player has to satisfy is named here, including the two that are
    // invisible in-game: the Draconis commission (without it August's comm link closes
    // before the menu opens, blocking every August-facing stage) and the fact that the rep
    // thresholds read August's PERSONAL standing, which only moves when buying from his
    // off-books store. See .claude/systems/blind-eye.md for the full trace.
    // =========================================================================

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == null) return false;
        float rep = getAdmiralRep();
        switch ((Stage) currentStage) {
            case EXPLORE_GATE:
                if (!hasCommission())
                    info.addPara("Requires a Draconis Defense Alliance commission", tc, pad);
                else if (rep < REP_GATE_LOGS)
                    info.addPara("Requires Welcoming standing with Admiral August", tc, pad);
                else
                    info.addPara("Ask Admiral August about the Fafnir Gate logs", tc, pad);
                return true;
            case RECEIVE_NOTE:
                if (rep < REP_GATE_NOTE)
                    info.addPara("Requires Friendly standing with Admiral August", tc, pad);
                else
                    info.addPara("Dock at any Alliance market", tc, pad);
                return true;
            case SPEAK_WITH_KORRIN:
                info.addPara("Dock at Ring-Port with your transponder off", tc, pad);
                return true;
            case PREPARE_FOR_RAID:
                if (getMarines() < XLII_RingPortAssault.MINIMUM_MARINES)
                    info.addPara("Requires " + XLII_RingPortAssault.MINIMUM_MARINES + " marines", tc, pad);
                else
                    info.addPara("Comm Korrin at Ring-Port to launch", tc, pad);
                return true;
            case ASSAULT_RING_PORT:
                info.addPara("Assault Ring-Port Station", tc, pad);
                return true;
            case DEBRIEF_KORRIN:
                // Deliberately points at August, not Korrin: the debrief only requires
                // $XLII_ringPortTaken, so the player can go straight there. Korrin is optional
                // and the description below says so.
                info.addPara("Report to Fleet Admiral August at Kori", tc, pad);
                return true;
            case RETURN_TO_AUGUST:
                if (!hasCommission())
                    info.addPara("Requires a Draconis Defense Alliance commission", tc, pad);
                else
                    info.addPara("Return to Fleet Admiral August at Kori", tc, pad);
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
                if (!hasCommission()) {
                    addCommissionLine(info, opad);
                    info.addPara("Beyond that, the Gate salvage findings are restricted. Reaching them takes %s standing with August personally.", 5f, h, "Welcoming");
                } else if (rep < REP_GATE_LOGS) {
                    info.addPara("The Gate salvage findings are restricted. August does not open them to those he hasn't learned to trust.", opad);
                    info.addPara("Requires %s standing with August personally - not with the Alliance.", 5f, h, "Welcoming");
                    addRegardLine(info, 5f);
                } else {
                    info.addPara("Ask Fleet Admiral August about the logs recovered from the Fafnir Gate. He has restricted access to them. They are not available to civilians.", opad);
                }
                break;
            case RECEIVE_NOTE:
                if (rep < REP_GATE_NOTE) {
                    info.addPara("August will not authorize the next step until he trusts you further.", opad);
                    info.addPara("Requires %s standing with August personally - not with the Alliance.", 5f, h, "Friendly");
                    addRegardLine(info, 5f);
                } else {
                    info.addPara("Dock at any Alliance market and keep your ear to the ground. Someone will find you.", opad);
                }
                break;
            case SPEAK_WITH_KORRIN:
                info.addPara("Dock at Ring-Port Station with your %s.", opad, h, "transponder off");
                info.addPara("Dock with it lit and nothing happens - no refusal, no contact, no explanation. Undock, cut the transponder, and come back in.", 5f);
                info.addPara("Then ask for Korrin.", 5f);
                break;
            case PREPARE_FOR_RAID:
                info.addPara("You have spoken with Korrin. The operation is clear.", opad);
                info.addPara("Korrin will not launch with fewer than %s aboard.", 5f, h,
                        XLII_RingPortAssault.MINIMUM_MARINES + " marines");
                info.addPara("That is the floor to begin, not the cost. Expect to lose between 25 and 275 of them depending on how the assault is fought.", 5f);
                info.addPara("Comm Korrin from Ring-Port when you have the numbers.", 5f);
                break;
            case ASSAULT_RING_PORT:
                info.addPara("Ring-Port's occupants have held that station for three decades. Fleet Admiral August requires that to end.", opad);
                info.addPara("The operation is live. Dock at Ring-Port to resume it.", 5f);
                break;
            case DEBRIEF_KORRIN:
                info.addPara("Ring-Port is secured - off the books, as agreed.", opad);
                info.addPara("Korrin is still at the station if you want the conversation. It is %s.", 5f, h, "optional");
                info.addPara("Either way, report to Fleet Admiral August at Kori. You can go straight there now.", 5f);
                break;
            case RETURN_TO_AUGUST:
                if (isRingPortTakenExternally()) {
                    info.addPara("Ring-Port Station has changed hands. Fleet Admiral August should be informed.", opad);
                } else {
                    info.addPara("The operation is complete. Report to Fleet Admiral August at Kori.", opad);
                    if (!Global.getSector().getMemoryWithoutUpdate().getBoolean("$XLII_blindEyeVictoryAcked")) {
                        info.addPara("Korrin is still at Ring-Port if you want that conversation. It is %s.", 5f, h, "optional");
                    }
                }
                if (!hasCommission()) addCommissionLine(info, 5f);
                break;
        }
    }

    /**
     * The commission gate. Without a Draconis commission {@code admiralPickGreeting} falls through
     * to the zero-condition rule, August cuts the link, and {@code XLII_AdmiralMainMenu} is never
     * reached - so every August-facing stage is hard-blocked with no in-game explanation.
     */
    private void addCommissionLine(TooltipMakerAPI info, float pad) {
        info.addPara("August takes no channel from anyone without a %s. Without one the link closes before it opens.",
                pad, Misc.getHighlightColor(), "Draconis Defense Alliance commission");
    }

    /**
     * The rep gates read August's personal standing, and the only thing that moves it is buying from
     * his off-books list. Repeated on every unmet rep gate because it is the one fact the player
     * cannot deduce from anywhere else in the game.
     */
    private void addRegardLine(TooltipMakerAPI info, float pad) {
        info.addPara("His regard is his own. It rises when you buy from the list he keeps off the books, and it does not move for Alliance faction standing, contract work, or donated cores.",
                Misc.getGrayColor(), pad);
    }

    private boolean hasCommission() {
        // Fully qualified: this file imports the vanilla ids.Factions for PIRATES.
        return levianeer.draconis.data.campaign.ids.Factions.DRACONIS
                .equals(Misc.getCommissionFactionId());
    }

    private int getMarines() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null ? fleet.getCargo().getMarines() : 0;
    }

    private float getAdmiralRep() {
        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson(ADMIRAL_ID);
        return admiral != null ? admiral.getRelToPlayer().getRel() : 0f;
    }

    private boolean isRingPortTakenExternally() {
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (market == null) return false;
        if (Factions.PIRATES.equals(market.getFactionId())) return false;
        return !Global.getSector().getMemoryWithoutUpdate().getBoolean(XLII_RingPortAssault.MEM_TAKEN);
    }
}
