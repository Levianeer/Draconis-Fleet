package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * AI for the Flux Cycler ship system.
 * Accumulates incoming shield damage via DamageTakenModifier and activates
 * the system when a meaningful hit is detected within one polling interval.
 */
public class XLII_FluxCyclerAI implements ShipSystemAIScript, DamageTakenModifier {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.08f, 0.12f);
    private boolean listenerRegistered = false;
    private float recentShieldDamage = 0f;

    // ==================== TUNING PARAMETERS ====================

    // Shield damage accumulated over one polling interval needed to trigger activation.
    private static final float DAMAGE_THRESHOLD = 750f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        if (!listenerRegistered) {
            ship.addListener(this);
            listenerRegistered = true;
        }

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        float damageTakenThisInterval = recentShieldDamage;
        recentShieldDamage = 0f;

        if (!AIUtils.canUseSystemThisFrame(ship)) return;
        if (hasHardBlock()) return;
        if (damageTakenThisInterval < DAMAGE_THRESHOLD) return;

        ship.useSystem();
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (shieldHit && system != null && !system.isActive() && system.getCooldownRemaining() == 0f) {
            recentShieldDamage += damage.getDamage();
        }
        return null;
    }

    private boolean hasHardBlock() {
        if (ship.getFluxTracker().isOverloadedOrVenting()) return true;
        return flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY);
    }
}