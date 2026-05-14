package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import levianeer.draconis.data.scripts.XLII_WingThreatPlugin;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in hullmod for all Draconis fighter hulls.
 * <p>
 * Threat detection is handled by XLII_WingThreatPlugin, which does a single pass
 * over all projectiles per frame and writes target time-mult values into a shared
 * cache. This hullmod reads that cache, smooths the value via lerp, and applies
 * timeMult + inverse RoF debuff to keep real-time fire rate neutral.
 * <p>
 * The lerp is throttled to ~20Hz (every 3 frames at 60fps) - the smoothing makes
 * sub-frame precision unnecessary. Trail updates are not throttled.
 */
public class XLII_DraconisWingHull extends BaseHullMod {

    private static final String MOD_ID = "XLII_wing_timedilation";

    // Time mult tuning
    private static final float RISE_RATE         = 0.25f;
    private static final float DECAY_RATE        = 0.05f;
    private static final float CLEANUP_THRESHOLD = 1.001f;

    // Throttle: re-evaluate smoothed mult at this interval rather than every frame
    private static final float LERP_INTERVAL = 0.05f; // ~20Hz

    // --- Engine trail: shock contrail (2 passes, mirroring halberd_1 / halberd_2) ---
    // Colors shifted to orange-red; all other values from magicTrail_data.csv halberd rows
    private static final float  SHOCK_SIZE_IN      = 7f;
    private static final float  SHOCK_SIZE_OUT     = 3f;
    private static final float  SHOCK_OPACITY      = 0.4f;
    private static final float  SHOCK_IN_DUR       = 0.1f;
    private static final float  SHOCK_MAIN_DUR     = 0f;
    private static final float  SHOCK_OUT_DUR      = 1.5f;
    private static final float  SHOCK_SPEED_START  = 15f;
    private static final float  SHOCK_SPEED_END    = 80f;
    private static final float  SHOCK_ROT_START    = 0f;
    private static final float  SHOCK_ROT_END      = 200f;
    private static final Color  SHOCK_COLOR_IN     = new Color(255, 100, 20, 255);
    private static final Color  SHOCK_COLOR_OUT    = new Color(160, 30, 10, 255);

    // --- Engine trail: blur glow (mirroring halberd_glow) ---
    private static final float  GLOW_SIZE_IN       = 60f;
    private static final float  GLOW_SIZE_OUT      = 30f;
    private static final float  GLOW_OPACITY       = 0.5f;
    private static final float  GLOW_MAIN_DUR      = 0.05f;
    private static final float  GLOW_OUT_DUR       = 0.5f;
    private static final Color  GLOW_COLOR_IN      = new Color(255, 80, 15, 255);
    private static final Color  GLOW_COLOR_OUT     = new Color(100, 20, 5, 255);

    // Duration / cooldown
    private static final float ACTIVE_DURATION   = 2f;  // seconds timeMult can run before forced cooldown
    private static final float COOLDOWN_DURATION = 2f;  // seconds before activation is allowed again

    // Afterimage
    private static final Color AFTERIMAGE_COLOR = new Color(100, 180, 255, 110);

    // Per-fighter state
    private static final Map<String, Float>        currentTimeMult    = new HashMap<>();
    private static final Map<String, IntervalUtil> lerpIntervals      = new HashMap<>();
    private static final Map<String, IntervalUtil> afterimageIntervals = new HashMap<>();
    private static final Map<String, Float>        activeTimers        = new HashMap<>();  // seconds above threshold
    private static final Map<String, Float>        cooldownTimers      = new HashMap<>();  // seconds remaining on cooldown

    // Trail sprites cached after first load
    private static SpriteAPI shockSprite;
    private static SpriteAPI glowSprite;
    private static boolean   spritesLoaded = false;

