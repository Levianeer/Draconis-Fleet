package levianeer.draconis.data.campaign.intel.fafnir;

/**
 * All player-facing strings for the Fafnir Access System.
 * Covers jump point denial, credential authentication, brute force transit,
 * and both bar events (TT Courier and Ring-Port paths).
 */
public class FafnirAccessStrings {
    private FafnirAccessStrings() {}

    // =========================================================================
    // Memory flag keys
    // =========================================================================

    public static final String MEM_ACCESS_GRANTED        = "$fafnirAccessGranted";
    public static final String MEM_ENTRY_PATH            = "$fafnirEntryPath";
    public static final String MEM_TT_QUEST_ACTIVE       = "$fafnirTTQuestActive";
    public static final String MEM_TT_DECLINED           = "$fafnirTTDeclined";
    public static final String MEM_RP_QUEST_ACTIVE        = "$fafnirRingPortQuestActive";
    public static final String MEM_RP_DELIVERY_DONE      = "$fafnirRingPortDeliveryDone";
    public static final String MEM_KORI_ARRIVAL_DONE     = "$fafnirKoriArrivalDone";

    /**
     * Set on a fleet entity (via {@code fleet.getMemoryWithoutUpdate()}) when the
     * monitor dispatches it to intercept the player. Cleared by
     * {@code XLII_CampaignPlugin} once the intercept dialog fires.
     */
    public static final String MEM_FLEET_INTERCEPT_TAG      = "$fafnirInterceptFleet";

    /** Set when the brute-force in-system intercept dialog has fired. */
    public static final String MEM_BF_INTERCEPT_DONE        = "$fafnirBFInterceptDone";
    /** Timestamp (Long) stored when the BF intercept fires; used to track the 3-day grace period. */
    public static final String MEM_BF_WARNING_TIMESTAMP     = "$fafnirBFWarningTimestamp";
    /** Set when the transverse-jump intercept dialog has fired. */
    public static final String MEM_TRANSVERSE_INTERCEPT_DONE = "$fafnirTransverseInterceptDone";

    public static final String PATH_TT_COURIER       = "tt_courier";
    public static final String PATH_RING_PORT        = "ring_port";
    public static final String PATH_BRUTE_FORCE      = "brute_force";
    public static final String PATH_TRANSVERSE_JUMP  = "transverse_jump";

    // =========================================================================
    // Reputation deltas applied at access time (0-1 float, Starsector rep scale)
    // =========================================================================

    /** +5 rep: sanctioned TT-coordinated entry */
    public static final float REP_GRANT_TT = 0.1f;
    /** No immediate penalty: brute forced through. Hostility is deferred to the 3-day grace timer. */
    public static final float REP_GRANT_BF = 0f;
    /** -3 rep: pirate-channel arrival noted in the ledger */
    public static final float REP_GRANT_RP = -0.1f;
    /** Rep delta applied after the 3-day BF grace period expires; pushes well past hostile threshold. */
    public static final float REP_BF_HOSTILE_DELTA  = -0.8f;
    /** Small rep hit on transverse-jump entry. */
    public static final float REP_TRANSVERSE_DELTA  = -0.1f;
    /** Transverse-jump rep penalty is floored here - never push the player to hostile on entry. */
    public static final float REP_TRANSVERSE_FLOOR  = -0.5f;
    /** In-game days before DDA turns hostile after a brute-force entry. */
    public static final float BRUTE_FORCE_GRACE_DAYS = 3f;

    // =========================================================================
    // Jump Point - Military IFF denial (Itoron's JP, Fringe JP)
    // =========================================================================

    public static final String MILITARY_DENIED_PARA1 =
            "The approach does not resolve.";

    public static final String MILITARY_DENIED_PARA2 =
            "Your navigation systems return nothing actionable. Without current density mapping and approach geometry, "
            + "the Rift ahead is just radiation - no path, no bearing, nothing to follow through.";

    public static final String MILITARY_DENIED_PARA3 =
            "Your sensors catch a faint return from inside. Something registered the approach. The response was silence.";

    // =========================================================================
    // Jump Point - Option labels
    // =========================================================================

    public static final String OPT_ACKNOWLEDGE =
            "Withdraw from the approach.";

