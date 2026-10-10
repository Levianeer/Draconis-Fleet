package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * {@link CarrierDoctrineAI} debug visualization - a ring per ship for its doctrine role, a
 * marker above each carrier for its current {@link CarrierDuty}, a line from STRIKE-duty
 * carriers to the wave's current target, and a line from CAP-duty carriers to whatever their
 * ship is actually currently targeting (green if friendly - escorting; orange if enemy -
 * harassing/intercepting). Both lines read ground truth (the AI's own
 * {@link CarrierDoctrineAI#activeStrikeStrikers}/{@link CarrierDoctrineAI#activeStrikeTarget}
 * for STRIKE, {@code ship.getShipTarget()} directly for CAP) rather than inferring intent, so a
 * carrier whose actual behavior has drifted from what the doctrine intended - the exact class of
 * bug this overlay was built to catch - shows up as a line going somewhere unexpected instead of
 * being masked by what the AI merely *meant* to do. Gated by the same
 * {@link CarrierDoctrineAI#debugMessagesEnabled} flag as the existing text debug messages -
 * see {@link CarrierDoctrinePlugin} for where this is attached.
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

        drawJobMarkers(alphaMult);
        drawTargetLines(alphaMult);
        drawCapLines(alphaMult);

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
