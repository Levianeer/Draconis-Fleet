package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.apache.log4j.Logger;
import levianeer.draconis.DraconisSettings;

/**
 * The adaptive admiral. Augments the vanilla admiral rather than suppressing it - no
 * {@code setNoOrders}, no {@code clearTasks}, no command point grant, because the measured baseline
 * beats every replacement-style controller this project has built. See
 * {@code .claude/systems/adaptive-admiral.md} for the design and the probe evidence behind it.
 * <p>
 * Its lever is each hull's personality ({@link PersonalitySlide}), driven by what
 * {@link DamageObserver} has seen. The matchup prediction contributes only an ordering.
 * Carrier wave discipline (pulling wings back to re-form) was ported from TaskForceAI and removed
 * again: 0 wins in 3 with it, 3 in 3 without - see the design doc. With {@code draconisAdmiralActs} false it observes and
 * logs without touching anything, which is the baseline it is measured against.
 */
public class DraconisAdmiralAI implements DoctrineController {

    private static final Logger log = Global.getLogger(DraconisAdmiralAI.class);

    private static final String FORTYSECOND_HULLMOD_ID = "XLII_fortysecond";
    private static final String DRACONIS_HULLMOD_ID = "XLII_draconishull";
    /** The admiral only acts when FortySecond is the fleet-point majority of our side. Allied
     * non-Draconis hulls in a mixed fleet are left alone rather than switching the admiral off. */
    private static final float FORTYSECOND_MAJORITY_FRACTION = 0.5f;

    /** How far ahead the model predicts. The personality lever needs 5-10s to take effect, so
     * reacting to the present is always too late; see the probe results. */
    private static final float WINDOW_SECONDS = 15f;

    private final int owner;
    private final MatchupModel model = new MatchupModel();
    private final ProfileCache profiles = new ProfileCache();
    private final DamageObserver observed = new DamageObserver();
    private final PersonalitySlide slide = new PersonalitySlide();
    private final OpeningPlan opening;
    private final FleetCohesion cohesion;
    private final List<ShipAPI> cohesionForward = new ArrayList<>();
    private final List<ShipAPI> cohesionPulledBack = new ArrayList<>();
    private final boolean acting = settingOn("draconisAdmiralActs");
    /** The vanilla admiral's own ~5s re-decide, randomised the same way it is. */
    private final IntervalUtil decide = new IntervalUtil(3.75f, 6.25f);
    private final List<ShipAPI> ours = new ArrayList<>();
    private final List<ShipAPI> theirs = new ArrayList<>();
    private final List<ShipAPI> all = new ArrayList<>();
    /** Every deployed enemy hull, spotted or not - {@link #theirs} only holds the ones we are aware of. */
    private final List<ShipAPI> enemiesDeployed = new ArrayList<>();
    /** Our Draconis carriers while the gate is open - steered or not, so the opening plan can
     * still record the matchup when it is only observing. */
    private final List<ShipAPI> airGroup = new ArrayList<>();
    private boolean gateOpen;

