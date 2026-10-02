package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Salvo Bank ship system. Every point of damage the ship deals is banked;
 * once enough damage has accumulated, a torpedo is added to standby, flanking
 * the ship like the Phase Torpedo Array. Activating the system releases the
 * whole bank as a ripple that always takes about as long as the system's
 * "active" duration (ship_systems.csv) to fire - more banked torpedoes means
 * a shorter gap between each one, not a longer ripple. Unlike the Phase
 * Torpedo Array this doesn't require repeated activations to build a stack,
 * and the torpedoes never phase.
 */
public class XLII_SalvoBankStats extends BaseShipSystemScript implements DamageDealtModifier {

    // ==================== TUNING PARAMETERS ====================

    private static final float SYSTEM_RANGE = 2000f;

    // Damage required to bank one torpedo; also the damage each banked torpedo deals on hit.
    private static final float DAMAGE_PER_TORPEDO = 250f;

    private static final float DAMAGE_BANK_FRACTION = 0.25f;

    // Hard cap on standby torpedoes; excess damage dealt while the bank is full is simply wasted.
    public static final int MAX_BANKED_TORPEDOES = 16;

    // Formation constants for the two-column torpedo layout (see updateFormation()).
    private static final float BASE_SPAWN_OFFSET = 150f;
    private static final float MAX_SPACING = 50f;
    private static final float MIN_SPACING = 15f;
    private static final float SPACING_SHRINK_PER_TORPEDO = 6f;
    private static final float FORMATION_POSITION_LERP = 0.05f;

    private static final Color TORPEDO_COLOR = new Color(255, 150, 50, 200);
    private static final Color TORPEDO_PULSE_COLOR = new Color(255, 150, 50, 100);
    private static final Color LAUNCH_ARC_COLOR = new Color(255, 150, 50, 120);
    private static final Color ARMED_JITTER_COLOR = new Color(255, 150, 50, 100);

    // ==================== STATE TRACKING ====================

    private static class VisualTorpedo {
        final Vector2f virtualPosition = new Vector2f();
        float effectTimer = 0f;
        float pulseTimer = 0f;
        boolean isStarboard;  // fixed at banking time - never reassigned as the list shrinks
    }

    private final List<VisualTorpedo> bankedTorpedoes = new ArrayList<>();
    private float damageAccumulated = 0f;
    private boolean startOnStarboard = true;  // alternates which side fills first each release cycle

    private ShipAPI ship;
    private WeaponAPI torpedoWeapon;
    private boolean listenerRegistered = false;
    private float prevEffectLevel = 0f;

    private boolean isLaunching = false;
    private float launchTimer = 0f;
    private float launchInterval = 0f;  // spacing between shots for the current volley, sized to fit getChargeActiveDur()
    private ShipAPI launchTarget;
    private boolean launchDumbfire;

    private final Object STATUSKEY_BANK = new Object();
    private final Object STATUSKEY_RANGE = new Object();

    // ==================== MAIN SYSTEM LOGIC ====================

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI shipEntity = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (shipEntity == null) return;
        this.ship = shipEntity;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        if (!listenerRegistered) {
            ship.addListener(this);
            listenerRegistered = true;
        }

        if (torpedoWeapon == null) {
            torpedoWeapon = engine.createFakeWeapon(ship, "XLII_salvotorp_launcher");
        }

        boolean isPlayer = ship == engine.getPlayerShip();

        // Detect activation when effectLevel reaches 1.0 (fully charged)
        boolean activated = (effectLevel >= 1f && prevEffectLevel < 1f);
        if (activated && !isLaunching && !bankedTorpedoes.isEmpty()) {
            beginRelease();
        }

        if (isLaunching && !engine.isPaused()) {
            updateRelease(engine);
        }

        if (!bankedTorpedoes.isEmpty() && !engine.isPaused()) {
            updateFormation(engine);
        }

        if (isPlayer) {
            updatePlayerStatus(engine);
        }

