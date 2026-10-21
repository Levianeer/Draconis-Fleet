package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.awt.Color;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * {@link TaskForceAI} debug visualization. Everything drawn reads the order actually issued or the
 * live value on the ship, never re-derived intent - a hull behaving differently from its orders then
 * shows as a line going somewhere unexpected instead of being masked.
 * <p>
 * Role rings: carrier cyan, screen yellow, battleline orange, pickets violet. The carrier standoff
 * point draws as a large cyan ring, the battleline point as a large orange ring, and the picket flank
 * point as a violet ring, each with a line from every hull <b>actually holding that order</b>.
 * <p>
 * Those lines are the important part. The formation orders are advisory - {@code RALLY_CARRIER},
 * {@code RALLY_TASK_FORCE} and {@code DEFEND} leave the hull its own AI - so a hull can be given a
 * station and simply not go. A role ring with no line to its station point is that failure, and it
 * is the same fact the summary reports as {@code holding station}.
 */
public class TaskForceDebugOverlay extends BaseCombatLayeredRenderingPlugin {

    protected static final float RING_MARGIN = 15f;
    protected static final int RING_SEGMENTS = 20;
    protected static final float RING_ALPHA = 0.7f;

    protected static final float STATION_RING = 300f;

    protected static final Color CARRIER_COLOR = Color.CYAN;
    protected static final Color SCREEN_COLOR = Color.YELLOW;
    protected static final Color LINE_COLOR = new Color(255, 90, 0);
    protected static final Color PICKET_COLOR = new Color(170, 110, 255);

    protected final TaskForceAI ai;

    public TaskForceDebugOverlay(TaskForceAI ai) {
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

        drawRings(ai.lastCarriers, CARRIER_COLOR, alphaMult);
        drawRings(ai.lastScreen, SCREEN_COLOR, alphaMult);
        drawRings(ai.lastBattleline, LINE_COLOR, alphaMult);
        drawRings(ai.lastPickets, PICKET_COLOR, alphaMult);

        drawStation(ai.lastCarrierLoc, ai.carrierAssignment, CARRIER_COLOR, alphaMult);
        drawStation(ai.lastLineLoc, ai.lineAssignment, LINE_COLOR, alphaMult);
        drawStation(ai.lastPicketLoc, ai.picketAssignment, PICKET_COLOR, alphaMult);
        drawStation(ai.lastCarrierLoc, ai.guardAssignment, SCREEN_COLOR, alphaMult);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    protected void drawRings(List<ShipAPI> ships, Color color, float alphaMult) {
        for (ShipAPI ship : ships) {
            if (ship == null || !ship.isAlive()) continue;
            drawCircle(ship.getLocation(), ship.getCollisionRadius() + RING_MARGIN, color, RING_ALPHA * alphaMult);
        }
    }

    /**
     * One formation point, plus a line from every hull <i>actually holding</i> that order - read back
     * from the task manager, not from the list we handed it. A ringed point with no lines means the
     * order was issued and is not being taken up.
     */
    protected void drawStation(Vector2f loc, AssignmentInfo assignment, Color color, float alphaMult) {
        if (loc == null || assignment == null) return;
        drawCircle(loc, STATION_RING, color, RING_ALPHA * alphaMult);
        for (ShipAPI ship : ai.engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.getOwner() != ai.owner) continue;
            if (!ship.isAlive() || ship.isHulk()) continue;
            if (ai.taskManager.getAssignmentFor(ship) != assignment) continue;
            drawLine(ship.getLocation(), loc, color, 0.25f * alphaMult);
        }
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

    protected void setColor(Color color, float alphaMult) {
        GL11.glColor4ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(),
                (byte) (255 * alphaMult));
    }
}