    private final Map<ShipAPI, Float> contactSeconds = new IdentityHashMap<>();
    private final Map<ShipAPI, Float> lastHullLevel = new IdentityHashMap<>();
    private final Map<ShipAPI, Float> hullLossRate = new IdentityHashMap<>();
    private final Map<ShipAPI, Float> quietSince = new IdentityHashMap<>();
    private static final float CONFIDENCE_SECONDS = 20f;
    /** Incoming DPS that could not kill a hull inside this many seconds is treated as not being a
     * threat at all. Without it, an EMA decaying towards zero sits in the denominator and reports
     * a time-to-death of 350,000 seconds. */
    private static final float THREAT_HORIZON_SECONDS = 300f;
    /** Hull-loss smoothing. Longer than the damage EMA because hull level only falls, so it can be
     * averaged harder without losing responsiveness. */
    private static final float HULL_LOSS_SECONDS = 20f;
    /** Hysteresis: it takes a clearly winning race to push a hull up, and only a losing one to pull
     * it back. Asymmetric on purpose - the measured cost of over-committing is far higher than the
     * cost of sitting a step too cautious. */
    private static final float RATIO_UP = 1.3f;
    private static final float RATIO_DOWN = 0.8f;
    /** Below this many seconds to live, a hull is pulled back whatever the race says. */
    private static final float TTD_URGENT = 25f;
    /** Above this, a hull is safe enough to be let forward. */
    private static final float TTD_SAFE = 90f;
    /** `ttdHull` under this is the emergency: the hull is being eaten now. It bottoms out at
     * 2.4-8.7s per hull, which is why it can only ever be an override, never the warning. */
    private static final float EMERGENCY_TTD_HULL = 12f;
    /** A carrier rebuilding its wings is capped at steady: its wings are its output, and throwing
     * a depleted deck forward is what wastes replacement rate. It is a cap, not a push downward,
     * because a cruiser or capital carrier can still be winning on its own guns. */
    private static final float CARRIER_REBUILD_FRR = 0.4f;
    /** How long a hull must go unthreatened before that counts as safe rather than as a lull. */
    private static final float QUIET_SECONDS = 10f;
    private static final float OUTMATCH_STEADY = 1.0f;
    private static final float OUTMATCH_CAUTIOUS = 0.6f;
    private static final float OUTMATCH_TIMID = 0.35f;
    /** Our hull level below which a hull may not go reckless, since reckless ignores its own safety. */
    private static final float RECKLESS_MIN_HULL = 0.5f;
    /** A lone enemy counts as an opening when we bring at least this many times its DP. */
    private static final float RECKLESS_OUTMATCH = 2.0f;
    private static final Comparator<MatchupModel.Pressure> PREDICTED_OUT_DESC =
            new Comparator<MatchupModel.Pressure>() {
                @Override
                public int compare(MatchupModel.Pressure a, MatchupModel.Pressure b) {
                    return Float.compare(b.predictedOut, a.predictedOut);
                }
            };
    private float elapsed;
    private float lastDecision;
    /**
     * The opening exchange: our observed damage dealt over damage taken in the first
     * {@link #OPENING_SECONDS} after contact. Over 15 battles a split at ~1.07 on this ratio sorted
     * 13 into the right result, and by 60s the winners and losers were indistinguishable - the
     * opening fighter exchange largely decides the battle. Logged once per battle so a config can be
     * judged given its opening rather than on raw wins. The sums add smoothed rates per decision, so
     * only their ratio means anything.
     */
    private static final float OPENING_SECONDS = 30f;
    private float contactAt = -1f;
    private float openingDealt;
    private float openingTaken;
    private boolean openingLogged;

