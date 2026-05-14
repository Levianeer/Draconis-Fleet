package levianeer.draconis.data.campaign.intel.fafnir;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.econ.XLII_HighCommand;
import org.apache.log4j.Logger;

/**
 * EveryFrameScript that detects unauthorized player entry into the Fafnir system
 * and dispatches a DDA patrol fleet to intercept.
 * <p>
 * Two scenarios are handled:
 * <ul>
 *   <li><b>Brute Force</b> – player used 10 SP + 35% CR at a Fafnir jump point.
 *       {@code $fafnirAccessGranted} and {@code $fafnirEntryPath = "brute_force"} are
 *       set before transit. On entry, the monitor finds the nearest DDA fleet, tags it
 *       with {@link FafnirAccessStrings#MEM_FLEET_INTERCEPT_TAG}, and issues an INTERCEPT
 *       order. {@link levianeer.draconis.data.campaign.XLII_CampaignPlugin} intercepts
 *       the resulting fleet interaction and shows the BF dialog. The monitor then runs
 *       the {@link FafnirAccessStrings#BRUTE_FORCE_GRACE_DAYS} hostility timer.</li>
 *   <li><b>Transverse Jump</b> – player bypassed all JPs. Same fleet dispatch; the
 *       campaign plugin shows the TJ dialog and applies effects on interaction.</li>
 * </ul>
 * <p>
 * If no DDA fleet is present in Fafnir when the player enters, the player slips through
 * undetected. The monitor keeps checking in case a fleet enters the system later.
 * <p>
 * Fleet INTERCEPT assignments are not persisted across save/load; the monitor re-issues
 * the assignment on every check tick as long as the fleet is not in combat.
 * <p>
 * Registered by {@code XLII_ModPlugin.onGameLoad()} whenever {@link #shouldRegister()}
 * returns true. Cleaned up by {@code cleanupOldScripts()} before re-registration.
 * Self-terminates once all pending work is complete.
 */
public class XLII_FafnirSystemMonitor implements EveryFrameScript {

    private static final Logger log = Global.getLogger(XLII_FafnirSystemMonitor.class);

    private static final String DRACONIS_FACTION_ID    = "XLII_draconis";
    private static final String FORTYSECOND_FACTION_ID = "XLII_fortysecond";
    private static final String FAFNIR_SYSTEM_NAME     = "Fafnir";

    /**
     * Max duration passed to addAssignment - fleet gives up the INTERCEPT order
     * after this many days if it still hasn't reached the target.
     */
    private static final float INTERCEPT_ASSIGNMENT_DAYS = 3f;

    /**
     * Key stored on the fleet's own memory to record its HighCommand home market entity.
     * Set during dispatch so {@link #releaseFleet} can restore the patrol assignment.
     */
    private static final String FLEET_MEM_HOME = "$fafnirInterceptHome";

    /** Days of ORBIT_AGGRESSIVE re-issued to a HighCommand fleet after intercept ends. */
    private static final float PATROL_RESUME_DAYS = 14f;

    /** Check interval in seconds - same cadence as the Sigma Octantis watchdog. */
    private final IntervalUtil checkInterval = new IntervalUtil(5f, 5f);

    /**
     * The fleet currently ordered to intercept the player. Serialized with the script.
     * Null until a DDA fleet is found and dispatched.
     */
    private CampaignFleetAPI interceptingFleet = null;

    private boolean done = false;

    // -------------------------------------------------------------------------
    // EveryFrameScript
    // -------------------------------------------------------------------------

    @Override
    public boolean isDone() { return done; }

    @Override
    public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        if (done) return;

        checkInterval.advance(amount);
        if (!checkInterval.intervalElapsed()) return;

