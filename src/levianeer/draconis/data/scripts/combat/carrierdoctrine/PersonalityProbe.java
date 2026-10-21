package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Throwaway experiment: does writing {@code ShipAIConfig.personalityOverride} from a fleet-level
 * script mid-battle actually change how a ship fights? Vanilla only does it from a ship system
 * ({@code IncursionModeSystemScript}), so a fleet-wide "aggression slide" design depends on this.
 * <p>
 * Hulls alternate into two halves; every {@link #PHASE_SECONDS} one half is reckless and the other
 * timid, then they swap. The halves are each other's control, so the admiral's own timers (the
 * 40-60s search-and-destroy flip) affect both equally. Grep starsector.log for {@code DraconisProbe}.
 * The admiral is left running; this issues no orders.
 */
public class PersonalityProbe {

    private static final Logger log = Global.getLogger(PersonalityProbe.class);

    private static final float PHASE_SECONDS = 40f;

    private final int owner;
    private final IntervalUtil sample = new IntervalUtil(1f, 1f);
    private final Map<ShipAPI, Boolean> groupA = new IdentityHashMap<>();
    private final Map<ShipAPI, String> lastWritten = new IdentityHashMap<>();
    private final float[][] sums = new float[2][4]; // [reckless, timid][distance, flux, wingAssaultSum, carrierSamples]
    private final int[] counts = new int[2];
    private float elapsed = 0f;
    private int phase = 0;
    private int resets = 0;
    private int noConfig = 0;

    public PersonalityProbe(int owner) {
        this.owner = owner;
        log.info("DraconisProbe,header,t,phase,ship,group,personality,enemyDist,flux,hull,wingAssault");
    }

    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) return;
        elapsed += amount;

        int nowPhase = (int) (elapsed / PHASE_SECONDS);
        if (nowPhase != phase) {
            logPhaseSummary();
            phase = nowPhase;
        }

        List<ShipAPI> ours = new ArrayList<>();
        List<ShipAPI> theirs = new ArrayList<>();
        for (ShipAPI ship : engine.getShips()) {
            if (!ship.isAlive() || ship.isFighter() || ship.isDrone() || ship.isStationModule()) continue;
            (ship.getOwner() == owner ? ours : theirs).add(ship);
        }

        for (ShipAPI ship : ours) {
            if (!groupA.containsKey(ship)) groupA.put(ship, groupA.size() % 2 == 0);
            if (ship.getShipAI() == null || ship.getShipAI().getConfig() == null) {
                noConfig++;
                continue;
            }
            ShipAIConfig config = ship.getShipAI().getConfig();
            String previous = lastWritten.get(ship);
            // Catches the engine (or another mod) quietly restoring the override between our writes.
            if (previous != null && !previous.equals(config.personalityOverride)) resets++;
            config.personalityOverride = personalityFor(ship);
            lastWritten.put(ship, config.personalityOverride);
        }

        sample.advance(amount);
        if (!sample.intervalElapsed()) return;

        for (ShipAPI ship : ours) {
            if (!lastWritten.containsKey(ship)) continue;
            boolean reckless = Personalities.RECKLESS.equals(lastWritten.get(ship));
            float dist = nearestDistance(ship, theirs);
            float wingAssault = wingAssaultFraction(ship, ours, theirs);
            int g = reckless ? 0 : 1;
            sums[g][0] += dist;
            sums[g][1] += ship.getFluxLevel();
            if (wingAssault >= 0f) {
                sums[g][3]++;
                sums[g][2] += wingAssault;
            }
            counts[g]++;
            log.info(String.format("DraconisProbe,row,%.0f,%d,%s,%s,%s,%.0f,%.2f,%.2f,%.2f",
                    elapsed, phase, ship.getName(), groupA.get(ship) ? "A" : "B",
                    lastWritten.get(ship), dist, ship.getFluxLevel(), ship.getHullLevel(), wingAssault));
        }
    }

    private String personalityFor(ShipAPI ship) {
        boolean aReckless = phase % 2 == 0;
        return groupA.get(ship) == aReckless ? Personalities.RECKLESS : Personalities.TIMID;
    }

    private static float nearestDistance(ShipAPI ship, List<ShipAPI> enemies) {
        float best = Float.MAX_VALUE;
        for (ShipAPI enemy : enemies) {
            best = Math.min(best, Misc.getDistance(ship.getLocation(), enemy.getLocation()));
        }
        return best == Float.MAX_VALUE ? -1f : best;
    }

    /** Share of a carrier's out-bound fighters nearer an enemy hull than a friendly one, or -1 with
     * none out. Read from position because the carrier's own ship target turned out not to reflect the
     * personality-weighted assault/escort roll. Fighters returning to rearm are excluded. */
    private static float wingAssaultFraction(ShipAPI carrier, List<ShipAPI> ours, List<ShipAPI> theirs) {
        int out = 0, assaulting = 0;
        for (FighterWingAPI wing : carrier.getAllWings()) {
            for (ShipAPI fighter : wing.getWingMembers()) {
                if (!fighter.isAlive() || wing.isReturning(fighter)) continue;
                out++;
                if (nearestDistance(fighter, theirs) < nearestDistance(fighter, ours)) assaulting++;
            }
        }
        return out == 0 ? -1f : (float) assaulting / out;
    }

    private void logPhaseSummary() {
        for (int g = 0; g < 2; g++) {
            int n = Math.max(1, counts[g]);
            float carrierN = Math.max(1f, sums[g][3]);
            log.info(String.format("DraconisProbe,summary,phase=%d,%s,samples=%d,meanEnemyDist=%.0f,meanFlux=%.2f,wingAssault=%.0f%% over %.0f carrier-samples,resets=%d,noConfig=%d",
                    phase, g == 0 ? "RECKLESS" : "TIMID", counts[g], sums[g][0] / n, sums[g][1] / n,
                    100f * sums[g][2] / carrierN, sums[g][3], resets, noConfig));
            sums[g] = new float[4];
            counts[g] = 0;
        }
    }
}
