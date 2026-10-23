package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Who beats whom, and how fast. Pairwise estimates feed a local-pressure read per hull, so a hull
 * that loses one-on-one but wins three-on-one scores correctly - the whole reason this exists
 * rather than a threat number per ship.
 * <p>
 * Every constant is read from the game rather than copied: damage-type multipliers off
 * {@link DamageType} (which has public setters, so another mod may have changed them), and the
 * armour constants off the game's own settings.json. See {@code .claude/systems/adaptive-admiral.md}.
 */
public class MatchupModel {

    /** Vanilla's own weighting of armour rating into HP-equivalent, and of dissipation into
     * absorbed damage, both from {@code BattleAutoresolverPluginImpl:764-778}. The second encodes
     * "ten seconds of dissipation", which is also roughly this model's prediction window. */
    private static final float ARMOR_TO_HP = 10f;
    private static final float DISSIPATION_SECONDS = 10f;

    private final float maxArmorDamageReduction;
    private final float minArmorFraction;

    public MatchupModel() {
        maxArmorDamageReduction = settingOr("maxArmorDamageReduction", 0.85f);
        minArmorFraction = settingOr("minArmorFraction", 0.05f);
    }

    /** What one hull faces and what it can do about it, over the prediction window. */
    public static class Pressure {
        public ShipAPI ship;
        /** Incoming effective DPS from every enemy that passes the reach gate. */
        public float threat;
        public float timeToDeath = Float.POSITIVE_INFINITY;
        public ShipAPI bestTarget;
        /** Time for everything of ours in reach to kill {@link #bestTarget}. */
        public float timeToKill = Float.POSITIVE_INFINITY;
        /** >1 means we expect to win this exchange. */
        public float advantage;
        /** Predicted damage onto everything reachable. A rank input only - the measured
         * absolute error was 69-668x. */
        public float predictedOut;
        public int predictedOutRank;
        public int threatCount;
        /** Our DP that can bear on this hull's threats - its own plus every ally able to reach one
         * of them - over the DP of every enemy able to reach it. Below 1 the hull is outmatched,
         * whether by numbers or by one bigger ship. Known before a shot lands, unlike the race. */
        public float outmatch = Float.POSITIVE_INFINITY;
        public int reachableTargets;
    }

    /**
     * Effective DPS from {@code attacker} onto {@code target}: shield phase and hull phase are
     * returned separately because they deplete in sequence, and a kinetic-heavy attacker can be
     * fast through shields and useless after.
     *
     * @param out two floats, [shieldDps, hullDps]
     */
    public void effectiveDps(ShipAPI attacker, ShipProfile profile, ShipAPI target, float[] out) {
        float armor = effectiveArmor(target);
        float shieldDps = 0f;
        float hullDps = 0f;
        float wingScale = profile.carrier ? attacker.getSharedFighterReplacementRate() : 1f;

        for (DamageType type : DamageType.values()) {
            float dps = profile.dps(type);
            if (dps <= 0f) continue;
            if (profile.carrier) dps *= wingScale;

            shieldDps += dps * type.getShieldMult() * shieldDamageTakenMult(target, type);

            // Vanilla's armour curve: the fraction of a hit that gets through is
            // hitStrength / (hitStrength + armour), floored by maxArmorDamageReduction.
            float hitStrength = profile.shot(type) * type.getArmorMult();
            // A non-finite hit strength would make this Infinity/Infinity, i.e. NaN, and one NaN
            // propagates through every sum in the fleet.
            float through = hitStrength <= 0f || Float.isInfinite(hitStrength) ? 1f
                    : hitStrength / (hitStrength + armor);
            through = Math.max(through, 1f - maxArmorDamageReduction);
            hullDps += dps * type.getHullMult() * through
                    * target.getMutableStats().getHullDamageTakenMult().getModifiedValue();
        }
        out[0] = shieldDps;
        out[1] = hullDps;
    }

