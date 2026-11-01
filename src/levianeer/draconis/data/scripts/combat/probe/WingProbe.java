package levianeer.draconis.data.scripts.combat.probe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Phase 1 probe 5 (wing control), run by the {@code xlii_probe_wings} mission on the AI side's
 * carriers. Vanilla's carrier logic changes the pull-back state only by issuing the toggle command
 * {@code PULL_BACK_FIGHTERS}, and honours a force target only while its timer is positive (decompiled
 * {@code ai/E}), so the probe blocks that command, sets the state itself, and refreshes the force
 * target and its timer every frame. Schedule:
 * <ul>
 *   <li>0-30 s vanilla; 30-50 s recall; 50-70 s strike the enemy Alwaid; 70-90 s switch to the enemy
 *   Juza; 90-110 s force target on our own Juza (does that make wings escort it?); then release.</li>
 * </ul>
 * Logged per carrier and phase: whether the pull-back state held, the share of fighters out, how
 * close they were to the target, and damage our fighters dealt to the forced target vs elsewhere.
 * Removable once answered.
 */
public class WingProbe extends BaseEveryFrameCombatPlugin implements DamageListener {

    private static final Logger log = Global.getLogger(WingProbe.class);

    private static final int SIDE = 1;
    private static final String[] PHASES = {"vanilla", "recall", "strikeA", "strikeB", "escort", "release"};
    private static final float[] PHASE_START = {0f, 30f, 50f, 70f, 90f, 110f};
    private static final float NEAR = 1000f;

    private static class PhaseStats {
        int samples;
        int pullMismatch;
        float outShare;
        float distSum;
        int distN;
        float nearSum;
        float onTargetDamage;
        float otherDamage;
    }

    private final IntervalUtil tick = new IntervalUtil(1f, 1f);
    private final Map<ShipAPI, PhaseStats[]> stats = new IdentityHashMap<>();
    private final List<ShipAPI> watched = new ArrayList<>();
    private int phase;
    private ShipAPI target;
    private boolean reported;