    public DraconisAdmiralAI(int owner) {
        this.owner = owner;
        this.opening = new OpeningPlan(owner, acting && settingOn("draconisAdmiralOpening"));
        this.cohesion = new FleetCohesion(owner, acting && settingOn("draconisAdmiralCohesion"));
        log.info("DraconisAdmiral,header,t,ship,id,carrier,confidence,obsIncomingDps,obsOutgoingDps,"
                + "ttdObserved,ttdHull,ttkObserved,ttdPredicted,ttkPredicted,predictedOutRank,"
                + "threats,targets,shieldRatio,focus,desired,applied,emergency,outmatch,fleetDistance,recklessGate");
        log.info("Draconis: DraconisAdmiralAI " + (acting ? "ACTING (step 3, personality slide)"
                : "observing only - set draconisAdmiralActs to let it act"));
        log.info("DraconisAdmiral,targetHeader,t,enemy,durability,incomingFromUs,ttkObserved,"
                + "ttkHull,hullLevel,shieldRatio");
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        elapsed += amount;
        profiles.advance(amount);

        gather(engine);
        for (ShipAPI ship : all) observed.watch(ship);
        rebuildAirGroup();
        opening.reapply(airGroup, ours);
        // Hulls at steady or above may be sent at the enemy main body; carriers included, as in AI
        // Tweaks. One the slide has pulled back as losing is never sent at the enemy (author
        // decision) - it falls back to our own main body instead if it has drifted off it.
        cohesionForward.clear();
        cohesionPulledBack.clear();
        for (ShipAPI ship : ours) {
            if (!gateOpen || !isDraconis(ship)) continue;
            if (slide.current(ship).ordinal() >= PersonalitySlide.BASELINE.ordinal()) {
                cohesionForward.add(ship);
            } else {
                cohesionPulledBack.add(ship);
            }
        }
        cohesion.advance(amount, elapsed, ours, cohesionForward, cohesionPulledBack, enemiesDeployed);

        decide.advance(amount);
        if (!decide.intervalElapsed()) return;

        float window = elapsed - lastDecision;
        lastDecision = elapsed;
        if (ours.isEmpty() || theirs.isEmpty()) {
            observed.closeWindow(window);
            return;
        }

        List<MatchupModel.Pressure> pressures = model.compute(ours, theirs, profiles, WINDOW_SECONDS);
        observed.closeWindow(window);
        updateContact(pressures, window);
        updateHullLoss(window);
        updateGate();
        // Bug fix: the air group is built at the start of the frame from the gate's previous state,
        // so on the decision where the gate first opens it is still empty. When contact landed on
        // that same decision the opening plan locked with ourCraft=0 - a carrier fleet read as having
        // no strike craft at all, and the posture never fired.
        rebuildAirGroup();

        // Prediction orders hulls; it never supplies a number of seconds. Measured over 8 hulls the
        // ordering held to within one place (log r=0.87) while the absolute scale was 69-668x out,
        // so a rank is the only part of it worth using.
        List<MatchupModel.Pressure> byPredictedOut = new ArrayList<>(pressures);
        float[] scratch = new float[2];
        for (MatchupModel.Pressure p : pressures) {
            p.predictedOut = predictedOutput(p, scratch);
        }
        Collections.sort(byPredictedOut, PREDICTED_OUT_DESC);
        for (int i = 0; i < byPredictedOut.size(); i++) byPredictedOut.get(i).predictedOutRank = i + 1;
        if (contactAt < 0f) {
            for (MatchupModel.Pressure p : pressures) {
                if (p.threatCount > 0) {
                    contactAt = elapsed;
                    break;
                }
            }
        }
        boolean inOpening = contactAt >= 0f && elapsed < contactAt + OPENING_SECONDS;
        if (gateOpen) {
            opening.decide(airGroup, enemiesDeployed, elapsed, contactAt, model, profiles);
        } else if (opening.active()) {
            opening.end(elapsed, "gate closed");
        }

        for (MatchupModel.Pressure p : pressures) {
            ShipProfile profile = profiles.get(p.ship);
            float confidence = confidence(p.ship);
            float incoming = observed.rateInto(p.ship, theirs);
            float outgoing = observed.rateFrom(p.ship, theirs);
            float ttdObserved = secondsToKill(model.durability(p.ship), incoming);
            updateQuiet(p.ship, ttdObserved > 0f);
            float ttdHull = hullTimeToDeath(p.ship);
            if (inOpening) {
                openingDealt += outgoing;
                openingTaken += incoming;
            }

            float ttkObserved = -1f;
            ShipAPI focus = p.bestTarget;
            if (focus != null) {
                ttkObserved = secondsToKill(model.durability(focus), observed.rateInto(focus, ours));
            }

            String recklessGate = recklessGate(p);
            PersonalitySlide.Step desired = desiredStep(p, ttdObserved, ttkObserved, confidence, recklessGate);
            boolean emergency = ttdHull > 0f && ttdHull < EMERGENCY_TTD_HULL;
            PersonalitySlide.Step applied;
            if (steering(p.ship)) {
                // The emergency outranks the opening posture: a carrier whose hull is being eaten
                // goes timid and stays out of the posture for the rest of the opening.
                if (emergency) opening.excuse(p.ship);
                if (profile.carrier && opening.applies(p.ship)) {
                    applied = slide.set(p.ship, opening.step());
                } else {
                    applied = slide.moveToward(p.ship, desired, emergency, window);
                }
            } else {
                slide.release(p.ship);
                applied = slide.current(p.ship);
            }

            log.info(String.format(
                    "DraconisAdmiral,row,%.0f,%s,%s,%d,%.2f,%.0f,%.0f,%.1f,%.1f,%.1f,%.1f,%.1f,%d,%d,%d,%.2f,%s,%s,%s,%d,%.2f,%.0f,%s",
                    elapsed, p.ship.getName(), p.ship.getId(), profile.carrier ? 1 : 0, confidence,
                    incoming, outgoing,
                    ttdObserved, ttdHull, ttkObserved,
                    clamp(p.timeToDeath), clamp(p.timeToKill),
                    p.predictedOutRank, p.threatCount, p.reachableTargets,
                    model.shieldRatio(p.ship), focus == null ? "none" : focus.getName(),
                    desired, applied, emergency ? 1 : 0, Math.min(99f, p.outmatch),
                    cohesion.distanceToMainBody(p.ship), recklessGate == null ? "-" : recklessGate));
        }

        // Target ordering is a fleet-level question, so it is logged once per decision rather than
        // per hull: which enemy our fleet should be concentrating on, and whether the damage we are
        // actually landing on it would kill it in a useful amount of time.
        logTargets();
        logFleet(pressures);
        if (contactAt >= 0f && !openingLogged && !inOpening) logOpening();
    }

