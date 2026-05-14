package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;

public class XLII_AdaptationField extends BaseHullMod {

    private static final float WINDOW_DURATION   = 3.0f;  // sliding window length in seconds
    private static final float VISUAL_RAMP_UP    = 0.3f;  // seconds to reach full visual intensity
    private static final float VISUAL_DECAY      = 1.5f;  // seconds to fade visual after fire stops
    private static final float FLUX_SCALING      = 0.03f; // threshold bonus per point of max flux capacity

    // Per hull size: {base DPS threshold, minimum damage multiplier}
    private static final Map<HullSize, float[]> SIZE_PARAMS = new HashMap<>();
    static {
        SIZE_PARAMS.put(HullSize.FRIGATE,      new float[]{  80f, 0.65f});
        SIZE_PARAMS.put(HullSize.DESTROYER,    new float[]{ 160f, 0.55f});
        SIZE_PARAMS.put(HullSize.CRUISER,      new float[]{ 320f, 0.45f});
        SIZE_PARAMS.put(HullSize.CAPITAL_SHIP, new float[]{ 550f, 0.35f});
    }

    private static final Color SHIELD_INNER_LOW  = new Color(255, 125, 125,  75); // LOW_TECH resting state
    private static final Color SHIELD_INNER_HIGH = new Color(125, 125, 255,  75); // HIGH_TECH at full attenuation
    private static final Color FIELD_JITTER_COLOR = new Color(140, 200, 255,  60);

    private static CombatEngineAPI lastEngine_protoAttenuation;
    private static final Map<ShipAPI, AttenuationTracker> trackers = new HashMap<>();

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_protoAttenuation) {
            lastEngine_protoAttenuation = engine;
            trackers.clear();
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        checkClearState();

        if (ship == null || !ship.isAlive()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        if (!trackers.containsKey(ship)) {
            float maxFlux = ship.getMutableStats().getFluxCapacity().getModifiedValue();
            float[] params = SIZE_PARAMS.getOrDefault(ship.getHullSize(), SIZE_PARAMS.get(HullSize.DESTROYER));
            float threshold = params[0] + maxFlux * FLUX_SCALING;
            float minMult   = params[1];
            AttenuationTracker tracker = new AttenuationTracker(ship, threshold, minMult);
            ship.addListener(tracker);
            trackers.put(ship, tracker);
        }

        AttenuationTracker tracker = trackers.get(ship);
        tracker.advance(amount);

        float level = tracker.attenuationLevel;

        // Interpolate shield inner color LOW_TECH -> HIGH_TECH based on attenuation level
        ShieldAPI shield = ship.getShield();
        if (shield != null) {
            int r = Math.round(SHIELD_INNER_LOW.getRed()   + (SHIELD_INNER_HIGH.getRed()   - SHIELD_INNER_LOW.getRed())   * level);
            int g = Math.round(SHIELD_INNER_LOW.getGreen() + (SHIELD_INNER_HIGH.getGreen() - SHIELD_INNER_LOW.getGreen()) * level);
            int b = Math.round(SHIELD_INNER_LOW.getBlue()  + (SHIELD_INNER_HIGH.getBlue()  - SHIELD_INNER_LOW.getBlue())  * level);
            int a = Math.round(SHIELD_INNER_LOW.getAlpha() + (SHIELD_INNER_HIGH.getAlpha() - SHIELD_INNER_LOW.getAlpha()) * level);
            shield.setInnerColor(new Color(r, g, b, a));
        }

        // Secondary jitter distortion at higher attenuation levels
        if (level > 0.25f) {
            ship.setJitter(this, FIELD_JITTER_COLOR, (level - 0.25f) / 0.75f, 2, 0f, 4f * level);
        }
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        float[] params = SIZE_PARAMS.getOrDefault(hullSize, SIZE_PARAMS.get(HullSize.DESTROYER));
        int floorPct = Math.round((1f - params[1]) * 100f);
        int windowSec = Math.round(WINDOW_DURATION);

        tooltip.addPara(
            "A prototype energy technology that dynamically reduces incoming shield damage based on the intensity " +
                   "of fire received. Tracks each attacking ship independently.",
                opad, h, windowSec + "s");

        tooltip.addPara(
                "Moderate fire has little effect. Sustained or high-burst fire triggers strong attenuation, " +
                       "reducing shield damage by up to %s from that attacker.",
            opad, h, floorPct + "%");

        tooltip.addPara(
            "Attenuation is calculated over a %s window per attacker. Coordinated fire from multiple ships " +
                   "bypasses the field far more effectively than a single concentrated source. Does not protect armor or hull.",
            opad, h, windowSec + "s");
    }

    static class AttenuationTracker implements DamageTakenModifier {

        private final ShipAPI ship;
        private final float threshold;
        private final float minMult;
        // attackerId -> deque of {gameTime, damage}
        private final Map<String, ArrayDeque<float[]>> hitLog = new HashMap<>();
        float attenuationLevel = 0f;

        AttenuationTracker(ShipAPI ship, float threshold, float minMult) {
            this.ship      = ship;
            this.threshold = threshold;
            this.minMult   = minMult;
        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target,
                                        DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (!shieldHit) return null;
            if (ship.getFluxTracker().isOverloaded()) return null;

            // Identify attacker
            String attackerId = "UNKNOWN";
            if (param instanceof DamagingProjectileAPI) {
                ShipAPI src = ((DamagingProjectileAPI) param).getSource();
                if (src != null) attackerId = src.getId();
            } else if (param instanceof BeamAPI) {
                ShipAPI src = ((BeamAPI) param).getSource();
                if (src != null) attackerId = src.getId();
            }

            // Record this hit in the sliding window
            float now = Global.getCombatEngine().getTotalElapsedTime(false);
            ArrayDeque<float[]> entries = hitLog.computeIfAbsent(attackerId, k -> new ArrayDeque<>());
            entries.addLast(new float[]{now, damage.getDamage()});

            // Prune entries outside the window
            while (!entries.isEmpty() && now - entries.peekFirst()[0] > WINDOW_DURATION) {
                entries.pollFirst();
            }

            // Compute this attacker's DPS over the window
            float sum = 0f;
            for (float[] entry : entries) sum += entry[1];
            float dps = sum / WINDOW_DURATION;

            // No attenuation below threshold; ramps down above with a floor
            float mult = Math.max(minMult, threshold / Math.max(threshold, dps));
            damage.getModifier().modifyMult("XLII_protoAttenuation", mult);
            return "XLII_protoAttenuation";
        }

        void advance(float amount) {
            float now = Global.getCombatEngine().getTotalElapsedTime(false);
            float targetAttenuation = 0f;

            for (ArrayDeque<float[]> entries : hitLog.values()) {
                while (!entries.isEmpty() && now - entries.peekFirst()[0] > WINDOW_DURATION) {
                    entries.pollFirst();
                }
                if (entries.isEmpty()) continue;

                float sum = 0f;
                for (float[] entry : entries) sum += entry[1];
                float dps  = sum / WINDOW_DURATION;
                float mult = Math.max(minMult, threshold / Math.max(threshold, dps));
                float attenuation = 1f - mult;
                if (attenuation > targetAttenuation) targetAttenuation = attenuation;
            }

            // Smooth visual level toward the target attenuation
            if (targetAttenuation > attenuationLevel) {
                attenuationLevel = Math.min(targetAttenuation, attenuationLevel + amount / VISUAL_RAMP_UP);
            } else {
                attenuationLevel = Math.max(targetAttenuation, attenuationLevel - amount / VISUAL_DECAY);
            }
        }
    }
}
