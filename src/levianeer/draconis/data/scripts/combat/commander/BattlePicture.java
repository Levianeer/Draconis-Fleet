package levianeer.draconis.data.scripts.combat.commander;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * The commander's shared picture of the enemy (part A, section 11.3 of the build doc), rebuilt by
 * {@link #update}. Enemy ships within {@link #LINK} of each other, chained, form a cluster. The
 * strongest cluster is the main body. Any other cluster that the main body cannot reach in time is a
 * stray: every one of its ships is more than the main body's longest gun reach plus {@link #STRAY_MARGIN}
 * away from every main-body ship. Whether a group can actually get to a stray without crossing the main
 * body's guns is a separate question, {@link #catchable}.
 * <p>
 * The main body and its core stay where they were unless something else is clearly stronger
 * ({@link #KEEP}): the strongest cluster and its strongest ship flip between near-equals every few
 * seconds otherwise, and the plan built on them would flip with them.
 */
final class BattlePicture {

    /** Enemy ships this close to each other support each other: one cluster. */
    static final float LINK = 2000f;
    /** Room a stray needs beyond the main body's gun reach before a group can catch it safely. */
    static final float STRAY_MARGIN = 1000f;
    /** The previous main body (or core) is kept while it has at least this share of the strongest's strength. */
    static final float KEEP = 0.75f;

    static final class Cluster {
        final List<ShipAPI> ships = new ArrayList<>();
        float strength;
        /** Strongest ship in the cluster. */
        ShipAPI core;
        final Vector2f centre = new Vector2f();
        /** Longest non-missile weapon range among its ships. */
        float reach;
        boolean main;
        boolean stray;
        /** Closest approach between this cluster's ships and the main body's; 0 for the main body. */
        float gapToMain;
        /** Farthest ship from the centre, to its edge. */
        float radius;
    }

    private final int side;
    final List<Cluster> clusters = new ArrayList<>();
    Cluster main;
    private final List<ShipAPI> previousMain = new ArrayList<>();
    private ShipAPI previousCore;

    BattlePicture(int side) {
        this.side = side;
    }

    void update(CombatEngineAPI engine) {
        clusters.clear();
        main = null;
        List<ShipAPI> enemies = new ArrayList<>();
        for (ShipAPI s : engine.getShips()) {
            if (isEnemy(s) && engine.getFleetManager(s.getOwner()).getDeployedFleetMember(s) != null) enemies.add(s);
        }
        while (!enemies.isEmpty()) {
            Cluster c = new Cluster();
            c.ships.add(enemies.remove(0));
            for (int i = 0; i < c.ships.size(); i++) {
                ShipAPI s = c.ships.get(i);
                for (int j = enemies.size() - 1; j >= 0; j--) {
                    if (Misc.getDistance(s.getLocation(), enemies.get(j).getLocation()) <= LINK) c.ships.add(enemies.remove(j));
                }
            }
            for (ShipAPI s : c.ships) {
                float w = strength(s);
                c.strength += w;
                c.centre.x += s.getLocation().x * w;
                c.centre.y += s.getLocation().y * w;
                if (c.core == null || w > strength(c.core)) c.core = s;
                c.reach = Math.max(c.reach, reach(s));
            }
            if (c.strength > 0f) c.centre.scale(1f / c.strength);
            else c.centre.set(c.core.getLocation());
            if (c.ships.contains(previousCore) && strength(previousCore) >= strength(c.core) * KEEP) c.core = previousCore;
            for (ShipAPI s : c.ships) c.radius = Math.max(c.radius, Misc.getDistance(s.getLocation(), c.centre) + s.getCollisionRadius());
            clusters.add(c);
            if (main == null || c.strength > main.strength) main = c;
        }
        if (main == null) {
            previousMain.clear();
            previousCore = null;
            return;
        }
        Cluster kept = null;
        int overlap = 0;
        for (Cluster c : clusters) {
            int n = 0;
            for (ShipAPI s : c.ships) if (previousMain.contains(s)) n++;
            if (n > overlap) {
                overlap = n;
                kept = c;
            }
        }
        if (kept != null && kept.strength >= main.strength * KEEP) main = kept;
        main.main = true;
        previousMain.clear();
        previousMain.addAll(main.ships);
        previousCore = main.core;
        for (Cluster c : clusters) {
            if (c == main) continue;
            c.gapToMain = Float.MAX_VALUE;
            for (ShipAPI s : c.ships) {
                for (ShipAPI m : main.ships) c.gapToMain = Math.min(c.gapToMain, Misc.getDistance(s.getLocation(), m.getLocation()));
            }
            c.stray = c.gapToMain > main.reach + STRAY_MARGIN;
        }
    }

    /**
     * Whether a group at {@code from} can reach {@code stray} without passing within the main body's gun
     * reach: the straight line to the stray's nearest ship stays clear of every main-body ship.
     */
    boolean catchable(Cluster stray, Vector2f from) {
        if (main == null || stray == main) return false;
        ShipAPI nearest = null;
        for (ShipAPI s : stray.ships) {
            if (nearest == null || Misc.getDistance(s.getLocation(), from) < Misc.getDistance(nearest.getLocation(), from)) nearest = s;
        }
        for (ShipAPI m : main.ships) {
            if (distanceToSegment(m.getLocation(), from, nearest.getLocation()) < main.reach) return false;
        }
        return true;
    }

    private static float distanceToSegment(Vector2f p, Vector2f a, Vector2f b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float lengthSquared = dx * dx + dy * dy;
        float u = lengthSquared == 0f ? 0f : Math.max(0f, Math.min(1f, ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared));
        return Misc.getDistance(p, new Vector2f(a.x + u * dx, a.y + u * dy));
    }

    /** Enemy strength within {@code radius} of {@code point}. */
    float enemyStrengthNear(Vector2f point, float radius) {
        float total = 0f;
        for (Cluster c : clusters) {
            for (ShipAPI s : c.ships) if (Misc.getDistance(s.getLocation(), point) <= radius) total += strength(s);
        }
        return total;
    }

    Cluster clusterOf(ShipAPI ship) {
        for (Cluster c : clusters) if (c.ships.contains(ship)) return c;
        return null;
    }

    boolean isEnemy(ShipAPI s) {
        return s.getOwner() != side && s.getOwner() != 100 && !s.isFighter() && s.isAlive() && !s.isHulk();
    }

    /** DP scaled by remaining hull: a rough like-for-like measure that also counts damage taken. */
    static float strength(ShipAPI ship) {
        float dp = ship.getFleetMember() == null ? 0f : ship.getFleetMember().getDeploymentPointsCost();
        return dp * ship.getHullLevel();
    }

    /** Longest non-missile weapon range: how far the ship threatens without counting missiles' one-off reach. */
    static float reach(ShipAPI ship) {
        float best = 0f;
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (w.isDecorative() || w.getType() == WeaponAPI.WeaponType.MISSILE) continue;
            best = Math.max(best, w.getRange());
        }
        return best;
    }
}
