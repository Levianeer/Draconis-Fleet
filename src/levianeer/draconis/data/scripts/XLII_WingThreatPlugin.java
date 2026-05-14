package levianeer.draconis.data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-pass combat plugin for XLII_DraconisWingHull time-dilation threat detection.
 * <p>
 * Registered once per battle (guarded by ACTIVE flag). Each frame it iterates all
 * projectiles once and distributes threat scores to all nearby Draconis fighters -
 * instead of each fighter independently scanning the full projectile list.
 * <p>
 * Results are stored in TARGET_MULTS, keyed by ship ID. XLII_DraconisWingHull reads
 * this cache in advanceInCombat() and handles smoothing/application from there.
 */
public class XLII_WingThreatPlugin implements EveryFrameCombatPlugin {

    // Shared with XLII_DraconisWingHull
    public static final Map<String, Float> TARGET_MULTS = new HashMap<>();

    // Must match constants in XLII_DraconisWingHull
    private static final float MAX_DETECT_RANGE  = 600f;
    private static final float MAX_TCA_SECONDS   = 1.5f;
    private static final float INTERCEPT_MARGIN  = 2.5f;
    private static final int   MAX_THREATS       = 3;
    private static final float MAX_TIME_MULT     = 4.0f;

    private static final float MAX_DETECT_RANGE_SQ = MAX_DETECT_RANGE * MAX_DETECT_RANGE;

    @Override
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    public void init(CombatEngineAPI engine) {
        TARGET_MULTS.clear();
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // Collect active Draconis fighters (those carrying our hullmod)
        List<ShipAPI> allShips = engine.getShips();
        // Temporary per-fighter state for this frame
        // fighterData[i] = { shipRef, locX, locY, velX, velY, interceptRadiusSq, bestProximity, threatCount }
        int fighterCount = 0;
        ShipAPI[] fighters = new ShipAPI[allShips.size()];
        float[] fx = new float[allShips.size()];  // fighter loc X
        float[] fy = new float[allShips.size()];  // fighter loc Y
        float[] fvx = new float[allShips.size()]; // fighter vel X
        float[] fvy = new float[allShips.size()]; // fighter vel Y
        float[] fir = new float[allShips.size()]; // intercept radius sq
        float[] bestProximity = new float[allShips.size()];
        int[] threatCount = new int[allShips.size()];

        for (ShipAPI ship : allShips) {
            if (ship == null || ship.isHulk()) continue;
            if (ship.getHullSize() != ShipAPI.HullSize.FIGHTER) continue;
            if (!ship.getVariant().hasHullMod("XLII_draconiswinghull")) continue;

            int i = fighterCount++;
            fighters[i] = ship;
            Vector2f loc = ship.getLocation();
            Vector2f vel = ship.getVelocity();
            fx[i] = loc.x;
            fy[i] = loc.y;
            fvx[i] = vel.x;
            fvy[i] = vel.y;
            float ir = ship.getCollisionRadius() * INTERCEPT_MARGIN;
            fir[i] = ir * ir;
            bestProximity[i] = 0f;
            threatCount[i] = 0;
        }

        if (fighterCount == 0) return;

        // Single pass over all projectiles
        List<DamagingProjectileAPI> projectiles = engine.getProjectiles();
        for (DamagingProjectileAPI proj : projectiles) {
            if (proj.didDamage() || proj.isFading()) continue;

            Vector2f projLoc = proj.getLocation();
            Vector2f projVel = proj.getVelocity();
            float px = projLoc.x;
            float py = projLoc.y;
            float pvx = projVel.x;
            float pvy = projVel.y;
            int owner = proj.getOwner();

            for (int i = 0; i < fighterCount; i++) {
                if (threatCount[i] >= MAX_THREATS) continue;
                if (owner == fighters[i].getOwner()) continue;

                // Distance pre-filter
                float dx = fx[i] - px;
                float dy = fy[i] - py;
                if (dx * dx + dy * dy > MAX_DETECT_RANGE_SQ) continue;

                // Closest-approach geometry (relative velocity in fighter's frame)
                float rvx = pvx - fvx[i];
                float rvy = pvy - fvy[i];
                float relVelSq = rvx * rvx + rvy * rvy;
                if (relVelSq < 1f) continue;

                float tca = (dx * rvx + dy * rvy) / relVelSq;
                if (tca < 0f || tca > MAX_TCA_SECONDS) continue;

                float relPosSq = dx * dx + dy * dy;
                float closestDistSq = Math.max(0f, relPosSq - tca * tca * relVelSq);
                if (closestDistSq > fir[i]) continue;

                float proximity = 1f - (tca / MAX_TCA_SECONDS);
                if (proximity > bestProximity[i]) bestProximity[i] = proximity;
                threatCount[i]++;
            }
        }

        // Write results to shared cache
        for (int i = 0; i < fighterCount; i++) {
            TARGET_MULTS.put(fighters[i].getId(),
                    1f + (MAX_TIME_MULT - 1f) * bestProximity[i]);
        }
    }

    @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
    @Override public void renderInWorldCoords(ViewportAPI viewport) {}
    @Override public void renderInUICoords(ViewportAPI viewport) {}
}