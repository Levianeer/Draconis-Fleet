package levianeer.draconis.data.scripts.combat.commander;

import java.awt.Color;

import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

/**
 * Visual debugger for the commander (test missions only, since only they run it), modelled on the
 * legacy carrier doctrine overlay. It draws what {@link TaskForces} and {@link BattlePicture} hold,
 * read live each frame:
 * <ul>
 * <li>every enemy ship's threat range (longest non-missile weapon), faint red;</li>
 * <li>a ring around each enemy cluster: crimson for the main body, orange for a stray, grey otherwise;</li>
 * <li>a ring on each of our ships in its group's colour, thicker on the lead, with a faint line from each
 * escort to its lead;</li>
 * <li>each lead's job: a white line and diamond to the core it attacks, a line and square to its hold
 * point, or a green line to the lead it regroups onto.</li>
 * </ul>
 * Job, balance and stance are spelled out in {@link TaskForces}'s floating text.
 */
final class CommanderOverlay extends BaseCombatLayeredRenderingPlugin {

    private static final int SEGMENTS = 48;
    private static final Color THREAT = new Color(255, 60, 60);
    private static final Color MAIN = new Color(220, 20, 60);
    private static final Color STRAY = new Color(255, 160, 60);
    private static final Color OTHER = new Color(140, 140, 140);
    private static final Color ATTACK = Color.WHITE;
    private static final Color REGROUP = new Color(60, 255, 120);

    private final TaskForces taskForces;

    CommanderOverlay(TaskForces taskForces) {
        super(CombatEngineLayers.JUST_BELOW_WIDGETS);
        this.taskForces = taskForces;
    }

    @Override
    public float getRenderRadius() {
        return 1000000f;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float alpha = viewport.getAlphaMult();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (BattlePicture.Cluster c : taskForces.picture.clusters) {
            for (ShipAPI s : c.ships) {
                if (s.isAlive()) circle(s.getLocation(), BattlePicture.reach(s), THREAT, 0.12f * alpha, 1f);
            }
            Color color = c.main ? MAIN : c.stray ? STRAY : OTHER;
            circle(c.centre, c.radius + 80f, color, 0.6f * alpha, c.main ? 3f : 2f);
        }

        for (TaskForces.Group g : taskForces.groups) {
            if (g.lead == null) continue;
            Color color = TaskForces.COLORS[(g.id - 1) % TaskForces.COLORS.length];
            for (ShipAPI s : g.ships) {
                boolean lead = s == g.lead;
                circle(s.getLocation(), s.getCollisionRadius() + 20f, color, 0.8f * alpha, lead ? 3f : 1.5f);
                if (!lead) line(s.getLocation(), g.lead.getLocation(), color, 0.25f * alpha, 1f);
            }
            if (taskForces.stance == TaskForces.Stance.WITHDRAW) continue;
            switch (g.job) {
                case ATTACK:
                    if (g.attacking != null && g.attacking.core.isAlive()) {
                        line(g.lead.getLocation(), g.attacking.core.getLocation(), ATTACK, 0.5f * alpha, 1.5f);
                        diamond(g.attacking.core.getLocation(), 40f, ATTACK, 0.8f * alpha);
                    }
                    break;
                case HOLD:
                    if (g.holdPoint != null) {
                        line(g.lead.getLocation(), g.holdPoint, color, 0.5f * alpha, 1.5f);
                        square(g.holdPoint, 40f, color, 0.8f * alpha);
                    }
                    break;
                case REGROUP:
                    if (g.regroupTo != null && g.regroupTo.lead != null) {
                        line(g.lead.getLocation(), g.regroupTo.lead.getLocation(), REGROUP, 0.6f * alpha, 2f);
                    }
                    break;
            }
        }
        GL11.glLineWidth(1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void circle(Vector2f centre, float radius, Color color, float alpha, float width) {
        color(color, alpha);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < SEGMENTS; i++) {
            double theta = 2 * Math.PI * i / SEGMENTS;
            GL11.glVertex2f(centre.x + radius * (float) Math.cos(theta), centre.y + radius * (float) Math.sin(theta));
        }
        GL11.glEnd();
    }

    private static void line(Vector2f from, Vector2f to, Color color, float alpha, float width) {
        color(color, alpha);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(from.x, from.y);
        GL11.glVertex2f(to.x, to.y);
        GL11.glEnd();
    }

    private static void diamond(Vector2f centre, float size, Color color, float alpha) {
        color(color, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(centre.x, centre.y + size);
        GL11.glVertex2f(centre.x + size, centre.y);
        GL11.glVertex2f(centre.x, centre.y - size);
        GL11.glVertex2f(centre.x - size, centre.y);
        GL11.glEnd();
    }

    private static void square(Vector2f centre, float size, Color color, float alpha) {
        color(color, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(centre.x - size, centre.y - size);
        GL11.glVertex2f(centre.x - size, centre.y + size);
        GL11.glVertex2f(centre.x + size, centre.y + size);
        GL11.glVertex2f(centre.x + size, centre.y - size);
        GL11.glEnd();
    }

    private static void color(Color color, float alpha) {
        GL11.glColor4ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(), (byte) (255 * Math.min(1f, alpha)));
    }
}
