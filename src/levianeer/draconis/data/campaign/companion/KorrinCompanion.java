package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import levianeer.draconis.data.campaign.characters.XLII_PersonEliasKorrin;
import org.apache.log4j.Logger;

/**
 * State machine and single mutation point for the Korrin companion system.
 * See .claude/systems/korrin-companion.md.
 * <p>
 * Design rule: the vanilla fleet UI can move Korrin out of the officer roster, but it can never
 * remove him from the player. Only {@link #returnToStation()} - reachable from his intel entry -
 * sends him away, and that is reversible.
 * <p>
 * One deliberate exception: {@link #leaveForever()} is a one-way transition into {@link
 * State#GONE}, fired only by the Uplink to God failure sequence (see
 * .claude/systems/uplink-to-god-endgame-redesign.md) when Korrin rode along and witnessed it. Every
 * other transition method in this class guards on its own required starting state, so none of them
 * can move a GONE Korrin anywhere else - that is what makes it terminal, not a special check here.
 */
public class KorrinCompanion {

    private static final Logger log = Global.getLogger(KorrinCompanion.class);

    public enum State {
        /** Not yet encountered. */
        UNMET,
        /** Posted at Ring-Port; reachable over the comm relay. */
        STATIONED,
        /** On the player's officer roster, commanding a ship. */
        ABOARD_OFFICER,
        /** Traveling with the player's fleet, holding no command. */
        ABOARD_RESERVE,
        /** Terminal - permanently gone. See {@link #leaveForever()}. */
        GONE
    }

    public static final String STATE_KEY = "$korrin_state";
    /** Set once he has been given a command, so the first-command comment fires only once. */
    private static final String FIRST_COMMAND_KEY = "$korrin_hadFirstCommand";

    /**
     * While Korrin holds a command he grants a matching officer slot, so taking him on never
     * costs the player one of their own. Flip to false to make him consume a slot instead.
     */
    private static final boolean GRANTS_OFFICER_SLOT = true;
    private static final String OFFICER_SLOT_MOD_ID = "XLII_korrin_aboard";

    private KorrinCompanion() {}

    public static PersonAPI getKorrin() {
        return Global.getSector().getImportantPeople().getPerson(XLII_PersonEliasKorrin.PERSON_ID);
    }

    public static State getState() {
        PersonAPI korrin = getKorrin();
        if (korrin == null) return State.UNMET;

        String raw = korrin.getMemoryWithoutUpdate().getString(STATE_KEY);
        if (raw == null) return State.UNMET;
        try {
            return State.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Draconis: unrecognised Korrin state '" + raw + "', treating as UNMET");
            return State.UNMET;
        }
    }

    public static boolean isAboard() {
        State state = getState();
        return state == State.ABOARD_OFFICER || state == State.ABOARD_RESERVE;
    }

    /**
     * True if he actually has a ship under him, not merely a place on the officer roster. The
     * fleet screen lets the player strip his command without standing him down, so these are
     * genuinely different situations.
     */
    public static boolean hasCommand() {
        PersonAPI korrin = getKorrin();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (korrin == null || fleet == null) return false;
        return fleet.getFleetData().getMemberWithCaptain(korrin) != null;
    }

    public static boolean isOnOfficerRoster() {
        PersonAPI korrin = getKorrin();
        if (korrin == null) return false;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        for (OfficerDataAPI od : fleet.getFleetData().getOfficersCopy()) {
            if (od.getPerson() == korrin) return true;
        }
        return false;
    }

    /**
     * Re-establishes runtime state on game load: creates his intel entry if he has been met,
     * and re-applies the officer slot modifier if he currently holds a command.
     */
    public static void init() {
        // Migration: builds before this one borrowed the player fleet's active person to scope
        // dialog memory. Nothing sets it any more, so clear anything a test save still carries.
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null && fleet.getActivePerson() != null
                && fleet.getActivePerson() == getKorrin()) {
            fleet.setActivePerson(null);
        }

        if (getState() == State.UNMET) return;