    public static final String OPT_TT_CREDENTIALS =
            "Use Tri-Tachyon provided transit nav data.";

    public static final String OPT_RING_PORT_CREDENTIALS =
            "Use Ring-Port contractor provided nav data.";

    /** %d = story point cost */
    public static final String OPT_BRUTE_FORCE_FMT =
            "Attempt forced transit through the Rift. [%d Story Points]";

    // =========================================================================
    // Access Granted - TT credentials
    // =========================================================================

    public static final String TT_AUTH_PARA1 =
            "The nav chip works.";

    public static final String TT_AUTH_PARA2 =
            "Your navigation officer keys in the transit data. The Rift opens ahead - not safe, exactly, but charted: "
            + "particle density mapped, the approach geometry threaded between the worst of it. "
            + "Someone else's prior suffering, reduced to numbers on a chip.";

    public static final String TT_AUTH_PARA3 =
            "Whatever else the chip contained, the Rift did not ask.";

    public static final String OPT_APPROACH_JP = "Approach the jump point.";

    // =========================================================================
    // Access Granted - Ring-Port contractor
    // =========================================================================

    public static final String RING_PORT_AUTH_PARA1 =
            "The approach resolves.";

    public static final String RING_PORT_AUTH_PARA2 =
            "The chip's nav data is not on any official chart. Ring-Port seems to run on a different kind of knowledge - "
            + "accumulated over cycles by people who needed a way in that the AIO wasn't watching. "
            + "The radiation climbs ahead, within tolerance. The way through is open.";

    public static final String RING_PORT_AUTH_PARA3 =
            "You are in their books now. The books are not kept by anyone who answers to the Alliance.";

    // =========================================================================
    // Access Granted - Brute force
    // =========================================================================

    public static final String BRUTE_SUCCESS_PARA1 =
            "There is no passage.";

    public static final String BRUTE_SUCCESS_PARA2 =
            "The Rift does not accommodate. What lies ahead is particle bombardment against hull coatings, "
            + "navigation arrays thrown into static, damage accrued in proportion to how long the hulls refuse to turn back.";

    public static final String BRUTE_SUCCESS_PARA3 =
            "The Rift keeps no ledger. The Office does.";

    // =========================================================================
    // Bar Event - TT Courier (approach prompt + dialog)
    // =========================================================================

    public static final String TT_BAR_SCENE =
            "A stoic looking and corporately dressed women sits alone. Professional attire - "
            + "civilian clothes, public market, a drink she isn't drinking. "
            + "She sees you notice her and gives a small wave.";

    public static final String TT_BAR_OPTION_APPROACH =
            "Approach the corporate woman in the corner.";

    public static final String TT_OPENING_PARA1 =
            "She doesn't acknowledge your arrival immediately. Finishes a notation in her data pad, "
            + "sets it face-down.";

    public static final String TT_OPENING_PARA2 =
            "\"I have a logistics coordination problem.\" She says it the way you'd say the weather. "
            + "\"Documentation needs to reach Kori on a schedule. The route has been compromised by "
            + "attention I'd prefer not to replicate.\"";

    public static final String TT_OPENING_PARA3 =
            "She slides a data chip across the table without looking at it. "
            + "\"Transit data are embedded. Your Nav Officer will know what to do with it.\"";

    public static final String TT_OPENING_PARA4 =
            "\"I don't need to know your other business. You don't need to know mine. "
            + "You'll be paid on delivery.\"";

    public static final String OPT_TT_ACCEPT    = "Accepted.";
    public static final String OPT_TT_ASK_WHO   = "Who do I deliver to?";
    public static final String OPT_TT_ASK_WHAT  = "What kind of documentation?";
    public static final String OPT_TT_DECLINE   = "Not interested.";

    public static final String TT_ASK_WHO_PARA1 =
            "\"The right people will find you once you're docked at Kori.\" "
            + "She gives a dramatic sigh. "
            + "\"If you must know, it's for the Alliance Intelligence Office.\"";

    public static final String TT_ASK_WHO_PARA2 =
            "She gieves a brief a pause.";

    public static final String TT_ASK_WHO_PARA3 =
            "\"Don't ask about them at the door. They're kind of jumpy...\"";

