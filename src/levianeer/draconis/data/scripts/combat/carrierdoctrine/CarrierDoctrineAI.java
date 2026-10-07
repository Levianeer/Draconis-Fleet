package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.loading.WingRole;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * AI-fleet-only "carrier doctrine" controller. One instance per battle, for the non-player side
 * only (see {@link CarrierDoctrinePlugin}). Structurally mirrors vanilla's ThreatCombatStrategyAI
 * (ticked at ~1s, actuates through CombatTaskManagerAPI, bypasses the normal admiral AI while
 * active) but with a fleet-composition gate specific to this doctrine: it only runs at all while
 * the fleet's own carrier(s) are at least as large as everything else it has deployed.
 * <p>
 * M1: role tagging (including per-carrier strike/CAP wing capability) and formation geometry.
 * M2: deck-load strike/recover cycling for carriers on STRIKE duty.
 * M3: CAP ladder (promote a STRIKE carrier onto CAP when enemy bombers outnumber coverage),
 * CAP escort targeting, and the fighter-only sweep wave ahead of bomber-carrying carriers.
 * M4: mission flag (COVER/DESTROY), the station-defense formation anchor, and the Leyte-rule
 * escort reservation.
 * M5: endurance/withdrawal (hull-cripple and PPT-exhaustion retreat+escort, immediate CAP
 * handoff on a coverage drop) and continuous-flow rotation between 2+ bomber-carrying STRIKE
 * carriers so strikes go out continuously instead of the whole group pulsing on/off together.
 * M6: battleline capped within half of our own strike wings' fighter range (so strikes launched
 * from the carriers can still reach over the line), and a concentration-of-fire bonus in strike
 * target selection for enemies already within our battleline's own weapon range.
 * Post-M6 amendment: the FortySecond gate "assumes command" of a mixed FortySecond/Draconis
 * Alliance/Alliance Intelligence Office fleet by fleet-point majority rather than requiring
 * every deployed ship to be FortySecond.
 * M7: picket flank distance as a ratio instead of a flat constant, and late-war sweep-gating
 * (hold the main wave past its base delay if the target is still contested, up to a capped
 * extension) - applied to both the single-group cycle and the rotation path, which previously
 * had no sweep-staging of its own at all.
 * M8: carrier evasion (fall back further when any carrier is under direct fighter threat) and
 * picket unpredictability (ThreatCombatStrategyAI-style Search & Destroy randomization adapted
 * for pickets).
 * See .claude/systems/carrier-doctrine.md.
 */
public class CarrierDoctrineAI {

    private static final Logger log = Global.getLogger(CarrierDoctrineAI.class);

    /** This doctrine's home faction, identified here by checking for this built-in hullmod
     * (present on every FortySecond-skinned hull) rather than by hull tags/skins alone, since
     * tags on a skin fully replace the base hull's tags and can't be relied on by themselves to
     * keep this from leaking onto any other Draconis fleet that happens to field a tagged hull.
     * Combat-API-only check, no campaign-layer bridging needed. */
    public static final String FORTYSECOND_HULLMOD_ID = "XLII_fortysecond";

    /** The shared hidden hullmod every *other* Draconis-family hull carries (regular Draconis
     * Alliance and Alliance Intelligence Office ships alike - neither has a distinct hullmod of
     * its own, they're both just XLII_draconishull). Used to let the doctrine "assume command"
     * of a fleet mixing FortySecond with these, rather than requiring every ship to be
     * FortySecond - see {@link #findFortySecondGateFailure}. */
    public static final String DRACONIS_HULLMOD_ID = "XLII_draconishull";

    /** FortySecond must exceed this fraction of the combined FortySecond + other-Draconis fleet
     * point total to "assume command" of a mixed fleet - 0.5 is a strict majority. A ship
     * carrying neither hullmod (truly unrelated to Draconis) still fails the gate outright,
     * regardless of this fraction - see {@link #findFortySecondGateFailure}. */
    public static float FORTYSECOND_MAJORITY_FRACTION = 0.5f;

    /** Testing-only on-screen combat messages (gate ACTIVE/INACTIVE, STRIKE/ROTATION/CAP
     * PROMOTION/WITHDRAWING/MISSION transitions) and nothing else - starsector.log diagnostics
     * (logReason/logCarrierDuties/logMission) are unaffected by this flag and always run.
     * Defaults off: M1-M5 are all implemented now, so this is no longer "testing-only" scaffolding
     * that's about to be stripped - leave it off for normal play and flip it on when testing. */
    public static boolean DEBUG_MESSAGES = false;

    // --- formation ratios - always expressed relative to something the battle already
    // measures, never a fixed distance, so they scale across weapon/PD tiers automatically ---
    public static float CARRIER_STANDOFF_RANGE_MULT = 1.35f;
    public static float CARRIER_STANDOFF_MIN = 1500f;
    // backstop against any outlier weapon (not just stations, which are already excluded from
    // the range calc) blowing the standoff distance out to where carriers won't engage at all
    public static float CARRIER_STANDOFF_MAX_MAP_FRACTION = 0.3f;
    public static float SCREEN_RANGE_MULT = 0.65f;
    public static float SCREEN_DISTANCE_MIN = 400f;
    public static float BATTLELINE_ADVANCE_FRACTION = 0.6f; // 0 = sit on the carriers, 1 = sit on the enemy
    // M7: was a flat 2000 - violated the "always a ratio" principle above it. Pickets sit near
    // the edge of our own strike wings' engagement range (doc: "near the edge of fighter
    // range"), falling back to a ratio of carrier standoff when there's nothing on STRIKE duty
    // to measure a fighter range from at all (e.g. an all-CAP fleet).
    public static float PICKET_FLANK_RANGE_MULT = 0.75f;

    // don't recreate a waypoint assignment unless the ideal spot drifted further than this -
    // same hysteresis trick as ThreatCombatStrategyAI.giveMovementOrder, avoids thrashing orders
    public static float WAYPOINT_REFRESH_THRESHOLD = 150f;

    // --- deck-load strike/recover thresholds (M2) ---
    // Hysteresis band, not one threshold: launch once FRR climbs above the high mark, only
    // recall once it falls below the low mark - otherwise a carrier sitting right at one
    // threshold would toggle every tick.
    public static float DECK_LOAD_FRR_THRESHOLD = 0.85f;
    public static float RECOVERY_FRR_THRESHOLD = 0.55f;

    // --- CAP ladder + fighter sweep (M3) ---
    // Minimum time before the CAP ladder re-evaluates a promotion/demotion it just made -
    // same hysteresis idea as the deck-load band, applied to a binary decision instead of a range.
    public static float CAP_LADDER_HOLD_SECONDS = 15f;
    // How long sweep (fighter-only) carriers fly alone before bomber-carrying carriers release -
    // doc's "3-6s", picked a representative single value, tune in the simulator.
    public static float SWEEP_DELAY_SECONDS = 4f;
    // M7 (late-war sweep-gating): once SWEEP_DELAY_SECONDS has elapsed, the main wave still
    // won't release into a target that's still thick with enemy fighters/interceptors - real
    // late-war task forces held bombers until local air cover over the target was actually
    // cleared, not just "N seconds after the sweep went out." Capped at this much *additional*
    // wait beyond the base delay so a target that never clears doesn't hold the main wave
    // forever - it releases anyway once this budget is spent.
    public static float SWEEP_MAX_CONTESTED_EXTENSION_SECONDS = 6f;

    // --- mission flag: COVER vs DESTROY (M4) ---
    // COVER's "protected zone" = carrier standoff + this many enemy gun ranges. The battleline
    // can't advance past it (and carrier strikes won't target outside it) unless released.
    public static float LINE_RELEASE_GUN_RANGE_MULT = 1f;

    // --- endurance / withdrawal (M5) ---
    // Bumped up from 0.3/0.5 - withdraw earlier rather than waiting until a ship is nearly dead.
    public static float CRIPPLE_HULL_FRACTION = 0.45f;
    // PPT-as-fuel: once CR has drained to this fraction of its own max (only possible after
    // peak performance time has actually run out), treat the ship the same as a hull cripple.
    public static float PPT_EXHAUSTED_CR_FRACTION = 0.65f;

    // --- carrier evasion (M8) ---
    // Enemy fighter wings near a single carrier (any role, same counting updateCapEscort uses)
    // needed to call that carrier under direct threat and trigger evasion for the whole group.
    public static float CARRIER_EVASION_THREAT_THRESHOLD = 2f;
    // Multiplies the normal carrier standoff distance while evading - still capped by
    // getStandoffMapCap() same as the baseline, so this can't blow standoff out further than
    // the existing backstop already allows.
    public static float CARRIER_EVASION_STANDOFF_MULT = 1.5f;
    // Minimum time to keep evading once triggered, even if the threat clears sooner - otherwise
    // the boosted standoff would flicker on/off as the enemy wing count crosses the threshold.
    public static float CARRIER_EVASION_HOLD_SECONDS = 10f;

    // --- picket unpredictability (M8) ---
    // Same idiom as vanilla ThreatCombatStrategyAI's Search & Destroy randomization for its own
    // Skirmish-tagged units, adapted for pickets: periodically let a picket off its flank
    // waypoint to roam/hunt independently instead of always holding formation.
    public static float PICKET_SND_BASE_SECONDS = 60f;
    public static float PICKET_SND_TIMER_SECONDS = 60f;
    public static float PICKET_SND_FRACTION = 0.5f; // chance per picket, per cycle, to go independent

    protected final int owner;
    protected final CombatEngineAPI engine;
    protected final CombatFleetManagerAPI fleetManager;
    protected final CombatFleetManagerAPI enemyFleetManager;
    protected final CombatTaskManagerAPI taskManager;
    protected final boolean allyMode = false; // this doctrine only ever runs for owner 1 (never player/ally side)