        KorrinIntel.ensureExists();
        if (getState() == State.ABOARD_OFFICER) {
            applyOfficerSlot(true);
        }
    }

    // --- Transitions -------------------------------------------------------------------------

    /** UNMET -> STATIONED. Called when the player first makes contact (Blind Eye Act 2). */
    public static void meet() {
        if (getState() != State.UNMET) return;
        setState(State.STATIONED);
        KorrinIntel.ensureExists();
    }

    /** STATIONED -> ABOARD_RESERVE. Drops him from Ring-Port's comm directory; he has left. */
    public static void comeAboard() {
        if (getState() != State.STATIONED) return;
        setState(State.ABOARD_RESERVE);
        XLII_PersonEliasKorrin.updatePlacement();
    }

    /** ABOARD_RESERVE -> ABOARD_OFFICER. Puts him on the officer roster. */
    public static void takeCommand() {
        if (getState() != State.ABOARD_RESERVE) return;

        PersonAPI korrin = getKorrin();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (korrin == null || fleet == null) return;

        applyOfficerSlot(true);
        if (!isOnOfficerRoster()) {
            fleet.getFleetData().addOfficer(korrin);
        }
        setState(State.ABOARD_OFFICER);

        // He has wanted a hull of his own for a long time and has never once asked for one.
        // Fires only the first time; standing him down and re-appointing him is not the beat.
        if (!korrin.getMemoryWithoutUpdate().getBoolean(FIRST_COMMAND_KEY)) {
            korrin.getMemoryWithoutUpdate().set(FIRST_COMMAND_KEY, true);
            KorrinTopicQueue.queue("korrin_first_command");
        }
    }

    /**
     * ABOARD_OFFICER -> ABOARD_RESERVE. He leaves the role, not the fleet.
     * Safe to call whether he is still on the roster or the player already removed him.
     */
    public static void standDown() {
        if (getState() != State.ABOARD_OFFICER) return;

        PersonAPI korrin = getKorrin();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (korrin != null && fleet != null && isOnOfficerRoster()) {
            // Null the captain before removing the officer - see MarketCMD.mercLeaves().
            FleetMemberAPI member = fleet.getFleetData().getMemberWithCaptain(korrin);
            if (member != null) member.setCaptain(null);
            fleet.getFleetData().removeOfficer(korrin);
        }
        applyOfficerSlot(false);
        setState(State.ABOARD_RESERVE);
    }

    /** ABOARD_* -> STATIONED. The only way to send him away, and it is still reversible. */
    public static void returnToStation() {
        if (!isAboard()) return;
        if (getState() == State.ABOARD_OFFICER) standDown();
        setState(State.STATIONED);
        XLII_PersonEliasKorrin.updatePlacement();
    }

    public static boolean isGoneForever() {
        return getState() == State.GONE;
    }

    /**
     * ABOARD_OFFICER/ABOARD_RESERVE -> GONE. Terminal, deliberately not reversible - see the class
     * doc. Only called from the Uplink to God failure sequence (XLII_KoriStrike, on a lost fleet
     * fight) and only when Korrin rode along ($XLII_burnTheMachineEliasStayed) and witnessed it.
     * Strips him from the officer roster/slot the same way standDown() does, then locks the state.
     */
    public static void leaveForever() {
        if (!isAboard()) return;

        PersonAPI korrin = getKorrin();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (korrin != null && fleet != null && isOnOfficerRoster()) {
            // Null the captain before removing the officer - see MarketCMD.mercLeaves(), same
            // reasoning as standDown() above.
            FleetMemberAPI member = fleet.getFleetData().getMemberWithCaptain(korrin);
            if (member != null) member.setCaptain(null);
            fleet.getFleetData().removeOfficer(korrin);
        }
        applyOfficerSlot(false);
        setState(State.GONE);
    }

    // --- Internals ---------------------------------------------------------------------------

    private static void setState(State state) {
        PersonAPI korrin = getKorrin();
        if (korrin == null) return;
        korrin.getMemoryWithoutUpdate().set(STATE_KEY, state.name());
        log.info("Draconis: Korrin state -> " + state);
    }

    private static void applyOfficerSlot(boolean apply) {
        if (!GRANTS_OFFICER_SLOT) return;

        PersonAPI player = Global.getSector().getPlayerPerson();
        if (player == null) return;

        if (apply) {
            player.getStats().getOfficerNumber().modifyFlat(OFFICER_SLOT_MOD_ID, 1f, KorrinStrings.INTEL_NAME);
        } else {
            player.getStats().getOfficerNumber().unmodifyFlat(OFFICER_SLOT_MOD_ID);
        }
    }
}