    private void logOpening() {
        openingLogged = true;
        String ratio = openingTaken > 0f ? String.format("%.2f", openingDealt / openingTaken)
                : openingDealt > 0f ? "perfect" : "n/a";
        log.info(String.format("DraconisAdmiral,opening,contactAt=%.0f,seconds=%.0f,ratio=%s,dealt=%.0f,taken=%.0f,%s",
                contactAt, OPENING_SECONDS, ratio, openingDealt, openingTaken, opening.summary()));
    }

    /** Fleet line per decision. {@code resets} is the safety check the whole slide rests on: a
     * non-zero value means something is clearing our personality override between writes. */
    private void logFleet(List<MatchupModel.Pressure> pressures) {
        int[] steps = new int[PersonalitySlide.Step.values().length];
        for (MatchupModel.Pressure p : pressures) steps[slide.current(p.ship).ordinal()]++;
        StringBuilder spread = new StringBuilder();
        for (PersonalitySlide.Step step : PersonalitySlide.Step.values()) {
            spread.append(step).append('=').append(steps[step.ordinal()]).append(' ');
        }
        log.info(String.format("DraconisAdmiral,fleet,%.0f,hulls=%d,enemies=%d,gate=%s,cohesion=%d,fallback=%d,resets=%d,%s",
                elapsed, pressures.size(), theirs.size(), gateOpen ? "open" : "closed", cohesion.redirected(),
                cohesion.fallingBack(),
                slide.getResets(), spread.toString().trim()));
    }

    /** Predicted damage onto every reachable enemy - ordering only, never read as a rate. */
    private float predictedOutput(MatchupModel.Pressure p, float[] scratch) {
        ShipProfile profile = profiles.get(p.ship);
        float sum = 0f;
        for (ShipAPI enemy : theirs) {
            if (!model.inReach(p.ship, profile, enemy, WINDOW_SECONDS)) continue;
            model.effectiveDps(p.ship, profile, enemy, scratch);
            sum += scratch[0] + scratch[1];
        }
        return sum;
    }

    /**
     * Where this hull should sit on the slide. Observation first: the race between how long it
     * lives and how long its target takes to die. Prediction contributes only an ordering, because
     * its absolute scale measured 69-668x out.
     */
    private PersonalitySlide.Step desiredStep(MatchupModel.Pressure p, float ttdObserved,
                                              float ttkObserved, float confidence, String recklessGate) {
        PersonalitySlide.Step step = PersonalitySlide.BASELINE;

        if (ttdObserved > 0f && ttkObserved > 0f) {
            float race = ttdObserved / ttkObserved;
            if (race >= RATIO_UP) {
                step = recklessGate != null ? PersonalitySlide.Step.RECKLESS : PersonalitySlide.Step.AGGRESSIVE;
            } else if (race <= RATIO_DOWN) {
                step = race <= RATIO_DOWN * 0.5f ? PersonalitySlide.Step.TIMID
                        : PersonalitySlide.Step.CAUTIOUS;
            }
        } else if (ttdObserved < 0f && p.reachableTargets > 0 && quietFor(p.ship) >= QUIET_SECONDS) {
            // Nothing meaningful is hitting this hull and it has something to hit. Gated on the
            // quiet having lasted: 39% of this step's first-run flips were a hull crossing the
            // threat-horizon sentinel and back, which made the choice bistable at the boundary.
            step = PersonalitySlide.Step.AGGRESSIVE;
        }

        // Hurt with nothing to show for it: no target is dying, so this is not a race worth running.
        if (ttdObserved > 0f && ttdObserved < TTD_URGENT && step.ordinal() > PersonalitySlide.Step.CAUTIOUS.ordinal()) {
            step = PersonalitySlide.Step.CAUTIOUS;
        }
        if (ttdObserved > TTD_SAFE && step.ordinal() < PersonalitySlide.BASELINE.ordinal()) {
            step = PersonalitySlide.BASELINE;
        }

        // Low confidence means the observed seconds are not earned yet, so stay near the baseline.
        // This is the whole "hesitant early, decisive later" effect, and it is data availability
        // rather than a scripted ramp.
        if (confidence < 1f) {
            int limit = PersonalitySlide.BASELINE.ordinal();
            int lo = limit - 1;
            int hi = limit + 1;
            if (step.ordinal() < lo) step = PersonalitySlide.Step.values()[lo];
            if (step.ordinal() > hi) step = PersonalitySlide.Step.values()[hi];
        }

        if (profiles.get(p.ship).carrier
                && p.ship.getSharedFighterReplacementRate() < CARRIER_REBUILD_FRR
                && step.ordinal() > PersonalitySlide.BASELINE.ordinal()) {
            step = PersonalitySlide.BASELINE;
        }

        // The 1-vs-many ceiling. After the confidence clamp on purpose: that clamp exists because
        // observed damage is not trustworthy yet, and this reads DP, which is known before a shot
        // lands - so an outmatched hull can back off before the fight starts. Only ever lowers.
        PersonalitySlide.Step ceiling = outmatchCeiling(p.outmatch);
        if (step.ordinal() > ceiling.ordinal()) step = ceiling;
        return step;
    }

