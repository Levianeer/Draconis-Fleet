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

    /** Set once the first-entry Rift dialog has fired (or been deliberately skipped). */
    public static final String MEM_RIFT_FIRST_ENTRY_DONE = "$fafnirRiftFirstEntryDone";

    /**
     * Set on a fleet entity (via {@code fleet.getMemoryWithoutUpdate()}) when the
     * monitor dispatches it to intercept the player. Cleared by
     * {@code XLII_CampaignPlugin} once the intercept dialog fires.
     */
    public static final String MEM_FLEET_INTERCEPT_TAG      = "$fafnirInterceptFleet";

    /** Set when the brute-force in-system intercept dialog has fired; guards against re-triggering it. */
    public static final String MEM_BF_INTERCEPT_DONE        = "$fafnirBFInterceptDone";
    /** Set when the transverse-jump intercept dialog has fired. */
    public static final String MEM_TRANSVERSE_INTERCEPT_DONE = "$fafnirTransverseInterceptDone";

    public static final String PATH_TT_COURIER       = "tt_courier";
    public static final String PATH_RING_PORT        = "ring_port";
    public static final String PATH_BRUTE_FORCE      = "brute_force";
    public static final String PATH_TRANSVERSE_JUMP  = "transverse_jump";

    // =========================================================================
    // Reputation deltas applied at access time (0-1 float, Starsector rep scale)
    // =========================================================================

    /** +rep: sanctioned TT-coordinated entry */
    public static final float REP_GRANT_TT = 0.1f;
    /** -rep: forced Rift transit, flagged and logged as a jurisdictional violation */
    public static final float REP_GRANT_BF = -0.1f;
    /** -rep: pirate-channel arrival noted in the ledger */
    public static final float REP_GRANT_RP = -0.1f;
    /** Small rep hit on transverse-jump entry. */
    public static final float REP_TRANSVERSE_DELTA  = -0.1f;
    /** Transverse-jump rep penalty is floored here - never push the player to hostile on entry. */
    public static final float REP_TRANSVERSE_FLOOR  = -0.25f;

    // =========================================================================
    // Jump Point - Military IFF denial (Itoron's JP, Fringe JP)
    // =========================================================================

    public static final String MILITARY_DENIED_PARA1 =
            "The approach does not resolve.";

    public static final String MILITARY_DENIED_PARA2 =
            "Your navigation systems return nothing actionable. Without current density mapping and approach geometry, "
            + "the Rift ahead is just radiation - no path, no bearing, nothing to follow through.";

    public static final String MILITARY_DENIED_PARA3 =
            "However, given traffic frequency, someone has to know how to make the approach. Draconis has been known to do "
            + "dealings with the Tri-Tachyon Corporation, and the Pirates often run guns to the First Fleet Rebels in Ring-Port.";

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

    public static final String OPT_APPROACH_JP = "Approach the jump point";

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
            "A stoic looking and corporately dressed woman sits alone. Professional attire - "
            + "civilian clothes, public market, a drink she isn't drinking. "
            + "She sees you notice her and gives a small wave.";

    public static final String TT_BAR_OPTION_APPROACH =
            "Approach the corporate woman in the corner.";

    // =========================================================================
    // Bar Event - Ring-Port (approach prompt + dialog)
    // =========================================================================

    public static final String RP_BAR_SCENE =
            "An older gruff looking man. Military posture in civilian clothes. "
            + "He makes a small gesture - not an invitation exactly, more like indicating "
            + "there's a conversation available if you want it.";

    public static final String RP_BAR_OPTION_APPROACH =
            "Approach the gruff looking man.";

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

    public static final String INTEL_DELETE_BUTTON = "Delete entry";

    /** Shown when deleting while the contract is still live - the reward is forfeit. */
    public static final String INTEL_DELETE_CONFIRM_ACTIVE =
            "Deleting this entry drops the contract. The delivery will no longer be tracked "
            + "and no payment will be made. This cannot be undone.";

    /** Shown when deleting after the delivery has already been paid out. */
    public static final String INTEL_DELETE_CONFIRM_DONE =
            "Are you sure you want to permanently delete this entry?";

    public static final String INTEL_INSTRUCTION_TT =
            "Dock at Kori Starport inside the Fafnir system to complete the delivery.";
    public static final String INTEL_INSTRUCTION_RP =
            "Dock at Ring-Port Station inside the Fafnir system to complete the delivery.";
}