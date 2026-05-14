package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.awt.*;
import java.util.List;

/**
 * AI for the Billhook torpedo. Unguided - no steering.
 * Handles all detonation logic against missiles and fighters:
 *   - 2+ enemies within CLUSTER_RANGE: early cluster detonation
 *   - any enemy within SINGLE_RANGE: single-target detonation
 * Ships are handled by direct collision (collisionRadius in the .proj).
 * Also replicates NeutronTorpedoOnFireEffect (0.33x launch velocity) on first advance().
 */
public class XLII_BillhookAI implements MissileAIPlugin {

    private static final float CLUSTER_RANGE = 75f;
    private static final float SINGLE_RANGE = 35f;
    private static final int CLUSTER_THRESHOLD = 2;

    private static final Color EXPLOSION_COLOR = new Color(255, 165, 50, 255);
    private static final Color PARTICLE_COLOR = new Color(255, 155, 155, 205);

    private CombatEngineAPI engine;
    private final MissileAPI missile;

    public XLII_BillhookAI(MissileAPI missile, ShipAPI launchingShip) {
        this.missile = missile;
    }

    @Override
    public void advance(float amount) {
        if (engine != Global.getCombatEngine()) {
            engine = Global.getCombatEngine();
        }

        if (engine.isPaused() || missile.isFading() || missile.isFizzling()) {
            return;
        }

        missile.giveCommand(ShipCommand.ACCELERATE);

        int clusterCount = 0;
        boolean anyInSingleRange = false;

        // Check hostile missiles
        List<MissileAPI> nearMissiles = CombatUtils.getMissilesWithinRange(missile.getLocation(), CLUSTER_RANGE);
        for (MissileAPI m : nearMissiles) {
            if (m.getOwner() == missile.getOwner() || m.isFading() || m.getCollisionClass() == CollisionClass.NONE) {
                continue;
            }
            clusterCount++;
            if (!anyInSingleRange && MathUtils.getDistance(missile.getLocation(), m.getLocation()) <= SINGLE_RANGE) {
                anyInSingleRange = true;
            }
        }

        // Check hostile fighters
        List<ShipAPI> nearShips = CombatUtils.getShipsWithinRange(missile.getLocation(), CLUSTER_RANGE);
        for (ShipAPI ship : nearShips) {
            if (ship.getOwner() == missile.getOwner() || !ship.isFighter() || !ship.isAlive()) {
                continue;
            }
            clusterCount++;
            if (!anyInSingleRange && MathUtils.getDistance(missile.getLocation(), ship.getLocation()) <= SINGLE_RANGE) {
                anyInSingleRange = true;
            }
        }

        if (clusterCount >= CLUSTER_THRESHOLD || anyInSingleRange) {
            detonate();
        }
    }

    private void detonate() {
        DamagingExplosionSpec boom = new DamagingExplosionSpec(
                0.1f,
                50f,
                35f,
                missile.getDamageAmount(),
                missile.getDamageAmount() * 0.5f,
                CollisionClass.PROJECTILE_FF,
                CollisionClass.PROJECTILE_FIGHTER,
                3f,
                3f,
                1f,
                100,
                PARTICLE_COLOR,
                EXPLOSION_COLOR
        );
        boom.setDamageType(missile.getDamageType());
        boom.setSoundSetId("explosion_flak");
        engine.spawnDamagingExplosion(boom, missile.getSource(), missile.getLocation());
        engine.removeEntity(missile);
    }

}