    /**
     * Reckless is an opportunity, not a ratio: it used to follow from the race reaching 2.6 alone.
     * Over 28 runs no hull died while reckless, but a reckless hull was ~1.3x as likely as an
     * aggressive one to die within 30s (12.7% vs 9.5%, 102 decisions) despite only ever being there
     * because it was winning. Now it needs a specific opening on top of a winning race, and a hull
     * healthy enough to ignore its own safety.
     *
     * @return why the gate is open - "overload" or "alone" - or null when it is shut
     */
    private static String recklessGate(MatchupModel.Pressure p) {
        if (p.ship.getHullLevel() < RECKLESS_MIN_HULL) return null;
        ShipAPI target = p.bestTarget;
        if (target != null && target.getFluxTracker() != null && target.getFluxTracker().isOverloadedOrVenting()) {
            return "overload";
        }
        if (p.threatCount == 1 && p.outmatch >= RECKLESS_OUTMATCH) return "alone";
        return null;
    }

    /** Below 1 the enemies that can reach this hull outweigh everything of ours that can answer
     * them. Same thresholds for every hull size: a lone frigate facing a cruiser is already deep in
     * the timid band on DP alone. */
    private static PersonalitySlide.Step outmatchCeiling(float outmatch) {
        if (outmatch < OUTMATCH_TIMID) return PersonalitySlide.Step.TIMID;
        if (outmatch < OUTMATCH_CAUTIOUS) return PersonalitySlide.Step.CAUTIOUS;
        if (outmatch < OUTMATCH_STEADY) return PersonalitySlide.Step.STEADY;
        return PersonalitySlide.Step.RECKLESS;
    }

    private void rebuildAirGroup() {
        airGroup.clear();
        for (ShipAPI ship : ours) {
            if (ship.getNumFighterBays() > 0 && gateOpen && isDraconis(ship)) airGroup.add(ship);
        }
    }

    /** Whether the admiral is allowed to move this hull: acting, gate open, and a Draconis hull. */
    private boolean steering(ShipAPI ship) {
        return acting && gateOpen && isDraconis(ship);
    }

    private static boolean isDraconis(ShipAPI ship) {
        return ship.getVariant().hasHullMod(FORTYSECOND_HULLMOD_ID)
                || ship.getVariant().hasHullMod(DRACONIS_HULLMOD_ID);
    }

    /** FortySecond must hold the fleet-point majority of our side for the admiral to act. Logged on
     * change only, since it is re-evaluated every decision. */
    private void updateGate() {
        int fortySecondFP = 0;
        int totalFP = 0;
        for (ShipAPI ship : ours) {
            FleetMemberAPI member = ship.getFleetMember();
            if (member == null) continue;
            int fp = member.getFleetPointCost();
            totalFP += fp;
            if (ship.getVariant().hasHullMod(FORTYSECOND_HULLMOD_ID)) fortySecondFP += fp;
        }
        boolean open = totalFP > 0 && fortySecondFP > totalFP * FORTYSECOND_MAJORITY_FRACTION;
        if (open != gateOpen) {
            log.info("Draconis: DraconisAdmiralAI gate " + (open ? "OPEN" : "CLOSED")
                    + " (FortySecond " + fortySecondFP + " of " + totalFP + " FP)");
        }
        gateOpen = open;
    }

    private static boolean settingOn(String id) {
        try {
            return DraconisSettings.getBoolean(id);
        } catch (Exception e) {
            return false;
        }
    }

