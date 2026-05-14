package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import java.util.List;

/**
 * Ship system version of the Draconis wing time-dilation effect.
 * Duration and cooldown are defined in ship_systems.csv; effectLevel handles smooth ramp-up/down.
 */
public class XLII_WingTimeDilationStats extends BaseShipSystemScript {

    public static final float MAX_TIME_MULT = 3f;

    // --- Engine trail: shock contrail ---
    private static final float  SHOCK_SIZE_IN     = 7f;
    private static final float  SHOCK_SIZE_OUT    = 3f;
    private static final float  SHOCK_OPACITY     = 0.4f;
    private static final float  SHOCK_IN_DUR      = 0.1f;
    private static final float  SHOCK_MAIN_DUR    = 0f;
    private static final float  SHOCK_OUT_DUR     = 1.5f;
    private static final float  SHOCK_SPEED_START = 15f;
    private static final float  SHOCK_SPEED_END   = 80f;
    private static final float  SHOCK_ROT_START   = 0f;
    private static final float  SHOCK_ROT_END     = 200f;
    private static final Color  SHOCK_COLOR_IN    = new Color(255, 100, 20, 255);
    private static final Color  SHOCK_COLOR_OUT   = new Color(160, 30, 10, 255);

    // --- Engine trail: blur glow ---
    private static final float  GLOW_SIZE_IN      = 60f;
    private static final float  GLOW_SIZE_OUT     = 30f;
    private static final float  GLOW_OPACITY      = 0.5f;
    private static final float  GLOW_MAIN_DUR     = 0.05f;
    private static final float  GLOW_OUT_DUR      = 0.5f;
    private static final Color  GLOW_COLOR_IN     = new Color(255, 80, 15, 255);
    private static final Color  GLOW_COLOR_OUT    = new Color(100, 20, 5, 255);

    private static final Color  AFTERIMAGE_COLOR  = new Color(100, 180, 255, 110);

    // Instance variable: one script instance per ship, so no HashMap needed.
    private final IntervalUtil afterimageInterval = new IntervalUtil(0.4f, 0.4f);

