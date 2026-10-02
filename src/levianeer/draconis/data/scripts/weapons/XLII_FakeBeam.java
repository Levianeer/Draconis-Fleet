package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicFakeBeamPlugin;
import org.magiclib.plugins.MagicTrailPlugin;
import org.magiclib.util.MagicFakeBeam;

import java.awt.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

/**
 * Local fork of {@link org.magiclib.util.MagicFakeBeam}'s spawnFakeBeam/spawnAdvancedFakeBeam.
 * <p>
 * MagicLib's collision filter compares a module's parent station against itself
 * ({@code ((ShipAPI) e).getParentStation() != e}), which is always true and never
 * actually excludes anything - so a ship's own modules block/clip its own fakebeams.
 * This fork compares against {@code source} instead, so the firing ship's own
 * modules are correctly ignored. Everything else is unchanged from MagicLib.
 */
public class XLII_FakeBeam {

    public static void spawnFakeBeam(CombatEngineAPI engine, Vector2f from, float range, float angle, float width, float full, float fading, float impactSize, Color core, Color fringe, float normalDamage, DamageType type, float emp, ShipAPI source) {

        CombatEntityAPI theTarget = null;
        float damage = normalDamage;

        Vector2f end = MathUtils.getPoint(from, range, angle);

        List<CombatEntityAPI> entity = CombatUtils.getEntitiesWithinRange(from, range + 500);
        if (!entity.isEmpty()) {
            for (CombatEntityAPI e : entity) {

                if (e.getCollisionClass() == CollisionClass.NONE) {
                    continue;
                }

                float newDamage = normalDamage;

                Vector2f col = new Vector2f(1000000, 1000000);
                if (e instanceof ShipAPI) {
                    if (
                            e != source
                                    &&
                                    ((ShipAPI) e).getParentStation() != source
                                    &&
                                    e.getCollisionClass() != CollisionClass.NONE
                                    &&
                                    !(e.getCollisionClass() == CollisionClass.FIGHTER && e.getOwner() == source.getOwner() && !((ShipAPI) e).getEngineController().isFlamedOut())
                                    &&
                                    CollisionUtils.getCollides(from, end, e.getLocation(), e.getCollisionRadius())
                    ) {
                        ShipAPI s = (ShipAPI) e;

                        Vector2f hitPoint = MagicFakeBeam.getShipCollisionPoint(from, end, s, angle);
                        if (hitPoint != null) {
                            col = hitPoint;
                        }

                        if (s.getHullSpec().getBaseHullId().startsWith("exigency_")) {
                            newDamage = normalDamage / 2;
                        }
                    }
                } else
                    if (
                            (e instanceof CombatAsteroidAPI
                                    ||
                                    (e instanceof MissileAPI)
                                            &&
                                            e.getOwner() != source.getOwner()
                            )
                                    &&
                                    CollisionUtils.getCollides(from, end, e.getLocation(), e.getCollisionRadius())
                    ) {
                        Vector2f cAst = MagicFakeBeam.getCollisionPointOnCircumference(from, end, e.getLocation(), e.getCollisionRadius());
                        if (cAst != null) {
                            col = cAst;
                        }
                    }

                if (
                        col.x != 1000000 &&
                                MathUtils.getDistanceSquared(from, col) < MathUtils.getDistanceSquared(from, end)) {
                    end = col;
                    theTarget = e;
                    damage = newDamage;
                }
            }

            if (theTarget != null) {
                engine.applyDamage(
                        theTarget,
                        end,
                        damage,
                        type,
                        emp,
                        false,
                        true,
                        source
                );
                engine.addHitParticle(
                        end,
                        new Vector2f(),
                        (float) Math.random() * impactSize / 2 + impactSize,
                        1,
                        full + fading,
                        fringe
                );
                engine.addHitParticle(
                        end,
                        new Vector2f(),
                        (float) Math.random() * impactSize / 4 + impactSize / 2,
                        1,
                        full,
                        core
                );
            }

            MagicFakeBeamPlugin.addBeam(full, fading, width, from, angle, MathUtils.getDistance(from, end) + 10, core, fringe);
        }
    }

