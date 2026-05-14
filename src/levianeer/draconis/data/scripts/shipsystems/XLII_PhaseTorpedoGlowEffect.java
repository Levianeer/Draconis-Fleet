package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.EnumSet;

/**
 * Renders phase ship-style glow effects on phase torpedoes.
 * Uses two layers: phaseDiffuse (soft wide glow) and phaseHighlight (bright focused glow).
 */
public class XLII_PhaseTorpedoGlowEffect extends BaseCombatLayeredRenderingPlugin {

    // Phase ship standard colors
    private static final Color EFFECT_COLOR_1 = new Color(255,175,255,255);  // bright highlight
    private static final Color EFFECT_COLOR_2 = new Color(255,0,255,150);    // vivid diffuse

    // Fade out duration in seconds
    private static final float FADE_OUT_DURATION = 0.5f;
    private static final float FADE_RATE = 1f / FADE_OUT_DURATION;

    private final MissileAPI missile;
    private SpriteAPI phaseHighlight;  // glow1 - brighter focused glow
    private SpriteAPI phaseDiffuse;    // glow2 - softer wide glow

    private boolean shouldFadeOut = false;
    private float fadeAlpha = 1f;

    // Jitter parameters (calculated in advance(), used in render())
    private float jitterLevel = 0f;
    private float jitterRangeBonus = 0f;

    // Pre-computed per-frame jitter offsets to avoid Math.random() calls in render()
    private static final int JITTER_COPIES = 11;
    private final float[] jitterX = new float[JITTER_COPIES];
    private final float[] jitterY = new float[JITTER_COPIES];

    public XLII_PhaseTorpedoGlowEffect(MissileAPI missile) {
        this.missile = missile;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);

        // Load glow sprites from registered category
        phaseHighlight = Global.getSettings().getSprite("missiles", "XLII_phasetorp_glow1");
        phaseDiffuse = Global.getSettings().getSprite("missiles", "XLII_phasetorp_glow2");

        // Set additive blend mode for glow effect
        phaseHighlight.setAdditiveBlend();
        phaseDiffuse.setAdditiveBlend();
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused()) return;

        // Update entity location to match missile
        entity.getLocation().set(missile.getLocation());

        // Handle fade out
        if (shouldFadeOut) {
            fadeAlpha -= FADE_RATE * amount;
            if (fadeAlpha < 0f) {
                fadeAlpha = 0f;
            }
        }

        // Constant jitter parameters
        // These are used by render() to manually jitter the glow sprites
        jitterLevel = 1.0f;           // Full intensity
        jitterRangeBonus = 9f;        // 9px shimmer range

        // Pre-compute jitter offsets here (once per frame) so render() needs no Math.random()
        for (int i = 0; i < JITTER_COPIES; i++) {
            jitterX[i] = ((float) Math.random() - 0.5f) * 2f * jitterRangeBonus;
            jitterY[i] = ((float) Math.random() - 0.5f) * 2f * jitterRangeBonus;
        }

        // Note: We don't call setJitter() on the missile here - instead, we manually
        // jitter the glow sprites in render() to keep the missile sprite stable
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        // Render on top of ships like phase ship glows
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (missile == null || phaseHighlight == null || phaseDiffuse == null) return;

        float x = missile.getLocation().x;
        float y = missile.getLocation().y;
        float facing = missile.getFacing();

        // Calculate alpha multiplier
        float alphaMult = viewport.getAlphaMult() * fadeAlpha;

        // Also account for missile fading
        if (missile.isFading()) {
            alphaMult *= missile.getBrightness();
        }

        // Get missile sprite size for scaling
        float missileWidth = missile.getSpriteAPI().getWidth();
        float missileHeight = missile.getSpriteAPI().getHeight();

        // === RENDER PHASE DIFFUSE (background glow) ===
        phaseDiffuse.setAngle(facing - 90f);
        phaseDiffuse.setSize(missileWidth, missileHeight);
        phaseDiffuse.setColor(EFFECT_COLOR_2);

        // Always render base layer for visibility
        phaseDiffuse.setAlphaMult(alphaMult * 0.7f);
        phaseDiffuse.renderAtCenter(x, y);

        // Render additional jittered copies during fade-in for shimmer effect
        if (jitterLevel > 0.01f && jitterRangeBonus > 0.1f) {
            float copyAlpha = alphaMult * 0.7f * (jitterLevel * 0.1f);
            phaseDiffuse.setAlphaMult(copyAlpha);
            for (int i = 0; i < JITTER_COPIES; i++) {
                phaseDiffuse.renderAtCenter(x + jitterX[i], y + jitterY[i]);
            }
        }

        // === RENDER PHASE HIGHLIGHT (foreground glow) ===
        phaseHighlight.setAngle(facing - 90f);
        phaseHighlight.setSize(missileWidth, missileHeight);
        phaseHighlight.setColor(EFFECT_COLOR_1);

        // Always render base layer for visibility
        phaseHighlight.setAlphaMult(alphaMult);
        phaseHighlight.renderAtCenter(x, y);

        // Render additional jittered copies during fade-in for shimmer effect
        if (jitterLevel > 0.01f && jitterRangeBonus > 0.1f) {
            float copyAlpha = alphaMult * (jitterLevel * 0.12f);
            phaseHighlight.setAlphaMult(copyAlpha);
            for (int i = 0; i < JITTER_COPIES; i++) {
                phaseHighlight.renderAtCenter(x + jitterX[i], y + jitterY[i]);
            }
        }
    }

    @Override
    public boolean isExpired() {
        // Expire when missile is gone or fully faded out
        return missile.isExpired() || !Global.getCombatEngine().isEntityInPlay(missile) || (shouldFadeOut && fadeAlpha <= 0f);
    }

    @Override
    public float getRenderRadius() {
        // Render radius based on missile size
        return 100f;
    }

    /**
     * Signals the glow effect to start fading out (called when missile dephases).
     */
    public void startFadeOut() {
        shouldFadeOut = true;
    }
}
