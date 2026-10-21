package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import org.apache.log4j.Logger;

/**
 * One ship's loadout-derived offensive reach and output, cached because walking every weapon's
 * derived stats each tick would be wasteful across ~600 pairs. Durability is deliberately *not*
 * here - it moves every frame and is computed live by {@link MatchupModel}.
 * <p>
 * DPS is kept split by {@link DamageType} and never summed: the shield and armour multipliers
 * differ per type, so one total would be meaningless. Per-shot damage is carried alongside DPS
 * because vanilla's armour curve reduces by hit strength, which is why a low-damage-per-shot
 * weapon cannot crack a capital's armour no matter its DPS.
 */
public class ShipProfile {

    private static final Logger log = Global.getLogger(ShipProfile.class);

    private static final int TYPES = DamageType.values().length;
    /** Weapon ids already warned about, so a broken stat is reported once, not every rebuild. */
    private static final java.util.Set<String> reported = new java.util.HashSet<>();

    public final boolean carrier;
    public final float speed;
    /** Longest range of any live weapon, missiles included - this is "can I reach it at all". */
    public final float weaponRange;
    /** Shortest live wing range across this carrier's decks, or 0. Shortest rather than longest
     * because a wave only concentrates where every wing can reach. */
    public final float wingRange;

    private final float[] dps = new float[TYPES];
    private final float[] shot = new float[TYPES];
    private float builtAt;

    public ShipProfile(ShipAPI ship, float now) {
        carrier = ship.getNumFighterBays() > 0;
        speed = ship.getMutableStats().getMaxSpeed().getModifiedValue();
        builtAt = now;

        float longest = 0f;
        float fluxPerSecond = 0f;
        // DPS-weighted mean per-shot damage per type: sum(dps * shot) first, divided out below.
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isDecorative() || weapon.isDisabled()) continue;
            if (weapon.getType() == WeaponType.LAUNCH_BAY) continue; // counted as wing DPS instead
            WeaponAPI.DerivedWeaponStatsAPI stats = weapon.getDerivedStats();
            if (stats == null) continue;
            float weaponDps = finite(stats.getSustainedDps(), weapon, "sustainedDps");
            if (weaponDps <= 0f) continue;
            int t = weapon.getDamageType().ordinal();
            dps[t] += weaponDps;
            shot[t] += weaponDps * finite(stats.getDamagePerShot(), weapon, "damagePerShot");
            fluxPerSecond += finite(stats.getFluxPerSecond(), weapon, "fluxPerSecond");
            longest = Math.max(longest, weapon.getRange());
        }
        weaponRange = longest;

        float wingReach = Float.MAX_VALUE;
        for (FighterWingAPI wing : ship.getAllWings()) {
            if (wing.isDestroyed()) continue;
            float range = wing.getRange();
            if (range > 0f) wingReach = Math.min(wingReach, range);
            for (ShipAPI fighter : wing.getWingMembers()) {
                for (WeaponAPI weapon : fighter.getAllWeapons()) {
                    if (weapon.isDecorative() || weapon.getType() == WeaponType.LAUNCH_BAY) continue;
                    WeaponAPI.DerivedWeaponStatsAPI stats = weapon.getDerivedStats();
                    float fighterDps = finite(stats.getSustainedDps(), weapon, "sustainedDps");
                    if (fighterDps <= 0f) continue;
                    int t = weapon.getDamageType().ordinal();
                    // Wing output is scaled by replacement rate at read time, not here: it changes
                    // constantly and this object is cached.
                    dps[t] += fighterDps;
                    shot[t] += fighterDps * finite(stats.getDamagePerShot(), weapon, "damagePerShot");
                }
            }
        }
        wingRange = wingReach == Float.MAX_VALUE ? 0f : wingReach;

        for (int t = 0; t < TYPES; t++) {
            if (dps[t] > 0f) shot[t] /= dps[t];
        }

        // Flux ceiling: a loadout that cannot pay for itself does not deliver its paper DPS.
        // Optimistic on purpose - shields draw on the same dissipation, and this ignores venting.
        MutableShipStatsAPI stats = ship.getMutableStats();
        float dissipation = stats.getFluxDissipation().getModifiedValue();
        if (fluxPerSecond > dissipation && dissipation > 0f) {
            float factor = dissipation / fluxPerSecond;
            for (int t = 0; t < TYPES; t++) dps[t] *= factor;
        }
    }

    /** Guards against a weapon whose derived stats are NaN or infinite. Real case, found when
     * every prediction came back NaN: one such weapon poisons an entire fleet's arithmetic through
     * {@code Infinity / Infinity}. Logged once per weapon id so the culprit is identifiable. */
    private static float finite(float value, WeaponAPI weapon, String what) {
        if (!Float.isNaN(value) && !Float.isInfinite(value)) return value;
        String id = weapon.getId();
        if (reported.add(id + '/' + what)) {
            log.warn("Draconis: weapon " + id + " reports non-finite " + what + " (" + value
                    + "), treating as 0 in the matchup model");
        }
        return 0f;
    }

    public float dps(DamageType type) {
        return dps[type.ordinal()];
    }

    /** DPS-weighted mean damage per shot for {@code type}, as hit strength for the armour curve.
     * Beams report a per-tick figure here, so their armour penetration is understated - a known
     * approximation, not a bug to fix without measuring first. */
    public float shot(DamageType type) {
        return shot[type.ordinal()];
    }

    public float totalDps() {
        float sum = 0f;
        for (float d : dps) sum += d;
        return sum;
    }

    /** Reach for concentration purposes: a carrier's wings, else its guns. */
    public float reach() {
        return carrier && wingRange > 0f ? wingRange : weaponRange;
    }

    public float age(float now) {
        return now - builtAt;
    }
}
