package levianeer.draconis.data.campaign.companion;

/**
 * User-facing strings for the Korrin companion system.
 * Conversation text lives in rules.csv - only intel-panel and campaign-message text belongs here.
 */
public class KorrinStrings {

    public static final String INTEL_NAME = "Contact: Elias Korrin";

    // Intel panel - posting lines, one per state
    /**
     * Current status, stated plainly for the contact panel. Where he is and what he is doing at
     * this moment is scene-setting and belongs in the Talk conversation, not here - see the
     * XLII_KorrinTalk greetings in rules.csv.
     */
    public static final String STATUS_STATIONED =
            "Posted at Ring-Port."; // Technically should never be seen.

    public static final String STATUS_COMMANDING =
            "Serving as an officer in your fleet, with a ship under him.";

    public static final String STATUS_OFFICER_NO_SHIP =
            "On your officer roster. No ship currently assigned to him.";

    public static final String STATUS_RESERVE =
            "Aboard in reserve. Holding no command and drawing no billet.";

    /**
     * PLACEHOLDER TEXT. State reached only via KorrinCompanion.leaveForever() - the Uplink to God
     * failure sequence (see .claude/systems/uplink-to-god-endgame-redesign.md). Terminal; no
     * buttons are ever shown for this state (see KorrinIntel's button switch).
     */
    public static final String STATUS_GONE =
            "[PLACEHOLDER] Gone. Not coming back.";

    public static final String BUTTON_TALK_TEXT = "Talk";
    public static final String BUTTON_TAKE_COMMAND_TEXT = "Give him a command";
    public static final String BUTTON_STAND_DOWN_TEXT = "Stand him down";
    public static final String BUTTON_RETURN_TO_POST_TEXT = "Return him to his post";

    // Intel panel - backlog
    public static final String BACKLOG_ONE = "He has something he wants to say to you.";
    public static final String BACKLOG_MANY = "He has %s things he wants to say to you.";

    // Campaign messages
    public static final String MSG_STOOD_DOWN =
            "Elias Korrin has given up his command. He remains with your fleet.";
    /** Aboard: he is right there, and it can wait until the player has a minute. */
    public static final String MSG_WANTS_A_WORD =
            "Elias Korrin would like a word when you have a minute.";
    /** Posted to Ring-Port: a man at a station does not call you mid-flight. */
    public static final String MSG_LEFT_WORD =
            "Elias Korrin has left word for you.";

    private KorrinStrings() {}
}