package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.IdentityHashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;

/**
 * Moves a hull along timid - cautious - steady - aggressive - reckless by writing
 * {@code ShipAIConfig.personalityOverride}, the one lever measured to change how a hull fights
 * without overriding its own judgement. Vanilla does the same from a ship system
 * ({@code IncursionModeSystemScript}); the personality probe confirmed it is read live from a
 * fleet-level script, with nothing overwriting it (resets=0 across two battles).
 * <p>
 * Three rules come straight from measurement, in {@code .claude/systems/adaptive-admiral.md}:
 * <ul>
 *   <li>One step per change, because a personality takes 5-10s to take effect.</li>
 *   <li>A minimum dwell, because the probe forced flips every 20-40s and lost 240 DP for 17.</li>
 *   <li>An emergency path that ignores the dwell, for a hull whose hull is already being eaten.</li>
 * </ul>
 * No restore on the way out: unlike a ship system this is meant to last the battle.
 */
public class PersonalitySlide {

    public enum Step {
        TIMID(Personalities.TIMID),
        CAUTIOUS(Personalities.CAUTIOUS),
        STEADY(Personalities.STEADY),
        AGGRESSIVE(Personalities.AGGRESSIVE),
        RECKLESS(Personalities.RECKLESS);

        public final String personality;

        Step(String personality) {
            this.personality = personality;
        }

        public Step towards(Step target) {
            if (ordinal() == target.ordinal()) return this;
            return values()[ordinal() + (target.ordinal() > ordinal() ? 1 : -1)];
        }
    }

    /** Every hull starts here: FortySecond's faction doctrine is aggression 3, which is steady. */
    public static final Step BASELINE = Step.STEADY;
    /**
     * Asymmetric dwell: cheap to pull back, expensive to commit. Moving two steps down to timid
     * then takes 12s rather than 30s, which is what the first acting run needed - timid was desired
     * five times and never reached. Oscillation is still bounded, because coming back up always
     * costs the full dwell, so a hull cannot flip-flop faster than once every {@value #DWELL_UP}
     * seconds in the direction that gets hulls killed.
     */
    private static final float DWELL_UP = 15f;
    private static final float DWELL_DOWN = 6f;

    private final Map<ShipAPI, Step> current = new IdentityHashMap<>();
    private final Map<ShipAPI, Float> heldFor = new IdentityHashMap<>();
    private final Map<ShipAPI, String> written = new IdentityHashMap<>();
    private int resets;

    public Step current(ShipAPI ship) {
        Step step = current.get(ship);
        return step == null ? BASELINE : step;
    }

    /**
     * Steps {@code ship} one place towards {@code desired}, if it has held its current step long
     * enough. An emergency ignores the dwell and goes straight to {@link Step#TIMID} - the measured
     * cost of being wrong about that is a hull, and the cost of oscillating is the whole battle.
     *
     * @return the step now applied
     */
    public Step moveToward(ShipAPI ship, Step desired, boolean emergency, float window) {
        Step now = current(ship);
        float held = held(ship) + window;

        Step next;
        if (emergency) {
            next = Step.TIMID;
        } else if (desired == now || held < dwellFor(now, desired)) {
            next = now;
        } else {
            next = now.towards(desired);
        }

        if (next != now) {
            current.put(ship, next);
            heldFor.put(ship, 0f);
        } else {
            heldFor.put(ship, held);
        }
        apply(ship, next);
        return next;
    }

    private static float dwellFor(Step now, Step desired) {
        return desired.ordinal() < now.ordinal() ? DWELL_DOWN : DWELL_UP;
    }

    public float held(ShipAPI ship) {
        Float value = heldFor.get(ship);
        return value == null ? 0f : value;
    }

    /** Hands {@code ship} back to its own officer's personality and forgets it, for when the admiral
     * stops steering it. Clearing to null rather than writing steady, so the hull is exactly as
     * vanilla would have it. */
    public void release(ShipAPI ship) {
        if (written.remove(ship) == null) return;
        current.remove(ship);
        heldFor.remove(ship);
        if (ship.getShipAI() != null && ship.getShipAI().getConfig() != null) {
            ship.getShipAI().getConfig().personalityOverride = null;
        }
    }

    /** Non-zero would mean something is clearing our override between writes - watched because the
     * whole slide depends on it not happening. */
    public int getResets() {
        return resets;
    }

    private void apply(ShipAPI ship, Step step) {
        if (ship.getShipAI() == null || ship.getShipAI().getConfig() == null) return;
        ShipAIConfig config = ship.getShipAI().getConfig();
        String previous = written.get(ship);
        if (previous != null && !previous.equals(config.personalityOverride)) resets++;
        config.personalityOverride = step.personality;
        written.put(ship, step.personality);
    }
}