    // Sprites are global resources; static is fine.
    private static SpriteAPI shockSprite;
    private static SpriteAPI glowSprite;
    private static boolean   spritesLoaded = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI ship)) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        // Ship-specific key so multiple ships using this system don't clobber each other
        // on the global engine.getTimeMult() modifier (same pattern as PhaseCloakStats).
        id += "_" + ship.getId();
        boolean isPlayer = ship == engine.getPlayerShip();

        float timeMult = 1f + (MAX_TIME_MULT - 1f) * effectLevel;

        // Time dilation
        stats.getTimeMult().modifyMult(id, timeMult);
        if (isPlayer) {
            engine.getTimeMult().modifyMult(id, 1f / timeMult);
        } else {
            engine.getTimeMult().unmodify(id);
        }

        // RoF debuff keeps real-time fire rate neutral
        float rateDebuff = 1f / timeMult;
        stats.getBallisticRoFMult().modifyMult(id, rateDebuff);
        stats.getMissileRoFMult().modifyMult(id, rateDebuff);
        stats.getEnergyRoFMult().modifyMult(id, rateDebuff);

        // Speed debuff (half-inverse: 1.0 at 1x -> ~0.5 at 3x)
        stats.getMaxSpeed().modifyMult(id, 2f / (timeMult + 1f));

        // Damage reduction scales with effectLevel (0 -> 50% at full)
        float damageMult = 1f - 0.5f * effectLevel;
        stats.getShieldDamageTakenMult().modifyMult(id, damageMult);
        stats.getArmorDamageTakenMult().modifyMult(id, damageMult);
        stats.getHullDamageTakenMult().modifyMult(id, damageMult);
        stats.getEmpDamageTakenMult().modifyMult(id, damageMult);

        // Engine trails and afterimage - only when meaningfully active
        if (effectLevel > 0.01f) {
            advanceTrails(ship);
            // Advance on real elapsed time so spawn rate is dilation-independent
            afterimageInterval.advance(engine.getElapsedInLastFrame());
            if (afterimageInterval.intervalElapsed()) renderAfterimage(ship);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (!(stats.getEntity() instanceof ShipAPI ship)) return;
        id += "_" + ship.getId();

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null) engine.getTimeMult().unmodify(id);

        stats.getTimeMult().unmodify(id);
        stats.getBallisticRoFMult().unmodify(id);
        stats.getMissileRoFMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        float timeMult = 1f + (MAX_TIME_MULT - 1f) * effectLevel;
        return switch (index) {
            case 0 -> new StatusData("time dilation x" + String.format("%.1f", timeMult), false);
            case 1 -> new StatusData("damage reduced by " + (int)(50f * effectLevel) + "%", false);
            default -> null;
        };
    }

    /**
     * Feeds two shock-contrail passes and one blur-glow pass per active engine into MagicTrailPlugin
     */
    private void advanceTrails(ShipAPI ship) {
        loadSprites();
        if (shockSprite == null && glowSprite == null) return;

        List<ShipEngineControllerAPI.ShipEngineAPI> engines =
                ship.getEngineController().getShipEngines();

        long baseId = Math.abs((long) ship.getId().hashCode()) % 100_000L;
        float angle = ship.getFacing() + 180f;

        for (int i = 0; i < engines.size(); i++) {
            ShipEngineControllerAPI.ShipEngineAPI eng = engines.get(i);
            if (!eng.isActive()) continue;

            Vector2f pos = new Vector2f(eng.getLocation().x, eng.getLocation().y);
            long slot = baseId * 30L + (long) i * 3;

            if (shockSprite != null) {
                // Pass A
                MagicTrailPlugin.addTrailMemberAdvanced(
                        null, (float) slot, shockSprite, pos,
                        SHOCK_SPEED_START, SHOCK_SPEED_END, angle,
                        SHOCK_ROT_START, SHOCK_ROT_END,
                        SHOCK_SIZE_IN, SHOCK_SIZE_OUT,
                        SHOCK_COLOR_IN, SHOCK_COLOR_OUT, SHOCK_OPACITY,
                        SHOCK_IN_DUR, SHOCK_MAIN_DUR, SHOCK_OUT_DUR,
                        true, -1f, 0f, (float) Math.random(),
                        null, null, null, 1f
                );
                // Pass B
                MagicTrailPlugin.addTrailMemberAdvanced(
                        null, (float)(slot + 1), shockSprite, pos,
                        SHOCK_SPEED_START, SHOCK_SPEED_END, angle,
                        SHOCK_ROT_START, SHOCK_ROT_END,
                        SHOCK_SIZE_IN, SHOCK_SIZE_OUT,
                        SHOCK_COLOR_IN, SHOCK_COLOR_OUT, SHOCK_OPACITY,
                        SHOCK_IN_DUR, SHOCK_MAIN_DUR, SHOCK_OUT_DUR,
                        true, -1f, 0f, (float) Math.random(),
                        null, null, null, 1f
                );
            }

            if (glowSprite != null) {
                MagicTrailPlugin.addTrailMemberAdvanced(
                        null, (float)(slot + 2), glowSprite, pos,
                        0f, 0f, angle, 0f, 0f,
                        GLOW_SIZE_IN, GLOW_SIZE_OUT,
                        GLOW_COLOR_IN, GLOW_COLOR_OUT, GLOW_OPACITY,
                        0f, GLOW_MAIN_DUR, GLOW_OUT_DUR,
                        false, -1f, 0f, (float) Math.random(),
                        null, null, null, 1f
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
        MagicRender.battlespace(
                Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                new Vector2f(ship.getLocation().x + cos * offsetX - sin * offsetY,
                             ship.getLocation().y + sin * offsetX + cos * offsetY),
                new Vector2f(0, 0),
                new Vector2f(sprite.getWidth(), sprite.getHeight()),
                new Vector2f(0, 0),
                angle, 0f,
                AFTERIMAGE_COLOR, true,
                0f, 0f, 0f, 0f, 0f,
                0.1f, 0.1f, 0.75f,
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
}