    private void logTargets() {
        for (ShipAPI enemy : theirs) {
            float onIt = observed.rateInto(enemy, ours);
            float durability = model.durability(enemy);
            log.info(String.format("DraconisAdmiral,target,%.0f,%s,%.0f,%.0f,%.1f,%.1f,%.2f,%.2f",
                    elapsed, enemy.getName(), durability, onIt,
                    secondsToKill(durability, onIt), hullTimeToDeath(enemy),
                    enemy.getHullLevel(), model.shieldRatio(enemy)));
        }
    }

    /** Seconds for {@code dps} to chew through {@code durability}, or -1 when the rate is too low
     * to matter inside {@link #THREAT_HORIZON_SECONDS}. -1 means "not under meaningful threat",
     * which is a usable answer; a five-figure number is not. */
    private static float secondsToKill(float durability, float dps) {
        if (dps <= 0f || durability <= 0f) return -1f;
        float seconds = durability / dps;
        return seconds > THREAT_HORIZON_SECONDS ? -1f : seconds;
    }

    /**
     * Time-to-death from how fast the hull is actually being eaten, rather than from incoming DPS.
     * Hull level only falls, so this is far less noisy than a damage-rate EMA - and "before the
     * player can finish them" is literally a statement about hull trajectory. Reads -1 until a
     * hull has started losing hull, which is correct: armour soaks first.
     */
    private float hullTimeToDeath(ShipAPI ship) {
        Float rate = hullLossRate.get(ship);
        if (rate == null || rate <= 0f) return -1f;
        float seconds = ship.getHullLevel() / rate;
        return seconds > THREAT_HORIZON_SECONDS ? -1f : seconds;
    }

    /** Seconds since this hull last had a meaningful threat on it. */
    private float quietFor(ShipAPI ship) {
        Float since = quietSince.get(ship);
        return since == null ? 0f : elapsed - since;
    }

    private void updateQuiet(ShipAPI ship, boolean threatened) {
        if (threatened) {
            quietSince.remove(ship);
        } else if (!quietSince.containsKey(ship)) {
            quietSince.put(ship, elapsed);
        }
    }

    private void updateHullLoss(float window) {
        if (window <= 0f) return;
        float alpha = 1f - (float) Math.exp(-window / HULL_LOSS_SECONDS);
        for (ShipAPI ship : all) {
            Float prior = lastHullLevel.put(ship, ship.getHullLevel());
            if (prior == null) continue;
            float loss = Math.max(0f, prior - ship.getHullLevel()) / window;
            Float rate = hullLossRate.get(ship);
            hullLossRate.put(ship, rate == null ? loss : rate + alpha * (loss - rate));
        }
    }

    /**
     * Contact time per hull, which is what {@link #confidence} is built on: a hull that has had
     * enemies in reach for a while has observations worth trusting, one that just arrived does not.
     * Time rather than damage volume, so a hull nobody is shooting still becomes confident that it
     * is safe.
     */
    private void updateContact(List<MatchupModel.Pressure> pressures, float window) {
        for (MatchupModel.Pressure p : pressures) {
            if (p.threatCount == 0 && p.reachableTargets == 0) continue;
            Float prior = contactSeconds.get(p.ship);
            contactSeconds.put(p.ship, (prior == null ? 0f : prior) + window);
        }
    }

    /** 0 at first contact, 1 once a hull has been in the fight for {@link #CONFIDENCE_SECONDS}.
     * Below 1 the observed seconds are not yet worth acting on, which is exactly why the admiral
     * should look hesitant early and decisive later. */
    private float confidence(ShipAPI ship) {
        Float seconds = contactSeconds.get(ship);
        if (seconds == null) return 0f;
        return Math.min(1f, seconds / CONFIDENCE_SECONDS);
    }

    private static float clamp(float seconds) {
        return Float.isInfinite(seconds) || seconds > 999f ? 999f : seconds;
    }

    private void gather(CombatEngineAPI engine) {
        ours.clear();
        theirs.clear();
        all.clear();
        enemiesDeployed.clear();
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.isStationModule()) continue;
            if (!ship.isAlive() || ship.isHulk()) continue;
            all.add(ship);
            if (ship.getOwner() == owner) {
                ours.add(ship);
            } else {
                enemiesDeployed.add(ship);
                if (engine.isAwareOf(owner, ship)) theirs.add(ship);
            }
        }
        profiles.retainOnly(all);
    }
}
