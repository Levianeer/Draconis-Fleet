package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * {@link CarrierDoctrineAI} debug visualization - a ring per ship for its doctrine role, a
 * marker above each carrier for its current {@link CarrierDuty}, a line from STRIKE-duty
 * carriers to the wave's current target, a line from CAP-duty carriers to whatever their ship
 * is actually currently targeting (green if friendly - escorting; orange if enemy -
 * harassing/intercepting), and a red line from any STRIKE-duty carrier whose own live target
 * doesn't match the rest of the group's. All of these read ground truth (the AI's own
 * {@link CarrierDoctrineAI#activeStrikeStrikers}/{@link CarrierDoctrineAI#activeStrikeTarget}
 * for the shared STRIKE line, {@code ship.getShipTarget()} directly for CAP and the red
 * mismatch line) rather than inferring intent, so a carrier whose actual behavior has drifted
 * from what the doctrine intended - the exact class of bug this overlay was built to catch -
 * shows up as a line going somewhere unexpected instead of being masked by what the AI merely
 * *meant* to do. Gated by the same {@link CarrierDoctrineAI#debugMessagesEnabled} flag as the
 * existing text debug messages - see {@link CarrierDoctrinePlugin} for where this is attached.
 * <p>
 * M11: also draws a ring around each enemy battle group ({@link CarrierDoctrineAI#enemyBattleGroups})
 * sized to encompass every member - crimson for the group {@link CarrierDoctrineAI#pickStrikeTarget}
 * treats as the enemy's main body ({@link CarrierDoctrineAI#primaryEnemyGroupShips}), dim gray for
 * every other group - so a segmentation bug (a main body that's clearly one army icon in the
 * engagement camera but doesn't ring as a single group here, or a ring that includes ships that
 * are obviously not part of that fight) is visible at a glance rather than only inferable from
 * targeting behavior.
 */
public class CarrierDoctrineDebugOverlay extends BaseCombatLayeredRenderingPlugin {

    protected static final float RING_MARGIN = 15f;
    protected static final int RING_SEGMENTS = 20;
    protected static final float RING_ALPHA = 0.7f;

    protected static final float JOB_MARKER_OFFSET = 40f;
    protected static final float JOB_MARKER_SIZE = 12f;

    protected static final float TARGET_LINE_ALPHA = 0.5f;
    protected static final float TARGET_MARKER_SIZE = 16f;
    protected static final float CAP_TARGET_MARKER_SIZE = 12f;

    protected static final Color ROLE_CARRIER_COLOR = Color.CYAN;
    protected static final Color ROLE_SCREEN_COLOR = Color.YELLOW;
    protected static final Color ROLE_BATTLELINE_COLOR = new Color(255, 90, 0);
    protected static final Color ROLE_PICKET_COLOR = Color.MAGENTA;
    protected static final Color JOB_MARKER_COLOR = Color.WHITE;
    protected static final Color TARGET_LINE_COLOR = Color.WHITE;
    protected static final Color CAP_ESCORT_LINE_COLOR = new Color(60, 255, 120);
    protected static final Color CAP_HARASS_LINE_COLOR = new Color(255, 160, 0);
    protected static final Color MISMATCH_LINE_COLOR = Color.RED;

    // M11: enemy battle-group rings.
    protected static final float GROUP_RING_MARGIN = 60f;
    protected static final float GROUP_RING_ALPHA = 0.35f;
    protected static final Color PRIMARY_GROUP_COLOR = new Color(220, 20, 60);
    protected static final Color SECONDARY_GROUP_COLOR = new Color(110, 110, 110);

    protected final CarrierDoctrineAI ai;

    public CarrierDoctrineDebugOverlay(CarrierDoctrineAI ai) {
        super(CombatEngineLayers.JUST_BELOW_WIDGETS);
        this.ai = ai;
    }

    @Override
    public float getRenderRadius() {
        return 1000000f;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!ai.debugMessagesEnabled) return;

        float alphaMult = viewport.getAlphaMult();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawRoleRings(ai.lastCarriers, ROLE_CARRIER_COLOR, alphaMult);
        drawRoleRings(ai.lastScreen, ROLE_SCREEN_COLOR, alphaMult);
        drawRoleRings(ai.lastBattleline, ROLE_BATTLELINE_COLOR, alphaMult);
        drawRoleRings(ai.lastPickets, ROLE_PICKET_COLOR, alphaMult);

        drawEnemyBattleGroups(alphaMult);
        drawJobMarkers(alphaMult);
        drawTargetLines(alphaMult);
        drawCapLines(alphaMult);
        drawMismatchedStrikeLines(alphaMult);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    protected void drawRoleRings(List<ShipAPI> ships, Color color, float alphaMult) {
        for (ShipAPI ship : ships) {
            if (ship == null || !ship.isAlive()) continue;
            drawCircle(ship.getLocation(), ship.getCollisionRadius() + RING_MARGIN, color, RING_ALPHA * alphaMult);
        }
    }

    protected void drawJobMarkers(float alphaMult) {
        for (Map.Entry<ShipAPI, CarrierDuty> entry : ai.carrierDuty.entrySet()) {
            ShipAPI ship = entry.getKey();
            if (!ship.isAlive()) continue;

            Vector2f loc = ship.getLocation();
            Vector2f markerLoc = new Vector2f(loc.x, loc.y + ship.getCollisionRadius() + JOB_MARKER_OFFSET);

            if (entry.getValue() == CarrierDuty.STRIKE) {
                drawTriangle(markerLoc, JOB_MARKER_SIZE, JOB_MARKER_COLOR, alphaMult);
            } else {
                drawSquare(markerLoc, JOB_MARKER_SIZE, JOB_MARKER_COLOR, alphaMult);
            }
        }
    }

    /** Ground truth, not intent: reads {@link CarrierDoctrineAI#activeStrikeStrikers}/
     * {@link CarrierDoctrineAI#activeStrikeTarget} - the same cache {@code reapplyActiveStrikeOrders}
     * enforces every frame - rather than inferring from {@code carrierDuty}/{@code strikeTarget}/
     * {@code rotationTarget}, so this line shows who the doctrine is *actually* currently
     * ordering to strike, not just who's nominally on STRIKE duty. */
    protected void drawTargetLines(float alphaMult) {
        ShipAPI target = ai.activeStrikeTarget;
        if (target == null || !target.isAlive()) return;

        for (ShipAPI carrier : ai.activeStrikeStrikers) {
            if (!carrier.isAlive()) continue;
            drawLine(carrier.getLocation(), target.getLocation(), TARGET_LINE_COLOR, TARGET_LINE_ALPHA * alphaMult);
        }
        if (!ai.activeStrikeStrikers.isEmpty()) {
            drawDiamond(target.getLocation(), TARGET_MARKER_SIZE, TARGET_LINE_COLOR, alphaMult);
        }
    }

    /** CAP-duty carriers have no single shared target the way STRIKE carriers do - each one's
     * wings follow whatever {@code ship.getShipTarget()} currently is for that specific carrier
     * (set by {@link CarrierDoctrineAI#updateCapEscort} to the most-threatened friendly carrier).
     * Reads that live value directly rather than re-deriving "who should it be escorting", so a
     * carrier that's drifted onto something else - an enemy, or a stale target - shows up as a
     * mismatched line instead of being masked. Green = escorting a friendly ship (expected);
     * orange = currently targeting an enemy (worth checking: opportunistic intercept, or a leftover
     * STRIKE target that never got cleared when this carrier was pulled onto CAP duty). */
    protected void drawCapLines(float alphaMult) {
        for (Map.Entry<ShipAPI, CarrierDuty> entry : ai.carrierDuty.entrySet()) {
            if (entry.getValue() != CarrierDuty.CAP) continue;
            ShipAPI ship = entry.getKey();
            if (!ship.isAlive()) continue;

            ShipAPI capTarget = ship.getShipTarget();
            if (capTarget == null || !capTarget.isAlive()) continue;

            boolean friendly = capTarget.getOwner() == ship.getOwner();
            Color color = friendly ? CAP_ESCORT_LINE_COLOR : CAP_HARASS_LINE_COLOR;
            drawLine(ship.getLocation(), capTarget.getLocation(), color, TARGET_LINE_ALPHA * alphaMult);
            drawDiamond(capTarget.getLocation(), CAP_TARGET_MARKER_SIZE, color, alphaMult);
        }
    }

    /** Catches exactly the class of bug this whole overlay exists for: a STRIKE-duty carrier
     * whose own live {@code ship.getShipTarget()} doesn't match {@link CarrierDoctrineAI#activeStrikeTarget}
     * (the group's intended, shared target) draws a red line to wherever it's *actually* aimed
     * instead - whether that's a different enemy, a stale target left over from a previous
     * wave/duty, or simply nothing at all. A carrier correctly on the shared target already gets
     * its line from {@link #drawTargetLines} and is skipped here, so this is purely "this one
     * doesn't agree with the rest of the group" at a glance. */
    protected void drawMismatchedStrikeLines(float alphaMult) {
        ShipAPI groupTarget = ai.activeStrikeTarget;
        for (Map.Entry<ShipAPI, CarrierDuty> entry : ai.carrierDuty.entrySet()) {
            if (entry.getValue() != CarrierDuty.STRIKE) continue;
            ShipAPI ship = entry.getKey();
            if (!ship.isAlive()) continue;

            ShipAPI actualTarget = ship.getShipTarget();
            if (actualTarget == null || !actualTarget.isAlive() || actualTarget == groupTarget) continue;

            drawLine(ship.getLocation(), actualTarget.getLocation(), MISMATCH_LINE_COLOR, TARGET_LINE_ALPHA * alphaMult);
            drawDiamond(actualTarget.getLocation(), TARGET_MARKER_SIZE, MISMATCH_LINE_COLOR, alphaMult);
        }
    }

    /** M11: one ring per {@link CarrierDoctrineAI#enemyBattleGroups} entry, centered on the
     * group's own centroid and sized to encompass every live member - crimson for whichever
     * group is this tick's {@link CarrierDoctrineAI#primaryEnemyGroupShips} (the main body
     * {@link CarrierDoctrineAI#pickStrikeTarget} is biased toward), dim gray otherwise. */
    protected void drawEnemyBattleGroups(float alphaMult) {
        for (List<DeployedFleetMemberAPI> group : ai.enemyBattleGroups) {
            Vector2f centroid = computeGroupCentroid(group);
            if (centroid == null) continue;

            float radius = computeGroupRadius(group, centroid);
            Color color = isPrimaryGroup(group) ? PRIMARY_GROUP_COLOR : SECONDARY_GROUP_COLOR;
            drawCircle(centroid, radius + GROUP_RING_MARGIN, color, GROUP_RING_ALPHA * alphaMult);
        }
    }

    protected Vector2f computeGroupCentroid(List<DeployedFleetMemberAPI> group) {
        float sumX = 0f, sumY = 0f;
        int count = 0;
        for (DeployedFleetMemberAPI member : group) {
            ShipAPI ship = member.getShip();
            if (ship == null || !ship.isAlive()) continue;
            sumX += ship.getLocation().x;
            sumY += ship.getLocation().y;
            count++;
        }
        if (count == 0) return null;
        return new Vector2f(sumX / count, sumY / count);
    }

    /** Furthest live member's own edge (distance from {@code centroid} plus that ship's
     * collision radius) from the centroid - the smallest ring that still encompasses the whole
     * group rather than just its center points. */
    protected float computeGroupRadius(List<DeployedFleetMemberAPI> group, Vector2f centroid) {
        float max = 0f;
        for (DeployedFleetMemberAPI member : group) {
            ShipAPI ship = member.getShip();
            if (ship == null || !ship.isAlive()) continue;
            float dist = Misc.getDistance(ship.getLocation(), centroid) + ship.getCollisionRadius();
            if (dist > max) max = dist;
        }
        return max;
    }

    protected boolean isPrimaryGroup(List<DeployedFleetMemberAPI> group) {
        for (DeployedFleetMemberAPI member : group) {
            ShipAPI ship = member.getShip();
            if (ship != null && ai.primaryEnemyGroupShips.contains(ship)) return true;
        }
        return false;
    }

    protected void drawCircle(Vector2f center, float radius, Color color, float alpha) {
        setColor(color, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double theta = 2 * Math.PI * i / RING_SEGMENTS;
            GL11.glVertex2f(center.x + radius * (float) Math.cos(theta), center.y + radius * (float) Math.sin(theta));
        }
        GL11.glEnd();
    }

    protected void drawLine(Vector2f from, Vector2f to, Color color, float alpha) {
        setColor(color, alpha);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(from.x, from.y);
        GL11.glVertex2f(to.x, to.y);
        GL11.glEnd();
    }

    protected void drawTriangle(Vector2f center, float size, Color color, float alpha) {
        setColor(color, alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(center.x, center.y + size);
        GL11.glVertex2f(center.x - size, center.y - size);
        GL11.glVertex2f(center.x + size, center.y - size);
        GL11.glEnd();
    }

    protected void drawSquare(Vector2f center, float size, Color color, float alpha) {
        setColor(color, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(center.x - size, center.y - size);
        GL11.glVertex2f(center.x - size, center.y + size);
        GL11.glVertex2f(center.x + size, center.y + size);
        GL11.glVertex2f(center.x + size, center.y - size);
        GL11.glEnd();
    }

    protected void drawDiamond(Vector2f center, float size, Color color, float alpha) {
        setColor(color, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(center.x, center.y + size);
        GL11.glVertex2f(center.x + size, center.y);
        GL11.glVertex2f(center.x, center.y - size);
        GL11.glVertex2f(center.x - size, center.y);
        GL11.glEnd();
    }

    protected void setColor(Color color, float alphaMult) {
        GL11.glColor4ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(),
                (byte) (255 * alphaMult));
    }
}
