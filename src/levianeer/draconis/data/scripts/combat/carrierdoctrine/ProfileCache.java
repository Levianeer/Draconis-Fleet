package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.IdentityHashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.ShipAPI;

/**
 * Keeps one {@link ShipProfile} per hull. Walking every weapon's derived stats is too expensive to
 * repeat across ~600 pairs a tick, but a profile does go stale as weapons are shot off and wings
 * die, so each is rebuilt after {@link #REFRESH_SECONDS}.
 * <p>
 * Keyed by ship identity, not name: the simulator hands out duplicate names, and keying
 * instrumentation by name silently merged sister ships earlier in this project.
 */
public class ProfileCache {

    private static final float REFRESH_SECONDS = 15f;

    private final Map<ShipAPI, ShipProfile> profiles = new IdentityHashMap<>();
    private float now;

    public void advance(float amount) {
        now += amount;
    }

    public ShipProfile get(ShipAPI ship) {
        ShipProfile profile = profiles.get(ship);
        if (profile == null || profile.age(now) > REFRESH_SECONDS) {
            profile = new ShipProfile(ship, now);
            profiles.put(ship, profile);
        }
        return profile;
    }

    /** Drops hulls that have left the engine, so a long battle does not accumulate dead entries. */
    public void retainOnly(java.util.Collection<ShipAPI> live) {
        if (profiles.size() <= live.size()) return;
        profiles.keySet().retainAll(new java.util.HashSet<>(live));
    }
}
