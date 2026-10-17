package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// Pulses width/sound/particles a few times during each full-charge window to sell the
// "rapid burst" feel, instead of relying on sub-frame chargeup/chargedown/burst-size
// values in weapon_data.csv (those are prone to frame-timestep jitter at this scale).
// Same approach Diable Avionics uses for its Glaux/Burchel burst beams.
public class XLII_ParticleLanceBurstEffect implements BeamEffectPlugin {

    private static final float PULSE_INTERVAL = 0.025f;

    private boolean initialized = false;
    private float baseWidth;
    private Color fringe;

    private boolean wasFullCharge = false;
    private final IntervalUtil pulseTimer = new IntervalUtil(PULSE_INTERVAL, PULSE_INTERVAL);

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine.isPaused()) return;

        if (!initialized) {
            initialized = true;
            baseWidth = beam.getWidth();
            fringe = beam.getFringeColor();
        }

        boolean fullCharge = beam.getBrightness() >= 1f;
        if (fullCharge && !wasFullCharge) {
            pulseTimer.forceIntervalElapsed();
        }
        wasFullCharge = fullCharge;

        if (!fullCharge) return;

        Vector2f start = beam.getFrom();
        Vector2f end = beam.getTo();
        if (MathUtils.getDistanceSquared(start, end) == 0) return;

        pulseTimer.advance(amount);

        float phase = pulseTimer.getElapsed() / PULSE_INTERVAL;
        beam.setWidth(baseWidth * (0.5f + 0.5f * (float) Math.cos(phase * (float) Math.PI * 2f)));

        if (pulseTimer.intervalElapsed()) {
            Global.getSoundPlayer().playSound(
                    "XLII_particlelance_fire", 1f, 0.6f, start, beam.getSource().getVelocity());
            engine.addHitParticle(
                    start, beam.getSource().getVelocity(),
                    MathUtils.getRandomNumberInRange(baseWidth * 1.5f, baseWidth * 2.5f),
                    0.6f, 0.12f, fringe);
        }
    }
}