    @Override
    public void init(CombatEngineAPI engine) {
        stats.clear();
        watched.clear();
        phase = 0;
        target = null;
        reported = false;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        float t = engine.getTotalElapsedTime(false);

        List<ShipAPI> carriers = new ArrayList<>();
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || !ship.isAlive() || ship.isHulk()) continue;
            if (!watched.contains(ship)) {
                watched.add(ship);
                ship.addListener(this);
            }
            if (ship.getOwner() == SIDE && !ship.getAllWings().isEmpty()) {
                carriers.add(ship);
                if (!stats.containsKey(ship)) {
                    PhaseStats[] perPhase = new PhaseStats[PHASES.length];
                    for (int i = 0; i < perPhase.length; i++) perPhase[i] = new PhaseStats();
                    stats.put(ship, perPhase);
                }
            }
        }

        int now = 0;
        for (int i = 0; i < PHASE_START.length; i++) if (t >= PHASE_START[i]) now = i;
        if (now != phase) {
            phase = now;
            target = pickTarget(engine);
            log.info(String.format("DraconisWing,phase,t=%.1f,phase=%s,target=%s", t, PHASES[phase],
                    target == null ? "-" : target.getName()));
        }
        if (target != null && !target.isAlive()) target = pickTarget(engine);

        for (ShipAPI carrier : carriers) control(carrier);

        tick.advance(amount);
        if (tick.intervalElapsed()) {
            for (ShipAPI carrier : carriers) sample(t, carrier);
        }

        if (!reported && engine.isCombatOver()) {
            reported = true;
            report();
        }
    }

    /** Every frame, while the probe owns the wings. */
    private void control(ShipAPI carrier) {
        String name = PHASES[phase];
        if ("vanilla".equals(name) || "release".equals(name)) {
            if ("release".equals(name) && carrier.getForceCarrierTarget() != null) {
                carrier.setForceCarrierTarget(null);
                carrier.setForceCarrierTargetTime(0f);
            }
            return;
        }
        carrier.blockCommandForOneFrame(ShipCommand.PULL_BACK_FIGHTERS);
        if ("recall".equals(name)) {
            carrier.setForceCarrierTarget(null);
            carrier.setForceCarrierTargetTime(0f);
            carrier.setPullBackFighters(true);
        } else {
            carrier.setPullBackFighters(false);
            carrier.setForceCarrierTarget(target);
            carrier.setForceCarrierTargetTime(target == null ? 0f : 1f);
        }
    }

    private void sample(float t, ShipAPI carrier) {
        PhaseStats s = stats.get(carrier)[phase];
        CombatEngineAPI engine = Global.getCombatEngine();
        String name = PHASES[phase];
        boolean wantPull = "recall".equals(name);
        boolean owned = !"vanilla".equals(name) && !"release".equals(name);
        int members = 0;
        int out = 0;
        int returning = 0;
        float distSum = 0f;
        int near = 0;
        int inPlay = 0;
        for (FighterWingAPI wing : carrier.getAllWings()) {
            for (ShipAPI fighter : wing.getWingMembers()) {
                members++;
                if (!engine.isEntityInPlay(fighter) || !fighter.isAlive()) continue;
                inPlay++;
                boolean back = wing.isReturning(fighter) || fighter.isLanding();
                if (back) returning++;
                else out++;
                if (target != null) {
                    float d = Misc.getDistance(fighter.getLocation(), target.getLocation()) - target.getCollisionRadius();
                    distSum += d;
                    if (d <= NEAR) near++;
                }
            }
        }
        s.samples++;
        if (owned && carrier.isPullBackFighters() != wantPull) s.pullMismatch++;
        s.outShare += members == 0 ? 0f : out / (float) members;
        if (target != null && inPlay > 0) {
            s.distSum += distSum / inPlay;
            s.distN++;
            s.nearSum += near / (float) inPlay;
        }
        log.info(String.format("DraconisWing,row,t=%.0f,phase=%s,carrier=%s,members=%d,inPlay=%d,out=%d,returning=%d,pull=%s,wantPull=%s,target=%s,meanDist=%s,near=%d",
                t, name, carrier.getName(), members, inPlay, out, returning, carrier.isPullBackFighters(),
                owned ? String.valueOf(wantPull) : "-", target == null ? "-" : target.getName(),
                target == null || inPlay == 0 ? "-" : String.format("%.0f", distSum / inPlay), near));
    }

    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
        if (!(source instanceof ShipAPI) || !(target instanceof ShipAPI)) return;
        ShipAPI fighter = (ShipAPI) source;
        if (!fighter.isFighter() || fighter.getOwner() != SIDE || fighter.getWing() == null) return;
        PhaseStats[] perPhase = stats.get(fighter.getWing().getSourceShip());
        if (perPhase == null) return;
        float damage = result.getDamageToHull() + result.getTotalDamageToArmor() + result.getDamageToShields();
        if (target == this.target) perPhase[phase].onTargetDamage += damage;
        else perPhase[phase].otherDamage += damage;
    }

    private void report() {
        for (Map.Entry<ShipAPI, PhaseStats[]> entry : stats.entrySet()) {
            for (int i = 0; i < PHASES.length; i++) {
                PhaseStats s = entry.getValue()[i];
                if (s.samples == 0) continue;
                log.info(String.format("DraconisWing,summary,carrier=%s,phase=%s,samples=%d,pullMismatch=%d,outShare=%.2f,meanDist=%s,nearShare=%s,"
                                + "damageOnTarget=%.0f,damageElsewhere=%.0f",
                        entry.getKey().getName(), PHASES[i], s.samples, s.pullMismatch, s.outShare / s.samples,
                        s.distN == 0 ? "-" : String.format("%.0f", s.distSum / s.distN),
                        s.distN == 0 ? "-" : String.format("%.2f", s.nearSum / s.distN), s.onTargetDamage, s.otherDamage));
            }
        }
    }

    /** strikeA: the enemy Alwaid; strikeB: the enemy Juza; escort: our own Juza. Falls back to the largest alive. */
    private ShipAPI pickTarget(CombatEngineAPI engine) {
        String name = PHASES[phase];
        String want;
        int owner;
        switch (name) {
            case "strikeA": want = "XLII_alwaid"; owner = 1 - SIDE; break;
            case "strikeB": want = "XLII_juza"; owner = 1 - SIDE; break;
            case "escort": want = "XLII_juza"; owner = SIDE; break;
            default: return null;
        }
        ShipAPI fallback = null;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || !ship.isAlive() || ship.isHulk() || ship.getOwner() != owner) continue;
            if (ship.getHullSpec().getBaseHullId().startsWith(want)) return ship;
            if (fallback == null || ship.getHullSize().ordinal() > fallback.getHullSize().ordinal()) fallback = ship;
        }
        return fallback;
    }
}