    public static final String TT_ASK_WHAT_PARA1 =
            "\"Tri-Tachyon research materials. Logistics data. "
            + "It's really not that important for you to know anything beyond that.\" "
            + "She glances at you, with a slight tinge of impatience.";

    public static final String TT_ASK_WHAT_PARA2 =
            "She picks up her drink for the first time.";

    public static final String TT_ASK_WHAT_PARA3 =
            "\"That is the complete answer to that question.\"";

    public static final String TT_ACCEPT_PARA1 =
            "She gives a single nod. The transaction is done.";

    public static final String TT_DECLINE_PARA1 =
            "She acknowledges this with a look that files the information away.";

    // =========================================================================
    // Bar Event - Ring-Port (approach prompt + dialog)
    // =========================================================================

    public static final String RP_BAR_SCENE =
            "An older gruff looking man. Military posture in civilian clothes. "
            + "He makes a small gesture - not an invitation exactly, more like indicating "
            + "there's a conversation available if you want it.";

    public static final String RP_BAR_OPTION_APPROACH =
            "Approach the gruff looking man.";

    public static final String RP_OPENING_PARA1 =
            "He doesn't offer a name.";

    public static final String RP_OPENING_PARA2 =
            "\"I have a package.\" Direct. No preamble. "
            + "\"Old Tri-Tach signals intelligence. Pre-war. Classified in a way that makes it difficult "
            + "to move through DDA-adjacent space without attracting the kind of "
            + "attention that ends careers.\"";

    public static final String RP_OPENING_PARA3 =
            "\"Job's clean. You get logged as a Ring-Port working contractor - which means "
            + "certain approaches open up that otherwise don't. "
            + "You understand what I'm saying.\"";

    public static final String RP_OPENING_PARA4 =
            "He looks at you the way people look at tools - not unkindly. Functionally.";

    public static final String RP_OPENING_PARA5 =
            "\"Interested?\"";

    public static final String OPT_RP_ACCEPT    = "Accepted.";
    public static final String OPT_RP_ASK_WHERE = "What's Ring-Port?";
    public static final String OPT_RP_ASK_WHAT  = "What are you shipping, exactly?";
    public static final String OPT_RP_DECLINE   = "Not interested.";

    public static final String RP_ASK_WHERE_PARA1 =
            "\"Former asteroid mining station. Current situation is more complicated.\"";

    public static final String RP_ASK_WHERE_PARA2 =
            "A pause.";

    public static final String RP_ASK_WHERE_PARA3 =
            "\"First Fleet veterans who didn't make it into August's new order. "
            + "They run the approach. You work for me, you're working for them.\"";

    public static final String RP_ASK_WHAT_PARA1 =
            "\"I said old intelligence files. Pre-war.\"";

    public static final String RP_ASK_WHAT_PARA2 =
            "He looks at you.";

    public static final String RP_ASK_WHAT_PARA3 =
            "\"Don't make this into something it isn't.\"";

    public static final String RP_ACCEPT_PARA1 =
            "He gives a nod that contains no warmth whatsoever.";

    public static final String RP_DECLINE_PARA1 =
            "He nods once. Files it.";

    // =========================================================================
    // Kori Arrival Dialog (TT path: AIO operative acknowledges delivery)
    // Fired by XLII_FafnirKoriArrivalDialogPlugin via pickInteractionDialogPlugin
    // =========================================================================

    public static final String KORI_ARRIVAL_SCENE =
            "There's a man at the docking office threshold with the stillness of someone "
            + "who has been there longer than you've been in system. He has no rank markings "
            + "and no reason to be waiting here that isn't you.";

    public static final String KORI_ARRIVAL_DIALOG_PARA1 =
            "He doesn't offer anything except a brief glance at your vessel registration tag, "
            + "then back to you.";

    public static final String KORI_ARRIVAL_DIALOG_PARA2 =
            "\"Transit log confirmed. Credentials reconciled.\"";

    public static final String KORI_ARRIVAL_DIALOG_PARA3 =
            "He turns away. The conversation appears to be complete.";

    // =========================================================================
    // Ring-Port Delivery Dialog (Ring-Port path: contact processes shipment)
    // Fired by XLII_FafnirRingPortDeliveryDialogPlugin via pickInteractionDialogPlugin
    // =========================================================================

