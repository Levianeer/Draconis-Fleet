package levianeer.draconis.data.campaign.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;
import com.fs.starfarer.api.loading.Description.Type;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * The Rift - A hyperspace "storm" surrounding the Fafnir system.
 * Applies CR drain and sensor disruption to fleets within its boundaries.
 */
public class XLII_RiftTerrainPlugin extends BaseTerrain {

    private static final Logger log = Global.getLogger(XLII_RiftTerrainPlugin.class);

    // Gameplay constants
    public static final float RIFT_RADIUS = 1500f;
    private static final float CR_DRAIN_PER_DAY = 0.05f; // 5% per day
    private static final float SENSOR_RANGE_MULT = 0.75f; // 25% sensor range
    private static final float WARNING_INTERVAL_DAYS = 4f; // Show warning every 4 days

    private Vector2f centerLocation;
    private float warningTimer = 0f;
    private float animationTime = 0f;

    @Override
    public void init(String terrainId, SectorEntityToken entity, Object param) {
        super.init(terrainId, entity, param);
        this.centerLocation = entity.getLocation();

        log.info("Draconis: Rift terrain initialized at " + centerLocation);
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
    }

    @Override
    public float getRenderRange() {
        // Return twice the radius so players can see the Rift from a distance
        return RIFT_RADIUS * 2f;
    }

    @Override
    public void advance(float amount) {
        // Update animation time (always, even when paused for smooth visuals)
        animationTime += amount;

        // Don't process gameplay effects if game is paused
        if (Global.getSector().isPaused()) {
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);

        // Get player fleet
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        // Modifier ID used for sensor range penalty
        String modId = "XLII_rift_sensor_penalty";

        // Clean up sensor penalty if fleet is not in hyperspace (e.g., entering jump point)
        if (playerFleet == null || !playerFleet.isInHyperspace()) {
            if (playerFleet != null) {
                playerFleet.getStats().getSensorRangeMod().unmodify(modId);
            }
            return;
        }

        // Check if player is within the Rift
        float distance = Misc.getDistance(playerFleet.getLocation(), centerLocation);
        boolean inRift = distance <= RIFT_RADIUS;

        if (inRift) {
            applyRiftEffects(playerFleet, days);
            // Only show warnings if the fleet has vulnerable ships (without immunity)
            if (hasVulnerableShips(playerFleet)) {
                updateWarnings(days);
            }
        } else {
            // Remove sensor penalty when fleet leaves the Rift
            playerFleet.getStats().getSensorRangeMod().unmodify(modId);
        }
    }

    /**
     * Apply CR drain and sensor penalties to the fleet
     */
    private void applyRiftEffects(CampaignFleetAPI fleet, float days) {
        // Apply CR drain to all ships (except those with immunity)
        float crLoss = CR_DRAIN_PER_DAY * days;
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();

        for (FleetMemberAPI member : members) {
            if (member.isMothballed()) continue;

            // Check for immunity: solar_shielding or XLII_draconishull hull mods
            boolean hasImmunity = member.getVariant().hasHullMod("solar_shielding") ||
                                  member.getVariant().hasHullMod("XLII_draconishull");

            if (hasImmunity) {
                continue; // Skip CR drain for immune ships
            }

            float currentCR = member.getRepairTracker().getCR();
            float newCR = Math.max(0f, currentCR - crLoss);
            member.getRepairTracker().setCR(newCR);
        }

        // Apply sensor range penalty via fleet stats (affects whole fleet)
        // Note: This is applied as a temporary modifier that should be removed when leaving
        String modId = "XLII_rift_sensor_penalty";
        fleet.getStats().getSensorRangeMod().modifyMult(modId, SENSOR_RANGE_MULT, "The Rift");
    }

    /**
     * Show periodic warning messages to the player
     */
    private void updateWarnings(float days) {
        warningTimer += days;

        if (warningTimer >= WARNING_INTERVAL_DAYS) {
            warningTimer = 0f;

            Global.getSector().getCampaignUI().addMessage(
                    "Your fleet continues to suffer from exposure to the Rift. Combat readiness is deteriorating.",
                    Misc.getNegativeHighlightColor()
            );
        }
    }

    /**
     * Check if the fleet has any vulnerable ships (ships without immunity to CR drain)
     * @return true if at least one non-mothballed ship lacks both solar_shielding and XLII_draconishull
     */
    private boolean hasVulnerableShips(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();

        for (FleetMemberAPI member : members) {
            if (member.isMothballed()) continue;

            // Check if this ship has immunity
            boolean hasImmunity = member.getVariant().hasHullMod("solar_shielding") ||
                                  member.getVariant().hasHullMod("XLII_draconishull");

            // If we find any ship without immunity, the fleet is vulnerable
            if (!hasImmunity) {
                return true;
            }
        }

        // All non-mothballed ships have immunity
        return false;
    }

