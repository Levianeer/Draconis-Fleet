package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.characters.XLII_PersonDanielAncker;
import levianeer.draconis.data.campaign.characters.XLII_PersonEmilAugust;
import levianeer.draconis.data.campaign.characters.XLII_PersonHaspelMonroe;
import levianeer.draconis.data.campaign.companion.KorrinCompanion;
import levianeer.draconis.data.campaign.companion.KorrinTopicQueue;
import levianeer.draconis.data.campaign.intel.longsight.crisis.XLII_LongsightCrisisManager;
import levianeer.draconis.data.campaign.rulecmd.XLII_NanoforgeExchange;
import levianeer.draconis.data.campaign.rulecmd.XLII_ShowBattlegroupAssessment;
import levianeer.draconis.data.campaign.rulecmd.XLII_ShowLongsightVisual;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeSystem;
import levianeer.draconis.data.scripts.world.systems.XLII_System;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.Random;

/**
 * InteractionDialogPlugin for Burn the Machine's climax - the strike on Kori's undercroft to sever
 * the Longsight installation, then the fleet-fight reckoning with Fleet Admiral August that follows.
 * <p>
 * Opened by {@code XLII_BeginKoriStrike} once the player has declared intent (set at the
 * Longsight-warning fork in {@code XLII_LongsightContactDialog} - see rules.csv's "Burn the Machine"
 * section). The real entry point is {@code State.AUGUST_INTERCEPT}, not the briefing - see
 * .claude/systems/uplink-to-god-endgame-redesign.md for why Cave moved here, pre-raid, instead of
 * living post-raid as it originally did.
 * <p>
 * Full flow: {@code showAugustInterceptApproach()}/{@code _shipList()}/{@code _intercept()}/
 * {@code _offer()} - August, tipped off by Longsight, confronts the player before any marines move
 * and offers the same deal Longsight already made once - its own fork, the real point of departure
 * from the raid entirely:
 * <ul>
 * <li><b>Stand down</b> -> {@code showAugustInterceptCave()}: grants the Longsight Uplink
 * ({@code XLII_NanoforgeExchange.giveUplink()}), ends the questline right there as Status Quo. No
 * raid, no fleet fight, no crisis - {@code XLII_LongsightCrisisManager} never registers on this
 * path. Unreachable once the raid has launched - this pre-raid offer is the only entry point.</li>
 * <li><b>This is happening anyway</b> -> {@code showAugustInterceptProceedResponse()} ->
 * {@code showBriefingMain()} -> the raid itself, hands off to the base game's marine-raid system
 * (MarketCMD.raidNonMarket(), driven from rules.csv - see XLII_burn_raid_setup/_success/_continue) ->
 * raid resolves, XLII_ResolveBurnRaid reopens this dialog at AFTERMATH -> hands off to rules.csv's
 * XLII_interception_open chain (August, the Forty-Second Battlegroup, and Ancker intercepting the
 * player as they leave) - no fork here anymore, Cave already happened or didn't; a single pacing
 * Continue (XLII_interception_declare) leads straight into Commit, firing
 * XLII_BeginBattlegroupFight and reopening this class at FLEET_FIGHT.</li>
 * </ul>
 * The fleet fight itself (launchBattlegroupFight()) -> <b>win</b>: boarding the crippled flagship
 * (showBoardingBeat(), hand-authored, no marine-raid mechanic) -> the Q&A-then-verdict scene with
 * August -> the escape reveal (Ancker and the Battlegroup's survivors were covering their own
 * extraction, not defending) -> finalize via finalizeStrike() (no market transfer - Kori stays
 * Draconis-owned; this is a fleet action, not a capture). <b>Loss</b> (see
 * .claude/systems/uplink-to-god-endgame-redesign.md): permanent and terminal, no retry - reopens at
 * FAILURE instead, where Longsight kills August directly (showFailureDeath()), Korrin leaves
 * permanently if he rode along (showFailureKorrinReaction()), and Monroe takes August's channel
 * (showFailureMonroeTakeover()) -> finalizeFailure(), which is the Office Takeover ending - the
 * *only* path that registers the sector-wide {@code XLII_LongsightCrisisManager} crisis. (Several
 * older comments in this codebase call Cave/Stand down "Office Takeover" instead - that's stale
 * terminology from before this redesign moved the crisis trigger off of it; Cave is Status Quo.)
 * <p>
 * Previously ran its own hand-rolled combat and narrated ground beats with manually tracked marine
 * losses (see {@code XLII_KoriInfiltration}, now just a compat-constants shim) - replaced by hooking
 * into MarketCMD for the raid itself, with the fleet fight/boarding sequence as a new, separate
 * post-raid encounter. See work/outline/main-story-endings.md's "The shape" for the full rationale.
 */
public class XLII_KoriStrike implements InteractionDialogPlugin {

    private static final Logger log = Global.getLogger(XLII_KoriStrike.class);

    public static final String PLANET_ENTITY_ID = "kori";
    public static final String MEM_COMPLETE      = "$XLII_burnTheMachineComplete";
    public static final String MEM_AUGUST_FATE   = "$XLII_augustFate";
    public static final String MEM_RIFT_COLLAPSED = "$XLII_riftCollapsed";
    public static final String MEM_MARINES_LOST  = "$XLII_koriStrikeMarinesLost";
    public static final String MEM_ELIAS_STAYED  = "$XLII_burnTheMachineEliasStayed";

    /**
     * Set by finalizeFailure() - the fleet fight was lost. Distinct from MEM_COMPLETE, which means
     * the strike concluded via finalizeStrike() (the Commit-and-win path) - never reached here. See
     * .claude/systems/uplink-to-god-endgame-redesign.md.
     */
    public static final String MEM_FAILED = "$XLII_burnTheMachineFailed";

    /**
     * Persists the player's transponder state across the round-trip through rules.csv/MarketCMD -
     * launchRaid() forces the transponder on before handing off (see that method's notes) and
     * XLII_ResolveBurnRaid restores it from this flag once the raid concludes, since Java object
     * state doesn't survive that hop (same reason MEM_MARINES_LOST exists).
     */
    public static final String MEM_TRANSPONDER_WAS_ON = "$XLII_burnRaidTransponderWasOn";

    /**
     * Set as soon as the marine raid resolves (XLII_ResolveBurnRaid), well before MEM_COMPLETE
     * (which only fires after August's fate). Bug fix: XLII_kori_opt_strike_launch used to gate on
     * !MEM_COMPLETE alone, so "Launch the strike on Kori" stayed visible through the aftermath,
     * interception, fleet fight, and boarding beats - clicking it again started a redundant second
     * raid. Gated on !MEM_RAID_COMPLETE instead.
     */
    public static final String MEM_RAID_COMPLETE = "$XLII_burnTheMachineRaidComplete";

    // CENTCOM's own disruption window after the raid severs the chamber's power spine - matches
    // the vanilla precedent in HIActionStage.java (15 + random * 45 = 15-60 days), applied via
    // Industry.setDisrupted() in disruptCentcom() below.
    private static final float CENTCOM_DISRUPT_MIN_DAYS = 15f;
    private static final float CENTCOM_DISRUPT_MAX_DAYS = 60f;

    // The Forty-Second is a real, combat-capable faction in this mod (data/world/factions/
    // XLII_fortysecond.faction) with its own dedicated hull pool (FortySecond_* variants under
    // data/variants/fortysecond/), not a Draconis-hulled fleet reassigned to pirates for hostility
    // resolution - see createBattlegroupFleet() below.
    private static final String FORTYSECOND_FACTION = levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

    // rules.csv trigger fired via dialog.setPlugin(new RuleBasedInteractionDialogPluginImpl(...))
    // once the player commits at the briefing - see XLII_burn_raid_setup in rules.csv, which sets
    // $raidDifficulty/$raidGoBackTrigger/$raidContinueTrigger and registers the single custom
    // objective via AddRaidObjective before showing the vanilla "mktRaidNonMarket" option.
    private static final String RAID_SETUP_TRIGGER = "XLII_burn_raid_setup";

    // rules.csv trigger for the interception's confrontation chain (August/Battlegroup/Ancker) -
    // see XLII_interception_open in rules.csv, "Burn the Machine" section.
    private static final String INTERCEPTION_TRIGGER = "XLII_interception_open";

    private static final int NUM_BOARDING_BEATS = 3;

    // Briefing options
    private static final String OPT_LAUNCH    = "xlii_kori_launch";
    private static final String OPT_LEAVE_ACK = "xlii_kori_leave_ack";

