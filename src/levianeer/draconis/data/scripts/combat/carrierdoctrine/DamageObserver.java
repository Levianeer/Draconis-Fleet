package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;

/**
 * What damage was actually dealt, per attacking hull, so predictions can be checked against
 * reality. Structure follows Detailed Combat Results (Nick XR,
 * {@code mods/DetailedCombatResults}): a {@link DamageListener} on each target hull, with the
 * attacker resolved from the projectile or beam that the engine hands to the callback.
 * <p>
 * Fighter damage is credited to the fighter's carrier, so a carrier's output is comparable with
 * the wing DPS its {@link ShipProfile} predicts.
 */
public class DamageObserver {

    /** Keyed by {@code attackerId + '>' + victimId}. Per-pair rather than per-attacker because
     * the first validation pass could only compare a hull's prediction against a fleet-wide
     * observed total, which made the comparison unreadable. Ids, not names: the simulator
     * duplicates names across sister ships. */
    private final Map<String, Float> pairTotal = new HashMap<>();
    private final Map<String, Float> pairAtLastWindow = new HashMap<>();
    /** Smoothed damage per second per pair. Raw window rates are far too spiky to trigger on - a
     * bomber run lands in one window and nothing lands in the next. */
    private final Map<String, Float> pairRate = new HashMap<>();

    /** Seconds for a pair's rate to respond to a change. Long enough to ride out a reload cycle. */
    private static final float SMOOTHING_SECONDS = 15f;

    /** Attaches to a hull once; the listener writes into this observer's totals for its lifetime. */
    public void watch(ShipAPI ship) {
        if (ship.hasListenerOfClass(Recorder.class)) return;
        ship.addListener(new Recorder(this));
    }

    /** Cumulative damage {@code attacker} has dealt to {@code victim}. */
    public float dealt(ShipAPI attacker, ShipAPI victim) {
        return value(pairTotal, key(attacker.getId(), victim.getId()));
    }

    /** Cumulative damage {@code attacker} has dealt to everything in {@code victims}. */
    public float dealt(ShipAPI attacker, Iterable<ShipAPI> victims) {
        float sum = 0f;
        for (ShipAPI victim : victims) sum += dealt(attacker, victim);
        return sum;
    }

    /** Damage {@code attacker} dealt to {@code victims} since the last {@link #closeWindow},
     * as a rate. Unsmoothed: for reporting, not for deciding. */
    public float dealtRate(ShipAPI attacker, Iterable<ShipAPI> victims, float windowSeconds) {
        if (windowSeconds <= 0f) return 0f;
        float sum = 0f;
        for (ShipAPI victim : victims) {
            String k = key(attacker.getId(), victim.getId());
            sum += value(pairTotal, k) - value(pairAtLastWindow, k);
        }
        return sum / windowSeconds;
    }

    /** Smoothed DPS that {@code attackers} are landing on {@code victim}. This is the number the
     * admiral decides on: observed, so it needs no prediction, and available within seconds of
     * first contact. */
    public float rateInto(ShipAPI victim, Iterable<ShipAPI> attackers) {
        float sum = 0f;
        for (ShipAPI attacker : attackers) {
            sum += value(pairRate, key(attacker.getId(), victim.getId()));
        }
        return sum;
    }

    /** Smoothed DPS {@code attacker} is landing on {@code victims}. */
    public float rateFrom(ShipAPI attacker, Iterable<ShipAPI> victims) {
        float sum = 0f;
        for (ShipAPI victim : victims) {
            sum += value(pairRate, key(attacker.getId(), victim.getId()));
        }
        return sum;
    }

    /**
     * Rolls every pair's smoothed rate forward and starts a new window. Pairs that took no damage
     * this window decay towards zero on their own, so a hull that stops being shot at stops
     * reading as threatened.
     *
     * @param windowSeconds real seconds since the previous call
     */
    public void closeWindow(float windowSeconds) {
        if (windowSeconds > 0f) {
            float alpha = 1f - (float) Math.exp(-windowSeconds / SMOOTHING_SECONDS);
            for (Map.Entry<String, Float> entry : pairTotal.entrySet()) {
                String k = entry.getKey();
                float delta = entry.getValue() - value(pairAtLastWindow, k);
                float rate = delta / windowSeconds;
                pairRate.put(k, value(pairRate, k) + alpha * (rate - value(pairRate, k)));
            }
        }
        pairAtLastWindow.putAll(pairTotal);
    }

    private static String key(String attackerId, String victimId) {
        return attackerId + '>' + victimId;
    }

    private static float value(Map<String, Float> map, String key) {
        Float v = map.get(key);
        return v == null ? 0f : v;
    }

    private void record(ShipAPI attacker, ShipAPI victim, ApplyDamageResultAPI result) {
        float total = result.getDamageToHull() + result.getTotalDamageToArmor()
                + result.getDamageToShields();
        if (total <= 0f) return;
        String k = key(attacker.getId(), victim.getId());
        pairTotal.put(k, value(pairTotal, k) + total);
    }

    /** Separate class rather than an anonymous one so {@code hasListenerOfClass} can find it. */
    public static class Recorder implements DamageListener {

        private final DamageObserver observer;

        public Recorder(DamageObserver observer) {
            this.observer = observer;
        }

        @Override
        public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
            ShipAPI attacker = resolveAttacker(source);
            if (attacker == null || !(target instanceof ShipAPI)) return;
            ShipAPI victim = (ShipAPI) target;
            // Fighters are credited to their carrier on both sides of the pair, so a wing's damage
            // lands on the carrier's row rather than a short-lived fighter's.
            if (victim.isFighter() && victim.getWing() != null && victim.getWing().getSourceShip() != null) {
                victim = victim.getWing().getSourceShip();
            }
            observer.record(attacker, victim, result);
        }

        private static ShipAPI resolveAttacker(Object source) {
            ShipAPI ship = null;
            if (source instanceof ShipAPI) {
                ship = (ShipAPI) source;
            } else if (source instanceof DamagingProjectileAPI) {
                ship = ((DamagingProjectileAPI) source).getSource();
            } else if (source instanceof BeamAPI) {
                ship = ((BeamAPI) source).getSource();
            }
            if (ship == null) return null;
            // A fighter's output belongs to its carrier, which is what the profile predicted.
            return ship.isFighter() && ship.getWing() != null && ship.getWing().getSourceShip() != null
                    ? ship.getWing().getSourceShip() : ship;
        }
    }
}