    @Override
    public boolean containsEntity(SectorEntityToken entity) {
        if (entity == null || centerLocation == null) {
            return false;
        }

        float distance = Misc.getDistance(entity.getLocation(), centerLocation);
        return distance <= RIFT_RADIUS;
    }

    @Override
    public float getTooltipWidth() {
        return 375f; // Match vanilla terrain tooltip width
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 10f;
        float small = 5f;
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addTitle(getTerrainName());

        // Load description from descriptions.csv (matching vanilla pattern)
        tooltip.addPara(Global.getSettings().getDescription(getTerrainId(), Type.TERRAIN).getText1(), pad);

        // Effects section - heading only shows when expanded
        float nextPad = pad;
        if (expanded) {
            tooltip.addSectionHeading("Effects", Alignment.MID, pad);
            nextPad = small;
        }

        // Effects description (contextual, matching vanilla narrative style)
        tooltip.addPara("Reduces combat readiness by %s per day and sensor range to %s for ships without protection. " +
                        "The exotic radiation permeates hull plating and disrupts sensor arrays.",
                nextPad,
                bad,
                String.format("%.1f%%", CR_DRAIN_PER_DAY * 100f),
                String.format("%d%%", (int)(SENSOR_RANGE_MULT * 100f))
        );

        // Protection information (contextual paragraph)
        tooltip.addPara("Ships equipped with %s or Draconis-built hulls are " +
                        "unaffected by the Rift's combat readiness degradation.",
                pad,
                highlight,
                "Solar Shielding"
        );

        tooltip.addPara("The sensor range penalty affects all ships, regardless of protective measures.",
                gray,
                pad
        );

        // Combat section - heading only shows when expanded
        if (expanded) {
            tooltip.addSectionHeading("Combat", Alignment.MID, pad);

            tooltip.addPara("No combat effects.", nextPad);
            nextPad = small;
        } else {
            nextPad = pad;
        }
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (centerLocation == null) return;

        float alphaMult = viewport.getAlphaMult();

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);  // Additive blending for glow

        // Swirling particles
        renderParticleField(centerLocation.x, centerLocation.y, alphaMult);

        // Lightning arcs
        renderLightning(centerLocation.x, centerLocation.y, alphaMult);

        // Abyss core - eye of the storm
        renderAbyssCore(centerLocation.x, centerLocation.y, alphaMult);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    /**
     * Calculate fade-out alpha based on distance from center
     * Returns 1.0 at center, fades to 0.0 at outer edge
     */
    private float calculateEdgeFade(float distanceFromCenter) {
        // Start fading at 70% of the radius, fully transparent at edge
        float fadeStartRadius = RIFT_RADIUS * 0.7f;
        float fadeEndRadius = RIFT_RADIUS;

        if (distanceFromCenter < fadeStartRadius) {
            return 1.0f; // Full opacity in the center
        } else if (distanceFromCenter >= fadeEndRadius) {
            return 0.0f; // Fully transparent at the edge
        } else {
            // Smooth fade between start and end
            float fadeProgress = (distanceFromCenter - fadeStartRadius) / (fadeEndRadius - fadeStartRadius);
            return 1.0f - fadeProgress;
        }
    }

    /**
     * Renders swirling particle field with edge fade-out
     */
    private void renderParticleField(float x, float y, float alphaMult) {
        int particleCount = 150; // Increased to compensate for removed clouds

        GL11.glPointSize(2.5f); // Slightly larger particles
        GL11.glBegin(GL11.GL_POINTS);

        for (int i = 0; i < particleCount; i++) {
            float angleOffset = i * 137.5f; // Golden angle for even distribution
            float radiusRatio = (i / (float)particleCount);
            float particleRadius = RIFT_RADIUS * radiusRatio;

            // Spiral motion - slowed down
            float spiralSpeed = 3f / (radiusRatio + 0.1f); // Slower rotation
            float angle = (float)Math.toRadians(angleOffset + animationTime * spiralSpeed);

            float px = x + (float)Math.cos(angle) * particleRadius;
            float py = y + (float)Math.sin(angle) * particleRadius;

            // Color varies by distance - more magenta near center, more purple outside
            float magentaRatio = 1.0f - radiusRatio;
            float r = 120 + (60 * magentaRatio);
            float g = 40;
            float b = 180 - (60 * magentaRatio);

            // Apply edge fade based on distance from center
            float edgeFade = calculateEdgeFade(particleRadius);
            float particleAlpha = alphaMult * 0.7f * edgeFade;

            GL11.glColor4f(r / 255f, g / 255f, b / 255f, particleAlpha);
            GL11.glVertex2f(px, py);
        }

        GL11.glEnd();
    }