        StarSystemAPI fafnir = Global.getSector().getStarSystem(FAFNIR_SYSTEM_NAME);
        if (fafnir == null) return;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        boolean playerInFafnir = fafnir.equals(playerFleet.getContainingLocation());

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        // --- BF timer phase ---
        // Dialog was already shown by pickInteractionDialogPlugin; counting down to hostility.
        // If the player leaves Fafnir before the timer expires, cancel it - leaving peacefully
        // is treated as compliance with the 72-hour order. Apply the lighter transverse-jump
        // rep penalty instead so there is still a consequence for the intrusion.
        if (mem.getBoolean(FafnirAccessStrings.MEM_BF_INTERCEPT_DONE)) {
            if (!playerInFafnir) {
                log.info("Draconis: Fafnir BF timer cancelled - player left system, applying TJ rep penalty");
                applyTransverseRepPenalty();
                mem.unset(FafnirAccessStrings.MEM_BF_WARNING_TIMESTAMP);
                done = true;
                return;
            }
            Long ts = (Long) mem.get(FafnirAccessStrings.MEM_BF_WARNING_TIMESTAMP);
            if (ts != null) {
                float elapsed = Global.getSector().getClock().getElapsedDaysSince(ts);
                if (elapsed >= FafnirAccessStrings.BRUTE_FORCE_GRACE_DAYS) {
                    applyBFHostility(mem);
                    done = true;
                }
            }
            return;
        }

        // --- Fleet dispatch: only relevant while player is in Fafnir ---
        if (!playerInFafnir) return;

        // Commissioned players are pre-authorized; shut down the monitor
        if ("XLII_draconis".equals(Misc.getCommissionFactionId())) { done = true; return; }

        // --- Determine which intercept scenario is pending ---
        boolean isBruteForce = FafnirAccessStrings.PATH_BRUTE_FORCE.equals(
                mem.getString(FafnirAccessStrings.MEM_ENTRY_PATH));

        boolean isTransverse = !mem.getBoolean(FafnirAccessStrings.MEM_ACCESS_GRANTED)
                && !mem.getBoolean(FafnirAccessStrings.MEM_TRANSVERSE_INTERCEPT_DONE)
                && !mem.getBoolean(FafnirAccessStrings.MEM_TT_QUEST_ACTIVE)
                && !mem.getBoolean(FafnirAccessStrings.MEM_RP_QUEST_ACTIVE);

        if (!isBruteForce && !isTransverse) {
            done = true;
            return;
        }

        // --- Ensure we have a live tagged fleet to intercept with ---
        if (!isFleetAliveInFafnir(interceptingFleet, fafnir)) {
            // First time detecting entry, or previously dispatched fleet has left/despawned.
            // Look for a new one. If none is present, player slips through undetected
            // this tick; we'll check again on the next interval.
            interceptingFleet = findNearestDDAFleet(fafnir, playerFleet);
            if (interceptingFleet == null) return;

            interceptingFleet.getMemoryWithoutUpdate()
                    .set(FafnirAccessStrings.MEM_FLEET_INTERCEPT_TAG, true);

            // Save the fleet's HighCommand home entity so releaseFleet() can restore
            // the patrol assignment. Should always succeed since findNearestDDAFleet()
            // only returns patrol fleets; log a warning if the industry is missing.
            SectorEntityToken home = findHighCommandHome(interceptingFleet, fafnir);
            if (home != null) {
                interceptingFleet.getMemoryWithoutUpdate().set(FLEET_MEM_HOME, home);
            } else {
                log.warn("Draconis: Fafnir intercept - no HighCommand found in system for patrol fleet "
                        + interceptingFleet.getNameWithFaction() + "; patrol will not be restored on release");
            }

            log.info("Draconis: Fafnir intercept - dispatching "
                    + interceptingFleet.getNameWithFaction());
        }

