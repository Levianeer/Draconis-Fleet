package levianeer.draconis.data.campaign;

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
 * Sigma Octantis core presence. Operates in two phases:
 * <p>
 * Phase 1 — Rep watch: checks Draconis reputation every 5 seconds. When the player
 * turns hostile (rep <= -0.5), sets a persistent flag and advances to Phase 2.
 * <p>
 * Phase 2 — Core watch: checks fleet officers and cargo every 5 seconds. When the
 * core is found (including after a save/load or retrieval from market storage), fires
 * the confrontation dialog.
 * <p>
 * Registered by XLII_NanoforgeExchange when the core is first awarded, and
 * re-registered by XLII_ModPlugin.onGameLoad() on subsequent loads if needed.
 * Self-removes once the confrontation fires.
 */
public class XLII_SigmaOctantisWatchdog implements EveryFrameScript {

    private static final Logger log = Global.getLogger(XLII_SigmaOctantisWatchdog.class);

    public static final String CONFRONTATION_FLAG    = "$global.XLII_sigma_octantis_confrontation_done";
    public static final String PLAYER_HOSTILE_FLAG   = "$global.XLII_sigma_octantis_player_hostile";
    public static final String WARNING_FLAG          = "$global.XLII_sigma_octantis_warning_done";
    public static final String WARNING_TIMESTAMP_KEY = "$global.XLII_sigma_octantis_warning_timestamp";
    public static final String NANOFORGE_QUEST_FLAG  = "$global.XLII_nanoforgeQuestComplete";

    private static final String DRACONIS_FACTION_ID = "XLII_draconis";
    private static final float WARNING_THRESHOLD     = -0.25f;
    private static final float HOSTILE_THRESHOLD     = -0.5f;
    private static final float WARNING_COOLDOWN_DAYS = 30f; // Time to course-correct to keep Octantis

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

            // Fire the early warning once at the warning threshold
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
            log.info("Draconis: Sigma Octantis watchdog - player went hostile (rep: " + rel
                    + "). Monitoring fleet for core presence.");
        } else {
            // Phase 2: watch fleet until the core is retrieved
            if (isSigmaOctantisInFleet()) {
                fireConfrontation();
            }
        }
    }

    // -------------------------------------------------------------------------

    private static boolean isSigmaOctantisInFleet() {
        final String CORE_ID = XLII_SigmaOctantisOfficerPlugin.CORE_ID;
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
        log.info("Draconis: Sigma Octantis early warning triggered (Draconis rep: " + rel
                + "). Grace period of " + (int) WARNING_COOLDOWN_DAYS + " days begins upon dismissal.");

        Global.getSector().getCampaignUI().showInteractionDialog(
                new XLII_SigmaOctantisWarning(),
                Global.getSector().getPlayerFleet()
        );
    }

    private void fireConfrontation() {
        float rel = Global.getSector().getFaction(DRACONIS_FACTION_ID)
                .getRelationship(Factions.PLAYER);
        log.info("Draconis: Sigma Octantis confrontation triggered (Draconis rep: " + rel + ")");

        Global.getSector().getMemoryWithoutUpdate().set(CONFRONTATION_FLAG, true);
        done = true;

        Global.getSector().getCampaignUI().showInteractionDialog(
                new XLII_SigmaOctantisConfrontation(),
                Global.getSector().getPlayerFleet()
        );
    }
}