        prevEffectLevel = effectLevel;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Never called - runScriptWhileIdle:true in system file.
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (bankedTorpedoes.isEmpty()) return null;
        if (system.getState() != ShipSystemAPI.SystemState.IDLE) return null;
        return "READY (" + bankedTorpedoes.size() + ")";
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return !bankedTorpedoes.isEmpty();
    }

    /**
     * Current bank size. Exposed for XLII_SalvoBankAI, which holds off on
     * releasing a token salvo since the cooldown afterward is the same
     * length no matter how many torpedoes were in it.
     */
    public int getBankedCount() {
        return bankedTorpedoes.size();
    }

    // ==================== DAMAGE TRACKING ====================

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (ship == null) return null;

        // Don't let the salvo's own torpedoes feed the bank - otherwise every
        // hit would immediately queue up another torpedo, snowballing forever.
        if (param instanceof DamagingProjectileAPI proj
                && "XLII_salvotorp".equals(proj.getProjectileSpecId())) {
            return null;
        }

        // No banking unless the system is fully IDLE (not charging, releasing, or
        // cooling down) - otherwise weapon fire during/after a release queues up
        // the next torpedo for free.
        if (ship.getSystem().getState() != ShipSystemAPI.SystemState.IDLE) return null;

        // Only damage to living enemy ships/fighters counts - not allies,
        // hulks, missiles, or asteroids.
        if (!(target instanceof ShipAPI)) return null;
        ShipAPI targetShip = (ShipAPI) target;
        if (!targetShip.isAlive()) return null;
        if (targetShip.getOwner() == ship.getOwner()) return null;

        if (bankedTorpedoes.size() >= MAX_BANKED_TORPEDOES) return null;

        damageAccumulated += damage.getDamage() * DAMAGE_BANK_FRACTION;
        while (damageAccumulated >= DAMAGE_PER_TORPEDO && bankedTorpedoes.size() < MAX_BANKED_TORPEDOES) {
            damageAccumulated -= DAMAGE_PER_TORPEDO;
            bankTorpedo();
        }

        return null;
    }

    /**
     * Adds one torpedo to standby and gives a small spawn flash for feedback.
     */
    private void bankTorpedo() {
        int index = bankedTorpedoes.size();
        boolean isStarboard = startOnStarboard ? (index % 2 == 0) : (index % 2 != 0);

        VisualTorpedo vt = new VisualTorpedo();
        vt.virtualPosition.set(ship.getLocation());
        vt.isStarboard = isStarboard;
        bankedTorpedoes.add(vt);

        CombatEngineAPI engine = Global.getCombatEngine();
        Vector2f loc = ship.getLocation();
        Vector2f vel = ship.getVelocity();

        engine.addSmoothParticle(loc, vel, 15f, 0.8f, 0.4f, TORPEDO_COLOR);
        engine.addSmoothParticle(loc, vel, 6f, 1f, 0.3f, new Color(255, 220, 180, 200));
    }

    // ==================== FORMATION UPDATE ====================

    /**
     * Arranges banked torpedoes into two columns flanking the ship, recomputed
     * every frame so within-column spacing shrinks as more torpedoes join -
     * they bunch closer together instead of the columns growing longer.
     */
    private void updateFormation(CombatEngineAPI engine) {
        float amount = engine.getElapsedInLastFrame();

        int count = bankedTorpedoes.size();
        float spacing = Math.max(MIN_SPACING, MAX_SPACING - (count - 1) * SPACING_SHRINK_PER_TORPEDO);

        int starboardSlot = 0;
        int portSlot = 0;

        for (VisualTorpedo vt : bankedTorpedoes) {
            int sideSlot = vt.isStarboard ? starboardSlot++ : portSlot++;
            float distance = BASE_SPAWN_OFFSET + sideSlot * spacing;

            float angle = ship.getFacing() + (vt.isStarboard ? 90f : -90f);
            Vector2f offset = Misc.getUnitVectorAtDegreeAngle(angle);
            offset.scale(distance);

            Vector2f idealPosition = new Vector2f(ship.getLocation());
            Vector2f.add(idealPosition, offset, idealPosition);

            vt.virtualPosition.x += (idealPosition.x - vt.virtualPosition.x) * FORMATION_POSITION_LERP;
            vt.virtualPosition.y += (idealPosition.y - vt.virtualPosition.y) * FORMATION_POSITION_LERP;

            vt.effectTimer += amount;
            vt.pulseTimer += amount;

            renderTorpedo(vt, engine);
        }
    }

    /**
     * Cheap per-frame render: one pulsing glow plus an occasional ripple.
     * Kept light since torpedoes can sit in standby for the whole engagement.
     */
    private void renderTorpedo(VisualTorpedo vt, CombatEngineAPI engine) {
        Vector2f pos = vt.virtualPosition;
        Vector2f zeroVel = new Vector2f();

        float pulse = 0.7f + 0.3f * (float) FastTrig.sin(vt.effectTimer * 2f);
        engine.addSmoothParticle(pos, zeroVel, 5f * pulse, 0.8f * pulse, 0.15f, TORPEDO_COLOR);

        if (vt.pulseTimer >= 0.4f) {
            vt.pulseTimer = 0f;
            engine.addSmoothParticle(pos, zeroVel, 11f, 0.5f, 0.35f, TORPEDO_PULSE_COLOR);
        }
    }

    // ==================== RELEASE HANDLING ====================

    /**
     * Locks in the target (or dumbfire) and the per-shot spacing for this
     * volley, then fires the first torpedo immediately; the rest follow in
     * updateRelease().
     */
    private void beginRelease() {
        ShipAPI target = findTarget(ship);
        boolean targetInRange = isInRange(ship, target);
        launchTarget = targetInRange ? target : null;
        launchDumbfire = (launchTarget == null);

        int count = bankedTorpedoes.size();
        launchInterval = count > 1 ? ship.getSystem().getChargeActiveDur() / (count - 1) : 0f;

        isLaunching = true;
        launchTimer = 0f;
    }

    /**
     * Fires one banked torpedo every launchInterval seconds until the bank
     * (or the queue accumulated during the volley) is drained.
     */
    private void updateRelease(CombatEngineAPI engine) {
        launchTimer -= engine.getElapsedInLastFrame();
        if (launchTimer > 0f) return;

        VisualTorpedo vt = bankedTorpedoes.remove(0);
        launchSingleTorpedo(vt, engine);

        if (bankedTorpedoes.isEmpty()) {
            isLaunching = false;
            startOnStarboard = !startOnStarboard;
        } else {
            launchTimer = launchInterval;
        }
    }

    /**
     * Spawns a single real missile from a banked torpedo's standby position.
     */
    private void launchSingleTorpedo(VisualTorpedo vt, CombatEngineAPI engine) {
        float launchAngle = launchDumbfire
            ? ship.getFacing()
            : Misc.getAngleInDegrees(vt.virtualPosition, launchTarget.getLocation());

        MissileAPI missile = (MissileAPI) engine.spawnProjectile(
            ship,
            torpedoWeapon,
            "XLII_salvotorp_launcher",
            vt.virtualPosition,
            ship.getFacing(),
            new Vector2f()
        );

        if (missile == null) return;

        missile.setJitter(this, ARMED_JITTER_COLOR, 0.5f, 2, 0f, 2.5f);

        Vector2f velocity = Misc.getUnitVectorAtDegreeAngle(launchAngle);
        velocity.scale(missile.getSpec().getLaunchSpeed());
        missile.getVelocity().set(velocity);

        engine.spawnEmpArcVisual(
            ship.getLocation(),
            ship,
            missile.getLocation(),
            missile,
            5f,
            LAUNCH_ARC_COLOR,
            Color.WHITE
        );
    }

    // ==================== TARGET FINDING ====================

    private ShipAPI findTarget(ShipAPI ship) {
        ShipAPI target = ship.getShipTarget();

        if (target != null && target.isAlive()) {
            return target;
        }

        return Misc.findClosestShipEnemyOf(
            ship,
            ship.getMouseTarget() != null ? ship.getMouseTarget() : ship.getLocation(),
            ShipAPI.HullSize.FIGHTER,
            SYSTEM_RANGE,
            true
        );
    }

    private boolean isInRange(ShipAPI ship, ShipAPI target) {
        if (target == null) return false;

        float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
        float radSum = ship.getCollisionRadius() + target.getCollisionRadius();

        return dist <= SYSTEM_RANGE + radSum;
    }

    // ==================== PLAYER UI ====================

    private void updatePlayerStatus(CombatEngineAPI engine) {
        if (!bankedTorpedoes.isEmpty()) {
            int progressPct = (int) ((damageAccumulated / DAMAGE_PER_TORPEDO) * 100f);
            engine.maintainStatusForPlayerShip(
                STATUSKEY_BANK,
                ship.getSystem().getSpecAPI().getIconSpriteName(),
                ship.getSystem().getDisplayName(),
                bankedTorpedoes.size() + " / " + MAX_BANKED_TORPEDOES + " banked (next " + progressPct + "%)",
                false
            );
        }

        ShipAPI target = findTarget(ship);
        if (target != null && !bankedTorpedoes.isEmpty()) {
            boolean inRange = isInRange(ship, target);
            engine.maintainStatusForPlayerShip(
                STATUSKEY_RANGE,
                ship.getSystem().getSpecAPI().getIconSpriteName(),
                ship.getSystem().getDisplayName(),
                inRange ? "IN RANGE" : "OUT OF RANGE",
                !inRange
            );
        }
    }
}