    // August's pre-raid confrontation - see .claude/systems/uplink-to-god-endgame-redesign.md
    private static final String OPT_AUGUST_INTERCEPT_APPROACH_CONTINUE = "xlii_kori_august_intercept_approach_continue";
    private static final String OPT_AUGUST_INTERCEPT_SHIPLIST_PROCEED  = "xlii_kori_august_intercept_shiplist_proceed";
    private static final String OPT_AUGUST_INTERCEPT_SHIPLIST_LEAVE   = "xlii_kori_august_intercept_shiplist_leave";
    private static final String OPT_AUGUST_INTERCEPT_CONTINUE = "xlii_kori_august_intercept_continue";
    private static final String OPT_AUGUST_INTERCEPT_STAND_DOWN = "xlii_kori_august_intercept_stand_down";
    private static final String OPT_AUGUST_INTERCEPT_PROCEED    = "xlii_kori_august_intercept_proceed";
    private static final String OPT_AUGUST_INTERCEPT_PROCEED_CONTINUE = "xlii_kori_august_intercept_proceed_continue";
    private static final String OPT_AUGUST_INTERCEPT_CAVE_CLOSE = "xlii_kori_august_intercept_cave_close";

    // Aftermath options - the post-raid chamber scene (author request), then the practical outcome
    private static final String OPT_CHAMBER_ENTRY_CONTINUE   = "xlii_kori_chamber_entry_continue";
    private static final String OPT_CHAMBER_ORB_CONTINUE     = "xlii_kori_chamber_orb_continue";
    private static final String OPT_CHAMBER_COUNCIL_CONTINUE = "xlii_kori_chamber_council_continue";
    private static final String OPT_AFTERMATH_CONTINUE = "xlii_kori_aftermath_continue";

    // Boarding beat option
    private static final String OPT_BOARDING_CONTINUE = "xlii_kori_boarding_continue";

    // August scene options
    private static final String OPT_ASK_WHY     = "xlii_august_ask_why";
    private static final String OPT_ASK_KNOW    = "xlii_august_ask_know";
    private static final String OPT_PROCEED     = "xlii_august_proceed";
    private static final String OPT_EXECUTE     = "xlii_august_execute";
    private static final String OPT_SPARE       = "xlii_august_spare";
    private static final String OPT_CONFIRM_FATE = "xlii_august_confirm_fate";
    private static final String OPT_RECONSIDER  = "xlii_august_reconsider";
    private static final String OPT_CLOSE       = "xlii_august_close";

    // Escape reveal option
    private static final String OPT_ESCAPE_CONTINUE = "xlii_kori_escape_continue";

    // Ancker's parting taunt (fires regardless of August's fate) - author request
    private static final String OPT_ANCKER_TAUNT_CONTINUE = "xlii_kori_ancker_taunt_continue";
    private static final String OPT_ANCKER_TAUNT_CLOSE    = "xlii_kori_ancker_taunt_close";

    // Failure sequence options (fleet fight lost) - see .claude/systems/uplink-to-god-endgame-redesign.md
    private static final String OPT_FAILURE_DEATH_CONTINUE  = "xlii_kori_failure_death_continue";
    private static final String OPT_FAILURE_KORRIN_CONTINUE = "xlii_kori_failure_korrin_continue";
    private static final String OPT_FAILURE_MONROE_CONTINUE = "xlii_kori_failure_monroe_continue";

    private enum State { AUGUST_INTERCEPT, BRIEFING, AFTERMATH, FLEET_FIGHT, BOARDING, AUGUST_QA, AUGUST_VERDICT, ESCAPE_REVEAL, FAILURE }

    private InteractionDialogAPI dialog;
    // Default is AUGUST_INTERCEPT, not BRIEFING - the pre-raid confrontation is now the real entry
    // point for a fresh launch (see .claude/systems/uplink-to-god-endgame-redesign.md). The other
    // constructors below (AFTERMATH via the marine-raid-resolved constructor, FLEET_FIGHT via
    // forFleetFight()) explicitly overwrite this, so they're unaffected.
    private State state = State.AUGUST_INTERCEPT;
    private int totalMarinesLost = 0;
    private String infiltrationOutcome = null;
    private boolean eliasStayed = false;
    private int currentBoardingBeat = 0;

    private boolean askedWhy;
    private boolean askedKnow;
    private String pendingFate = null;

    public XLII_KoriStrike() {}

    /**
     * Used by XLII_ResolveBurnRaid once the marine raid itself has resolved - skips straight to the
     * aftermath beat instead of the briefing.
     */
    public XLII_KoriStrike(int marinesLost, String infiltrationOutcome) {
        this.state = State.AFTERMATH;
        this.totalMarinesLost = marinesLost;
        this.infiltrationOutcome = infiltrationOutcome;
    }

    /**
     * Used by XLII_BeginBattlegroupFight once the interception's Commit branch fires - starts
     * directly at the fleet fight, skipping briefing/aftermath. marinesLost/infiltrationOutcome are
     * read back from global memory (set in showRaidAftermath(), not passed here) since this instance
     * is created fresh after a round-trip through rules.csv's interception chain, not carried over
     * from the raid-aftermath instance.
     */
    public static XLII_KoriStrike forFleetFight() {
        XLII_KoriStrike strike = new XLII_KoriStrike();
        strike.state = State.FLEET_FIGHT;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        strike.totalMarinesLost = mem.getInt(MEM_MARINES_LOST);
        strike.infiltrationOutcome = mem.getString(XLII_KoriInfiltration.MEM_OUTCOME);
        return strike;
    }

    /**
     * Used by XLII_ReturnToBriefing when the player clicks "Go back" on the native marine raid
     * deployment picker (MarketCMD's own mktRaidGoBack) - returns to the point right after August's
     * pre-raid confrontation resolved, not the very start of that confrontation. Backing out of the
     * deployment picker itself commits nothing (no marines spent, no raid objective completed), so
     * this isn't a second bail-out past the confrontation/confirm gauntlet already passed to get
     * here - it's just backing out of the marine raid UI, same as any other "Go back" on a native
     * MarketCMD screen.
     */
    public static XLII_KoriStrike forBriefing() {
        XLII_KoriStrike strike = new XLII_KoriStrike();
        strike.state = State.BRIEFING;
        return strike;
    }

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        eliasStayed = Global.getSector().getMemoryWithoutUpdate().getBoolean(MEM_ELIAS_STAYED);
        if (state == State.AUGUST_INTERCEPT) {
            showAugustInterceptApproach();
        } else if (state == State.BRIEFING) {
            showBriefingMain();
        } else if (state == State.AFTERMATH) {
            showRaidAftermath();
        } else if (state == State.FLEET_FIGHT) {
            launchBattlegroupFight();
        } else if (state == State.BOARDING) {
            showBoardingBeat(currentBoardingBeat);
        } else if (state == State.AUGUST_QA) {
            showAugustQA();
        } else if (state == State.ESCAPE_REVEAL) {
            showEscapeReveal();
        } else if (state == State.FAILURE) {
            showFailureDeath();
        }
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        if (text != null) {
            dialog.addOptionSelectedText(optionData);
        }
        String key = (String) optionData;

        if (state == State.AUGUST_QA || state == State.AUGUST_VERDICT) {
            handleAugustOption(key);
            return;
        }