    public static final String RP_DELIVERY_DIALOG_PARA1 =
            "He scans your transponder without preamble and checks something off on his slate.";

    public static final String RP_DELIVERY_DIALOG_PARA2 =
            "\"Shipment's in.\" He doesn't look up. \"Good.\"";

    public static final String RP_DELIVERY_DIALOG_PARA3 =
            "He marks it in the system. No receipt. No signature. "
            + "Just a note in whatever ledger Ring-Port keeps. You're in their books.";

    public static final String RP_DELIVERY_DIALOG_PARA4 =
            "He goes back to the manifests. You're done here.";

    // =========================================================================
    // Intel panel - FafnirAccessMissionIntel
    // =========================================================================

    /** Credit reward for completing the TT Courier path. */
    public static final float REWARD_TT = 80_000f;
    /** Credit reward for completing the Ring-Port Contractor path. */
    public static final float REWARD_RP = 150_000f;

    public static final String INTEL_NAME_TT = "Smuggling - Kori, Fafnir";
    public static final String INTEL_NAME_RP = "Smuggling - Ring-Port Station";

    // Mission objective and destination labels used to build the intel description dynamically.
    public static final String INTEL_OBJECTIVE_TT    = "classified documentation";
    public static final String INTEL_OBJECTIVE_RP    = "sensitive intelligence files";
    public static final String INTEL_DESTINATION_TT  = "Kori Starport";
    public static final String INTEL_DESTINATION_RP  = "Ring-Port Station";

    /** Format arg: credit amount string (e.g. "80,000"). */
    public static final String INTEL_REWARD_LINE = "%s on delivery";
    /** Format arg: credit amount string. Used on completion. */
    public static final String INTEL_PAID_LINE   = "%s received";

    public static final String INTEL_DELIVERY_CONFIRMED = "Delivery confirmed.";

    // =========================================================================
    // In-system Intercept - Brute Force (XLII_FafnirUnauthorizedEntryDialog)
    // Fleet hail on first arrival after forced Rift transit.
    // Tone: clipped, bureaucratic, no threats of immediate action - just logged facts.
    // =========================================================================

    public static final String BF_INTERCEPT_PARA1 =
            "\"Unauthorised Rift transit is a violation of Alliance territorial jurisdiction.\"";

    public static final String BF_INTERCEPT_PARA2 =
            "\"Your entry has been flagged, your fleet logged, and the matter referred to Command.\"";

    public static final String BF_INTERCEPT_PARA3 =
            "\"You are directed to clear Fafnir space immediately. Failure to comply within the "
            + "prescribed window will be treated as a hostile incursion.\"";

    public static final String BF_INTERCEPT_PARA4 =
            "\"You have seventy-two hours. This frequency will not respond again.\"";

    public static final String OPT_BF_ACKNOWLEDGE = "Understood.";

    // =========================================================================
    // In-system Intercept - Transverse Jump (XLII_FafnirUnauthorizedEntryDialog)
    // Fleet hail on first arrival via transverse jump - no valid protocol to deny.
    // Tone: same clinical military register, but caught off-guard, no precedent found.
    // =========================================================================

    public static final String TJ_INTERCEPT_PARA1 =
            "\"We.. um... don't have a flag for... what you just did...\"";

    public static final String TJ_INTERCEPT_PARA2 =
            "An awkward pause follows, one that lasts a little too long while the captain logs something out of sight. "
            + "\"Well, we have a flag for it now.\"";

    public static final String TJ_INTERCEPT_PARA3 =
            "\"There is no precedent in Alliance regs for denying access via unsanctioned transit vectors. "
            + "I've checked. We're aware you're here.\"";

    public static final String TJ_INTERCEPT_PARA4 =
            "\"Conduct yourself accordingly. We'll be watching.\"";

    public static final String OPT_TJ_ACKNOWLEDGE = "Understood.";

    public static final String INTEL_INSTRUCTION_TT =
            "Dock at Kori Starport inside the Fafnir system to complete the delivery.";
    public static final String INTEL_INSTRUCTION_RP =
            "Dock at Ring-Port Station inside the Fafnir system to complete the delivery.";
}