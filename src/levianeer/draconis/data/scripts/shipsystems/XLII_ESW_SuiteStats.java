package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.combat.EntropyAmplifierStats.KEY_TARGET;

public class XLII_ESW_SuiteStats extends BaseShipSystemScript {

    private static final float MAX_RANGE = 2500f;
    private static final float MAX_REDUCTION = 25f;
    private static final float MIN_REDUCTION = 5f;
    private static final Color TEXT_COLOR = new Color(200, 200, 200, 255);
    private static final float EMP_RADIUS_SCALE = 0.3f;
    private static final float EMP_COOLDOWN = 0.75f; // Minimum delay between EMP arcs per target

    private static final float ROTATION_SPEED = 5f; // degrees per second
    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f;
    private static final Color RING_COLOR = new Color(90, 165, 255, 55);

    private CombatFleetManagerAPI.AssignmentInfo defendAssignment = null;

    private final Map<ShipAPI, Float> affectedShips = new HashMap<>();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || ship.getLocation() == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        float currentTime = engine.getTotalElapsedTime(false);

        // Ring FX: effectLevel scales 0->1 on IN and 1->0 on OUT for grow/shrink animation
        if (effectLevel > 0f) {
            float angle = currentTime * ROTATION_SPEED;
            float scaledSize = MAX_RANGE * 2f * SPRITE_ALIGNMENT_SCALE * effectLevel;
            SpriteAPI ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring");
            MagicRender.singleframe(
                    ringSprite,
                    ship.getLocation(),
                    new Vector2f(scaledSize, scaledSize),
                    angle,
                    RING_COLOR,
                    true
            );
        }
        List<ShipAPI> enemies = engine.getShips();

        for (ShipAPI target : enemies) {
            if (target.getOwner() == ship.getOwner() || target.isHulk() || target.isFighter() || target.isPhased()) {
                continue;
            }

            float distance = Misc.getDistance(ship.getLocation(), target.getLocation());
            if (distance > MAX_RANGE) continue;

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
                engine.addFloatingText(target.getLocation(), "Weapon's Jammed!", 24f, TEXT_COLOR, target, 0.5f, 0.5f);
                spawnEmpArcEffect(engine, target);
            } else {
                float lastTriggered = affectedShips.get(target);
                if (currentTime - lastTriggered >= EMP_COOLDOWN) {
                    affectedShips.put(target, currentTime);
                    spawnEmpArcEffect(engine, target);
                }
            }
        }
        if (ship.getOwner() != 0) {
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
                    new Color(100, 180, 255, 180),
                    new Color(200, 200, 255, 220)
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