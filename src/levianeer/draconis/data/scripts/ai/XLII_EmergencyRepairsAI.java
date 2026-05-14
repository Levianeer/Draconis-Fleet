package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

public class XLII_EmergencyRepairsAI implements ShipSystemAIScript {

    private static final float HULL_REPAIR_PERCENT = 0.30f;

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.25f, 0.5f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;
        tracker.setInterval(
            0.25f + (float) Math.random() * 0.15f,
            0.5f + (float) Math.random() * 0.25f
        );
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;
        if (!AIUtils.canUseSystemThisFrame(ship)) return;
        if (system.isOutOfAmmo()) return;

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        // Do not use while in danger — system imposes a speed penalty
        if (missileDangerDir != null) return;
        if (collisionDangerDir != null) return;
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) return;

        // Use only when we gain the maximum hull restore (lost >= 30% hull)
        if (ship.getHullLevel() > 1f - HULL_REPAIR_PERCENT) return;

        ship.useSystem();
    }
}