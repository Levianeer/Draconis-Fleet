package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.fs.starfarer.api.impl.combat.EntropyAmplifierStats.KEY_TARGET;

public class XLII_ESW_SuiteStats extends BaseShipSystemScript {

    private static final float MAX_RANGE = 2500f;
    private static final float MAX_REDUCTION = 25f;
    private static final float MIN_REDUCTION = 5f;
    private static final Color TEXT_COLOR = new Color(200, 200, 200, 255);
    private static final float EMP_RADIUS_SCALE = 0.3f;
    private static final float EMP_COOLDOWN = 0.75f; // Minimum delay between EMP arcs per target

    private static final float ROTATION_SPEED = -50f; // degrees per second
    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f; // ring art is smaller than PNG bounds
    private static final Color RING_COLOR = new Color(155, 255, 0, 10);
    private static final float MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
    private static final Color EMP_ARC_FRINGE = new Color(100, 180, 255, 180);
    private static final Color EMP_ARC_CORE = new Color(200, 200, 255, 220);

    private CombatFleetManagerAPI.AssignmentInfo defendAssignment = null;
    private boolean assignmentGiven = false;
    private SpriteAPI ringSprite = null;

    private static final Set<ShipAPI> globallyNotifiedTargets =
            Collections.newSetFromMap(new WeakHashMap<>());

    private final Map<ShipAPI, Float> affectedShips = new HashMap<>();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || ship.getLocation() == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        float currentTime = engine.getTotalElapsedTime(false);

        // Ring FX: rendered in screen space so it's pixel-perfect at any zoom level.
        // effectLevel fades the ring in on IN and out on OUT.
        if (effectLevel > 0f) {
            ViewportAPI view = engine.getViewport();
            Vector2f loc = ship.getLocation();
            if (view.isNearViewport(loc, MAX_RANGE)) {
                float screenScale = Global.getSettings().getScreenScaleMult();
                float radius = (MAX_RANGE + ship.getCollisionRadius()) * 2f * SPRITE_ALIGNMENT_SCALE * screenScale * effectLevel / view.getViewMult();
                float angle = currentTime * ROTATION_SPEED;
                if (ringSprite == null) ringSprite = Global.getSettings().getSprite("fx", "XLII_radar_ring");
                ringSprite.setSize(radius, radius);
                ringSprite.setColor(RING_COLOR);
                ringSprite.setAdditiveBlend();
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
                GL11.glOrtho(0, Display.getWidth(), 0, Display.getHeight(), -1, 1);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                ringSprite.setAngle(angle);
                ringSprite.renderAtCenter(
                        view.convertWorldXtoScreenX(loc.x) * screenScale,
                        view.convertWorldYtoScreenY(loc.y) * screenScale
                );
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
        List<ShipAPI> enemies = engine.getShips();

        for (ShipAPI target : enemies) {
            if (target.getOwner() == ship.getOwner() || target.isHulk() || target.isFighter() || target.isPhased()) {
                continue;
            }

            float dx = target.getLocation().x - ship.getLocation().x;
            float dy = target.getLocation().y - ship.getLocation().y;
            float distSq = dx * dx + dy * dy;
            if (distSq > MAX_RANGE_SQ) continue;
            float distance = (float) Math.sqrt(distSq);

            // Get the reduction percentage based on distance
            float reductionFactor = getReductionFactor(distance);
            float reductionPercentage = (reductionFactor / 100f) * effectLevel;

            // Modify the range as you were already doing
            float finalMultiplier = 1f - reductionPercentage;
            target.getMutableStats().getBallisticWeaponRangeBonus().modifyMult(id, finalMultiplier);
            target.getMutableStats().getEnergyWeaponRangeBonus().modifyMult(id, finalMultiplier);

            if (target == Global.getCombatEngine().getPlayerShip()) {
                // Pass the reduction percentage as a string to display it
                String reductionText = String.format("%.0f%% range reduced", reductionPercentage * 100f);

                // Show status on player ship
                Global.getCombatEngine().maintainStatusForPlayerShip(
                        KEY_TARGET,
                        ship.getSystem().getSpecAPI().getIconSpriteName(),
                        ship.getSystem().getDisplayName(),
                        reductionText,
                        true
                );
            }

            if (!affectedShips.containsKey(target)) {
                affectedShips.put(target, currentTime);
                if (target.getParentStation() == null && !globallyNotifiedTargets.contains(target)) {
                    globallyNotifiedTargets.add(target);
                    engine.addFloatingText(target.getLocation(), "Weapon's Jammed!", 24f, TEXT_COLOR, target, 0.5f, 0.5f);
                }
                spawnEmpArcEffect(engine, target);
            } else {
                float lastTriggered = affectedShips.get(target);
                if (currentTime - lastTriggered >= EMP_COOLDOWN) {
                    affectedShips.put(target, currentTime);
                    spawnEmpArcEffect(engine, target);
                }
            }
        }
        if (ship.getOwner() != 0 && !assignmentGiven) {
            CombatFleetManagerAPI fleetManager = engine.getFleetManager(ship.getOwner());
            CombatTaskManagerAPI taskManager = fleetManager.getTaskManager(false);
            DeployedFleetMemberAPI member = fleetManager.getDeployedFleetMember(ship);
            if (member != null) {
                defendAssignment = taskManager.createAssignment(
                        CombatAssignmentType.DEFEND,
                        member,
                        false
                );
                taskManager.giveAssignment(member, defendAssignment, false);
                assignmentGiven = true;
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        for (ShipAPI s : engine.getShips()) {
            if (s.isHulk()) continue;
            s.getMutableStats().getBallisticWeaponRangeBonus().unmodify(id);
            s.getMutableStats().getEnergyWeaponRangeBonus().unmodify(id);
        }

        if (ship.getOwner() != 0 && defendAssignment != null) {
            CombatFleetManagerAPI fleetManager = engine.getFleetManager(ship.getOwner());
            CombatTaskManagerAPI taskManager = fleetManager.getTaskManager(false);
            taskManager.removeAssignment(defendAssignment);
            defendAssignment = null;
        }

        assignmentGiven = false;
        globallyNotifiedTargets.removeAll(affectedShips.keySet());
        affectedShips.clear();
    }

    private float getReductionFactor(float distance) {
        return MIN_REDUCTION + (distance / MAX_RANGE) * (MAX_REDUCTION - MIN_REDUCTION);
    }

    private void spawnEmpArcEffect(CombatEngineAPI engine, ShipAPI target) {
        if (target == null || engine == null) return;

        int numArcs = MathUtils.getRandomNumberInRange(1, 2);
        for (int i = 0; i < numArcs; i++) {
            Vector2f point1 = MathUtils.getRandomPointInCircle(target.getLocation(), target.getCollisionRadius() * EMP_RADIUS_SCALE);
            Vector2f point2 = MathUtils.getRandomPointInCircle(target.getLocation(), target.getCollisionRadius() * EMP_RADIUS_SCALE);

            engine.spawnEmpArcVisual(
                    point1, target,
                    point2, target,
                    20f,
                    EMP_ARC_FRINGE,
                    EMP_ARC_CORE
            );
        }
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Jamming Enemy Weapons!", false);
        }
        return null;
    }
}