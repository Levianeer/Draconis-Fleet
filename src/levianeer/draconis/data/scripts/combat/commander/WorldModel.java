package levianeer.draconis.data.scripts.combat.commander;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * What the commander knows about the battle, rebuilt a few times a second: every ship's firepower,
 * staying power, reach and speed, and how the enemy is grouped. See {@code draconis-commander.md}
 * section 5.1.
 */
final class WorldModel {

    /** Armour rating is per cell; this turns it into rough hit points across the hits a ship takes. */
    private static final float ARMOR_WEIGHT = 4f;
    /** Missiles run out, so they count for a fraction of their listed DPS. */
    private static final float MISSILE_WEIGHT = 0.25f;
    /** Enemy ships this close (centre to centre) to another count as one group. */
    private static final float CLUSTER_LINK = 2000f;

    static final class Unit {
        final ShipAPI ship;
        final float dps;
        final float ehp;
        /** Centre to the edge of its longest non-missile weapon. */
        final float zone;
        final float gunRange;
        final float speed;

        Unit(ShipAPI ship) {
            this.ship = ship;
            this.dps = dps(ship);
            this.ehp = ehp(ship);
            this.gunRange = gunRange(ship);
            this.zone = gunRange + ship.getCollisionRadius();
            this.speed = ship.getMaxSpeed();
        }

        Vector2f loc() {
            return ship.getLocation();
        }
    }

    final int side;
    final List<Unit> ours = new ArrayList<>();
    final List<Unit> theirs = new ArrayList<>();
    final List<List<Unit>> clusters = new ArrayList<>();

    WorldModel(int side) {
        this.side = side;
    }

    void rebuild(CombatEngineAPI engine) {
        ours.clear();
        theirs.clear();
        clusters.clear();
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isStation() || ship.isStationModule() || !ship.isAlive() || ship.isHulk()) continue;
            if (!onMap(engine, ship)) continue;
            if (ship.getOwner() == side) ours.add(new Unit(ship));
            else if (ship.getOwner() == 1 - side) theirs.add(new Unit(ship));
        }
        cluster();
    }

    /** Single-link grouping of enemy ships. */
    private void cluster() {
        List<Unit> left = new ArrayList<>(theirs);
        while (!left.isEmpty()) {
            List<Unit> group = new ArrayList<>();
            group.add(left.remove(0));
            for (int i = 0; i < group.size(); i++) {
                Unit a = group.get(i);
                for (int j = left.size() - 1; j >= 0; j--) {
                    if (Misc.getDistance(a.loc(), left.get(j).loc()) <= CLUSTER_LINK) group.add(left.remove(j));
                }
            }
            clusters.add(group);
        }
    }

    /** Enemy ships that can bring their guns to {@code point} with {@code margin} to spare. */
    List<Unit> enemiesReaching(Vector2f point, float margin) {
        List<Unit> result = new ArrayList<>();
        for (Unit u : theirs) {
            if (Misc.getDistance(u.loc(), point) <= u.zone + margin) result.add(u);
        }
        return result;
    }

    /** Lanchester square law over heterogeneous ships: (sum DPS x sum EHP) of a over that of b. */
    static float ratio(Collection<Unit> a, Collection<Unit> b) {
        float theirs = power(b);
        if (theirs <= 0f) return Float.MAX_VALUE;
        float r = power(a) / theirs;
        return Float.isNaN(r) ? 1f : r;
    }

    private static float power(Collection<Unit> units) {
        float dps = 0f;
        float ehp = 0f;
        for (Unit u : units) {
            dps += u.dps;
            ehp += u.ehp;
        }
        return dps * ehp;
    }

    static Vector2f centroid(Collection<Unit> units) {
        float x = 0f;
        float y = 0f;
        for (Unit u : units) {
            x += u.loc().x;
            y += u.loc().y;
        }
        return new Vector2f(x / units.size(), y / units.size());
    }

    /**
     * Weapons with a zero-second refire cycle report infinite DPS (e.g. {@code XLII_flarelauncher}, a
     * system flare launcher), and one such weapon makes a whole side infinitely strong, so non-finite
     * values are skipped.
     */
    private static float dps(ShipAPI ship) {
        float total = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isDecorative() || weapon.isDisabled() || weapon.isPermanentlyDisabled()) continue;
            float dps;
            if (weapon.getType() == WeaponAPI.WeaponType.MISSILE) {
                if (weapon.usesAmmo() && weapon.getAmmo() <= 0) continue;
                dps = weapon.getDerivedStats().getDps() * MISSILE_WEIGHT;
            } else {
                dps = weapon.getDerivedStats().getSustainedDps();
            }
            if (!Float.isNaN(dps) && !Float.isInfinite(dps)) total += dps;
        }
        return total;
    }

    private static float ehp(ShipAPI ship) {
        float ehp = ship.getHitpoints() + ship.getArmorGrid().getArmorRating() * ARMOR_WEIGHT;
        ShieldAPI shield = ship.getShield();
        boolean shielded = shield != null && shield.getType() != ShieldAPI.ShieldType.NONE
                && shield.getType() != ShieldAPI.ShieldType.PHASE;
        if (shielded && !ship.getFluxTracker().isOverloadedOrVenting()) {
            ehp += (ship.getMaxFlux() - ship.getCurrFlux()) / Math.max(0.1f, shield.getFluxPerPointOfDamage());
        }
        return ehp;
    }

    static float gunRange(ShipAPI ship) {
        float max = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isDecorative() || weapon.getType() == WeaponAPI.WeaponType.MISSILE) continue;
            max = Math.max(max, weapon.getRange());
        }
        return max;
    }

    /** Inside the battle map, i.e. not waiting in an off-map warp-in hold. */
    static boolean onMap(CombatEngineAPI engine, ShipAPI ship) {
        Vector2f loc = ship.getLocation();
        return Math.abs(loc.x) <= engine.getMapWidth() / 2f && Math.abs(loc.y) <= engine.getMapHeight() / 2f;
    }
}