    protected final IntervalUtil tick = new IntervalUtil(0.8f, 1.2f);

    protected boolean abort = false;
    protected boolean doctrineActive = false;
    protected String lastLoggedReason = null; // only log on change, avoid spamming once/sec forever
    protected String lastLoggedDuties = null; // separate throttle slot from lastLoggedReason
    protected String lastLoggedMission = null; // separate throttle slot, mission flag transitions

    protected AssignmentInfo carrierAssignment;
    protected AssignmentInfo screenAssignment;
    protected AssignmentInfo battlelineAssignment;
    protected AssignmentInfo picketAssignment;

    /** Per-carrier strike/CAP capability, recomputed every tick from current wing loadout. */
    protected final Map<ShipAPI, EnumSet<CarrierDuty>> carrierCapability = new LinkedHashMap<>();

    /** Which duty each carrier is actually flying this cycle - defaults come from
     * {@link #assignCarrierDuties}, then {@link #updateCapLadder} may override one dual-capable
     * carrier's default STRIKE assignment to CAP if enemy bomber presence demands it. */
    protected final Map<ShipAPI, CarrierDuty> carrierDuty = new LinkedHashMap<>();

    protected boolean strikeLaunched = false;
    protected boolean mainWaveReleased = false; // true once bomber-carrying carriers have joined the sweep
    protected float sweepTimer = 0f;
    protected ShipAPI strikeTarget;

    /** The one STRIKE-capable carrier currently pulled into CAP duty by the ladder, if any. */
    protected ShipAPI capPromotion;
    protected float capLadderHoldTimer = 0f;
    protected int lastCapCount = -1; // -1 = not yet measured; used to detect a drop in CAP coverage

    /** Continuous-flow rotation (M5): used only once there are 2+ bomber-carrying STRIKE
     * carriers. Splits them into two alternating groups so one is always either striking or
     * freshly launched while the other rebuilds, instead of the whole group pulsing on/off
     * together. See {@link #runRotatingMainWave}. */
    protected boolean rotationActive = false;
    protected int activeMainGroup = 0;
    protected ShipAPI rotationTarget;

    /** Ships already ordered to retreat for endurance reasons (hull cripple or PPT exhaustion) -
     * tracked so we don't re-issue the retreat/escort order every tick. */
    protected final Set<ShipAPI> withdrawingShips = new LinkedHashSet<>();
    /** LIGHT_ESCORT assignments created in handleWithdrawals() - tracked so deactivate() can
     * tear them down too, same as the four formation assignment fields. */
    protected final List<AssignmentInfo> withdrawalEscortAssignments = new ArrayList<>();

    protected float evasionHoldTimer = 0f;
    protected Boolean lastLoggedEvading = null; // Boolean, not boolean - null means "never logged yet"

    /** Countdown to the next picket Search & Destroy roll - same single-shared-timer structure
     * as ThreatCombatStrategyAI's own untilSNDOnSkirmishUnits, not a per-ship timer, since our
     * pickets share one DEFEND AssignmentInfo and a per-ship timer would tempt touching that
     * shared assignment per-ship (risking removing it for every picket, not just one). */
    protected float untilPicketSND = 0f;

    public CarrierDoctrineAI(int owner) {
        this.owner = owner;
        engine = Global.getCombatEngine();
        fleetManager = engine.getFleetManager(owner);
        enemyFleetManager = engine.getFleetManager(owner == 0 ? 1 : 0);
        taskManager = fleetManager.getTaskManager(allyMode);
        // NOTE: the command-point grant is applied in activateIfNeeded(), not here - this
        // constructor runs the instant ANY XLII_fortysecond ship enters combat on either side
        // (see XLII_FortySecond.applyEffectsAfterShipAddedToCombatEngine), regardless of whether
        // the gate ever passes. Granting +1e9 CP unconditionally here would buff owner 1's admiral
        // AI in an ordinary fight with zero FortySecond ships on that side - e.g. a player flying
        // a single FortySecond hull against an unrelated vanilla enemy - for the whole battle,
        // with no way to revoke it since the gate failing never calls deactivate() on something
        // that was never activated.

        if (fleetManager.getGoal() == FleetGoal.ESCAPE || enemyFleetManager.getGoal() == FleetGoal.ESCAPE) {
            abort = true;
        }

        resetPicketSNDTimer();
        log.info("CarrierDoctrineAI constructed for owner " + owner + (abort ? " (aborted: ESCAPE goal)" : ""));
    }

    public void advance(float amount) {
        if (abort) return;
        if (engine.isPaused()) return;

        tick.advance(amount);
        if (!tick.intervalElapsed()) return;

        // filter to allyMode, matching ThreatCombatStrategyAI's own precedent - without this, a
        // Nexerelin-style allied sub-fleet deployed alongside this fleet would be visible here
        // even though the owner-1 non-ally taskManager may lack authority to command it
        List<DeployedFleetMemberAPI> deployed = new ArrayList<>();
        for (DeployedFleetMemberAPI member : fleetManager.getDeployedCopyDFM()) {
            if (member.isAlly() == allyMode) deployed.add(member);
        }
        if (deployed.isEmpty()) {
            logReason("no deployed members");
            deactivate();
            return;
        }

        // --- hard gate: the whole premise only holds while the carrier(s) are at least as
        // large as everything else this fleet has deployed. A bigger non-carrier ship present
        // (including one that reinforces mid-battle) means this isn't really a carrier group.
        // Stations/modules excluded - a friendly station being defended is a fixed objective,
        // not a competing "biggest ship" that should disqualify carrier doctrine just because
        // it's hull-size-huge. ---
        HullSize fleetApex = HullSize.DEFAULT;
        HullSize carrierApex = HullSize.DEFAULT;
        for (DeployedFleetMemberAPI member : deployed) {
            if (member.isFighterWing() || member.isStation() || member.isStationModule() || member.getShip() == null) continue;
            ShipAPI ship = member.getShip();
            HullSize size = ship.getHullSize();
            if (size.ordinal() > fleetApex.ordinal()) fleetApex = size;
            if (ship.getHullSpec().hasTag(CarrierDoctrineTags.CARRIER) && size.ordinal() > carrierApex.ordinal()) {
                carrierApex = size;
            }
        }

        boolean sizeGateOk = carrierApex.ordinal() > HullSize.DEFAULT.ordinal() && carrierApex.ordinal() >= fleetApex.ordinal();
        String fortySecondFailure = sizeGateOk ? findFortySecondGateFailure(deployed) : "n/a (size gate already failed)";
        boolean viable = sizeGateOk && fortySecondFailure == null;
        if (!viable) {
            if (!sizeGateOk) {
                logReason("size gate failed: carrierApex=" + carrierApex + " fleetApex=" + fleetApex
                        + " (need a deployed XLII_CARRIER-tagged ship at least as large as everything else)");
            } else {
                logReason("fortysecond majority gate failed: " + fortySecondFailure);
            }
            deactivate();
            return;
        }

        // --- role buckets. Carrier is the hard-gated role above; screen/battleline/picket get
        // a softer, per-ship size band relative to the carrier - a mistagged ship just falls
        // through (stays on default AI), it doesn't take the whole controller down. ---
        List<DeployedFleetMemberAPI> carriers = new ArrayList<>();
        List<DeployedFleetMemberAPI> screen = new ArrayList<>();
        List<DeployedFleetMemberAPI> battleline = new ArrayList<>();
        List<DeployedFleetMemberAPI> pickets = new ArrayList<>();

        for (DeployedFleetMemberAPI member : deployed) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            ShipAPI ship = member.getShip();

            if (ship.getHullSpec().hasTag(CarrierDoctrineTags.CARRIER)) {
                carriers.add(member);
                continue;
            }

            HullSize size = ship.getHullSize();
            if (ship.getHullSpec().hasTag(CarrierDoctrineTags.SCREEN) && size.ordinal() < carrierApex.ordinal()) {
                screen.add(member);
            } else if (ship.getHullSpec().hasTag(CarrierDoctrineTags.BATTLELINE) && size.ordinal() >= carrierApex.ordinal() - 1) {
                battleline.add(member);
            } else if (ship.getHullSpec().hasTag(CarrierDoctrineTags.PICKET) && size.ordinal() <= HullSize.FRIGATE.ordinal()) {
                pickets.add(member);
            }
        }

        if (carriers.isEmpty()) {
            logReason("no XLII_CARRIER-tagged ship in the deployed bucket (should not happen if the size gate passed)");
            deactivate();
            return;
        }