        // Re-issue INTERCEPT assignment each tick to survive save/load resets.
        // Only skip if the fleet is currently in combat (don't yank it out of a fight).
        // XLII_CampaignPlugin.pickInteractionDialogPlugin() will intercept the resulting
        // fleet interaction and show our custom dialog instead of the vanilla fleet dialog.
        if (interceptingFleet.getBattle() == null) {
            interceptingFleet.clearAssignments();
            interceptingFleet.addAssignment(
                    FleetAssignment.INTERCEPT, playerFleet, INTERCEPT_ASSIGNMENT_DAYS, (Script) null);
        }
    }

    // -------------------------------------------------------------------------
    // Hostility application (called after 3-day BF grace period)
    // -------------------------------------------------------------------------

    /** Applies the transverse-jump rep penalty, floored so the player is never pushed to hostile. */
    public static void applyTransverseRepPenalty() {
        FactionAPI draconis = Global.getSector().getFaction(DRACONIS_FACTION_ID);
        float current = draconis.getRelationship(Factions.PLAYER);
        float newRep  = Math.max(FafnirAccessStrings.REP_TRANSVERSE_FLOOR,
                                 current + FafnirAccessStrings.REP_TRANSVERSE_DELTA);
        float actual  = newRep - current;
        if (actual != 0f) {
            Global.getSector().getPlayerFaction()
                    .adjustRelationship(DRACONIS_FACTION_ID, actual);
            log.info(String.format("Draconis: Transverse jump rep: %.2f -> %.2f (delta %.2f)",
                    current, newRep, actual));
        }
    }

    public static void applyBFHostility(MemoryAPI mem) {
        log.info("Draconis: Fafnir BF grace period expired - applying hostility");
        Global.getSector().getPlayerFaction()
                .adjustRelationship(DRACONIS_FACTION_ID, FafnirAccessStrings.REP_BF_HOSTILE_DELTA);
        mem.unset(FafnirAccessStrings.MEM_BF_WARNING_TIMESTAMP);
        log.info("Draconis: DDA rep delta applied: " + FafnirAccessStrings.REP_BF_HOSTILE_DELTA);
    }

    // -------------------------------------------------------------------------
    // Effects applied by XLII_CampaignPlugin when the interaction fires
    // -------------------------------------------------------------------------

    /**
     * Called by {@link levianeer.draconis.data.campaign.XLII_CampaignPlugin}
     * when it intercepts an interaction with the tagged fleet.
     * Applies pre-dialog side-effects and releases the fleet from its intercept order.
     *
     * @param fleet the intercepting fleet (will have its assignments cleared and tag removed)
     * @param mem   global sector memory
     */
    public static void onBruteForceInterceptFired(CampaignFleetAPI fleet, MemoryAPI mem) {
        log.info("Draconis: Fafnir BF intercept - interaction intercepted by campaign plugin");
        mem.set(FafnirAccessStrings.MEM_BF_INTERCEPT_DONE, true);
        mem.set(FafnirAccessStrings.MEM_BF_WARNING_TIMESTAMP,
                Global.getSector().getClock().getTimestamp());
        releaseFleet(fleet);
    }

    /**
     * Called by {@link levianeer.draconis.data.campaign.XLII_CampaignPlugin}
     * when it intercepts an interaction with the tagged fleet.
     * Applies all transverse-jump side-effects and releases the fleet.
     *
     * @param fleet the intercepting fleet
     * @param mem   global sector memory
     */
    public static void onTransverseInterceptFired(CampaignFleetAPI fleet, MemoryAPI mem) {
        log.info("Draconis: Fafnir transverse jump intercept - interaction intercepted");
        mem.set(FafnirAccessStrings.MEM_TRANSVERSE_INTERCEPT_DONE, true);
        mem.set(FafnirAccessStrings.MEM_ACCESS_GRANTED, true);
        mem.set(FafnirAccessStrings.MEM_ENTRY_PATH, FafnirAccessStrings.PATH_TRANSVERSE_JUMP);

        applyTransverseRepPenalty();
        releaseFleet(fleet);
    }

    /**
     * Clears the INTERCEPT assignment and the intercept tag from the fleet.
     * If the fleet was a HighCommand patrol (home entity saved in fleet memory),
     * re-queues ORBIT_AGGRESSIVE -> GO_TO_LOCATION_AND_DESPAWN so it resumes normal duty
     * rather than idling indefinitely.
     */
    private static void releaseFleet(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.isDespawning()) return;

        SectorEntityToken home = (SectorEntityToken) fleet.getMemoryWithoutUpdate().get(FLEET_MEM_HOME);

        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().unset(FafnirAccessStrings.MEM_FLEET_INTERCEPT_TAG);
        fleet.getMemoryWithoutUpdate().unset(FLEET_MEM_HOME);

        if (home != null) {
            fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, home, PATROL_RESUME_DAYS, (Script) null);
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, home, 1000f, (Script) null);
            log.info("Draconis: Fafnir intercept - HighCommand fleet released, patrol assignments restored");
        }
    }

    // -------------------------------------------------------------------------
    // Fleet helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if {@code fleet} is non-null, not despawning, still located inside
     * the Fafnir system, and still tagged as the active intercept fleet.
     */
    private static boolean isFleetAliveInFafnir(CampaignFleetAPI fleet, StarSystemAPI fafnir) {
        return fleet != null
                && !fleet.isDespawning()
                && fafnir.equals(fleet.getContainingLocation())
                && fleet.getMemoryWithoutUpdate().getBoolean(FafnirAccessStrings.MEM_FLEET_INTERCEPT_TAG);
    }

    /**
     * Returns the nearest untagged DDA patrol fleet in the Fafnir system to the player,
     * or {@code null} if none is present.
     * <p>
     * Only idle vanilla {@link MemFlags#MEMORY_KEY_PATROL_FLEET} fleets are eligible.
     * This excludes raid fleets, custom mod fleets, and any fleet whose assignments
     * cannot be safely interrupted and restored via {@link #releaseFleet}. Fleets
     * already engaged in a military response, busy, or performing a special action
     * are also skipped so we do not interfere with those systems.
     */
    private static CampaignFleetAPI findNearestDDAFleet(StarSystemAPI fafnir,
                                                         CampaignFleetAPI playerFleet) {
        CampaignFleetAPI nearest     = null;
        float            nearestDist = Float.MAX_VALUE;

        for (CampaignFleetAPI fleet : fafnir.getFleets()) {
            if (fleet.isPlayerFleet()) continue;
            if (fleet.isDespawning()) continue;
            String factionId = fleet.getFaction().getId();
            if (!DRACONIS_FACTION_ID.equals(factionId) && !FORTYSECOND_FACTION_ID.equals(factionId)) continue;
            if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) continue;
            // Skip patrol fleets that are already engaged in another vanilla system
            if (fleet.getMemoryWithoutUpdate().contains(MemFlags.FLEET_MILITARY_RESPONSE)) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_BUSY)) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_SPECIAL_ACTION)) continue;

            float dist = Misc.getDistance(fleet, playerFleet);
            if (dist < nearestDist) {
                nearest     = fleet;
                nearestDist = dist;
            }
        }

        if (nearest != null) {
            log.debug("Draconis: Nearest DDA fleet: " + nearest.getNameWithFaction()
                    + " (dist " + (int) nearestDist + ")");
        }
        return nearest;
    }

    /**
     * If {@code fleet} is a HighCommand patrol fleet (has {@link MemFlags#MEMORY_KEY_PATROL_FLEET}
     * and the system contains an active {@link XLII_HighCommand} industry), returns the
     * primary entity of that market so it can be used to restore patrol assignments on release.
     * Returns {@code null} for any other fleet type.
     */
    private static SectorEntityToken findHighCommandHome(CampaignFleetAPI fleet, StarSystemAPI system) {
        if (!fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) return null;

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!system.equals(market.getContainingLocation())) continue;
            for (Industry i : market.getIndustries()) {
                if (i instanceof XLII_HighCommand) return market.getPrimaryEntity();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Registration guard
    // -------------------------------------------------------------------------

    /**
     * Returns true if there is still pending work for the monitor to do, so it should
     * be (re-)registered on game load. Avoids running a no-op instance every load.
     */
    public static boolean shouldRegister() {
        // Commissioned players are pre-authorized; no monitor needed
        if ("XLII_draconis".equals(Misc.getCommissionFactionId())) return false;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        // BF intercept not yet fired
        if (FafnirAccessStrings.PATH_BRUTE_FORCE.equals(mem.getString(FafnirAccessStrings.MEM_ENTRY_PATH))
                && !mem.getBoolean(FafnirAccessStrings.MEM_BF_INTERCEPT_DONE)) {
            return true;
        }

        // BF timer still counting down
        if (mem.getBoolean(FafnirAccessStrings.MEM_BF_INTERCEPT_DONE)
                && mem.get(FafnirAccessStrings.MEM_BF_WARNING_TIMESTAMP) != null) {
            return true;
        }

        // Potential transverse jump (no access granted, intercept not done)
        if (!mem.getBoolean(FafnirAccessStrings.MEM_ACCESS_GRANTED)
                && !mem.getBoolean(FafnirAccessStrings.MEM_TRANSVERSE_INTERCEPT_DONE)) {
            return true;
        }

        return false;
    }
}