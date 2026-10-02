package levianeer.draconis.data.campaign.intel.longsight;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import org.apache.log4j.Logger;

/**
 * EveryFrameScript that monitors the player's Draconis Alliance reputation and
 * Longsight core presence. Operates in two phases:
 * <p>
 * Phase 1 - Rep watch: checks Draconis reputation every 5 seconds. When the player
 * turns hostile (rep <= -0.5), sets a persistent flag and advances to Phase 2.
 * <p>
 * Phase 2 - Core watch: checks fleet officers and cargo every 5 seconds. When the
 * core is found (including after a save/load or retrieval from market storage), fires
 * the confrontation dialog.
 * <p>
 * Registered by XLII_NanoforgeExchange when the core is first awarded, and
 * re-registered by XLII_ModPlugin.onGameLoad() on subsequent loads if needed.
 * Self-removes once the confrontation fires.
 */
public class XLII_LongsightWatchdog implements EveryFrameScript {

    private static final Logger log = Global.getLogger(XLII_LongsightWatchdog.class);

    public static final String CONFRONTATION_FLAG    = "$global.XLII_longsight_confrontation_done";
    public static final String PLAYER_HOSTILE_FLAG   = "$global.XLII_longsight_player_hostile";
    public static final String WARNING_FLAG          = "$global.XLII_longsight_warning_done";
    public static final String WARNING_TIMESTAMP_KEY = "$global.XLII_longsight_warning_timestamp";

    /**
     * Gates re-registration on game load - see XLII_ModPlugin.onGameLoad(). Used to be
     * "$XLII_nanoforgeQuestComplete", set the moment the nanoforge is merely delivered
     * (XLII_nanoforge_deliver_success, the first step of the questline) - true for virtually
     * every player who progresses this far, regardless of ending. That re-armed the watchdog on
     * load even for a player who took the Burn the Machine (destroy) route and never held the
     * Longsight core. The watchdog only makes sense once the player actually has the uplink
     * (Office Takeover / Cave path), which is what this flag tracks - no "$global." prefix on the
     * literal key, matching LongsightQuestMission's own
     * GlobalBooleanChecker("$XLII_longsightUplinkGranted"). Currently set by
     * XLII_KoriStrike's showAugustInterceptCave() directly in Java (previously set by
     * XLII_interception_cave in rules.csv, since deleted - see
     * .claude/systems/uplink-to-god-endgame-redesign.md). First-time registration
     * (XLII_NanoforgeExchange.giveUplink(), called from both entry points) was already correct;
     * only the on-load re-registration gate was wrong.
     */
    public static final String UPLINK_GRANTED_FLAG   = "$XLII_longsightUplinkGranted";

    private static final String DRACONIS_FACTION_ID = "XLII_draconis";
    private static final float WARNING_THRESHOLD     = -0.25f;
    private static final float HOSTILE_THRESHOLD     = -0.5f;
    private static final float WARNING_COOLDOWN_DAYS = 30f; // Time to course-correct to keep Longsight

    private final IntervalUtil checkInterval = new IntervalUtil(5f, 5f);

    private boolean done = false;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(CONFRONTATION_FLAG)) {
            done = true;
            return;
        }

        checkInterval.advance(amount);
        if (!checkInterval.intervalElapsed()) return;

        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(PLAYER_HOSTILE_FLAG)) {
            // Phase 1: watch rep until the player turns hostile
            FactionAPI draconis = Global.getSector().getFaction(DRACONIS_FACTION_ID);
            if (draconis == null) return;

            float rel = draconis.getRelationship(Factions.PLAYER);

            if (rel <= WARNING_THRESHOLD
                    && !Global.getSector().getMemoryWithoutUpdate().getBoolean(WARNING_FLAG)) {
                fireWarning(rel);
            }

            if (rel > HOSTILE_THRESHOLD) return;

            // After the warning fires, give the player a grace period to course-correct
            // before locking in the hostile phase.
            Long warningTs = (Long) Global.getSector().getMemoryWithoutUpdate()
                    .get(WARNING_TIMESTAMP_KEY);
            if (warningTs != null) {
                float elapsed = Global.getSector().getClock().getElapsedDaysSince(warningTs);
                if (elapsed < WARNING_COOLDOWN_DAYS) return;
            }

            Global.getSector().getMemoryWithoutUpdate().set(PLAYER_HOSTILE_FLAG, true);
            log.info("Draconis: Longsight watchdog - player went hostile (rep: " + rel
                    + "). Monitoring fleet for core presence.");
        } else {
            // Phase 2: watch fleet until the core is retrieved
            if (isLongsightInFleet()) {
                fireConfrontation();
            }
        }
    }

    // -------------------------------------------------------------------------

    private static boolean isLongsightInFleet() {
        final String CORE_ID = XLII_LongsightOfficerPlugin.CORE_ID;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            PersonAPI captain = member.getCaptain();
            if (captain != null && CORE_ID.equals(captain.getAICoreId())) return true;
        }

        for (OfficerDataAPI officerData : playerFleet.getFleetData().getOfficersCopy()) {
            if (CORE_ID.equals(officerData.getPerson().getAICoreId())) return true;
        }

        return playerFleet.getCargo().getCommodityQuantity(CORE_ID) > 0f;
    }

    private void fireWarning(float rel) {
        log.info("Draconis: Longsight early warning triggered (Draconis rep: " + rel
                + "). Grace period of " + (int) WARNING_COOLDOWN_DAYS + " days begins upon dismissal.");

        Global.getSector().getCampaignUI().showInteractionDialog(
                new XLII_LongsightWarning(),
                Global.getSector().getPlayerFleet()
        );
    }

    private void fireConfrontation() {
        float rel = Global.getSector().getFaction(DRACONIS_FACTION_ID)
                .getRelationship(Factions.PLAYER);
        log.info("Draconis: Longsight confrontation triggered (Draconis rep: " + rel + ")");

        Global.getSector().getMemoryWithoutUpdate().set(CONFRONTATION_FLAG, true);
        done = true;

        Global.getSector().getCampaignUI().showInteractionDialog(
                new XLII_LongsightConfrontation(),
                Global.getSector().getPlayerFleet()
        );
    }
}