    /**
     * Renders constant background radiation arcs - subtle, dark energy tendrils with edge fade
     */
    private void renderLightning(float x, float y, float alphaMult) {
        // Draw constant, very subtle radiation arcs
        GL11.glLineWidth(1.5f); // Thinner than before
        GL11.glBegin(GL11.GL_LINES);

        // Draw 8 persistent arcs at fixed angles with slow organic movement
        for (int i = 0; i < 8; i++) {
            // Base angle evenly distributed
            float baseAngle = (float)(i * 2 * Math.PI / 8);

            // Add slow sine wave movement for organic feel
            float angleOffset = (float)Math.sin(animationTime * 0.3 + i) * 0.3f;
            float angle = baseAngle + angleOffset;

            // Length varies slowly
            float lengthMod = (float)Math.sin(animationTime * 0.2 + i * 0.5) * 0.15f + 0.85f;
            float length = RIFT_RADIUS * lengthMod * 0.8f;

            // Calculate arc endpoints
            float startRadius = RIFT_RADIUS * 0.3f; // Start from inner region
            float startX = x + (float)Math.cos(angle) * startRadius;
            float startY = y + (float)Math.sin(angle) * startRadius;
            float endX = x + (float)Math.cos(angle) * length;
            float endY = y + (float)Math.sin(angle) * length;

            // Apply edge fade to the arc (use end point distance for fade calculation)
            float edgeFade = calculateEdgeFade(length);

            // Very subtle, dark purple color - darker than particles
            float arcAlpha = (0.2f + (float)Math.sin(animationTime * 0.4 + i) * 0.05f) * alphaMult * edgeFade;
            GL11.glColor4f(80f / 255f, 30f / 255f, 120f / 255f, arcAlpha);

            GL11.glVertex2f(startX, startY);
            GL11.glVertex2f(endX, endY);
        }

        GL11.glEnd();
    }

    /**
     * Renders the Abyss core - a dark center with thin pulsing rings (eye of the storm)
     */
    private void renderAbyssCore(float x, float y, float alphaMult) {
        int segments = 64;
        float coreRadius = RIFT_RADIUS * 0.25f;

        // Draw dark center - near-black void
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(20f / 255f, 10f / 255f, 30f / 255f, alphaMult * 0.3f); // Very dark purple
        GL11.glVertex2f(x, y); // Center

        for (int i = 0; i <= segments; i++) {
            float angle = (float)(2.0 * Math.PI * i / segments);
            float dx = (float)Math.cos(angle) * coreRadius;
            float dy = (float)Math.sin(angle) * coreRadius;
            GL11.glVertex2f(x + dx, y + dy);
        }
        GL11.glEnd();

        // Draw thin pulsing rings
        GL11.glLineWidth(2.0f);

        // Ring 1 - Outer rim (multiple overlapping rings for blur effect)
        // Desaturated purple-gray colors with lower transparency
        float rimPulse = (float)(Math.sin(animationTime * 0.3) * 0.1 + 0.9); // Gentle pulse 0.8-1.0
        drawThinRing(x, y, coreRadius * 0.95f, segments, 130, 80, 110, alphaMult * rimPulse * 0.4f);
        drawThinRing(x, y, coreRadius * 0.96f, segments, 130, 80, 110, alphaMult * rimPulse * 0.3f);
        drawThinRing(x, y, coreRadius * 0.97f, segments, 130, 80, 110, alphaMult * rimPulse * 0.2f);
        drawThinRing(x, y, coreRadius * 0.98f, segments, 130, 80, 110, alphaMult * rimPulse * 0.1f);

        // Ring 2 - Middle ring (slower, opposite phase)
        float middlePulse = (float)(Math.sin(animationTime * 0.2 + Math.PI) * 0.15 + 0.75); // 0.6-0.9
        float middleRadius = coreRadius * 0.7f + (float)Math.sin(animationTime * 0.4) * 20f; // Slow expansion
        drawThinRing(x, y, middleRadius, segments, 120, 80, 130, alphaMult * middlePulse * 0.3f);

        // Ring 3 - Inner ring (fastest, subtle)
        float innerPulse = (float)(Math.sin(animationTime * 0.5 + Math.PI * 0.5) * 0.2 + 0.6); // 0.4-0.8
        float innerRadius = coreRadius * 0.4f + (float)Math.sin(animationTime * 0.6) * 15f; // Faster expansion
        drawThinRing(x, y, innerRadius, segments, 110, 70, 130, alphaMult * innerPulse * 0.25f);
    }

    /**
     * Helper to draw a thin ring
     */
    private void drawThinRing(float x, float y, float radius, int segments, int r, int g, int b, float alpha) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r / 255f, g / 255f, b / 255f, alpha);

        for (int i = 0; i < segments; i++) {
            float angle = (float)(2.0 * Math.PI * i / segments);
            float dx = (float)Math.cos(angle) * radius;
            float dy = (float)Math.sin(angle) * radius;
            GL11.glVertex2f(x + dx, y + dy);
        }

        GL11.glEnd();
    }

    @Override
    public String getTerrainName() {
        return "The Rift";
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public boolean isTooltipExpandable() {
        return true;
    }
}