    @SuppressWarnings("deprecation")
    public static void spawnAdvancedFakeBeam(CombatEngineAPI engine, Vector2f from, float range, float angle, float widthIn, float widthOut, float growth, String textureCore, String textureFringe, float textureLength, float textureScroll, float smoothIn, float smoothOut, float full, float fading, float impactSize, Color core, Color fringe, float normalDamage, DamageType type, float emp, ShipAPI source) {

        CombatEntityAPI theTarget = null;
        float damage = normalDamage;

        Vector2f end = MathUtils.getPoint(from, range, angle);

        List<CombatEntityAPI> entity = CombatUtils.getEntitiesWithinRange(from, range + 500);
        if (!entity.isEmpty()) {
            for (CombatEntityAPI e : entity) {

                if (e.getCollisionClass() == CollisionClass.NONE) {
                    continue;
                }

                float newDamage = normalDamage;

                Vector2f col = new Vector2f(1000000, 1000000);
                if (e instanceof ShipAPI) {
                    if (
                            e != source
                                    &&
                                    ((ShipAPI) e).getParentStation() != source
                                    &&
                                    e.getCollisionClass() != CollisionClass.NONE
                                    &&
                                    !(e.getCollisionClass() == CollisionClass.FIGHTER && e.getOwner() == source.getOwner() && !((ShipAPI) e).getEngineController().isFlamedOut())
                                    &&
                                    CollisionUtils.getCollides(from, end, e.getLocation(), e.getCollisionRadius())
                    ) {
                        ShipAPI s = (ShipAPI) e;

                        Vector2f hitPoint = MagicFakeBeam.getShipCollisionPoint(from, end, s, angle);
                        if (hitPoint != null) {
                            col = hitPoint;
                        }

                        if (s.getHullSpec().getBaseHullId().startsWith("exigency_")) {
                            newDamage = normalDamage / 2;
                        }
                    }
                } else
                    if (
                            (e instanceof CombatAsteroidAPI
                                    ||
                                    (e instanceof MissileAPI)
                                            &&
                                            e.getOwner() != source.getOwner()
                            )
                                    &&
                                    CollisionUtils.getCollides(from, end, e.getLocation(), e.getCollisionRadius())
                    ) {
                        Vector2f cAst = MagicFakeBeam.getCollisionPointOnCircumference(from, end, e.getLocation(), e.getCollisionRadius());
                        if (cAst != null) {
                            col = cAst;
                        }
                    }

                if (
                        col.x != 1000000 &&
                                MathUtils.getDistanceSquared(from, col) < MathUtils.getDistanceSquared(from, end)) {
                    end = col;
                    theTarget = e;
                    damage = newDamage;
                }
            }

            if (theTarget != null) {
                engine.applyDamage(
                        theTarget,
                        end,
                        damage,
                        type,
                        emp,
                        false,
                        true,
                        source
                );
                engine.addHitParticle(
                        end,
                        new Vector2f(),
                        (float) Math.random() * impactSize / 2 + impactSize,
                        1,
                        full + fading,
                        fringe
                );
                engine.addHitParticle(
                        end,
                        new Vector2f(),
                        (float) Math.random() * impactSize / 4 + impactSize / 2,
                        1,
                        full,
                        core
                );
            }

            if (MathUtils.isWithinRange(from, end, smoothIn + smoothOut)) {
                end = MathUtils.getPoint(from, smoothIn + smoothOut + 2, angle);
            }

            float ID = MagicTrailPlugin.getUniqueID();
            SpriteAPI texture = Global.getSettings().getSprite("fx", textureCore);

            MagicTrailPlugin.addTrailMemberAdvanced(
                    null,
                    ID,
                    texture,
                    from,
                    0,
                    0,
                    angle,
                    0,
                    0,
                    widthIn / 3,
                    widthIn / 3 + growth,
                    core,
                    fringe,
                    1,
                    0,
                    full,
                    fading,
                    GL_SRC_ALPHA,
                    GL_ONE,
                    textureLength,
                    textureScroll,
                    new Vector2f(),
                    null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER,
                    1
            );

            MagicTrailPlugin.addTrailMemberAdvanced(null, ID, texture, MathUtils.getPoint(from, smoothIn, angle), 0, 0, angle, 0, 0, widthIn / 2, widthIn * 0.75f + growth, core, fringe, 1, 0, full, fading, GL_SRC_ALPHA, GL_ONE, textureLength, textureScroll, new Vector2f(), null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER, 1);
            MagicTrailPlugin.addTrailMemberAdvanced(null, ID, texture, MathUtils.getPoint(end, smoothOut, angle + 180), 0, 0, angle, 0, 0, widthOut / 2, widthOut * 0.75f + growth, core, fringe, 1, 0, full, fading, GL_SRC_ALPHA, GL_ONE, textureLength, textureScroll, new Vector2f(), null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER, 1);
            MagicTrailPlugin.addTrailMemberAdvanced(null, ID, texture, end, 0, 0, angle, 0, 0, widthOut / 3, widthOut / 3 + growth, core, fringe, 1, 0, full, fading, GL_SRC_ALPHA, GL_ONE, textureLength, textureScroll, new Vector2f(), null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER, 1);

            ID = MagicTrailPlugin.getUniqueID();
            texture = Global.getSettings().getSprite("fx", textureFringe);

            MagicTrailPlugin.addTrailMemberAdvanced(null, ID, texture, from, 0, 0, angle, 0, 0, widthIn / 2, widthIn / 2 + growth, fringe, fringe, 1, 0, full, fading, GL_SRC_ALPHA, GL_ONE, textureLength, textureScroll, new Vector2f(), null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER, 1);
            MagicTrailPlugin.addTrailMemberAdvanced(null, ID, texture, MathUtils.getPoint(from, smoothIn, angle), 0, 0, angle, 0, 0, widthIn, widthIn + growth, fringe, fringe, 1, 0, full, fading, GL_SRC_ALPHA, GL_ONE, textureLength, textureScroll, new Vector2f(), null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER, 1);
            MagicTrailPlugin.addTrailMemberAdvanced(null, ID, texture, MathUtils.getPoint(end, smoothOut, angle + 180), 0, 0, angle, 0, 0, widthOut, widthOut + growth, fringe, fringe, 1, 0, full, fading, GL_SRC_ALPHA, GL_ONE, textureLength, textureScroll, new Vector2f(), null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER, 1);
            MagicTrailPlugin.addTrailMemberAdvanced(null, ID, texture, end, 0, 0, angle, 0, 0, widthOut / 2, widthOut / 2 + growth, fringe, fringe, 1, 0, full, fading, GL_SRC_ALPHA, GL_ONE, textureLength, textureScroll, new Vector2f(), null,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER, 1);
        }
    }
}
