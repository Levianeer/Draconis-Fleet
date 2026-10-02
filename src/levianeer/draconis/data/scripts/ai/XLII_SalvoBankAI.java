package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import levianeer.draconis.data.scripts.shipsystems.XLII_SalvoBankStats;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * AI for the Salvo Bank ship system.
 * The bank fills passively from damage dealt, and releasing it costs the same
 * charge/active/cooldown lockout no matter how many torpedoes are in it - so
 * dumping just 1-2 the moment a target appears wastes most of that lockout.
 * Instead, hold out for a real salvo (or a full bank) before releasing.
 * canBeActivated() already covers the rest (bank empty, cooldown, mid-release,
 * etc.) via the stats script's isUsable() override.
 */
public class XLII_SalvoBankAI implements ShipSystemAIScript {

    // Matches XLII_SalvoBankStats.SYSTEM_RANGE
    private static final float RANGE = 2000f;

    // Don't release until the bank is at least half full (or capped out) -
    // a bigger ripple for the same post-release lockout.
    private static final int MIN_RELEASE_COUNT = (XLII_SalvoBankStats.MAX_BANKED_TORPEDOES + 1) / 2;

    private ShipAPI ship;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(1f, 1.5f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        if (!system.canBeActivated() || !AIUtils.canUseSystemThisFrame(ship)) return;
        if (!hasEnoughBanked()) return;

        if (hasTargetInRange()) {
            ship.useSystem();
        }
    }

    private boolean hasEnoughBanked() {
        return ((XLII_SalvoBankStats) system.getScript()).getBankedCount() >= MIN_RELEASE_COUNT;
    }

    private boolean hasTargetInRange() {
        ShipAPI shipTarget = ship.getShipTarget();
        if (shipTarget != null && shipTarget.isAlive()
                && MathUtils.isWithinRange(ship.getLocation(), shipTarget.getLocation(), RANGE)) {
            return true;
        }
        return !AIUtils.getNearbyEnemies(ship, RANGE).isEmpty();
    }
}