        logReason("GATE PASSED: carriers=" + carriers.size() + " screen=" + screen.size()
                + " battleline=" + battleline.size() + " pickets=" + pickets.size());
        activateIfNeeded();
        handleWithdrawals(deployed);
        updateCarrierCapability(carriers);
        assignCarrierDuties(carriers);
        updateCapLadder(carriers, getFormationAnchor(carriers));
        logCarrierDuties();
        updateCapEscort(carriers);
        runStrikeCycle(carriers, battleline);
        updateFormation(carriers, screen, battleline, pickets);
        updatePicketUnpredictability(pickets);
    }

    /**
     * FortySecond doesn't need every deployed ship to be FortySecond - it "assumes command" of
     * a fleet mixing it with regular Draconis Alliance or Alliance Intelligence Office ships
     * (neither of which carries a distinct hullmod of their own, just the shared
     * {@link #DRACONIS_HULLMOD_ID}) as long as FortySecond remains the fleet-point majority,
     * per {@link #FORTYSECOND_MAJORITY_FRACTION}. A ship carrying *neither* hullmod - truly
     * unrelated to Draconis - still fails the gate outright regardless of FP totals, same
     * strict "abort if anything doesn't match" idiom ThreatCombatStrategyAI uses for its own
     * hullmod check: this doctrine should never leak onto some unrelated fleet that merely
     * happens to be fighting alongside a FortySecond/Draconis one.
     *
     * Stations/modules are excluded here for the same reason the size gate excludes them: a
     * defended friendly station can never carry either hullmod, so without this exclusion a
     * COVER mission (station defense) would fail this gate on every single tick.
     *
     * Non-FortySecond ships swept up this way get no doctrine-assigned role at all - FortySecond
     * tags only exist on the 8 FortySecond skins, so a plain Draconis/Intel-Office ship falls
     * through every role bucket untouched. With no admiral orders (suppressed while the doctrine
     * is active) and no CombatTaskManagerAPI assignment, such a ship defaults to search-and-
     * destroy per the API's own documented behavior for an unassigned member - it still fights,
     * just outside doctrine's coordination.
     *
     * @return null if FortySecond is the fleet-point majority (or the sole family present),
     *         otherwise a description of why not - either the first truly-unaffiliated ship
     *         found, or the FP totals that failed to clear the majority threshold (diagnosable
     *         from the log without needing to reproduce it interactively).
     */
    protected String findFortySecondGateFailure(List<DeployedFleetMemberAPI> deployed) {
        int fortySecondFP = 0;
        int otherDraconisFP = 0;

        for (DeployedFleetMemberAPI member : deployed) {
            if (member.isFighterWing() || member.isStation() || member.isStationModule() || member.getShip() == null) continue;
            ShipAPI ship = member.getShip();

            if (ship.getVariant().hasHullMod(FORTYSECOND_HULLMOD_ID)) {
                fortySecondFP += member.getMember().getFleetPointCost();
            } else if (ship.getVariant().hasHullMod(DRACONIS_HULLMOD_ID)) {
                otherDraconisFP += member.getMember().getFleetPointCost();
            } else {
                return ship.getName() + " (hull " + ship.getHullSpec().getHullId()
                        + ") carries neither " + FORTYSECOND_HULLMOD_ID + " nor " + DRACONIS_HULLMOD_ID;
            }
        }

        if (fortySecondFP == 0 && otherDraconisFP == 0) {
            return "no deployed non-fighter, non-station ships to check";
        }
        int totalFP = fortySecondFP + otherDraconisFP;
        if (fortySecondFP <= totalFP * FORTYSECOND_MAJORITY_FRACTION) {
            return "FortySecond is not the fleet-point majority (FortySecond=" + fortySecondFP
                    + ", other Draconis=" + otherDraconisFP + ")";
        }
        return null;
    }

    protected void activateIfNeeded() {
        if (doctrineActive) return;
        doctrineActive = true;
        // granted here, not in the constructor - only once the gate has actually passed, so an
        // ordinary fight that merely contains an XLII_fortysecond ship without ever qualifying
        // never gets owner 1's admiral AI permanently CP-inflated for no reason
        taskManager.getCommandPointsStat().modifyFlat("CarrierDoctrineAI", 1000000000);
        if (fleetManager.getAdmiralAI() != null) {
            fleetManager.getAdmiralAI().setNoOrders(true);
        }
        debugMessage("Carrier Doctrine: ACTIVE");
    }

    /** Hands control back to the normal admiral AI and drops our waypoints - used when the
     * composition gate fails, including mid-battle (e.g. a capital-sized reinforcement arrives). */
    protected void deactivate() {
        if (!doctrineActive) return;
        doctrineActive = false;

        taskManager.getCommandPointsStat().unmodifyFlat("CarrierDoctrineAI");
        if (fleetManager.getAdmiralAI() != null) {
            fleetManager.getAdmiralAI().setNoOrders(false);
        }

        if (carrierAssignment != null) { taskManager.removeAssignment(carrierAssignment); carrierAssignment = null; }
        if (screenAssignment != null) { taskManager.removeAssignment(screenAssignment); screenAssignment = null; }
        if (battlelineAssignment != null) { taskManager.removeAssignment(battlelineAssignment); battlelineAssignment = null; }
        if (picketAssignment != null) { taskManager.removeAssignment(picketAssignment); picketAssignment = null; }
        for (AssignmentInfo info : withdrawalEscortAssignments) {
            taskManager.removeAssignment(info);
        }
        withdrawalEscortAssignments.clear();

        // hand fighter/target control back cleanly - don't leave a carrier stuck in Regroup or
        // still pointed at a stale CAP-escort target once vanilla admiral AI resumes managing it.
        // Iterates carrierCapability, not carrierDuty - every carrier processed by
        // updateCarrierCapability ends up there even if its capability came out empty (e.g. it
        // lost its last useful wing), whereas carrierDuty skips those ships entirely, which would
        // otherwise leave them stuck pulled-back forever even across this very cleanup.
        for (ShipAPI ship : carrierCapability.keySet()) {
            if (!ship.isAlive()) continue;
            ship.setPullBackFighters(false);
            ship.setShipTarget(null);
        }
        carrierCapability.clear();
        carrierDuty.clear();
        resetSingleGroupStrikeState();
        capPromotion = null;
        capLadderHoldTimer = 0f;
        lastCapCount = -1;
        rotationActive = false;
        activeMainGroup = 0;
        rotationTarget = null;
        withdrawingShips.clear();
        evasionHoldTimer = 0f;
        lastLoggedEvading = null;
        lastLoggedDuties = null;
        lastLoggedMission = null;
        debugMessage("Carrier Doctrine: INACTIVE");
    }

    /** Logs a gate-state reason to starsector.log, but only when it changes from the last
     * logged reason - a 1s tick logging the same failure forever would flood the log. */
    protected void logReason(String reason) {
        if (reason.equals(lastLoggedReason)) return;
        lastLoggedReason = reason;
        log.info("CarrierDoctrineAI[owner=" + owner + "]: " + reason);
    }

    /** Logs the current carrier -> duty assignment to starsector.log, only when it changes -
     * added specifically to verify the bomber-vs-interceptor-count tie-break actually produces
     * standing CAP carriers rather than everything defaulting to STRIKE. */
    protected void logCarrierDuties() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ShipAPI, CarrierDuty> entry : carrierDuty.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getKey().getName()).append("=").append(entry.getValue());
        }
        String summary = sb.toString();
        if (summary.equals(lastLoggedDuties)) return;
        lastLoggedDuties = summary;
        log.info("CarrierDoctrineAI[owner=" + owner + "]: duties: " + summary);
    }

    protected void debugMessage(String text) {
        if (!DEBUG_MESSAGES) return;
        if (engine.getCombatUI() == null) return;
        engine.getCombatUI().addMessage(0, (owner == 0 ? "[player] " : "[enemy] ") + text);
    }

    /**
     * Endurance/withdrawal (M5): any deployed, non-fighter ship on our side - not just carriers,
     * every role - gets ordered to retreat once it's either a hull cripple (below
     * {@link #CRIPPLE_HULL_FRACTION}) or PPT-exhausted (CR drained to
     * {@link #PPT_EXHAUSTED_CR_FRACTION} of its own max - only possible once peak performance
     * time has actually run out, so this is "PPT as fuel" without needing a direct PPT-seconds
     * accessor). Pairs the retreat with a Light Escort from the nearest other healthy ship,
     * same idiom vanilla's own escort assignments use, so the retreating ship isn't abandoned
     * on its way off the map. Tracked in {@link #withdrawingShips} so the order is only issued
     * once per ship rather than re-applied every tick.
     *
     * Retreat is ordered as a *direct* retreat ({@code orderRetreat(member, cp, direct=true)}) -
     * every Draconis/FortySecond hull carries {@code XLII_SystemHullModBase.WarpDriveScript},
     * whose only activation condition is {@code ship.isDirectRetreat()}. A direct retreat order
     * is exactly what flips that flag, so this is what makes a withdrawing ship actually
     * Transverse Jump out instead of just sailing for the map edge like a plain retreat would.
     */
    protected void handleWithdrawals(List<DeployedFleetMemberAPI> deployed) {
        withdrawingShips.retainAll(stillDeployedShips(deployed));

        for (DeployedFleetMemberAPI member : deployed) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            if (!member.canBeGivenRetreatOrders()) continue; // e.g. a friendly station - can't retreat at all
            ShipAPI ship = member.getShip();
            if (!ship.isAlive() || ship.isHulk()) continue;
            if (withdrawingShips.contains(ship)) continue;

            float maxCR = ship.getMutableStats().getMaxCombatReadiness().getModifiedValue();
            boolean crippled = ship.getHullLevel() < CRIPPLE_HULL_FRACTION;
            boolean pptExhausted = maxCR > 0f && ship.getCurrentCR() < maxCR * PPT_EXHAUSTED_CR_FRACTION;
            if (!crippled && !pptExhausted) continue;

            taskManager.orderRetreat(member, false, true);
            withdrawingShips.add(ship);
            debugMessage("Carrier Doctrine: WITHDRAWING " + ship.getName()
                    + (crippled ? " (hull cripple)" : " (PPT exhausted)"));

            DeployedFleetMemberAPI escort = pickWithdrawalEscort(ship, deployed);
            if (escort != null) {
                AssignmentInfo info = taskManager.createAssignment(CombatAssignmentType.LIGHT_ESCORT, member, false);
                taskManager.setAssignmentWeight(info, 0f);
                taskManager.giveAssignment(escort, info, false);
                withdrawalEscortAssignments.add(info);
            }
        }
    }

    protected Set<ShipAPI> stillDeployedShips(List<DeployedFleetMemberAPI> deployed) {
        Set<ShipAPI> result = new LinkedHashSet<>();
        for (DeployedFleetMemberAPI member : deployed) {
            if (member.getShip() != null) result.add(member.getShip());
        }
        return result;
    }

    /** Closest other alive, non-withdrawing, non-fighter ship on our side - a simple stand-in
     * for "whoever's free and nearby," not a dedicated escort-role selection. */
    protected DeployedFleetMemberAPI pickWithdrawalEscort(ShipAPI withdrawing, List<DeployedFleetMemberAPI> deployed) {
        DeployedFleetMemberAPI best = null;
        float bestDist = Float.MAX_VALUE;
        for (DeployedFleetMemberAPI member : deployed) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            ShipAPI ship = member.getShip();
            if (ship == withdrawing || !ship.isAlive() || ship.isHulk()) continue;
            if (withdrawingShips.contains(ship)) continue;

            float dist = Misc.getDistance(ship.getLocation(), withdrawing.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = member;
            }
        }
        return best;
    }

    /**
     * Per-carrier strike/CAP capability from actual deployed wing composition - BOMBER wings
     * contribute STRIKE, INTERCEPTOR wings contribute CAP, FIGHTER wings are flex (contribute
     * both), and SUPPORT/ASSAULT wings are ignored entirely for doctrine purposes. A carrier
     * capable of both ends up with both flags set; which duty it actually flies this cycle is
     * an M2 concern (deck-load/recovery) - this method only classifies, it doesn't act.
     */
    protected void updateCarrierCapability(List<DeployedFleetMemberAPI> carriers) {
        carrierCapability.clear();
        for (DeployedFleetMemberAPI member : carriers) {
            ShipAPI ship = member.getShip();
            EnumSet<CarrierDuty> capability = EnumSet.noneOf(CarrierDuty.class);
            for (FighterWingAPI wing : ship.getAllWings()) {
                if (wing.getSpec() == null) continue;
                WingRole role = wing.getSpec().getRole();
                if (role == WingRole.BOMBER) {
                    capability.add(CarrierDuty.STRIKE);
                } else if (role == WingRole.INTERCEPTOR) {
                    capability.add(CarrierDuty.CAP);
                } else if (role == WingRole.FIGHTER) {
                    capability.add(CarrierDuty.STRIKE);
                    capability.add(CarrierDuty.CAP);
                }
                // WingRole.SUPPORT, WingRole.ASSAULT: not usable for this doctrine, ignored
            }
            carrierCapability.put(ship, capability);
        }
    }

    /**
     * Resolves each carrier's capability set (see {@link #updateCarrierCapability}) into one
     * duty for this cycle. Single-capability carriers are locked to that duty. A carrier with
     * neither (support/assault wings only, or none) gets no duty and is left alone.
     *
     * A dual-capable carrier (has both a bomber-contributing and a CAP-contributing wing) is
     * decided primarily by which role actually dominates its loadout - count of dedicated
     * BOMBER wings vs dedicated INTERCEPTOR wings. This matters because almost every FortySecond
     * carrier variant carries at least one generalist FIGHTER wing, which counts toward both
     * capabilities (see {@link #updateCarrierCapability}) - a naive "can it strike at all ->
     * STRIKE" tie-break would send even a CAP-heavy loadout (e.g. 1 FIGHTER + 2 INTERCEPTOR,
     * zero bombers) to STRIKE just because of that one flex wing, leaving almost no standing CAP
     * in practice.
     *
     * When the loadout vote is a genuine tie (including 0-0, a FIGHTER-flex-only carrier), hull
     * size breaks it: CRUISER-or-bigger leans STRIKE, DESTROYER-or-smaller leans CAP. Keeps the
     * biggest, most valuable strike wings concentrated on the biggest hulls rather than scattered
     * arbitrarily, so a coordinated strike isn't missing its heaviest hitter over a coin-flip tie.
     */
    protected void assignCarrierDuties(List<DeployedFleetMemberAPI> carriers) {
        carrierDuty.clear();
        for (DeployedFleetMemberAPI member : carriers) {
            ShipAPI ship = member.getShip();
            EnumSet<CarrierDuty> capability = carrierCapability.get(ship);
            if (capability == null || capability.isEmpty()) continue;

            if (!capability.contains(CarrierDuty.STRIKE)) {
                carrierDuty.put(ship, CarrierDuty.CAP);
            } else if (!capability.contains(CarrierDuty.CAP)) {
                carrierDuty.put(ship, CarrierDuty.STRIKE);
            } else {
                int bombers = 0, interceptors = 0;
                for (FighterWingAPI wing : ship.getAllWings()) {
                    if (wing.getSpec() == null) continue;
                    WingRole role = wing.getSpec().getRole();
                    if (role == WingRole.BOMBER) bombers++;
                    else if (role == WingRole.INTERCEPTOR) interceptors++;
                }
                CarrierDuty duty;
                if (bombers != interceptors) {
                    duty = interceptors > bombers ? CarrierDuty.CAP : CarrierDuty.STRIKE;
                } else {
                    duty = ship.getHullSize().ordinal() >= HullSize.CRUISER.ordinal() ? CarrierDuty.STRIKE : CarrierDuty.CAP;
                }
                carrierDuty.put(ship, duty);
            }
        }
    }

    /** Resets the four single-group strike-cycle fields together - was previously copy-pasted
     * at every reset site, risking a future change missing one and leaving stale state behind. */
    protected void resetSingleGroupStrikeState() {
        strikeLaunched = false;
        mainWaveReleased = false;
        sweepTimer = 0f;
        strikeTarget = null;
    }

    /** M7: true once enough enemy fighter presence sits near {@code target} to call it still
     * contested airspace - any role counts, not just bombers/interceptors, since any of them
     * could intercept an incoming bomber wave. */
    protected boolean isTargetContested(ShipAPI target) {
        return target != null && countEnemyFighterWingsNear(target.getLocation(), false) > 0;
    }

    /**
     * M7 late-war sweep-gating: the base {@link #SWEEP_DELAY_SECONDS} timer alone isn't enough -
     * real late-war task forces held the main wave until the target had actually lost local air
     * cover, not just "N seconds after the sweep went out." Once the base timer expires, keeps
     * holding (by returning false, letting {@code sweepTimer} keep counting negative) as long as
     * the target is still contested, up to {@link #SWEEP_MAX_CONTESTED_EXTENSION_SECONDS} of
     * extra wait - past that budget it releases anyway regardless, so a target that never clears
     * can't hold the main wave forever.
     */
    protected boolean isMainWaveReadyToRelease(float sweepTimer, ShipAPI target) {
        if (sweepTimer > 0f) return false;
        return !isTargetContested(target) || -sweepTimer > SWEEP_MAX_CONTESTED_EXTENSION_SECONDS;
    }

    /** Arms (or skips, if there's nothing to stage with) the sweep-then-main-wave delay for the
     * group about to become active - used by {@link #runRotatingMainWave} both on first launch
     * and on every subsequent swap, so each wave gets its own sweep-ahead treatment. */
    protected void armSweepStaging(List<ShipAPI> sweepCarriers) {
        if (sweepCarriers.isEmpty()) {
            mainWaveReleased = true;
            sweepTimer = 0f;
        } else {
            mainWaveReleased = false;
            sweepTimer = SWEEP_DELAY_SECONDS;
        }
    }

    /**
     * Deck-load strike/recover cycle for every carrier on STRIKE duty: hold Regroup until every
     * strike carrier's FRR clears {@link #DECK_LOAD_FRR_THRESHOLD}, then launch at a shared
     * target so wings arrive together, and recall back to Regroup once the minimum FRR among
     * them drops below {@link #RECOVERY_FRR_THRESHOLD}. Hysteresis between the two thresholds,
     * tracked via {@link #strikeLaunched}, keeps this from flapping.
     *
     * Fighter sweep (M3): STRIKE-duty carriers with no bomber wing at all (fighter/interceptor-
     * only, on STRIKE duty via the FIGHTER flex capability) launch immediately as the "sweep" -
     * bomber-carrying carriers hold Regroup for {@link #SWEEP_DELAY_SECONDS} longer before
     * releasing as the "main wave". A carrier's wings launch atomically (setPullBackFighters is
     * per-ship, not per-wing), so a carrier with any bomber wing is main-wave regardless of what
     * else it also carries - the split is which carriers go in which wave, not which wings.
     *
     * Continuous-flow rotation (M5): with 2+ bomber-carrying ("main") STRIKE carriers, this
     * synchronized single-group cycle is replaced by {@link #runRotatingMainWave} instead -
     * every main carrier launching and recalling together leaves a gap with no strikes at all
     * while the whole group regroups in lockstep. With fewer than 2, there's nothing to
     * alternate between, so the single-group cycle below still applies directly (this is also
     * exactly the doc's "one carrier: run strike/rebuild cycles" case).
     */
    protected void runStrikeCycle(List<DeployedFleetMemberAPI> carriers, List<DeployedFleetMemberAPI> battleline) {
        Vector2f zoneCenter = null;
        Float zoneRadius = null;
        if (isCoverMission()) {
            zoneCenter = getFormationAnchor(carriers);
            float enemyRange = getEnemyLongestNonMissileRange();
            zoneRadius = getCarrierStandoffDistance(enemyRange) + enemyRange * LINE_RELEASE_GUN_RANGE_MULT;
        }

        List<ShipAPI> sweepCarriers = new ArrayList<>();
        List<ShipAPI> mainCarriers = new ArrayList<>();
        for (DeployedFleetMemberAPI member : carriers) {
            ShipAPI ship = member.getShip();
            if (carrierDuty.get(ship) != CarrierDuty.STRIKE) continue;
            if (hasBomberWing(ship)) {
                mainCarriers.add(ship);
            } else {
                sweepCarriers.add(ship);
            }
        }

        if (mainCarriers.size() >= 2) {
            // rotation owns mainCarriers + sweepCarriers entirely - reset the single-group
            // fields so a later drop back below 2 main carriers starts that cycle fresh rather
            // than resuming a stale launched/target state from before rotation took over.
            resetSingleGroupStrikeState();
            runRotatingMainWave(sweepCarriers, mainCarriers, zoneCenter, zoneRadius, battleline);
            return;
        }
        if (rotationActive || activeMainGroup != 0 || rotationTarget != null) {
            // symmetric reset going the other way - dropped from 2+ main carriers back to 1 or
            // 0, hand everything in rotation state back to Regroup and let the code below
            // restart the single-group cycle from a clean slate.
            for (ShipAPI ship : mainCarriers) ship.setPullBackFighters(true);
            rotationActive = false;
            activeMainGroup = 0;
            rotationTarget = null;
        }

        List<ShipAPI> strikeCarriers = new ArrayList<>(sweepCarriers);
        strikeCarriers.addAll(mainCarriers);

        if (strikeCarriers.isEmpty()) {
            resetSingleGroupStrikeState();
            return;
        }

        if (!strikeLaunched) {
            boolean deckLoadReady = true;
            for (ShipAPI ship : strikeCarriers) {
                if (ship.getSharedFighterReplacementRate() < DECK_LOAD_FRR_THRESHOLD) {
                    deckLoadReady = false;
                    break;
                }
            }
            if (deckLoadReady) {
                strikeTarget = pickStrikeTarget(zoneCenter, zoneRadius, battleline);
                strikeLaunched = strikeTarget != null;
                if (strikeLaunched) {
                    if (!sweepCarriers.isEmpty() && !mainCarriers.isEmpty()) {
                        mainWaveReleased = false;
                        sweepTimer = SWEEP_DELAY_SECONDS;
                        debugMessage("Carrier Doctrine: STRIKE SWEEP LAUNCHED (" + sweepCarriers.size()
                                + " fighter-only carriers -> " + strikeTarget.getName()
                                + ", main wave in " + SWEEP_DELAY_SECONDS + "s)");
                    } else {
                        // nothing to stage a sweep with (either everyone has bombers, or nobody
                        // does) - launch the whole strike group together, same as before M3
                        mainWaveReleased = true;
                        debugMessage("Carrier Doctrine: STRIKE LAUNCHED (" + strikeCarriers.size()
                                + " carriers -> " + strikeTarget.getName() + ")");
                    }
                }
            }
        } else {
            if (!mainWaveReleased) {
                sweepTimer -= tick.getIntervalDuration();
                if (isMainWaveReadyToRelease(sweepTimer, strikeTarget)) {
                    mainWaveReleased = true;
                    debugMessage("Carrier Doctrine: STRIKE MAIN WAVE RELEASED (" + mainCarriers.size() + " carriers"
                            + (isTargetContested(strikeTarget) ? ", target still contested but held long enough)" : ")"));
                }
            }

            if (strikeTarget == null || !strikeTarget.isAlive() || !engine.isAwareOf(owner, strikeTarget)) {
                strikeTarget = pickStrikeTarget(zoneCenter, zoneRadius, battleline);
            }

            float minFRR = Float.MAX_VALUE;
            for (ShipAPI ship : strikeCarriers) {
                minFRR = Math.min(minFRR, ship.getSharedFighterReplacementRate());
            }

            if (strikeTarget == null || minFRR < RECOVERY_FRR_THRESHOLD) {
                resetSingleGroupStrikeState();
                debugMessage("Carrier Doctrine: STRIKE RECALLED (FRR below " + RECOVERY_FRR_THRESHOLD + ")");
            }
        }

        for (ShipAPI ship : sweepCarriers) {
            ship.setPullBackFighters(!strikeLaunched);
            if (strikeLaunched && strikeTarget != null) {
                ship.setShipTarget(strikeTarget);
            }
        }
        for (ShipAPI ship : mainCarriers) {
            boolean launch = strikeLaunched && mainWaveReleased;
            ship.setPullBackFighters(!launch);
            if (launch && strikeTarget != null) {
                // vanilla carrier AI can re-pick its own target on its own logic - re-apply
                // every tick while launched rather than setting it once and hoping it holds
                ship.setShipTarget(strikeTarget);
            }
        }
    }

    /**
     * Continuous-flow rotation (M5), for 2+ bomber-carrying STRIKE carriers. Splits them into
     * two alternating groups (even/odd index - membership isn't tracked persistently per ship,
     * just recomputed each tick from current duty, which is fine since it only matters which
     * *group* is active, not which specific ships started in it) so that while one group is
     * out on a strike, the other is already rebuilding FRR, and they swap the instant the
     * active group needs to recall - rather than the whole main-carrier set pulsing on/off
     * together with a gap where nothing is striking at all.
     *
     * Sweep carriers always launch alongside whichever group is currently active, since their
     * role (soak PD/CAP ahead of the bombers) is relevant any time a strike is out at all - but
     * (M7) the *active* group itself still waits on {@link #mainWaveReleased}/{@link #sweepTimer}
     * the same way the single-group cycle does, re-armed fresh every time a new group becomes
     * active (first launch or a swap), so each wave gets its own sweep-ahead treatment rather
     * than losing the fighter-sweep advantage entirely just because rotation is running.
     */
    protected void runRotatingMainWave(List<ShipAPI> sweepCarriers, List<ShipAPI> mainCarriers,
                                        Vector2f zoneCenter, Float zoneRadius, List<DeployedFleetMemberAPI> battleline) {
        List<ShipAPI> groupA = new ArrayList<>();
        List<ShipAPI> groupB = new ArrayList<>();
        for (int i = 0; i < mainCarriers.size(); i++) {
            (i % 2 == 0 ? groupA : groupB).add(mainCarriers.get(i));
        }
        if (groupB.isEmpty()) {
            // an odd split landed everyone in groupA this tick (e.g. exactly 2 carriers and one
            // just died) - nothing to rotate with right now, fall back to running groupA solo
            // rather than leaving groupB permanently empty and "active" by default.
            groupB.addAll(groupA);
        }

        List<ShipAPI> active = activeMainGroup == 0 ? groupA : groupB;
        List<ShipAPI> standby = activeMainGroup == 0 ? groupB : groupA;

        if (!rotationActive) {
            if (allFrrAbove(active, DECK_LOAD_FRR_THRESHOLD)) {
                rotationTarget = pickStrikeTarget(zoneCenter, zoneRadius, battleline);
                rotationActive = rotationTarget != null;
                if (rotationActive) {
                    armSweepStaging(sweepCarriers);
                    debugMessage("Carrier Doctrine: ROTATION STARTED (group of " + active.size()
                            + " -> " + rotationTarget.getName() + ")");
                }
            }
        } else {
            if (!mainWaveReleased) {
                sweepTimer -= tick.getIntervalDuration();
                if (isMainWaveReadyToRelease(sweepTimer, rotationTarget)) {
                    mainWaveReleased = true;
                    debugMessage("Carrier Doctrine: ROTATION MAIN WAVE RELEASED (" + active.size()
                            + (isTargetContested(rotationTarget) ? " carriers, target still contested but held long enough)" : " carriers)"));
                }
            }

            if (rotationTarget == null || !rotationTarget.isAlive() || !engine.isAwareOf(owner, rotationTarget)) {
                rotationTarget = pickStrikeTarget(zoneCenter, zoneRadius, battleline);
            }

            if (rotationTarget == null || minFrr(active) < RECOVERY_FRR_THRESHOLD) {
                if (rotationTarget != null && allFrrAbove(standby, DECK_LOAD_FRR_THRESHOLD)) {
                    activeMainGroup = 1 - activeMainGroup;
                    List<ShipAPI> swap = active; active = standby; standby = swap;
                    armSweepStaging(sweepCarriers);
                    debugMessage("Carrier Doctrine: ROTATION SWAP (group of " + active.size()
                            + " now striking, group of " + standby.size() + " rebuilding)");
                } else {
                    rotationActive = false;
                    debugMessage("Carrier Doctrine: ROTATION PAUSED (standby group not ready yet)");
                }
            }
        }

        for (ShipAPI ship : sweepCarriers) {
            ship.setPullBackFighters(!rotationActive);
            if (rotationActive && rotationTarget != null) ship.setShipTarget(rotationTarget);
        }
        for (ShipAPI ship : active) {
            boolean launch = rotationActive && mainWaveReleased;
            ship.setPullBackFighters(!launch);
            if (launch && rotationTarget != null) ship.setShipTarget(rotationTarget);
        }
        for (ShipAPI ship : standby) {
            ship.setPullBackFighters(true);
        }
    }

    protected boolean allFrrAbove(List<ShipAPI> ships, float threshold) {
        for (ShipAPI ship : ships) {
            if (ship.getSharedFighterReplacementRate() < threshold) return false;
        }
        return true;
    }

    protected float minFrr(List<ShipAPI> ships) {
        float min = Float.MAX_VALUE;
        for (ShipAPI ship : ships) {
            min = Math.min(min, ship.getSharedFighterReplacementRate());
        }
        return min;
    }

    /**
     * Enemy carriers first, then ships already engaged by our battleline, then the highest-
     * flux/overloaded, then the biggest hull - a rough priority order, not a literal port of
     * anything, tuned in the simulator like everything else.
     *
     * @param zoneCenter/zoneRadius when non-null, restricts candidates to inside this circle -
     *                              COVER mode's "carriers strike only targets inside the
     *                              protected zone" rule. Pass null for DESTROY (no restriction).
     * @param battleline            concentration-of-fire bonus (M6): candidates already within
     *                              one of our battleline ships' own weapon range score higher -
     *                              a proxy for "already engaged," since there's no reliable way
     *                              to query a ship's current live target. Finishing off a target
     *                              the line is already hitting beats splitting damage further.
     */
    protected ShipAPI pickStrikeTarget(Vector2f zoneCenter, Float zoneRadius, List<DeployedFleetMemberAPI> battleline) {
        ShipAPI best = null;
        float bestScore = -1f;
        for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            ShipAPI ship = member.getShip();
            if (ship.isHulk() || !ship.isAlive() || !engine.isAwareOf(owner, ship)) continue;
            if (zoneCenter != null && Misc.getDistance(ship.getLocation(), zoneCenter) > zoneRadius) continue;

            float score = 0f;
            if (ship.getHullSpec().getHints().contains(ShipTypeHints.CARRIER)) score += 1000f;
            if (isEngagedByBattleline(ship, battleline)) score += 75f;
            score += ship.getHullSize().ordinal() * 10f;
            score += ship.getFluxTracker().getFluxLevel() * 100f;
            if (ship.getFluxTracker().isOverloaded()) score += 200f;

            if (score > bestScore) {
                bestScore = score;
                best = ship;
            }
        }
        return best;
    }

    protected boolean hasBomberWing(ShipAPI ship) {
        for (FighterWingAPI wing : ship.getAllWings()) {
            if (wing.getSpec() != null && wing.getSpec().getRole() == WingRole.BOMBER) return true;
        }
        return false;
    }

    /**
     * M6: the minimum wing range (not average - conservatively, every strike wing needs to be
     * able to reach) across every STRIKE-duty carrier's wings, using the same
     * {@link FighterWingAPI#getRange()} pattern already used for enemy wings in
     * {@link #countEnemyFighterWingsNear}. Returns {@code Float.MAX_VALUE} (no cap) if there's
     * nothing on STRIKE duty with an actual wing to measure - e.g. an all-CAP fleet, where the
     * battleline's distance from the carriers isn't limited by strike reach at all.
     */
    protected float getOwnFighterEngagementRange(List<DeployedFleetMemberAPI> carriers) {
        float min = Float.MAX_VALUE;
        for (DeployedFleetMemberAPI member : carriers) {
            ShipAPI ship = member.getShip();
            if (ship == null || carrierDuty.get(ship) != CarrierDuty.STRIKE) continue;
            for (FighterWingAPI wing : ship.getAllWings()) {
                if (wing.getSpec() == null) continue;
                float range = wing.getRange();
                if (range <= 0f) range = wing.getSpec().getRange();
                if (range > 0f && range < min) min = range;
            }
        }
        return min;
    }

    /**
     * M7: pickets sit near the edge of our own strike wings' engagement range - doc: "post
     * pickets out on the flanks near the edge of fighter range." Falls back to a ratio of the
     * (already map-capped) carrier standoff distance when there's no STRIKE-duty wing to
     * measure a fighter range from at all (e.g. an all-CAP fleet), so pickets still scale with
     * the battle rather than reverting to a flat distance.
     */
    protected float getPicketFlankDistance(List<DeployedFleetMemberAPI> carriers, float carrierStandoff) {
        float fighterRange = getOwnFighterEngagementRange(carriers);
        float basis = fighterRange < Float.MAX_VALUE ? fighterRange : carrierStandoff;
        return basis * PICKET_FLANK_RANGE_MULT;
    }

    /**
     * McCain's move: if enemy bomber wings within their own commit range of the carrier group
     * outnumber our current CAP coverage, pull one dual-capable carrier off STRIKE duty onto
     * CAP, and hold that decision for {@link #CAP_LADDER_HOLD_SECONDS} before reconsidering it
     * (release works the same way in reverse) - without the hold, this would re-decide every
     * tick as the enemy wing count fluctuates by one.
     *
     * CAP handoff (M5): the hold is bypassed the instant measured CAP coverage drops below what
     * it was last tick - a CAP carrier lost or crippled (see {@link #handleWithdrawals}) is a
     * real loss of coverage, not the kind of noisy one-off fluctuation the hold exists to ignore.
     */
    protected void updateCapLadder(List<DeployedFleetMemberAPI> carriers, Vector2f carrierCenter) {
        if (capPromotion != null) {
            EnumSet<CarrierDuty> capability = carrierCapability.get(capPromotion);
            boolean stillDeployed = carrierDuty.containsKey(capPromotion);
            if (!stillDeployed || capability == null || !capability.contains(CarrierDuty.CAP)) {
                capPromotion = null;
            } else {
                carrierDuty.put(capPromotion, CarrierDuty.CAP);
            }
        }

        int capCount = 0;
        for (CarrierDuty duty : carrierDuty.values()) {
            if (duty == CarrierDuty.CAP) capCount++;
        }

        // CAP handoff (M5): if coverage just dropped - a CAP carrier died, retreated, or its
        // promotion was just invalidated above - don't sit out the rest of the hysteresis hold.
        // That hold exists to stop flip-flopping in a stable situation; it was never meant to
        // delay reacting to an actual loss of coverage by up to CAP_LADDER_HOLD_SECONDS.
        if (lastCapCount >= 0 && capCount < lastCapCount) {
            capLadderHoldTimer = 0f;
        }
        lastCapCount = capCount;

        if (capLadderHoldTimer > 0f) {
            capLadderHoldTimer -= tick.getIntervalDuration();
            return;
        }

        int enemyBomberWings = countEnemyBomberWingsNear(carrierCenter);

        if (enemyBomberWings > capCount && capPromotion == null) {
            // prefer the SMALLEST eligible carrier, not just the first one found - the ladder
            // should sacrifice the least valuable striker for CAP duty, not arbitrarily grab
            // whichever one happens to be first in deployment order (which could just as easily
            // be the biggest carrier with the most strike wings, gutting the main strike group).
            ShipAPI candidate = null;
            for (DeployedFleetMemberAPI member : carriers) {
                ShipAPI ship = member.getShip();
                if (carrierDuty.get(ship) != CarrierDuty.STRIKE) continue;
                EnumSet<CarrierDuty> capability = carrierCapability.get(ship);
                if (capability == null || !capability.contains(CarrierDuty.CAP)) continue;

                if (candidate == null || ship.getHullSize().ordinal() < candidate.getHullSize().ordinal()) {
                    candidate = ship;
                }
            }
            if (candidate != null) {
                capPromotion = candidate;
                carrierDuty.put(candidate, CarrierDuty.CAP);
                capLadderHoldTimer = CAP_LADDER_HOLD_SECONDS;
                debugMessage("Carrier Doctrine: CAP PROMOTION (" + candidate.getName()
                        + " pulled off strike duty, enemy bombers=" + enemyBomberWings + ")");
            }
        } else if (enemyBomberWings < capCount && capPromotion != null) {
            debugMessage("Carrier Doctrine: CAP PROMOTION RELEASED (" + capPromotion.getName() + " back to strike duty)");
            capPromotion = null;
            capLadderHoldTimer = CAP_LADDER_HOLD_SECONDS;
        }
    }

    /**
     * Points every CAP-duty carrier's wings at whichever of our own carriers has the most
     * enemy fighter presence of any role nearby - not just bombers. The ladder's promotion
     * decision (see {@link #updateCapLadder}) is deliberately bomber-specific, since that's
     * about whether there's enough CAP to handle incoming strikes - but once a carrier has CAP
     * assigned, it should respond to whatever enemy small craft (fighters, interceptors, not
     * just bombers) are actually threatening a carrier, not only strike wings. setShipTarget on
     * a friendly ship makes its wings escort it.
     */
    protected void updateCapEscort(List<DeployedFleetMemberAPI> carriers) {
        List<ShipAPI> capCarriers = new ArrayList<>();
        for (DeployedFleetMemberAPI member : carriers) {
            if (carrierDuty.get(member.getShip()) == CarrierDuty.CAP) {
                capCarriers.add(member.getShip());
            }
        }
        if (capCarriers.isEmpty()) return;

        ShipAPI mostThreatened = null;
        int mostThreatenedCount = -1;
        for (DeployedFleetMemberAPI member : carriers) {
            ShipAPI ship = member.getShip();
            int threat = countEnemyFighterWingsNear(ship.getLocation(), false);
            if (threat > mostThreatenedCount) {
                mostThreatenedCount = threat;
                mostThreatened = ship;
            }
        }
        if (mostThreatened == null) return;

        for (ShipAPI ship : capCarriers) {
            ship.setShipTarget(mostThreatened);
        }
    }

    protected int countEnemyBomberWingsNear(Vector2f loc) {
        return countEnemyFighterWingsNear(loc, true);
    }

    /** Counts aware enemy fighter wings currently within their own engagement range of
     * {@code loc} - using each wing's own {@link FighterWingAPI#getRange()} as the threat
     * radius, since that's the real distance at which its carrier would actually commit it.
     * @param bombersOnly restrict to bomber-role wings (for the CAP ladder's strike-threat
     *                     count) or count every role (for escort-target selection). */
    protected int countEnemyFighterWingsNear(Vector2f loc, boolean bombersOnly) {
        int count = 0;
        for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
            if (!member.isFighterWing() || member.getShip() == null) continue;
            ShipAPI leader = member.getShip();
            if (!leader.isAlive() || !engine.isAwareOf(owner, leader)) continue;

            FighterWingAPI wing = leader.getWing();
            if (wing == null || wing.getSpec() == null) continue;
            if (bombersOnly && !wing.getSpec().isBomber()) continue;

            float range = wing.getRange();
            if (range <= 0f) range = wing.getSpec().getRange();
            if (Misc.getDistance(leader.getLocation(), loc) <= range) count++;
        }
        return count;
    }

    protected void updateFormation(List<DeployedFleetMemberAPI> carriers, List<DeployedFleetMemberAPI> screen,
                                    List<DeployedFleetMemberAPI> battleline, List<DeployedFleetMemberAPI> pickets) {
        Vector2f carrierCenter = getFormationAnchor(carriers);
        Vector2f enemyCenter = getEnemyCenterOfMass();

        if (!hasAwareEnemy()) {
            // nothing to form up against yet this tick - leave existing orders alone
            return;
        }

        Vector2f axis = Misc.getUnitVector(carrierCenter, enemyCenter);
        Vector2f perp = new Vector2f(axis.y, -axis.x);

        float enemyRange = getEnemyLongestNonMissileRange();
        float carrierStandoff = getCarrierStandoffDistance(enemyRange);

        // M8: doc's air-defense ladder ends with "carriers evade - back away from the attack."
        // A group-level reaction, not per-ship - simpler than splitting the shared RALLY_CARRIER
        // waypoint per threatened ship, and still delivers the doctrinal intent (the carrier
        // group as a whole falls back further when under direct threat, then returns to the
        // normal standoff once clear). The boosted distance propagates into protectedZoneRadius
        // below too, which is coherent - an evading group's protected zone should grow with it.
        if (updateCarrierEvasion(carriers)) {
            carrierStandoff = Math.min(carrierStandoff * CARRIER_EVASION_STANDOFF_MULT, getStandoffMapCap());
        }

        boolean cover = isCoverMission();
        float protectedZoneRadius = carrierStandoff + enemyRange * LINE_RELEASE_GUN_RANGE_MULT;
        boolean lineReleased = !cover || isLineReleased(carrierCenter, protectedZoneRadius);
        logMission(cover, lineReleased);

        // Anchored to the enemy's position, never to the carrier's own current position - if
        // the carrier's target depends on where it currently is, the standoff order compounds
        // every tick instead of converging to the intended distance. Confirmed: the old
        // carrierCenter-based formula (battlelineLoc from carrierCenter, carrierLoc from
        // battlelineLoc) had a stable fixed point at ~1.67x the configured standoff, not the
        // standoff itself, precisely because each tick's order was computed from last tick's
        // order. Compute carrierLoc directly from enemyCenter first, then derive battlelineLoc
        // from that stable point instead of from the carrier's moving position.
        Vector2f carrierLoc = new Vector2f(axis);
        carrierLoc.scale(-carrierStandoff);
        Vector2f.add(carrierLoc, enemyCenter, carrierLoc);

        Vector2f toEnemy = Vector2f.sub(enemyCenter, carrierLoc, new Vector2f());
        toEnemy.scale(BATTLELINE_ADVANCE_FRACTION);
        Vector2f battlelineLoc = Vector2f.add(carrierLoc, toEnemy, new Vector2f());

        if (cover && !lineReleased) {
            // COVER, not released: the line holds inside the protected zone rather than
            // advancing toward the enemy - cap it at the zone radius instead of the usual
            // BATTLELINE_ADVANCE_FRACTION position. Checked against the carrier's actual
            // current position (not carrierLoc) - this doesn't feed back into carrierLoc's own
            // computation above, so it's safe to use the real position here.
            float dist = Misc.getDistance(carrierCenter, battlelineLoc);
            if (dist > protectedZoneRadius) {
                Vector2f capped = Misc.getUnitVector(carrierCenter, battlelineLoc);
                capped.scale(protectedZoneRadius);
                battlelineLoc = Vector2f.add(capped, carrierCenter, new Vector2f());
            }
        }

        // M6: "line-to-enemy distance should stay under about half of fighter engagement range"
        // so the carriers' own strikes can still reach over the line. Measured against carrierLoc
        // (the stable anchor), not carrierCenter - safe for the same reason the COVER cap above
        // is safe to use either point: neither feeds back into carrierLoc's own computation.
        float ownFighterRange = getOwnFighterEngagementRange(carriers);
        if (ownFighterRange < Float.MAX_VALUE) {
            float maxBattlelineDist = ownFighterRange * 0.5f;
            float distFromCarrier = Misc.getDistance(carrierLoc, battlelineLoc);
            if (distFromCarrier > maxBattlelineDist) {
                Vector2f capped = Misc.getUnitVector(carrierLoc, battlelineLoc);
                capped.scale(maxBattlelineDist);
                battlelineLoc = Vector2f.add(capped, carrierLoc, new Vector2f());
            }
        }

        carrierAssignment = holdPosition(carriers, CombatAssignmentType.RALLY_CARRIER, carrierLoc, carrierAssignment);

        // With no dedicated battleline ship deployed (e.g. 1 Alwaid + 5 Errakis - carrier + screen
        // only, nothing XLII_BATTLELINE-tagged), there would otherwise be nothing pushing forward
        // at all - the screen would just ring the carrier and the fleet sits passively. Have the
        // screen take over holding the forward line instead of its usual ring position in that case.
        boolean screenCoversBattleline = battleline.isEmpty() && !screen.isEmpty();

        // Leyte rule (DESTROY only): if the screen is standing in for a missing battleline,
        // never send every single escort forward - hold one back to guard the carriers, since
        // DESTROY's unrestricted advance has nothing else stopping the carrier group from being
        // left with zero protection. Not applied in COVER, where the line isn't pushing far
        // from the carriers in the first place.
        List<DeployedFleetMemberAPI> forwardScreen = screen;
        List<DeployedFleetMemberAPI> reservedGuard = Collections.emptyList();
        if (screenCoversBattleline && !cover && screen.size() > 1) {
            reservedGuard = screen.subList(0, 1);
            forwardScreen = screen.subList(1, screen.size());
        }

        if (!battleline.isEmpty()) {
            battlelineAssignment = holdPosition(battleline, CombatAssignmentType.RALLY_TASK_FORCE, battlelineLoc, battlelineAssignment);
        } else if (screenCoversBattleline) {
            battlelineAssignment = holdPosition(forwardScreen, CombatAssignmentType.RALLY_TASK_FORCE, battlelineLoc, battlelineAssignment);
        } else if (battlelineAssignment != null) {
            taskManager.removeAssignment(battlelineAssignment);
            battlelineAssignment = null;
        }

        if (!reservedGuard.isEmpty()) {
            screenAssignment = holdPosition(reservedGuard, CombatAssignmentType.DEFEND, carrierLoc, screenAssignment);
        } else if (!screen.isEmpty() && !screenCoversBattleline) {
            float screenRange = getScreenMinPDRange(screen);
            float screenDist = Math.max(SCREEN_DISTANCE_MIN, screenRange * SCREEN_RANGE_MULT);
            Vector2f screenLoc = new Vector2f(axis);
            screenLoc.scale(screenDist);
            Vector2f.add(screenLoc, carrierLoc, screenLoc);
            screenAssignment = holdPosition(screen, CombatAssignmentType.DEFEND, screenLoc, screenAssignment);
        } else if (screenAssignment != null) {
            taskManager.removeAssignment(screenAssignment);
            screenAssignment = null;
        }

        if (!pickets.isEmpty()) {
            float picketFlankOffset = getPicketFlankDistance(carriers, carrierStandoff);
            Vector2f picketLoc = new Vector2f(perp);
            picketLoc.scale(picketFlankOffset);
            Vector2f.add(picketLoc, battlelineLoc, picketLoc);
            picketAssignment = holdPosition(pickets, CombatAssignmentType.DEFEND, picketLoc, picketAssignment);
        }
    }

    /**
     * M8 picket unpredictability: same idiom as vanilla ThreatCombatStrategyAI's Search &
     * Destroy randomization for its own Skirmish-tagged units, adapted for pickets. On an
     * average ~105-135s cycle, each picket independently has a {@link #PICKET_SND_FRACTION}
     * (50%) chance to go rogue for 45-75s - {@link com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags#IGNORES_ORDERS}
     * set directly on the ship, not touching the shared DEFEND assignment at all (removing a
     * shared AssignmentInfo through one member's lookup risks cancelling it for every picket,
     * not just the one going rogue - IGNORES_ORDERS sidesteps that entirely since it's a
     * per-ship AI override, independent of the assignment's own bookkeeping). This is a pure
     * timer, not threat-reactive, matching Threat's own "some units just go feral" flavor.
     */
    protected void updatePicketUnpredictability(List<DeployedFleetMemberAPI> pickets) {
        untilPicketSND -= tick.getIntervalDuration();
        if (untilPicketSND > 0f) return;

        for (DeployedFleetMemberAPI member : pickets) {
            ShipAPI ship = member.getShip();
            if (ship == null || ship.getAI() == null) continue;
            if ((float) Math.random() > PICKET_SND_FRACTION) continue;

            float duration = PICKET_SND_BASE_SECONDS * (0.75f + (float) Math.random() * 0.5f);
            ship.getAIFlags().setFlag(AIFlags.IGNORES_ORDERS, duration);
            debugMessage("Carrier Doctrine: PICKET " + ship.getName() + " GONE ROGUE (" + Math.round(duration) + "s)");
        }
        resetPicketSNDTimer();
    }

    protected void resetPicketSNDTimer() {
        untilPicketSND = PICKET_SND_TIMER_SECONDS * (0.75f + (float) Math.random() * 0.5f) + PICKET_SND_BASE_SECONDS;
    }

    /** COVER vs DESTROY - keyed off whether this fleet is actually defending a station, the one
     * unambiguous "this is a defensive posture" signal the combat API exposes directly. */
    protected boolean isCoverMission() {
        return fleetManager.isDefendingStation();
    }

    protected void logMission(boolean cover, boolean lineReleased) {
        String summary = cover ? ("COVER (line " + (lineReleased ? "released" : "holding") + ")") : "DESTROY";
        if (summary.equals(lastLoggedMission)) return;
        lastLoggedMission = summary;
        log.info("CarrierDoctrineAI[owner=" + owner + "]: mission=" + summary);
        debugMessage("Carrier Doctrine: MISSION " + summary);
    }

    /** Capped at a fraction of the map's smaller dimension as a backstop - even with stations
     * excluded above, nothing should be able to push the carriers' standoff distance to where
     * they're functionally refusing to participate in the battle at all. */
    protected float getCarrierStandoffDistance(float enemyRange) {
        float standoff = Math.max(CARRIER_STANDOFF_MIN, enemyRange * CARRIER_STANDOFF_RANGE_MULT);
        return Math.min(standoff, getStandoffMapCap());
    }

    protected float getStandoffMapCap() {
        return Math.min(engine.getMapWidth(), engine.getMapHeight()) * CARRIER_STANDOFF_MAX_MAP_FRACTION;
    }

    /**
     * M8 carrier evasion: true while the carrier group should fall back further than its normal
     * standoff - doc's "carriers evade, back away from the attack," the last rung of the
     * air-defense ladder. Triggers if *any* carrier has enough enemy fighter presence nearby
     * (same "any role" counting {@link #updateCapEscort} uses for its own threat check), and
     * stays triggered for {@link #CARRIER_EVASION_HOLD_SECONDS} after the threat clears -
     * without the hold, the boosted standoff would flicker on/off as the enemy count crosses
     * the threshold tick to tick.
     */
    protected boolean updateCarrierEvasion(List<DeployedFleetMemberAPI> carriers) {
        boolean threatened = false;
        for (DeployedFleetMemberAPI member : carriers) {
            ShipAPI ship = member.getShip();
            if (ship == null) continue;
            if (countEnemyFighterWingsNear(ship.getLocation(), false) >= CARRIER_EVASION_THREAT_THRESHOLD) {
                threatened = true;
                break;
            }
        }

        if (threatened) {
            evasionHoldTimer = CARRIER_EVASION_HOLD_SECONDS;
        } else if (evasionHoldTimer > 0f) {
            evasionHoldTimer -= tick.getIntervalDuration();
        }

        boolean evading = evasionHoldTimer > 0f;
        if (lastLoggedEvading == null || evading != lastLoggedEvading.booleanValue()) {
            lastLoggedEvading = evading;
            log.info("CarrierDoctrineAI[owner=" + owner + "]: carrier evasion " + (evading ? "ENGAGED" : "CLEARED"));
            debugMessage("Carrier Doctrine: EVASION " + (evading ? "ENGAGED" : "CLEARED"));
        }
        return evading;
    }

    /**
     * TF 34 release rule: in COVER mode the battleline normally holds inside the protected
     * zone, but is released to advance like DESTROY once either enemy surface ships are
     * already inside that zone (nothing left to gain by holding back), or the enemy has no
     * carriers left at all (nothing left to protect our own carrier standoff against).
     */
    protected boolean isLineReleased(Vector2f carrierCenter, float protectedZoneRadius) {
        boolean enemyHasCarrier = false;
        boolean enemyInsideZone = false;
        for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            ShipAPI ship = member.getShip();
            if (ship.isHulk() || !ship.isAlive() || !engine.isAwareOf(owner, ship)) continue;

            if (ship.getHullSpec().getHints().contains(ShipTypeHints.CARRIER)) {
                enemyHasCarrier = true;
            }
            if (Misc.getDistance(ship.getLocation(), carrierCenter) <= protectedZoneRadius) {
                enemyInsideZone = true;
            }
        }
        return enemyInsideZone || !enemyHasCarrier;
    }

    /**
     * Holds a bucket of ships on one shared waypoint assignment, only recreating the waypoint
     * when the ideal position has drifted past {@link #WAYPOINT_REFRESH_THRESHOLD} - same
     * hysteresis idiom as ThreatCombatStrategyAI.giveMovementOrder, to avoid thrashing orders
     * every tick as the carrier group/enemy drift slightly.
     */
    protected AssignmentInfo holdPosition(List<DeployedFleetMemberAPI> members, CombatAssignmentType type,
                                           Vector2f loc, AssignmentInfo existing) {
        if (existing != null) {
            if (existing.getTarget() == null || Misc.getDistance(existing.getTarget().getLocation(), loc) > WAYPOINT_REFRESH_THRESHOLD) {
                taskManager.removeAssignment(existing);
                existing = null;
            }
        }
        if (existing == null) {
            AssignmentTargetAPI wp = taskManager.createWaypoint2(loc, allyMode);
            existing = taskManager.createAssignment(type, wp, false);
        }
        for (DeployedFleetMemberAPI member : members) {
            taskManager.giveAssignment(member, existing, false);
        }
        return existing;
    }

    protected Vector2f centerOf(List<DeployedFleetMemberAPI> members) {
        Vector2f com = new Vector2f();
        int count = 0;
        for (DeployedFleetMemberAPI member : members) {
            if (member.getShip() == null) continue;
            Vector2f.add(member.getShip().getLocation(), com, com);
            count++;
        }
        if (count > 0) com.scale(1f / count);
        return com;
    }

    /**
     * The point everything else in this doctrine orients around. Normally just the average
     * position of our deployed carriers - but when defending a friendly station (always a
     * COVER mission, see {@link #isCoverMission}), the station becomes the anchor instead of
     * the carrier average. It's a fixed, stable point worth protecting in its own right, not
     * just one more data point in a moving average - and since it doesn't move, anchoring the
     * whole formation (carrier standoff axis, protected zone, CAP-ladder threat detection,
     * strike-target restriction) on it directly is both more correct and more stable than
     * blending it into {@link #centerOf}. Falls back to the carrier average if no friendly
     * station is actually deployed.
     */
    protected Vector2f getFormationAnchor(List<DeployedFleetMemberAPI> carriers) {
        ShipAPI station = getFriendlyStation();
        if (station != null) return new Vector2f(station.getLocation());
        return centerOf(carriers);
    }

    protected ShipAPI getFriendlyStation() {
        for (DeployedFleetMemberAPI member : fleetManager.getDeployedCopyDFM()) {
            if (member.isAlly() != allyMode) continue;
            if (member.isStation() && member.getShip() != null && member.getShip().isAlive()) {
                return member.getShip();
            }
        }
        return null;
    }

    protected boolean hasAwareEnemy() {
        for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            if (engine.isAwareOf(owner, member.getShip())) return true;
        }
        return false;
    }

    protected Vector2f getEnemyCenterOfMass() {
        Vector2f com = new Vector2f();
        float weight = 0;
        for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            if (!engine.isAwareOf(owner, member.getShip())) continue;
            Vector2f.add(member.getShip().getLocation(), com, com);
            weight++;
        }
        if (weight > 0) com.scale(1f / weight);
        return com;
    }

    /**
     * Longest non-missile gun range among deployed, aware enemy *ships* - deliberately excludes
     * stations AND their modules. A station's weapon range is a siege-objective balance number,
     * often a large fraction of the whole map, not a mobile threat the carrier group needs to
     * out-maneuver; using it here was sending the carrier standoff distance to absurd lengths
     * (the carrier effectively refusing to approach at all) whenever the enemy fielded one.
     * Big stations deploy as a main hub plus separate weapon modules (isStationModule()) - the
     * modules, not the hub, are usually where the actual long-range guns live, so excluding
     * only isStation() wasn't enough on its own. The battleline/screen still have to deal with
     * the station's actual fire once they're close to it - this only affects how far back the
     * carriers themselves hang.
     */
    protected float getEnemyLongestNonMissileRange() {
        float max = 0f;
        for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
            if (member.isFighterWing() || member.isStation() || member.isStationModule() || member.getShip() == null) continue;
            if (!engine.isAwareOf(owner, member.getShip())) continue;
            float range = getShipLongestNonMissileRange(member.getShip());
            if (range > max) max = range;
        }
        return max;
    }

    protected float getShipLongestNonMissileRange(ShipAPI ship) {
        float max = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            WeaponType type = weapon.getType();
            if (type == WeaponType.MISSILE || type == WeaponType.LAUNCH_BAY) continue;
            if (weapon.getRange() > max) max = weapon.getRange();
        }
        return max;
    }

    /** M6 concentration-of-fire: true if {@code candidate} is within one of our battleline
     * ships' own weapon range - the closest available proxy for "already engaged," since ship
     * AI's live target isn't reliably queryable from here. */
    protected boolean isEngagedByBattleline(ShipAPI candidate, List<DeployedFleetMemberAPI> battleline) {
        if (battleline == null) return false;
        for (DeployedFleetMemberAPI member : battleline) {
            ShipAPI line = member.getShip();
            if (line == null || !line.isAlive() || line.isHulk()) continue;
            float range = getShipLongestNonMissileRange(line);
            if (range > 0f && Misc.getDistance(candidate.getLocation(), line.getLocation()) <= range) return true;
        }
        return false;
    }

    /**
     * Smallest "best PD range" across the screen, not the biggest - sizing the ring off the
     * shortest-ranged escort is what actually keeps every screen ship's PD bubble overlapping
     * the carriers; sizing off the longest would leave the short-ranged ones too far out.
     */
    protected float getScreenMinPDRange(List<DeployedFleetMemberAPI> screen) {
        float min = Float.MAX_VALUE;
        for (DeployedFleetMemberAPI member : screen) {
            if (member.getShip() == null) continue;
            float best = 0f;
            for (WeaponAPI weapon : member.getShip().getAllWeapons()) {
                if (!weapon.hasAIHint(AIHints.PD)) continue;
                if (weapon.getRange() > best) best = weapon.getRange();
            }
            if (best > 0f && best < min) min = best;
        }
        return min == Float.MAX_VALUE ? SCREEN_DISTANCE_MIN : min;
    }
}