    /** Seconds for {@code attacker} alone to kill {@code target} from its current state. */
    public float timeToKill(ShipAPI attacker, ShipProfile profile, ShipAPI target, float[] scratch) {
        effectiveDps(attacker, profile, target, scratch);
        return timeToKill(scratch[0], scratch[1], target);
    }

    public float timeToKill(float shieldDps, float hullDps, ShipAPI target) {
        float shieldPhase = 0f;
        float shields = remainingShieldHp(target);
        if (shields > 0f) {
            if (shieldDps <= 0f) return Float.POSITIVE_INFINITY;
            shieldPhase = shields / shieldDps;
        }
        float hull = remainingHullHp(target);
        if (hullDps <= 0f) return Float.POSITIVE_INFINITY;
        return shieldPhase + hull / hullDps;
    }

    /** Hull plus armour in HP-equivalent, scaled by how much is left. */
    public float remainingHullHp(ShipAPI ship) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        float hull = stats.getHullBonus().computeEffective(ship.getHullSpec().getHitpoints())
                * ship.getHullLevel();
        return hull + effectiveArmor(ship) * ARMOR_TO_HP;
    }

    /** Damage the shield can still absorb: unused flux capacity plus a window of dissipation,
     * converted by the live flux-per-damage. Zero for a ship with no shield or one that is down. */
    public float remainingShieldHp(ShipAPI ship) {
        ShieldAPI shield = ship.getShield();
        if (shield == null || shield.getType() == ShieldAPI.ShieldType.NONE) return 0f;
        MutableShipStatsAPI stats = ship.getMutableStats();
        float headroom = Math.max(0f, stats.getFluxCapacity().getModifiedValue() - ship.getCurrFlux());
        float budget = headroom + stats.getFluxDissipation().getModifiedValue() * DISSIPATION_SECONDS;
        float fluxPerDamage = Math.max(0.1f, shield.getFluxPerPointOfDamage());
        return budget / fluxPerDamage;
    }

    /** Everything an attacker has to chew through to kill this hull, in HP-equivalent. */
    public float durability(ShipAPI ship) {
        return remainingHullHp(ship) + remainingShieldHp(ship);
    }

    /** Shield-tanky (towards 1) versus armour-tanky (towards 0), vanilla's own ratio. Used to
     * describe a target, not to time anything. */
    public float shieldRatio(ShipAPI ship) {
        float hullStr = Math.max(1f, remainingHullHp(ship));
        float shieldStr = Math.max(0f, remainingShieldHp(ship));
        return shieldStr / (shieldStr + hullStr);
    }

    private float effectiveArmor(ShipAPI ship) {
        float rating = ship.getArmorGrid().getArmorRating();
        // Armour never strips below minArmorFraction of maximum, which is what blunts low
        // per-shot weapons even against a stripped hull.
        return Math.max(rating * minArmorFraction, rating * armorLevel(ship));
    }

    /** Current armour as a fraction of maximum, averaged over the grid. */
    private static float armorLevel(ShipAPI ship) {
        float[][] grid = ship.getArmorGrid().getGrid();
        float max = ship.getArmorGrid().getMaxArmorInCell();
        if (max <= 0f) return 1f;
        float sum = 0f;
        int cells = 0;
        for (float[] row : grid) {
            for (float cell : row) {
                sum += cell;
                cells++;
            }
        }
        return cells == 0 ? 1f : sum / (cells * max);
    }

    private static float shieldDamageTakenMult(ShipAPI target, DamageType type) {
        MutableShipStatsAPI stats = target.getMutableStats();
        float mult = stats.getShieldDamageTakenMult().getModifiedValue();
        switch (type) {
            case KINETIC: return mult * stats.getKineticShieldDamageTakenMult().getModifiedValue();
            case HIGH_EXPLOSIVE: return mult * stats.getHighExplosiveShieldDamageTakenMult().getModifiedValue();
            case FRAGMENTATION: return mult * stats.getFragmentationShieldDamageTakenMult().getModifiedValue();
            case ENERGY: return mult * stats.getEnergyShieldDamageTakenMult().getModifiedValue();
            default: return mult;
        }
    }

    /**
     * Whether {@code attacker} can engage {@code target} inside {@code window} seconds: its reach
     * plus whatever ground it can make up, which is nothing at all against something faster.
     * This is the gate the TaskForce futility release was patching over after the fact.
     */
    public boolean inReach(ShipAPI attacker, ShipProfile profile, ShipAPI target, float window) {
        float gap = Misc.getDistance(attacker.getLocation(), target.getLocation());
        float closing = Math.max(0f, profile.speed - target.getMutableStats().getMaxSpeed().getModifiedValue());
        return gap <= profile.reach() + closing * window;
    }

    /**
     * Local pressure for every hull in {@code ours}. Allies in reach of a target contribute to its
     * time-to-kill, so a losing duel that is actually a winning gang-up reads correctly.
     */
    public List<Pressure> compute(List<ShipAPI> ours, List<ShipAPI> theirs,
                                  ProfileCache profiles, float window) {
        List<Pressure> result = new ArrayList<>(ours.size());
        float[] scratch = new float[2];

        for (ShipAPI ship : ours) {
            ShipProfile profile = profiles.get(ship);
            Pressure p = new Pressure();
            p.ship = ship;
            List<ShipAPI> threats = new ArrayList<>();

            for (ShipAPI enemy : theirs) {
                ShipProfile enemyProfile = profiles.get(enemy);
                if (!inReach(enemy, enemyProfile, ship, window)) continue;
                threats.add(enemy);
                effectiveDps(enemy, enemyProfile, ship, scratch);
                // One incoming number: shields and hull both have to be chewed through, and for
                // "how fast do I die" the hull phase is what ends the hull.
                p.threat += scratch[1];
                p.threatCount++;
            }
            float durability = durability(ship);
            p.timeToDeath = p.threat <= 0f ? Float.POSITIVE_INFINITY : durability / p.threat;
            p.outmatch = outmatch(ship, threats, ours, profiles, window);

            for (ShipAPI enemy : theirs) {
                if (!inReach(ship, profile, enemy, window)) continue;
                p.reachableTargets++;
                float shieldDps = 0f;
                float hullDps = 0f;
                for (ShipAPI friend : ours) {
                    ShipProfile friendProfile = profiles.get(friend);
                    if (!inReach(friend, friendProfile, enemy, window)) continue;
                    effectiveDps(friend, friendProfile, enemy, scratch);
                    shieldDps += scratch[0];
                    hullDps += scratch[1];
                }
                float ttk = timeToKill(shieldDps, hullDps, enemy);
                if (ttk < p.timeToKill) {
                    p.timeToKill = ttk;
                    p.bestTarget = enemy;
                }
            }

            p.advantage = p.timeToKill <= 0f ? Float.POSITIVE_INFINITY
                    : p.timeToDeath / p.timeToKill;
            result.add(p);
        }
        return result;
    }

    private float outmatch(ShipAPI ship, List<ShipAPI> threats, List<ShipAPI> ours,
                           ProfileCache profiles, float window) {
        float threatDP = 0f;
        for (ShipAPI enemy : threats) threatDP += deploymentPoints(enemy);
        if (threatDP <= 0f) return Float.POSITIVE_INFINITY;

        float supportDP = deploymentPoints(ship);
        for (ShipAPI friend : ours) {
            if (friend == ship) continue;
            ShipProfile friendProfile = profiles.get(friend);
            for (ShipAPI enemy : threats) {
                if (inReach(friend, friendProfile, enemy, window)) {
                    supportDP += deploymentPoints(friend);
                    break;
                }
            }
        }
        return supportDP / threatDP;
    }

    private static float deploymentPoints(ShipAPI ship) {
        FleetMemberAPI member = ship.getFleetMember();
        return member == null ? 0f : member.getDeploymentPointsCost();
    }

    private static float settingOr(String key, float fallback) {
        try {
            return (float) Global.getSettings().getFloat(key);
        } catch (Exception e) {
            return fallback;
        }
    }
}