        if (OPT_CHAMBER_ENTRY_CONTINUE.equals(key)) {
            showChamberOrb();
        } else if (OPT_CHAMBER_ORB_CONTINUE.equals(key)) {
            showChamberCouncil();
        } else if (OPT_CHAMBER_COUNCIL_CONTINUE.equals(key)) {
            showChamberAftermath();
        } else if (OPT_AFTERMATH_CONTINUE.equals(key)) {
            launchInterception();
        } else if (OPT_BOARDING_CONTINUE.equals(key)) {
            advanceBoardingBeat();
        } else if (OPT_ESCAPE_CONTINUE.equals(key)) {
            showAnckerTaunt();
        } else if (OPT_ANCKER_TAUNT_CONTINUE.equals(key)) {
            showAnckerTauntFarewell();
        } else if (OPT_ANCKER_TAUNT_CLOSE.equals(key)) {
            finalizeStrike();
        } else if (OPT_FAILURE_DEATH_CONTINUE.equals(key)) {
            if (eliasStayed) {
                showFailureKorrinReaction();
            } else {
                showFailureMonroeTakeover();
            }
        } else if (OPT_FAILURE_KORRIN_CONTINUE.equals(key)) {
            showFailureMonroeTakeover();
        } else if (OPT_FAILURE_MONROE_CONTINUE.equals(key)) {
            finalizeFailure();
        } else if (OPT_AUGUST_INTERCEPT_APPROACH_CONTINUE.equals(key)) {
            showAugustInterceptShipList();
        } else if (OPT_AUGUST_INTERCEPT_SHIPLIST_PROCEED.equals(key)) {
            showAugustIntercept();
        } else if (OPT_AUGUST_INTERCEPT_SHIPLIST_LEAVE.equals(key)) {
            leaveToNormalInteraction();
        } else if (OPT_AUGUST_INTERCEPT_CONTINUE.equals(key)) {
            showAugustInterceptOffer();
        } else if (OPT_AUGUST_INTERCEPT_STAND_DOWN.equals(key)) {
            showAugustInterceptCave();
        } else if (OPT_AUGUST_INTERCEPT_PROCEED.equals(key)) {
            showAugustInterceptProceedResponse();
        } else if (OPT_AUGUST_INTERCEPT_PROCEED_CONTINUE.equals(key)) {
            // Portrait clears here, after the click (author correction) - not inside
            // showAugustInterceptProceedResponse() itself, which would have cleared it while the
            // player was still reading that same screen. Reuses vanilla's own ShowDefaultVisual
            // directly (same class rules.csv content calls under the hood) rather than re-deriving
            // its target-type branching here - XLII_KoriStrike opens on Kori's own entity, so this
            // resolves to Kori's planet image, matching the "no portrait" convention this file
            // already uses for its own environment-only beats.
            new ShowDefaultVisual().execute(null, dialog, java.util.Collections.<Misc.Token>emptyList(), null);
            state = State.BRIEFING;
            showBriefingMain();
        } else if (OPT_AUGUST_INTERCEPT_CAVE_CLOSE.equals(key)) {
            dialog.dismiss();
        } else if (OPT_LAUNCH.equals(key)) {
            launchRaid();
        } else if (OPT_LEAVE_ACK.equals(key)) {
            dialog.dismiss();
        } else {
            leaveToNormalInteraction();
        }
    }

    @Override public void optionMousedOver(String text, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI result) {}
    @Override public Object getContext() { return null; }
    @Override public java.util.Map<String, MemoryAPI> getMemoryMap() { return null; }

    // -------------------------------------------------------------------------
    // August's pre-raid confrontation (see .claude/systems/uplink-to-god-endgame-redesign.md). The
    // real entry point into XLII_KoriStrike (via XLII_kori_opt_strike_launch, XLII_BeginKoriStrike) -
    // the operational briefing below is reached only via the "I'm doing this" fork here. This is the
    // one clean entry point to Cave: stand down here and no marines are ever committed. Once the
    // player proceeds past this point, Cave is unreachable (see the interception's notes in
    // rules.csv) - the raid itself, not the later Commit/Cave choice, is the real point of no return.
    //
    // Sequence: showAugustInterceptApproach() is pure environment, no portrait, read before contact.
    // showAugustInterceptShipList() is the Battlegroup ship-list preview + confirm, ahead of August's
    // appeal. showAugustIntercept() opens the channel with his reveal/reasoning.
    // showAugustInterceptOffer() is the offer, its deadline, and the fork.
    // showAugustInterceptCave() is the grant, reached only from Stand down.
    // showAugustInterceptProceedResponse() is a closing line from "This is happening anyway," his
    // portrait clearing only after the player clicks past it, leading into the briefing below.
    // -------------------------------------------------------------------------

    /**
     * Pure environment, no portrait - just the approach to CENTCOM. No contact yet - see
     * showAugustInterceptShipList() for the ship-list/confirm gate that follows, and
     * showAugustIntercept() for where the channel actually opens, after that gate is passed.
     */
    private void showAugustInterceptApproach() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "CENTCOM resolves out of the dark the way every hardened position does at range - slowly, " +
            "then all at once, cycling through sensor ghosts and decoy emissions before the real shape " +
            "of it settles into something your targeting computer can actually commit to. Nobody has " +
            "plotted a course here without Fleet Admiral August's own authorization in longer than " +
            "anyone currently serving under him can confirm."
        );
        text.addPara(
            "Command begins compiling what little is actually known about what's deployed to defend " +
            "it, in case any of it becomes relevant later."
        );

        opts.addOption("Continue", OPT_AUGUST_INTERCEPT_APPROACH_CONTINUE);
    }

    /**
     * Ship-list preview + the mechanical confirm, ahead of August's own confrontation rather than
     * after it. Not yet the true point of no return (August's offer, immediately after, still gives
     * a real way out) - the confirm here represents committing to see this through, not the literal
     * irreversible raid launch, so its wording doesn't claim finality.
     */
    private void showAugustInterceptShipList() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "The tactical assessment compiles itself while you're still committing to the approach - " +
            "whatever's actually deployed to defend CENTCOM, or close enough to it to matter."
        );
        XLII_ShowBattlegroupAssessment.render(dialog);

        opts.addOption("Proceed", OPT_AUGUST_INTERCEPT_SHIPLIST_PROCEED);
        opts.addOptionConfirmation(OPT_AUGUST_INTERCEPT_SHIPLIST_PROCEED,
                "Continuing from this point onwards carries real consequences depending on the outcome. " +
                "If you fail, you could permantly alter the future of the sector and the Draconis Alliance. " +
                "Make sure you have backed up your save file. " +
                "Are you certain you want to proceed?",
                "Proceed", "Cancel");
        opts.addOption("Leave", OPT_AUGUST_INTERCEPT_SHIPLIST_LEAVE);
    }

    /**
     * August's reveal - the channel opening moved here (from the old approach beat) since contact
     * now happens only after the ship-list/confirm gate, not before it.
     */
    private void showAugustIntercept() {
        showAugustPortrait();
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "The channel opens before you've finished routing the operation's own final parameters - " +
            "unprompted, uninvited. Whoever's on the other end already knew you would get here."
        );
        text.addPara(
            "\"Commander. I know what you intend to do,\" he says, already mid-thought, already past " +
            "the point of easing into it. \"It told me. Not directly - I have never once " +
            "received a message from it in my life, and I do not intend to start reading its silences " +
            "as a courtesy now. But I know.\""
        );

        opts.addOption("Continue", OPT_AUGUST_INTERCEPT_CONTINUE);
    }

    private void showAugustInterceptOffer() {
        showAugustPortrait();
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "\"Raise this any further and it stops being between the two of us,\" he says. \"It becomes " +
            "real to people who will not be as forgiving about how it started as I might be.\""
        );
        text.addPara(
            "\"There is still a version of this where nobody dies for it. Stand down now, before a " +
            "single marine reaches that facility, and I will arrange the same terms Longsight already " +
            "offered you once, at the very beginning. No strings attached.\""
        );
        text.addPara(
            "A pause, longer than anything else he has said to you."
        );
        text.addPara(
            "\"Once you launch, that offer is withdrawn. Not by me. By the fact that it will no longer " +
            "mean anything.\""
        );

        // No "Hold position" here - unlike the briefing below, this is a direct personal appeal with
        // a stated deadline. A player who wavers has to answer one of the two real questions, not
        // dodge both - consistent with this redesign's "no zero-cost outs" principle.
        opts.addOption("This is happening", OPT_AUGUST_INTERCEPT_PROCEED);
        opts.addOption("Stand down", OPT_AUGUST_INTERCEPT_STAND_DOWN);
    }

    /**
     * Short beat between "This is happening anyway" and the operational briefing - August gets a
     * closing line rather than the scene cutting away. His portrait clears only after the player
     * clicks Continue (see that handler in optionSelected()), so the visual change lands on the far
     * side of the click rather than under a screen still being read.
     */
    private void showAugustInterceptProceedResponse() {
        showAugustPortrait();
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "\"Then this is what must happen,\" he says. Nothing in his voice argues the point " +
            "any further - only the flat certainty of a man closing a ledger written against him. " +
            "\"Ludd save us all...\""
        );

        opts.addOption("Continue", OPT_AUGUST_INTERCEPT_PROCEED_CONTINUE);
    }

    /**
     * Pre-raid Cave resolution - the ONLY entry point to Status Quo (see the section-header
     * note above). Cave is not reachable at all once the raid has happened
     * (XLII_interception_declare in rules.csv no longer offers it). This method calls
     * XLII_NanoforgeExchange.giveUplink() + sets $XLII_longsightUplinkGranted directly -
     * LongsightQuestMission already watches that same flag for its own Stage.COMPLETED transition.
     */
    private void showAugustInterceptCave() {
        showAugustPortrait();
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "You hear a deep exhale followed by a short pause and with it a change in tone. " +
            "\"Then we are finished here,\" he says, \"before it ever had to start.\" " +
            "Something in his voice that isn't quite relief and isn't quite regret - " +
            "close enough to both that you can't tell which one is winning."
        );
        text.addPara(
                "\"And, as promised...\""
        );
        text.addPara(
            "Data streams to your command console anyway - encrypted manifests, operational " +
            "assessments, a transfer authorization carrying August's personal cipher, and Monroe's."
        );
        text.addPara(
            "\"Needless to say, do not lose it, Commander. We try to avoid giving these out freely.\""
        );

        // No "global." prefix on the literal key - that's a rules.csv-parser-only convention,
        // stripped before the key is ever written; a raw Java .set() call uses the bare key, matching
        // MEM_COMPLETE and LongsightQuestMission's own GlobalBooleanChecker reading this exact key.
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_longsightUplinkGranted", true);
        XLII_NanoforgeExchange.giveUplink(dialog);

        opts.addOption("Understood", OPT_AUGUST_INTERCEPT_CAVE_CLOSE);
    }

    // -------------------------------------------------------------------------
    // Briefing
    // -------------------------------------------------------------------------

    /**
     * The operational briefing - reached only via "This is happening anyway" at August's pre-raid
     * confrontation, through showAugustInterceptProceedResponse()'s Continue (which clears his
     * portrait first). Korrin already gave his own reaction to declaring intent in
     * XLII_LongsightContactDialog.showKorrinReactionToContinue(), so no additional entrance beat is
     * needed here. No illustration of its own - Kori Strike has no art set up for one; the
     * illustration system is Ring-Port Assault's alone, only character portraits are used here.
     * <p>
     * The ship-list preview and confirm moved off this screen to showAugustInterceptShipList(),
     * ahead of August's confrontation rather than after it - "Launch the operation" below is a plain
     * option, since that confirm already fired earlier and a second one back-to-back would read as
     * nagging, not stakes.
     */
    private void showBriefingMain() {
        TextPanelAPI text = dialog.getTextPanel();

        text.addPara(
            "His portrait is gone from the display by the time the channel settles into pure data - " +
            "whatever else August has to say about this, he's apparently decided you don't need to " +
            "watch him say it."
        );
        text.addPara(
            "The entire plan hinges on surprise from everyone but August - even with Longsight's watchful eye, it is " +
            "unlikely it would've revealed itself to the regular Naval forces. No officers reassigned in advance, no " +
            "window bought by someone else's sacrifice - just a commander outside of their control moving marines against " +
            "the Navy's own home system while the Navy is still convinced that isn't a sentence anyone would actually finish."
        );
        text.addPara(
            "Kori's home defense is real, but it is postured for outside threats, not you. Your " +
            "marine commander is ready and can stage the operation - once, you doubt you'll get a second chance."
        );
        text.addPara(
                "Either way, there is no coming back from this. The Draconis Defense Alliance will retialate in kind."
        );

        OptionPanelAPI opts = dialog.getOptionPanel();
        // Bug fix this method never cleared the option panel
        // before adding its own options - harmless as long as XLII_KoriStrike only ever opened via
        // a fresh showInteractionDialog() call (empty panel to start with), but a dialog.setPlugin()
        // swap into this method (the old direct XLII_LongsightContactDialog hand-off) carried over
        // whatever options the prior screen still had rendered unless cleared here first. Every other
        // scene method in this file already calls clearOptions() first; this was the one exception.
        // Kept even though this method is now only ever reached via a fresh showInteractionDialog()
        // call (XLII_BeginKoriStrike) - cheap insurance against the same bug recurring if that ever
        // changes again.
        opts.clearOptions();
        // No "Hold position" here anymore (author request) - by this point the player has already
        // passed two real commitment gates (the ship-list confirm, then August's own confirm-or-
        // stand-down fork), so a third bail-out this late is redundant rather than meaningful. Any
        // unrecognized option key still falls through to leaveToNormalInteraction() in
        // optionSelected()'s own catch-all, so nothing else needs to change if a leave path is ever
        // wanted back here.
        opts.addOption("Launch the operation", OPT_LAUNCH);
    }

    /**
     * See XLII_RingPortAssault.leaveToNormalInteraction() for why this doesn't reopen a bare
     * RuleBasedInteractionDialogPluginImpl - same soft-lock risk, avoided the same way. Kori is
     * Draconis-owned so it wasn't confirmed broken here, but there's no reason to keep the
     * fragile pattern once it's known bad.
     */
    private void leaveToNormalInteraction() {
        dialog.getOptionPanel().clearOptions();
        dialog.getTextPanel().addPara(
            "You hold position. Nothing is committed - the option is still there whenever you're ready to take it."
        );
        dialog.getOptionPanel().addOption("Continue", OPT_LEAVE_ACK);
    }

    // -------------------------------------------------------------------------
    // Handoff to the base-game marine raid system
    // -------------------------------------------------------------------------

    /**
     * Hands off to rules.csv/MarketCMD instead of running a custom combat encounter. The interaction
     * target is already Kori (set by XLII_BeginKoriStrike); XLII_burn_raid_setup sets up
     * $raidDifficulty and the single custom objective, then shows the vanilla "mktRaidNonMarket"
     * option, which rules.csv carries through the deployment picker and result screens.
     * XLII_burn_raid_continue (fired by MarketCMD once the raid finishes) calls
     * XLII_ResolveBurnRaid, which reopens this dialog at AFTERMATH.
     * <p>
     * Bug fix: forces the player's transponder on for the duration of the raid. MarketCMD's
     * raid-confirm screen offers a Story-Point option, "Make special efforts to keep your
     * preparations secret" (RAID_CONFIRM_STORY), that skips the real reputation penalty against
     * Draconis (Kori is a Draconis market) - separate from and in addition to
     * XLII_ResolveBurnRaid's own caught-tier penalty. There's no per-call config flag or
     * FireAll/FireBest hook to suppress just this option (unlike the fleet fight's FIDConfig) -
     * MarketCMD gates it with {@code if (tOn) options.setEnabled(RAID_CONFIRM_STORY, false)}, so
     * forcing the transponder on uses that same intended gate. The player's prior state is persisted
     * to MEM_TRANSPONDER_WAS_ON and restored by restoreTransponderIfNeeded() once the raid
     * concludes, since a fresh Java object doesn't carry this method's local state across the
     * rules.csv/MarketCMD hop. Only snapshots if MEM_TRANSPONDER_WAS_ON isn't already set - the
     * standing re-entry option lets the player back out and relaunch later, and without this guard a
     * second call before the first attempt's flag cleared would snapshot "on" instead of the
     * player's real original state.
     */
    private void launchRaid() {
        MemoryAPI sectorMem = Global.getSector().getMemoryWithoutUpdate();
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (!sectorMem.contains(MEM_TRANSPONDER_WAS_ON)) {
            sectorMem.set(MEM_TRANSPONDER_WAS_ON, playerFleet.isTransponderOn());
        }
        playerFleet.setTransponderOn(true);

        RuleBasedInteractionDialogPluginImpl raidSetup =
                new RuleBasedInteractionDialogPluginImpl(RAID_SETUP_TRIGGER);
        dialog.setPlugin(raidSetup);
        raidSetup.init(dialog);
    }

    /**
     * Restores whatever the player's transponder was set to before launchRaid() forced it on above -
     * no-op if MEM_TRANSPONDER_WAS_ON was never set or was already consumed. Called from both
     * XLII_ResolveBurnRaid (raid completed) and rules.csv's XLII_burn_raid_goback via
     * XLII_RestoreBurnRaidTransponder (player backed out before committing), so the transponder
     * never stays stuck on regardless of exit path.
     */
    public static void restoreTransponderIfNeeded() {
        MemoryAPI sectorMem = Global.getSector().getMemoryWithoutUpdate();
        if (!sectorMem.contains(MEM_TRANSPONDER_WAS_ON)) return;
        boolean wasOn = sectorMem.getBoolean(MEM_TRANSPONDER_WAS_ON);
        Global.getSector().getPlayerFleet().setTransponderOn(wasOn);
        sectorMem.unset(MEM_TRANSPONDER_WAS_ON);
    }

    /**
     * Hands off to rules.csv's post-raid interception chain (August/the Forty-Second Battlegroup/
     * Ancker confronting the player as they leave Kori). Same in-place
     * RuleBasedInteractionDialogPluginImpl swap as launchRaid(). No fork here anymore - Cave moved
     * pre-raid (showAugustInterceptCave(), reached from State.AUGUST_INTERCEPT, well before this
     * method ever runs) and is unreachable once the raid has launched, so
     * XLII_interception_declare is a single pacing Continue straight into Commit, which fires
     * XLII_BeginBattlegroupFight and reopens this class fresh at FLEET_FIGHT via forFleetFight().
     * See .claude/systems/uplink-to-god-endgame-redesign.md for the redesign that moved Cave here
     * from this chain.
     */
    private void launchInterception() {
        RuleBasedInteractionDialogPluginImpl interception =
                new RuleBasedInteractionDialogPluginImpl(INTERCEPTION_TRIGGER);
        dialog.setPlugin(interception);
        interception.init(dialog);
    }

    // -------------------------------------------------------------------------
    // Aftermath
    // -------------------------------------------------------------------------

    private void showRaidAftermath() {
        // Persisted here rather than at finalizeStrike() (earlier pass) - a fresh XLII_KoriStrike
        // instance created later by forFleetFight() needs to read these back after the interception's
        // round-trip through rules.csv, since Java object state doesn't survive that hop.
        MemoryAPI raidMem = Global.getSector().getMemoryWithoutUpdate();
        raidMem.set(MEM_MARINES_LOST, totalMarinesLost);
        if (infiltrationOutcome != null) {
            raidMem.set(XLII_KoriInfiltration.MEM_OUTCOME, infiltrationOutcome);
            // Queued here (moved off finalizeStrike(), which only runs on the Commit/destroy
            // ending) so Cave also gets it - the dig and its outcome already happened by now on
            // both paths, well before the interception fork picks between them. Walk-away never
            // reaches this method at all (no raid happens on that path), so it's still correctly
            // excluded. The debrief topic's own rules.csv rows branch on caught-vs-not
            // (XLII_korrin_c_infil_debrief_clean/_caught), covering all three outcome tiers
            // between them (clean and revealed share the same "clean" text).
            KorrinTopicQueue.queue("korrin_infiltration_debrief");
        }

        showChamberEntry();
    }

    /**
     * Inserted between the raid resolving and its practical outcome being reported: the player and
     * Korrin descending into the secured chamber to see what the archive/uplink actually was. Four
     * beats - this one (pure environment, no portrait), showChamberOrb() (Korrin's first reaction to
     * the sphere), showChamberCouncil() (the thousands-of-cores reveal), and showChamberAftermath()
     * (an unexplained beat, then the raid's practical text - marine losses, severed uplink).
     * <p>
     * Never states outright that the sphere is Longsight's own body, and Longsight never speaks in
     * this scene - per its character bible, it never explains its own nature. What the player takes
     * from this arrives through environment and Korrin's reaction (his "committee" line deliberately
     * doesn't resolve whether it's one mind or many), not exposition. No illustration - the
     * illustration system is Ring-Port Assault's alone; this scene relies on Korrin's portrait and
     * prose.
     */
    private void showChamberEntry() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        if (XLII_KoriInfiltration.OUTCOME_REVEALED.equals(infiltrationOutcome)) {
            text.addPara(
                "The undercroft is yours, but not quietly. Whatever else was watching this archive " +
                "noticed the noise you made getting into it."
            );
        } else {
            text.addPara(
                "The undercroft is yours. Whatever else was watching this archive gave no sign of it."
            );
        }
        text.addPara(
            "Your marines hold the corridor. What's past it is yours to walk into personally, and " +
            "Korrin doesn't wait to be asked - he's three steps ahead of you before the seal has " +
            "finished retracting."
        );

        opts.addOption("Continue", OPT_CHAMBER_ENTRY_CONTINUE);
    }

    private void showChamberOrb() {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin != null) {
            dialog.getVisualPanel().showPersonInfo(korrin, false);
        }

        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "The chamber opens into something too large for a handheld light to find an edge on, " +
            "all of it wrapped around one shape suspended dead-center: a sphere easily the size of " +
            "a frigate's hull, plated in something that isn't quite metal and isn't quite still, " +
            "its surface shifting in increments too slow to actually watch and too fast to have " +
            "ever been the same shape twice."
        );
        text.addPara(
            "\"Tell me that's not what I think it is,\" Korrin says, and doesn't wait for an " +
            "answer, because he already knows you don't have one either. \"How does something get " +
            "built like that? Or is 'built' the wrong verb?\""
        );

        opts.addOption("Continue", OPT_CHAMBER_ORB_CONTINUE);
    }

    private void showChamberCouncil() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "Closer in, the scaffolding around the sphere resolves into structure: tier on tier of " +
            "individual core housings, each no larger than a shipping crate, each lit with its own " +
            "status indicator, wired inward by cabling too thick to have been laid by anyone in " +
            "this century. Your suit's passive scanner gives up counting somewhere past the low " +
            "thousands and starts reporting a range instead."
        );
        text.addPara(
            "\"Thousands,\" Korrin says, reading the same number off his own slate. \"That's not " +
            "one of anything. That's a committee.\" He doesn't like the follow-up thought enough " +
            "to finish it politely. \"Or it was. Before it stopped needing to be.\""
        );

        opts.addOption("Continue", OPT_CHAMBER_COUNCIL_CONTINUE);
    }

    private void showChamberAftermath() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "Then, for exactly as long as it takes either of you to notice and not one second " +
            "longer, the shifting stops - the first true stillness anything in that chamber has " +
            "managed since you arrived, aimed, as far as you can tell, at nothing in particular."
        );
        text.addPara(
            "Neither of you says anything about it. Korrin puts the slate away without you asking " +
            "him to."
        );

        if (totalMarinesLost > 0) {
            AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, totalMarinesLost, text);
        } else {
            text.addPara("Your marines suffered no casualties.", new Color(153, 252, 0));
        }

        text.addPara(
            "The chamber's power spine is severed regardless of whatever that was, Longsight's " +
            "own uplink already gone dark on its own. Severed is not the same as finished, though " +
            "- CENTCOM's own damage-control crews could still reroute around a dead spine and " +
            "arrest the collapse spreading out from it, given enough uninterrupted time to try. " +
            "What's left standing between here and the surface is August, and the answer he was " +
            "always going to owe you eventually."
        );

        disruptCentcom(text);

        opts.addOption("Continue", OPT_AFTERMATH_CONTINUE);
    }

    /**
     * CENTCOM's own patrol spawning and ground defenses go down for a while as a direct,
     * mechanical consequence of the raid - reuses vanilla {@code Industry.setDisrupted()} (the same
     * mechanism a normal colony raid's "disrupt industry" objective uses), not a bespoke flag.
     * {@code XLII_HighCommand.isFunctional()} already composes with {@code BaseIndustry.isFunctional()}'s
     * own {@code isDisrupted()} check, so this alone halts QRF fleet spawning and the ground-defense
     * bonus for the duration - no further wiring needed. Day range matches the vanilla precedent in
     * {@code HIActionStage.java} (15-60 days).
     */
    private void disruptCentcom(TextPanelAPI text) {
        MarketAPI kori = Global.getSector().getEconomy().getMarket("kori_market");
        Industry centcom = kori != null ? kori.getIndustry("XLII_highcommand") : null;
        if (centcom == null) return;

        float days = CENTCOM_DISRUPT_MIN_DAYS
                + new Random().nextFloat() * (CENTCOM_DISRUPT_MAX_DAYS - CENTCOM_DISRUPT_MIN_DAYS);
        centcom.setDisrupted(days);

        text.addPara(
            "CENTCOM itself is still standing, but barely functioning now - patrol dispatch, grid " +
            "defense, the whole apparatus August built this command around, scrambling to recover " +
            "from a wound it was never built to take. It will be at least %s days before any of it " +
            "answers normally again.",
            Misc.getHighlightColor(),
            "" + (int) Math.round(days)
        );
    }

    // -------------------------------------------------------------------------
    // The fleet fight - the Forty-Second Battlegroup, intercepting as the player leaves Kori
    // -------------------------------------------------------------------------

    /**
     * Real space combat, hand-authored the same way XLII_RingPortAssault's own defender-fleet FID
     * is - no station, just the Battlegroup. Adapted from
     * XLII_RingPortAssault.createDefenderFleet()/launchAssault(). On win, reopens this same instance
     * at BOARDING via the identical dismiss+addTransientScript pattern Ring-Port uses. No per-ship
     * damage tracking - "the flagship couldn't jump out" (see the first boarding beat) is narration
     * only; nothing else in this codebase models per-ship survival after a
     * FleetInteractionDialogPluginImpl battle.
     */
    private void launchBattlegroupFight() {
        final CampaignFleetAPI battlegroup = createBattlegroupFleet();
        if (battlegroup == null) {
            log.error("Draconis: XLII_KoriStrike - failed to create Battlegroup fleet, aborting interception fight");
            dialog.dismiss();
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet.getContainingLocation() != null) {
            playerFleet.getContainingLocation().addEntity(battlegroup);
            battlegroup.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FIDConfig config = new FIDConfig();
        config.showCommLinkOption = false;
        config.showWarningDialogWhenNotHostile = false;
        config.dismissOnLeave = true;
        // Bug fix: without this, the pre-battle option screen offers both a plain "Disengage" and a
        // Story-Point "Disengage by executing a series of special maneuvers"
        // (FleetInteractionDialogPluginImpl.java) - either lets the player skip the scripted fight
        // entirely with zero combat. Both are gated behind "!noLeave", where noLeave =
        // !context.isEngagedInHostilities() && config.noLeaveOptionOnFirstEngagement - setting this
        // true removes both options until the player has engaged at least once. Vanilla precedent:
        // DwellerCMD.java sets the same flag for the same reason. Does NOT prevent a genuine
        // mid-battle retreat or loss once combat has started - that's handled below in notifyLeave()
        // with won=false, which routes into the failure sequence (see
        // .claude/systems/uplink-to-god-endgame-redesign.md). The vanilla source's full
        // noLeaveOption field (blocks leaving even mid-engagement) is deliberately not used - its own
        // doc warns it breaks retreat entirely and "gets broken completely by e.g. Phase Anchor."
        config.noLeaveOptionOnFirstEngagement = true;
        config.delegate = new BaseFIDDelegate() {
            private boolean won = false;

            @Override
            public void postPlayerSalvageGeneration(InteractionDialogAPI d,
                    FleetEncounterContext ctx, CargoAPI salvage) {
                won = true;
            }

            @Override
            public void notifyLeave(InteractionDialogAPI d) {
                if (won) {
                    state = State.BOARDING;
                    currentBoardingBeat = 1;
                    showPostFightDialog();
                } else {
                    // A loss is permanent and consequential (redesigned - see
                    // .claude/systems/uplink-to-god-endgame-redesign.md) - reopens this same instance
                    // at FAILURE via the same reopen helper the win branch uses.
                    if (battlegroup.getContainingLocation() != null) {
                        battlegroup.despawn();
                    }
                    state = State.FAILURE;
                    showPostFightDialog();
                }
            }
        };

        final FleetInteractionDialogPluginImpl fid = new FleetInteractionDialogPluginImpl(config);

        dialog.dismiss();

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;
            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    Global.getSector().getCampaignUI().showInteractionDialog(fid, battlegroup);
                }
            }
        });
    }

    /**
     * Extracted from createBattlegroupFleet() (this session) so previewBattlegroupFleet() can build
     * an identically-configured fleet for the pre-commit ship-list preview without duplicating the
     * quality/combatPts tuning in two places.
     */
    private static FleetParamsV3 buildBattlegroupParams(MarketAPI market) {
        FleetParamsV3 params = new FleetParamsV3();
        // Real Forty-Second hulls from its own dedicated faction/variant pool - not Draconis hulls
        // reassigned to pirates for FID hostility resolution (the XLII_RingPortAssault trick; that
        // fleet has no real faction of its own, the Forty-Second does). Quality/combatPts tuned up
        // from Ring-Port's own defender fleet (0.5/200) - starting values, see tools/CLAUDE.md.
        params.factionId  = FORTYSECOND_FACTION;
        params.fleetType  = FleetTypes.PATROL_LARGE;
        params.quality    = 0.9f;
        params.combatPts  = 320f;
        params.random     = new Random();
        if (market != null) {
            params.source = market;
        }
        return params;
    }

    /**
     * Builds a fleet with the same params as the real Battlegroup fight, purely for the pre-commit
     * ship-list preview (feeds XLII_ShowBattlegroupAssessment) - never added to a location, never
     * made hostile, discarded once its member list is read. A separate instance from whatever
     * launchBattlegroupFight() spawns for the real fight (different Random seed) - representative,
     * not guaranteed identical. Do not repurpose for anything needing the literal fight fleet.
     */
    public static CampaignFleetAPI previewBattlegroupFleet() {
        MarketAPI market = Global.getSector().getEconomy().getMarket("kori_market");
        return FleetFactoryV3.createFleet(buildBattlegroupParams(market));
    }

    private CampaignFleetAPI createBattlegroupFleet() {
        MarketAPI market = Global.getSector().getEconomy().getMarket("kori_market");
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(buildBattlegroupParams(market));
        if (fleet != null) {
            fleet.setFaction(FORTYSECOND_FACTION, true);
            // The Forty-Second defaults to neutral with the player (separate command structure from
            // Draconis - see DraconisWorldGen.setRelationship()), so this needs an explicit
            // hostility override rather than a faction reassignment - same MEMORY_KEY_MAKE_HOSTILE
            // pattern XLII_OfficeSystem.applyHostility()/XLII_LongsightBastionIntel.applyCrisisHostility()
            // use for a real, otherwise-friendly faction fighting the player without touching standing.
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
            // Never "XLII" on the page - that's this mod's own code prefix, not an in-fiction name.
            fleet.setNoFactionInName(true);
            fleet.setName("XLII Detachment");
            fleet.getMemoryWithoutUpdate().set("$XLII_koriStrikeBattlegroup", true);
        }
        return fleet;
    }

    private void showPostFightDialog() {
        final SectorEntityToken kori = Global.getSector().getEntityById(PLANET_ENTITY_ID);
        final XLII_KoriStrike self = this;

        Global.getSector().addTransientScript(new EveryFrameScript() {
            private boolean done = false;
            @Override public boolean isDone() { return done; }
            @Override public boolean runWhilePaused() { return true; }

            @Override
            public void advance(float amount) {
                if (!Global.getSector().getCampaignUI().isShowingDialog()) {
                    done = true;
                    SectorEntityToken target = kori != null ? kori
                            : Global.getSector().getPlayerFleet();
                    Global.getSector().getCampaignUI().showInteractionDialog(self, target);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Boarding the flagship - hand-authored, no marine-raid mechanic
    // -------------------------------------------------------------------------

    /**
     * Fixed narration/pacing, not a resource-cost decision - the player always reaches August and
     * Ancker always escapes regardless of anything here, so there's no RISKY/SAFE/SP choice
     * framework. Each beat advances via "Continue" only. The Battlegroup's men consistently give
     * ground while August's personal guard fights hard and is only overcome at the end - Ancker's
     * sabotage (jammed comms, sealed blast doors, misdirected reports, corrupted nav data) is openly
     * narrated as the cause during the fight, so the player can tell something is working against
     * their pursuit before "The escape" names who and why.
     */
    private void showBoardingBeat(int n) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        switch (n) {
            case 1:
                text.addPara(
                    "The flagship took too much damage in the fight to make its own jump - which is " +
                    "the only reason boarding it at all is possible. Every other hull that could still " +
                    "move already has."
                );
                text.addPara(
                    "The first corridor should be the hardest. It isn't. Whatever's defending it " +
                    "gives ground in under a minute, and somewhere behind that retreat your own " +
                    "marines report their headset comms cutting to static at exactly the wrong moments " +
                    "- not damage, not distance. Someone is jamming them, deliberately and well."
                );
                break;
            case 2:
                text.addPara(
                    "A blast door seals itself two meters ahead of your marines' advance - too early " +
                    "for the breach to have triggered it, too clean for an automated lockdown " +
                    "misfiring. Someone timed it. Whoever's doing this isn't fighting you. They're " +
                    "covering something, and covering it well."
                );
                text.addPara(
                    "What's actually standing and fighting is different entirely: August's own " +
                    "personal guard, dug into the approach to his position, giving no ground at all. " +
                    "It costs you to get past them, the way it should."
                );
                break;
            case 3:
                text.addPara(
                    "The last stretch to the helm is quiet in a way that has nothing to do with " +
                    "victory - corrupted nav data on every display your marines pass, misdirected " +
                    "squad reports sending half your boarding party the wrong way for critical " +
                    "minutes. Whoever has been doing this all along is still doing it, right up to " +
                    "the end."
                );
                text.addPara(
                    "August's guard makes their last stand at the helm's threshold. It is the only " +
                    "place in the entire ship someone actually meant to hold."
                );
                break;
        }

        opts.addOption("Continue", OPT_BOARDING_CONTINUE);
    }

    private void advanceBoardingBeat() {
        currentBoardingBeat++;
        if (currentBoardingBeat <= NUM_BOARDING_BEATS) {
            showBoardingBeat(currentBoardingBeat);
        } else {
            beginAugustScene();
        }
    }

    // -------------------------------------------------------------------------
    // August scene - Q&A, then the fate choice
    // -------------------------------------------------------------------------

    private void beginAugustScene() {
        state = State.AUGUST_QA;
        showAugustQA();
    }

    private void showAugustPortrait() {
        PersonAPI august = Global.getSector().getImportantPeople().getPerson(XLII_PersonEmilAugust.PERSON_ID);
        if (august != null) dialog.getVisualPanel().showPersonInfo(august, false);
    }

    private void showAugustQA() {
        showAugustPortrait();
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        if (!askedWhy && !askedKnow) {
            text.addPara(
                "He is standing when your marines reach him, alone, no honor guard, no second exit " +
                "covered. That is not an oversight. He has had time to arrange this exactly the way " +
                "he wants it arranged."
            );
            text.addPara(
                "\"You made better time than I allotted for,\" he says. No surprise in it. He " +
                "has clearly already run this outcome and several others."
            );
        }

        if (!askedWhy) {
            opts.addOption("Why Longsight? Why any of it?", OPT_ASK_WHY);
        }
        if (!askedKnow) {
            opts.addOption("Did you know what it would cost?", OPT_ASK_KNOW);
        }
        opts.addOption("There's nothing left to discuss", OPT_PROCEED);
    }

    private void handleAugustOption(String key) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();

        if (OPT_ASK_WHY.equals(key)) {
            askedWhy = true;
            text.addPara(
                "\"The Alliance has survived because its enemies always found somewhere easier to " +
                "be,\" he says. \"Longsight made that arithmetic permanent. I did not ask it to want " +
                "anything. I asked it to build. It has never once refused.\""
            );
            text.addPara(
                "A pause, and when he continues his voice has dropped the register he uses for " +
                "everyone else. \"I have wondered, more than once, whether that is the same thing " +
                "as trust. I've concluded it doesn't matter which it is.\""
            );
            showAugustQA();
            return;
        }

        if (OPT_ASK_KNOW.equals(key)) {
            askedKnow = true;
            text.addPara(
                "\"I knew enough,\" he says. \"I have never asked the Office for the specifics, and " +
                "I have never pretended that not asking absolves me of them.\""
            );
            text.addPara(
                "He looks at you the way he looks at everything before he answers it - the assessment " +
                "already made. \"You came here to end something I built because I would not. I don't " +
                "fault the reasoning. I built it because I decided the Alliance couldn't survive without " +
                "it. You've decided it can't survive with it. Only one of us gets to be right.\""
            );
            showAugustQA();
            return;
        }

        if (OPT_PROCEED.equals(key)) {
            showFateChoice();
            return;
        }

        if (OPT_EXECUTE.equals(key)) {
            if (pendingFate == null) { showFateDetail(OPT_EXECUTE); return; }
        }
        if (OPT_SPARE.equals(key)) {
            if (pendingFate == null) { showFateDetail(OPT_SPARE); return; }
        }
        if (OPT_CONFIRM_FATE.equals(key)) {
            resolveFate(pendingFate);
            return;
        }
        if (OPT_RECONSIDER.equals(key)) {
            pendingFate = null;
            showFateChoice();
            return;
        }
        if (OPT_CLOSE.equals(key)) {
            state = State.ESCAPE_REVEAL;
            showEscapeReveal();
        }
    }

    private void showFateChoice() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "\"I executed the demagogue myself,\" he says, unprompted, into the silence you leave " +
            "him. \"No trial. No one else present. I have never regretted the method, whatever I've " +
            "concluded about the necessity.\""
        );
        text.addPara(
            "He does not ask you to spare him. He does not ask you for anything. He simply waits, " +
            "standing, for you to decide what happens in the next several seconds - the last order " +
            "he will ever fail to give."
        );

        opts.addOption("End it here", OPT_EXECUTE);
        opts.addOption("Make him live with what he built", OPT_SPARE);
    }

    private void showFateDetail(String option) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();
        pendingFate = option;

        if (OPT_EXECUTE.equals(option)) {
            text.addPara(
                "Exactly what he did to the demagogue, exactly why. No trial, no institution, no one " +
                "else's authority invoked. The mirror completes itself and there is no undoing it " +
                "afterward."
            );
            opts.addOption("Commit", OPT_CONFIRM_FATE);
        } else if (OPT_SPARE.equals(option)) {
            text.addPara(
                "Not mercy. He built the doctrine that made his own life the one thing that couldn't " +
                "be spent on it. Leaving him alive inside the wreckage of that calculus is the harder " +
                "sentence, and you both know it."
            );
            opts.addOption("Commit", OPT_CONFIRM_FATE);
        }
        opts.addOption("Reconsider", OPT_RECONSIDER);
    }

    private void resolveFate(String fate) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        String fateFlag;
        if (OPT_EXECUTE.equals(fate)) {
            fateFlag = "executed";
            text.addPara(
                "It is over quickly, and quietly, and nobody else is present to record how. Fleet " +
                "Admiral Emil August dies the way he decided the demagogue should - on someone else's " +
                "authority, this time."
            );
        } else {
            fateFlag = "spared";
            text.addPara(
                "He does not thank you for it. He simply nods, once, the way he nods at an assessment " +
                "he disagrees with but has decided not to contest. Whatever remains of his command " +
                "authority dies in this room regardless. He does not."
            );
        }

        Global.getSector().getMemoryWithoutUpdate().set(MEM_AUGUST_FATE, fateFlag);

        // Executed: August is gone from Kori for good. Spared leaves him in place, alive and
        // powerless. markGone() (see XLII_PersonEmilAugust) sets a persistent flag so
        // updatePlacement() doesn't resurrect him on the next save load.
        if (!OPT_SPARE.equals(fate)) {
            XLII_PersonEmilAugust.markGone();
        }

        opts.addOption("Understood", OPT_CLOSE);
    }

    // -------------------------------------------------------------------------
    // The escape - Ancker and the Battlegroup's survivors were never actually defending
    // -------------------------------------------------------------------------

    /**
     * Names what "Boarding the flagship" already made felt but didn't confirm: the sabotage
     * throughout that fight was cover for the Battlegroup's own extraction, not random interference.
     * The flagship the player boarded couldn't jump out - the ship this names, the one they weren't
     * watching, could.
     * <p>
     * Also where Ancker is marked gone - his escape is established here, on either of August's fates.
     * XLII_PersonDanielAncker.markGone() sets a persistent flag so his placement isn't resurrected on
     * the next save load (same bug shape XLII_PersonEmilAugust.markGone() fixes for August above).
     * Only touches his Ring-Port comm entry, not his ImportantPeopleAPI registration, so
     * showAnckerTaunt()'s portrait lookup right after this still succeeds.
     */
    private void showEscapeReveal() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "Word reaches you before the flagship's wreck has finished cooling: emergency shuttles " +
            "launched during the boarding fight, boarded a second ship that was never disabled in the " +
            "first place, and made the jump out before anyone thought to stop them."
        );
        text.addPara(
            "Ancker was never in the corridors with you. Every jammed channel, every blast door " +
            "sealed at exactly the wrong second, every misdirected report - all of it bought that " +
            "ship its window, the same way you bought your own aboard Kori. The Battlegroup's men " +
            "were never actually defending anything. They were covering an extraction the whole time."
        );

        XLII_PersonDanielAncker.markGone();

        opts.addOption("Continue", OPT_ESCAPE_CONTINUE);
    }

    // -------------------------------------------------------------------------
    // Ancker's parting taunt - fires regardless of August's fate
    // -------------------------------------------------------------------------

    /**
     * A comms hail from Ancker after August's fate is decided - fires on both fates (Execute/Spare).
     * Ancker was already introduced via showEscapeReveal() regardless of fate, so skipping this beat
     * on Spare would make his earlier setup a dead end. The text never specifies execution or exile
     * ("I didn't think you'd go through with it" / "you're going to need a new admiral" both hold on
     * Spare too, since resolveFate()'s Spare branch has August's command authority end "in this room
     * regardless" even though he lives). He already escaped during the boarding fight
     * (showEscapeReveal() above) - this is him calling in from wherever that jump took him.
     * <p>
     * Split into an entrance beat (this one) and a follow-up (showAnckerTauntFarewell()) per the
     * character/scene-change Continue convention. Kept to two short beats - his bible: never raises
     * his voice, never gives a speech, lets incomplete sentences do the work.
     */
    private void showAnckerTaunt() {
        PersonAPI ancker = Global.getSector().getImportantPeople().getPerson(XLII_PersonDanielAncker.PERSON_ID);
        if (ancker != null) {
            dialog.getVisualPanel().showPersonInfo(ancker, false);
        }

        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "The channel opens without a request on your end, already carrying Daniel Ancker's " +
            "face by the time the connection settles - the clean, unhurried signal of someone who " +
            "was never worried about being traced. \"I didn't think you'd go through with it,\" he " +
            "says. \"I try not to be wrong about people.\""
        );

        opts.addOption("Continue", OPT_ANCKER_TAUNT_CONTINUE);
    }

    /** The rest of Ancker's taunt, then his farewell and the link closing - see showAnckerTaunt(). */
    private void showAnckerTauntFarewell() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "\"I don't mind it, this once.\" Whatever he actually feels about what happened to " +
            "August is filed already, someplace you'll never get to read. \"An interesting " +
            "variable, in the end. I don't say that often.\""
        );
        text.addPara(
            "\"Good luck, Commander.\" He means it, in whatever sense the word still applies to " +
            "him. \"You're going to need a new admiral. I'd start there.\" The channel closes on " +
            "his own terms, the same way it opened."
        );

        opts.addOption("Continue", OPT_ANCKER_TAUNT_CLOSE);
    }

    // -------------------------------------------------------------------------
    // Finalize
    // -------------------------------------------------------------------------

    private void finalizeStrike() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        // MEM_MARINES_LOST and XLII_KoriInfiltration.MEM_OUTCOME are already set - see
        // showRaidAftermath()'s comment for why that moved earlier this pass.
        mem.set(MEM_RIFT_COLLAPSED, true);
        // MEM_RIFT_COLLAPSED was set here from the start but never actually read anywhere -
        // the Rift itself kept running regardless. Wired up (author request): the Battlegroup
        // fight is what bought CENTCOM's collapse enough time to become irreversible, and this
        // is the point that's finally true, win, August's fate decided, Ancker gone. See
        // XLII_System.disableRiftTerrain().
        XLII_System.disableRiftTerrain();
        mem.set(MEM_COMPLETE, true);

        // Backward-compat with the archive infiltration this raid absorbed - ShapesOfOldMission and
        // XLII_LongsightContactDialog still read this flag by name.
        mem.set(XLII_KoriInfiltration.MEM_COMPLETE, true);
        // korrin_infiltration_debrief is queued from showRaidAftermath() now, not here - that fires
        // on both Commit and Cave (this method only runs on Commit), since the dig and its outcome
        // are already decided for both by the time either ending is reached.

        if (eliasStayed) {
            KorrinTopicQueue.queue("korrin_burn_epilogue");
        }

        // Ladon's garrison stands down for Monroe's vetting via ACCESS_GRANTED_FLAG and stays down
        // permanently for players who never return here - fine, since this flag only matters again
        // for a player who just went through Burn the Machine. Unsetting it makes the bastion
        // hostile again (XLII_OfficeSystem.applyHostility(), re-applied every tick by
        // XLII_OfficeGarrisonManager), giving the finale coda a real, ordinary hostile-fleet fight.
        mem.unset(XLII_OfficeSystem.ACCESS_GRANTED_FLAG);
        if (XLII_BastionDestructionMonitor.shouldRegister()) {
            Global.getSector().addScript(new XLII_BastionDestructionMonitor());
        }
        SectorEntityToken bastionEntity = Global.getSector().getEntityById(XLII_OfficeSystem.BASTION_ID);
        if (bastionEntity != null) {
            Misc.makeImportant(bastionEntity, "XLII_burnTheMachineFinale");
        }

        log.info("Draconis: XLII_KoriStrike - strike complete, marines lost: " + totalMarinesLost
                + ", August fate: " + mem.getString(MEM_AUGUST_FATE));

        dialog.dismiss();
    }

    // -------------------------------------------------------------------------
    // Failure - the fleet fight against the Forty-Second was lost. New terminal branch, see
    // .claude/systems/uplink-to-god-endgame-redesign.md for the full design rationale.
    // -------------------------------------------------------------------------

    /**
     * Beat 1: Longsight kills August directly, mirroring his own unwitnessed execution of the
     * demagogue (see showFateChoice() above) - deliberately NOT a distant, off-screen death reported
     * later; the player is present for it, immediately, at Kori. Longsight never explains this act,
     * consistent with its voice rule (never explains its own nature) - states the outcome, not the
     * reasoning, dropping its usual verse register for the one moment it actually kills someone.
     * <p>
     * Both portraits shown - August's re-asserted as primary, Longsight's arriving as a second
     * person ({@code dialog.getVisualPanel().showSecondPerson()}) right as its line lands, using the
     * same synthetic-portrait construction XLII_ShowLongsightVisual builds for its own primary-slot
     * usage (it has no ImportantPeopleAPI registration, so that class's buildPerson() is called
     * directly instead of routing through an id-based lookup).
     * <p>
     * The kill itself is grounded in XLII_MarginalAllocation's established combat signature (forced
     * overload, no Revival Protocol save) rather than stated outright or left to pure inference -
     * measured against the ordinary combat overloads the player has already survived dozens of
     * times, so the absence of the usual mercy window reads as final without naming the mechanic.
     */
    private void showFailureDeath() {
        showAugustPortrait();
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "There is no debrief to sit through, no report to read afterward - the only channel " +
            "still open is the one August never closed."
        );

        dialog.getVisualPanel().showSecondPerson(XLII_ShowLongsightVisual.buildPerson());
        text.addPara(
            "\"It was accounted for,\" the signal says, carrying none of the register it has used " +
            "with you before. \"So is this.\""
        );
        text.addPara(
            "\"I have already-\" August doesn't finish it. The feed flashes white once. No alarm. " +
            "No half-second to brace, the way an overload is supposed to give a crew. Just the " +
            "flash, and then the channel carrying nothing at all."
        );
        text.addPara(
            "You were listening when it happened, and there is nothing to show for having been."
        );

        opts.addOption("Continue", OPT_FAILURE_DEATH_CONTINUE);
    }

    /**
     * Beat 2: Korrin's reaction and permanent departure - fires only if he rode along
     * ($XLII_burnTheMachineEliasStayed). He witnesses this directly, not secondhand - the payoff to
     * his own loyalty-fork line at commit ("Ask me again after..."). Skipped entirely if he didn't
     * ride along - see optionSelected()'s dispatch out of OPT_FAILURE_DEATH_CONTINUE.
     * KorrinCompanion.leaveForever() is called from finalizeFailure(), not here - this beat is text
     * only.
     */
    private void showFailureKorrinReaction() {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin != null) {
            dialog.getVisualPanel().showPersonInfo(korrin, false);
        }

        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "Korrin says nothing for long enough that you check the connection is still live."
        );
        text.addPara(
            "\"That's it,\" he says, eventually. \"It just did that. It had the entire war to think " +
            "about it, and it didn't even take a full second.\""
        );
        text.addPara(
            "He's quieter for the next part, and you get the sense it isn't really meant for you. " +
            "\"I had an answer. Ask me again after, I told you. This is after.\""
        );
        text.addPara(
            "When he does look at you directly, whatever was in his voice a moment ago is already " +
            "gone, filed somewhere he doesn't intend to open again. \"I'm not doing this again. " +
            "Whatever this was going to become - I'm done being part of it.\""
        );
        text.addPara(
            "He doesn't wait for you to answer. By the time your ship reaches dock, there's nothing " +
            "left of him aboard to argue with."
        );

        opts.addOption("Continue", OPT_FAILURE_KORRIN_CONTINUE);
    }

    /**
     * Beat 3: Monroe takes August's channel the instant it goes dark - the same ShowPersonVisual
     * device used for August at the interception's open, opposite outcome. Pays off two
     * previously-planted lines from Chain of Custody's Monroe Vetting scene: her claim to be the
     * Alliance's real continuity and her promise the player would be told things "when the Alliance
     * needs you to have them." Matches her "never caught flat-footed" characterization (see her
     * win-path epilogue in XLII_BurnTheMachineEpilogue). Also the crisis's real inciting moment -
     * see finalizeFailure() below for where XLII_LongsightCrisisManager gets registered.
     */
    private void showFailureMonroeTakeover() {
        PersonAPI monroe = Global.getSector().getImportantPeople().getPerson(XLII_PersonHaspelMonroe.PERSON_ID);
        if (monroe != null) {
            dialog.getVisualPanel().showPersonInfo(monroe, false);
        }

        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        text.addPara(
            "August's channel doesn't close. It simply changes hands."
        );
        text.addPara(
            "\"Continuity answers to whoever planned past that point,\" she says, without anything " +
            "resembling a greeting first. \"I told you who that was, when you asked.\""
        );
        text.addPara(
            "\"You were also told you would be given what you needed to know, when the Alliance " +
            "actually needed you to have it.\" A pause exactly as long as it needs to be, and no " +
            "longer. \"That time is now.\""
        );
        text.addPara(
            "\"The Office does not require your permission for what happens next, Commander.\" The " +
            "faintest emphasis on the title, as though testing whether it still applies to anyone. " +
            "\"It never did.\""
        );
        text.addPara(
            "The channel closes on her own terms, exactly the way August's always did."
        );

        opts.addOption("Continue", OPT_FAILURE_MONROE_CONTINUE);
    }

    /**
     * Closes out the failure branch. Registers the crisis manager here instead of Cave -
     * XLII_NanoforgeExchange's give_uplink branch no longer does this - since Longsight, having just
     * removed the one person keeping it leashed, has no remaining reason to stay hidden. Sets
     * MEM_FAILED, distinct from MEM_COMPLETE (finalizeStrike()'s Commit-and-win path, never reached
     * here). LongsightQuestMission reads MEM_FAILED as a fourth Stage.COMPLETED trigger, alongside
     * the bastion-destroyed, uplink-granted, and uplink-refused endings.
     * <p>
     * Bug fix: this method never removed August from Kori's comm directory, despite
     * showFailureDeath() stating he's dead - the player could still hail a dead man.
     * XLII_PersonEmilAugust.markGone() fixes it, same call resolveFate()'s Execute branch uses.
     */
    private void finalizeFailure() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(MEM_FAILED, true);

        XLII_PersonEmilAugust.markGone();
        // Ancker is marked gone here too, implying he's fled now that his handler is dead - not
        // narrated (no beat addresses him directly the way showEscapeReveal() does on the win path),
        // a silent mechanical consequence only.
        XLII_PersonDanielAncker.markGone();

        if (eliasStayed) {
            KorrinCompanion.leaveForever();
        }

        mem.set(XLII_LongsightCrisisManager.DEBUG_FORCE_KEY, true);
        XLII_LongsightCrisisManager.createIfNecessary();

        log.info("Draconis: XLII_KoriStrike - fleet fight lost, August killed by Longsight, crisis triggered");

        dialog.dismiss();
    }
}