    // Key used in CombatEngineAPI.getCustomData() to guard single registration per battle.
    // getCustomData() is scoped to the combat instance, so this resets automatically each battle.
    private static final String THREAT_PLUGIN_KEY = "XLII_wing_threat_active";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null && !engine.getCustomData().containsKey(THREAT_PLUGIN_KEY)) {
            engine.getCustomData().put(THREAT_PLUGIN_KEY, Boolean.TRUE);
            engine.addPlugin(new XLII_WingThreatPlugin());
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || ship.isHulk() || ship.getHullSize() != HullSize.FIGHTER) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        String shipId = ship.getId();
        float current = currentTimeMult.getOrDefault(shipId, 1f);

        // Trail update every frame - uses last known smoothed mult so it stays smooth
        // even though the lerp below is throttled.
        if (current >= CLEANUP_THRESHOLD) {
            advanceTrails(ship);
            // Afterimage: advance on real elapsed time so spawn rate is dilation-independent
            float globalAmount = engine.getElapsedInLastFrame();
            IntervalUtil ai = afterimageIntervals.computeIfAbsent(shipId, k -> new IntervalUtil(0.4f, 0.4f));
            ai.advance(globalAmount);
            if (ai.intervalElapsed()) renderAfterimage(ship);
        }

        // Advance cooldown every frame (not throttled - needs real elapsed time)
        float cooldown = cooldownTimers.getOrDefault(shipId, 0f);
        if (cooldown > 0f) {
            cooldown -= amount;
            if (cooldown > 0f) {
                cooldownTimers.put(shipId, cooldown);
            } else {
                cooldownTimers.remove(shipId);
            }
        }

        // Advance active timer every frame while above threshold and not on cooldown.
        // When duration expires, snap off immediately - DECAY_RATE is too slow to
        // visibly clear the effect within the cooldown window.
        if (current >= CLEANUP_THRESHOLD && !cooldownTimers.containsKey(shipId)) {
            float activeTime = activeTimers.getOrDefault(shipId, 0f) + amount;
            if (activeTime >= ACTIVE_DURATION) {
                // Snap the mult to 1f and remove modifiers immediately
                currentTimeMult.put(shipId, 1f);
                MutableShipStatsAPI stats = ship.getMutableStats();
                stats.getTimeMult().unmodify(MOD_ID);
                stats.getBallisticRoFMult().unmodify(MOD_ID);
                stats.getMissileRoFMult().unmodify(MOD_ID);
                stats.getEnergyRoFMult().unmodify(MOD_ID);
                stats.getMaxSpeed().unmodify(MOD_ID);
                cooldownTimers.put(shipId, COOLDOWN_DURATION);
                activeTimers.remove(shipId);
                lerpIntervals.remove(shipId); // reset so lerp doesn't fire mid-cooldown
                afterimageIntervals.remove(shipId);
                return; // nothing left to do this frame
            } else {
                activeTimers.put(shipId, activeTime);
            }
        }

        // Throttle lerp updates - no need to smooth every single frame
        IntervalUtil interval = lerpIntervals.computeIfAbsent(
                shipId, k -> new IntervalUtil(LERP_INTERVAL, LERP_INTERVAL));
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        // On cooldown: hold at 1f, don't apply any modifiers
        if (cooldownTimers.containsKey(shipId)) {
            currentTimeMult.remove(shipId);
            return;
        }

        float targetTimeMult = XLII_WingThreatPlugin.TARGET_MULTS.getOrDefault(shipId, 1f);
        float rate = (targetTimeMult > current) ? RISE_RATE : DECAY_RATE;
        float next = current + (targetTimeMult - current) * rate;
        currentTimeMult.put(shipId, next);

        applyModifiers(ship.getMutableStats(), next, shipId);
    }

    /**
     * Feeds three trail segments per active engine into MagicTrailPlugin each frame:
     * two shock-contrail passes (halberd_1/2 equivalent) and one blur-glow pass.
     * No entities are spawned - MagicTrail stores segments internally as structs.
     * Trails fade naturally when this stops being called (timeMult drops below threshold).
     */
    private void advanceTrails(ShipAPI ship) {
        loadSprites();
        if (shockSprite == null && glowSprite == null) return;

        List<ShipEngineControllerAPI.ShipEngineAPI> engines =
                ship.getEngineController().getShipEngines();

        // Stable ID block: 30 slots per ship (up to 10 engines × 3 passes each),
        // low collision risk with other mods.
        long baseId = Math.abs((long) ship.getId().hashCode()) % 100_000L;
        float angle = ship.getFacing() + 180f; // rearward

        for (int i = 0; i < engines.size(); i++) {
            ShipEngineControllerAPI.ShipEngineAPI eng = engines.get(i);
            if (!eng.isActive()) continue;

            Vector2f pos = new Vector2f(eng.getLocation().x, eng.getLocation().y);
            long slot = baseId * 30L + (long) i * 3;

            // Pass A - shock contrail (halberd_1 equivalent: randomVelocity ~0.6)
            if (shockSprite != null) {
                MagicTrailPlugin.addTrailMemberAdvanced(
                        null,
                        (float) slot,
                        shockSprite,
                        pos,
                        SHOCK_SPEED_START,
                        SHOCK_SPEED_END,
                        angle,
                        SHOCK_ROT_START,
                        SHOCK_ROT_END,
                        SHOCK_SIZE_IN,
                        SHOCK_SIZE_OUT,
                        SHOCK_COLOR_IN,
                        SHOCK_COLOR_OUT,
                        SHOCK_OPACITY,
                        SHOCK_IN_DUR,
                        SHOCK_MAIN_DUR,
                        SHOCK_OUT_DUR,
                        true,           // additive
                        -1f,            // textureLoopLength (-1 = no repeat, matches CSV)
                        0f,             // textureScrollSpeed
                        (float) Math.random(), // randomTextureOffset
                        null,           // offsetVelocity
                        null,           // advancedOptions
                        null,           // layerToRenderOn (MagicTrail default)
                        1f              // frameOffsetMult
                );

                // Pass B - shock contrail (halberd_2 equivalent: same params, separate ID)
                MagicTrailPlugin.addTrailMemberAdvanced(
                        null,
                        (float) (slot + 1),
                        shockSprite,
                        pos,
                        SHOCK_SPEED_START,
                        SHOCK_SPEED_END,
                        angle,
                        SHOCK_ROT_START,
                        SHOCK_ROT_END,
                        SHOCK_SIZE_IN,
                        SHOCK_SIZE_OUT,
                        SHOCK_COLOR_IN,
                        SHOCK_COLOR_OUT,
                        SHOCK_OPACITY,
                        SHOCK_IN_DUR,
                        SHOCK_MAIN_DUR,
                        SHOCK_OUT_DUR,
                        true,
                        -1f,
                        0f,
                        (float) Math.random(),
                        null,
                        null,
                        null,
                        1f
                );
            }

            // Pass C - wide blur glow (halberd_glow equivalent: stationary, no rotation)
            if (glowSprite != null) {
                MagicTrailPlugin.addTrailMemberAdvanced(
                        null,
                        (float) (slot + 2),
                        glowSprite,
                        pos,
                        0f,             // startSpeed: glow sits at spawn point
                        0f,             // endSpeed
                        angle,
                        0f,             // startAngularVelocity
                        0f,             // endAngularVelocity
                        GLOW_SIZE_IN,
                        GLOW_SIZE_OUT,
                        GLOW_COLOR_IN,
                        GLOW_COLOR_OUT,
                        GLOW_OPACITY,
                        0f,             // inDuration
                        GLOW_MAIN_DUR,
                        GLOW_OUT_DUR,
                        false,          // not additive (matches halberd_glow)
                        -1f,
                        0f,
                        (float) Math.random(),
                        null,
                        null,
                        null,
                        1f
                );
            }
        }
    }

    private static void renderAfterimage(ShipAPI ship) {
        SpriteAPI sprite = ship.getSpriteAPI();
        float offsetX = sprite.getWidth() / 2 - sprite.getCenterX();
        float offsetY = sprite.getHeight() / 2 - sprite.getCenterY();
        float angle = ship.getFacing() - 90f;
        float cos = (float) FastTrig.cos(Math.toRadians(angle));
        float sin = (float) FastTrig.sin(Math.toRadians(angle));
        float trueOffsetX = cos * offsetX - sin * offsetY;
        float trueOffsetY = sin * offsetX + cos * offsetY;
        MagicRender.battlespace(
                Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                new Vector2f(ship.getLocation().x + trueOffsetX, ship.getLocation().y + trueOffsetY),
                new Vector2f(0, 0),
                new Vector2f(sprite.getWidth(), sprite.getHeight()),
                new Vector2f(0, 0),
                angle,
                0f,
                AFTERIMAGE_COLOR,
                true,
                0f, 0f, 0f, 0f, 0f,
                0.1f, 0.1f,
                0.75f,
                CombatEngineLayers.BELOW_SHIPS_LAYER
        );
    }

    private static void loadSprites() {
        if (spritesLoaded) return;
        spritesLoaded = true;
        try { shockSprite = Global.getSettings().getSprite("fx", "XLII_shock_contrail"); }
        catch (Exception ignored) {}
        try { glowSprite  = Global.getSettings().getSprite("fx", "XLII_blur_contrail");  }
        catch (Exception ignored) {}
    }

    private void applyModifiers(MutableShipStatsAPI stats, float timeMult, String shipId) {
        if (timeMult >= CLEANUP_THRESHOLD) {
            stats.getTimeMult().modifyMult(MOD_ID, timeMult);
            float rateDebuff = 1f / timeMult;
            stats.getBallisticRoFMult().modifyMult(MOD_ID, rateDebuff);
            stats.getMissileRoFMult().modifyMult(MOD_ID, rateDebuff);
            stats.getEnergyRoFMult().modifyMult(MOD_ID, rateDebuff);
            float speedDebuff = 2f / (timeMult + 1f); // half-inverse: 1.0 at 1x → 0.4 at 4x
            stats.getMaxSpeed().modifyMult(MOD_ID, speedDebuff);
        } else {
            stats.getTimeMult().unmodify(MOD_ID);
            stats.getBallisticRoFMult().unmodify(MOD_ID);
            stats.getMissileRoFMult().unmodify(MOD_ID);
            stats.getEnergyRoFMult().unmodify(MOD_ID);
            stats.getMaxSpeed().unmodify(MOD_ID);
            currentTimeMult.remove(shipId);
            lerpIntervals.remove(shipId);
            afterimageIntervals.remove(shipId);
            activeTimers.remove(shipId);
            // cooldownTimers intentionally not cleared - cooldown must survive deactivation
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && ship.getHullSize() == HullSize.FIGHTER;
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public int getDisplaySortOrder() { return 0; }

    @Override
    public int getDisplayCategoryIndex() { return 0; }
}