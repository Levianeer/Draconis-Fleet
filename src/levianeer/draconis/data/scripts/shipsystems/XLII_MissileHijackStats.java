package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class XLII_MissileHijackStats extends BaseShipSystemScript {

    private static final float DISABLE_RADIUS = 1500f;

    private static final float ROTATION_SPEED = 0f; // degrees per second
    private static final Color RING_COLOR = new Color(255, 90, 165, 40);
    private static final Color CONVERSION_COLOR = new Color(50, 255, 50, 155);

    // Target-lock marker: drawn on each missile currently dwelling in range, shrinks
    // to nothing exactly as that missile's own dwell timer completes and it converts.
    private static final String MARKER_SPRITE_CATEGORY = "fx";
    private static final String MARKER_SPRITE_ID = "XLII_jammer_ring";
    private static final Color MARKER_COLOR = new Color(255, 90, 165, 200);
    private static final float MARKER_SIZE = 150f; // full-size px diameter, tune in-game

    // Seconds a missile must stay continuously in range (while the system is ACTIVE)
    // before it converts. Leaving range at any point resets its progress to zero.
    // Scaled up per-missile by ECCM rating - see getDwellThreshold().
    public static final float DWELL_THRESHOLD = 0.75f;

    // Converted missiles get this multiple of their original max flight time as fresh
    // remaining flight time, on top of the plain 1:1 reset - the dwell delay already eats
    // into their original flight time, so a straight reset isn't enough for them to
    // reliably reach a newly-assigned target.
    private static final float CONVERSION_FLIGHT_TIME_MULT = 1.5f;

    private SpriteAPI ringSprite = null;
    private SpriteAPI markerSprite = null;
    private boolean textDisplayed = false;
    private final Map<MissileAPI, Float> missileDwellTime = new HashMap<>();

    public static Color TEXT_COLOR = new Color(200, 200, 200);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || ship.isHulk()) {
            missileDwellTime.clear();
            return;
        }

        // Calculate effective disable radius
        float effectiveDisableRadius = DISABLE_RADIUS;
        if (ship.isFighter()) {
            effectiveDisableRadius = DISABLE_RADIUS * 0.25f;
        }

        // Show activation text once when the system becomes fully active
        if (state == State.ACTIVE && !textDisplayed) {
            ship.getFluxTracker().showOverloadFloatyIfNeeded("Jamming!", TEXT_COLOR, 1f, true);
            textDisplayed = true;
        }

        // Animated ring: grows in on IN and fades out on OUT, scaled by effectLevel.
        if (effectLevel > 0f) {
            drawScreenSpaceRing(ship, effectiveDisableRadius, effectLevel);
        }

        // Static ring at the system's true max range - unaffected by effectLevel,
        // constant for the whole IN/ACTIVE/OUT window so the full engagement zone
        // is visible immediately instead of only once chargeup finishes.
        boolean systemActive = state == State.IN || state == State.ACTIVE || state == State.OUT;
        if (systemActive) {
            drawScreenSpaceRing(ship, effectiveDisableRadius, 1f);
        }

        // Missiles must stay continuously in range for DWELL_THRESHOLD seconds while the
        // system is ACTIVE to convert; leaving range at any point resets their progress.
        if (state == State.ACTIVE) {
            trackAndConvertMissiles(ship, effectiveDisableRadius);
            drawMissileMarkers();
        } else {
            missileDwellTime.clear();
        }
    }

    // Rendered in screen space so it's pixel-perfect at any zoom level. sizeFraction
    // scales the radius: pass effectLevel for the animated ring, 1f for the static one.
    private void drawScreenSpaceRing(ShipAPI ship, float effectiveDisableRadius, float sizeFraction) {
        CombatEngineAPI engine = Global.getCombatEngine();
        ViewportAPI view = engine.getViewport();
        Vector2f loc = ship.getLocation();
        if (!view.isNearViewport(loc, effectiveDisableRadius)) return;

        float screenScale = Global.getSettings().getScreenScaleMult();
        float radius = (effectiveDisableRadius + ship.getCollisionRadius()) * 2f * screenScale * sizeFraction / view.getViewMult();
        float angle = engine.getTotalElapsedTime(false) * ROTATION_SPEED;
        if (ringSprite == null) ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring");
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

    private void trackAndConvertMissiles(ShipAPI ship, float effectiveDisableRadius) {
        float elapsed = Global.getCombatEngine().getElapsedInLastFrame();

        List<MissileAPI> nearbyMissiles = CombatUtils.getMissilesWithinRange(ship.getLocation(), effectiveDisableRadius);
        Set<MissileAPI> inRangeNow = new HashSet<>();
        for (MissileAPI missile : nearbyMissiles) {
            if (missile.getSource() == null || missile.getSource().getOwner() == ship.getOwner()) continue;
            if (missile.isFading()) continue;
            if (missile.isFlare()) continue;
            inRangeNow.add(missile);
        }

        // Leaving range (or dying) at any point breaks the lock and resets progress.
        missileDwellTime.keySet().removeIf(m -> !inRangeNow.contains(m));

        CombatEntityAPI redirectTarget = null;
        for (MissileAPI missile : inRangeNow) {
            float threshold = getDwellThreshold(missile);
            float dwell = missileDwellTime.getOrDefault(missile, 0f) + elapsed;
            if (dwell < threshold) {
                missileDwellTime.put(missile, dwell);
                continue;
            }

            if (redirectTarget == null) {
                redirectTarget = ship.getShipTarget();
                if (redirectTarget == null) redirectTarget = AIUtils.getNearestEnemy(ship);
            }
            if (redirectTarget != null) {
                hijackMissile(missile, ship, redirectTarget);
                missileDwellTime.remove(missile);
            } else {
                // Ready to convert but nothing to redirect at yet - hold at the
                // threshold and retry next frame instead of losing the lock.
                missileDwellTime.put(missile, threshold);
            }
        }
    }

    // Missiles with a better ECCM rating resist the jam signal longer, so their dwell
    // timer takes proportionally longer to fill. 0 ECCM chance is the plain DWELL_THRESHOLD;
    // 1.0 (full ECCM resistance) doubles it.
    private static float getDwellThreshold(MissileAPI missile) {
        return DWELL_THRESHOLD * (1f + missile.getECCMChance());
    }

    // Each tracked missile has its own dwell progress, so its marker shrinks
    // independently of the others. Redrawn fresh every frame from live dwell time.
    private void drawMissileMarkers() {
        if (missileDwellTime.isEmpty()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        ViewportAPI view = engine.getViewport();
        float screenScale = Global.getSettings().getScreenScaleMult();
        if (markerSprite == null) markerSprite = Global.getSettings().getSprite(MARKER_SPRITE_CATEGORY, MARKER_SPRITE_ID);
        markerSprite.setColor(MARKER_COLOR);
        markerSprite.setAdditiveBlend();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
        GL11.glOrtho(0, Display.getWidth(), 0, Display.getHeight(), -1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        for (Map.Entry<MissileAPI, Float> entry : missileDwellTime.entrySet()) {
            MissileAPI missile = entry.getKey();
            if (missile.isFading() || !engine.isEntityInPlay(missile)) continue;
            float sizeFraction = 1f - Math.min(1f, entry.getValue() / getDwellThreshold(missile));
            if (sizeFraction <= 0f) continue;
            float radius = MARKER_SIZE * screenScale * sizeFraction / view.getViewMult();
            markerSprite.setSize(radius, radius);
            // Missiles can be well outside the camera viewport while still within the
            // (up to 1000-unit) hijack range, unlike the ship-anchored ring above - GL
            // clips off-screen sprites for free, so skip the viewport pre-check here.
            markerSprite.renderAtCenter(
                    view.convertWorldXtoScreenX(missile.getLocation().x) * screenScale,
                    view.convertWorldYtoScreenY(missile.getLocation().y) * screenScale
            );
        }
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private void hijackMissile(MissileAPI missile, ShipAPI ship, CombatEntityAPI redirectTarget) {
        // Change ownership to friendly
        missile.setOwner(ship.getOwner());
        missile.setSource(ship);

        // Set collision class to prevent friendly fire while still hitting enemies
        missile.setCollisionClass(CollisionClass.MISSILE_NO_FF);

        // Retarget to whatever the ship has targeted, if the missile can be guided
        if (missile.getAI() instanceof GuidedMissileAI ai) {
            ai.setTarget(redirectTarget);
        }

        // Snap facing instantly toward the new target
        missile.setFacing(VectorUtils.getAngle(missile.getLocation(), redirectTarget.getLocation()));

        // Give the missile a fresh lifetime to reach its new target: remaining flight
        // time becomes CONVERSION_FLIGHT_TIME_MULT times its original max, instead of
        // just a 1:1 reset, to make up for the time already spent dwelling.
        float elapsedTime = missile.getFlightTime();
        float originalMaxFlightTime = missile.getMaxFlightTime();
        missile.setMaxFlightTime(elapsedTime + originalMaxFlightTime * CONVERSION_FLIGHT_TIME_MULT);

        spawnConversionParticle(missile.getLocation());
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        textDisplayed = false;
        missileDwellTime.clear();
    }

    private void spawnConversionParticle(Vector2f location) {
        // Spawn hit particles to indicate missile hijacking
        float angle = 0f;

        Global.getCombatEngine().addHitParticle(
                location,
                new Vector2f((float) Math.cos(Math.toRadians(angle)), (float) Math.sin(Math.toRadians(angle))),
                10f,
                1f,
                0.1f,
                CONVERSION_COLOR
        );
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Hijacking Enemy Missiles!", false);
        }
        return null;
    }
}
