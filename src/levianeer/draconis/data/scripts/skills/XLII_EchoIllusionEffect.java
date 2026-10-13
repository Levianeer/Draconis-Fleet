package levianeer.draconis.data.scripts.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import java.util.List;

/**
 * Officer-skill effect giving the Fafnir Mirror guard fleets (see
 * levianeer.draconis.data.campaign.events.XLII_MirrorAssault) their translucent, color-cycling
 * "echo" look - real, killable combatants wearing Longsight's illusion, not real hulls.
 * <p>
 * Ported from a dynamically-applied hullmod ({@code XLII_EchoIllusion}) to a skill after repeated
 * live testing confirmed the hullmod's applyEffectsAfterShipCreation/advanceInCombat never fired
 * for any ship in a real fight, despite the mod being confirmed present via hasHullMod() == true
 * at the campaign layer - an unresolved engine-level mystery with no further leads. This mechanism
 * is the same AfterShipCreationSkillEffect idiom XLII_MarginalAllocation's MalfunctionAura/
 * GhostEcho already use for per-ship combat plugins, which is real, shipped, confirmed-working
 * functionality in this codebase - unlike the abandoned hullmod, which had no such precedent.
 * XLII_MirrorAssault assigns this skill directly to every guard ship's captain (creating one if the
 * ship has none) rather than relying on hull mod propagation.
 */
public class XLII_EchoIllusionEffect implements ShipSkillEffect, AfterShipCreationSkillEffect {

    public static final String SKILL_ID = "XLII_echo_illusion_skill";

    private static final float ALPHA = 0.3f;

    @Override
    public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {}

    @Override
    public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {}

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        engine.addPlugin(new EchoVisualPlugin(ship));
    }

    @Override
    public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {}

    @Override
    public String getEffectDescription(float level) {
        return "This ship is an illusion projected by Longsight";
    }

    @Override
    public String getEffectPerLevelDescription() { return null; }

    @Override
    public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }

    /** Per-ship visual-only plugin: constant translucency plus a fading color-cycled afterimage. */
    public static class EchoVisualPlugin extends BaseEveryFrameCombatPlugin {

        private final ShipAPI ship;
        private final IntervalUtil afterimageInterval = new IntervalUtil(0.25f, 0.25f);

        public EchoVisualPlugin(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            if (ship == null || !ship.isAlive() || ship.isHulk()) {
                engine.removePlugin(this);
                return;
            }
            if (engine.isPaused()) return;

            ship.setExtraAlphaMult(ALPHA);

            afterimageInterval.advance(amount);
            if (afterimageInterval.intervalElapsed()) {
                renderAfterimage(ship);
            }
        }

        private static void renderAfterimage(ShipAPI ship) {
            SpriteAPI sprite = ship.getSpriteAPI();
            float offsetX = sprite.getWidth() / 2f - sprite.getCenterX();
            float offsetY = sprite.getHeight() / 2f - sprite.getCenterY();
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
                    getGhostColor(),
                    true,
                    0f, 0f, 0f, 0f, 0f,
                    0.1f, 0.1f,
                    0.75f,
                    CombatEngineLayers.BELOW_SHIPS_LAYER
            );
        }

        private static Color getGhostColor() {
            Color[] colors = new Color[]{
                    new Color(0, 255, 255, 100),
                    new Color(0, 255, 125, 100),
                    new Color(0, 0, 255, 100),
                    new Color(255, 0, 255, 100),
                    new Color(255, 0, 0, 100),
            };
            float period = 0.05f;
            float totalTime = Global.getCombatEngine().getTotalElapsedTime(true);
            float fraction = (totalTime % period) / period;
            float cycleTime = fraction * colors.length;
            int i1 = (int) cycleTime;
            int i2 = (i1 + 1) % colors.length;
            float t = cycleTime - i1;
            Color c1 = colors[i1], c2 = colors[i2];
            return new Color(
                    (int) (c1.getRed() + t * (c2.getRed() - c1.getRed())),
                    (int) (c1.getGreen() + t * (c2.getGreen() - c1.getGreen())),
                    (int) (c1.getBlue() + t * (c2.getBlue() - c1.getBlue())),
                    (int) (c1.getAlpha() + t * (c2.getAlpha() - c1.getAlpha()))
            );
        }
    }
}
