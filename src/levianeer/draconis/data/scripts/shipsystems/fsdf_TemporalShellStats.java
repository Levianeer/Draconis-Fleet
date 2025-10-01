package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;

public class fsdf_TemporalShellStats extends BaseShipSystemScript {

	public static final float MAX_TIME_MULT = 25f;

	private final IntervalUtil jitterInterval = new IntervalUtil(0.25f, 0.25f);

	@Override
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		if (!(stats.getEntity() instanceof ShipAPI ship))
			return;
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
		id += "_" + ship.getId();

		// Create afterimage effect at regular intervals.
		jitterInterval.advance(Global.getCombatEngine().getElapsedInLastFrame());
		if (jitterInterval.intervalElapsed()) {
			SpriteAPI sprite = ship.getSpriteAPI();
			float offsetX = sprite.getWidth() / 2 - sprite.getCenterX();
			float offsetY = sprite.getHeight() / 2 - sprite.getCenterY();
			float angle = ship.getFacing() - 90f;
			float cos = (float) FastTrig.cos(Math.toRadians(angle));
			float sin = (float) FastTrig.sin(Math.toRadians(angle));
			float trueOffsetX = cos * offsetX - sin * offsetY;
			float trueOffsetY = sin * offsetX + cos * offsetY;

			// Use the dynamically shifting color for the afterimage.
			Color afterimageColor = getAfterimageColor();

			org.magiclib.util.MagicRender.battlespace(
					Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
					new Vector2f(ship.getLocation().x + trueOffsetX, ship.getLocation().y + trueOffsetY),
					new Vector2f(0, 0),
					new Vector2f(sprite.getWidth(), sprite.getHeight()),
					new Vector2f(0, 0),
					angle,
					0f,
					afterimageColor,
					true,
					0f, 0f, 0f, 0f, 0f,
					0.1f, 0.1f,
					0.75f,
					CombatEngineLayers.BELOW_SHIPS_LAYER
			);
		}

		// Time multiplier adjustment.
		float timeMult = 1f + (MAX_TIME_MULT - 1f) * effectLevel;
		stats.getTimeMult().modifyMult(id, timeMult);
		if (player) {
			Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / timeMult);
		} else {
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}

		// Maneuvering Jets effects.
		if (state == State.OUT) {
			stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
			stats.getMaxTurnRate().unmodify(id);
		} else {
			stats.getMaxSpeed().modifyFlat(id, 50f);
			stats.getAcceleration().modifyPercent(id, 200f * effectLevel);
			stats.getDeceleration().modifyPercent(id, 200f * effectLevel);
			stats.getTurnAcceleration().modifyFlat(id, 30f * effectLevel);
			stats.getTurnAcceleration().modifyPercent(id, 200f * effectLevel);
			stats.getMaxTurnRate().modifyFlat(id, 15f);
			stats.getMaxTurnRate().modifyPercent(id, 100f);
		}
	}

	@Override
	public void unapply(MutableShipStatsAPI stats, String id) {
		if (!(stats.getEntity() instanceof ShipAPI ship))
			return;
        id += "_" + ship.getId();

		Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getTimeMult().unmodify(id);

		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
	}

	@Override
	public StatusData getStatusData(int index, State state, float effectLevel) {
        return switch (index) {
            case 0 -> new StatusData("time flow altered", false);
            case 1 -> new StatusData("enhanced maneuverability", false);
            default -> null;
        };
	}

	// Returns a color that cycles smoothly between teal, blue, pink, and red.
	private Color getAfterimageColor() {
		// Define the key colors with a fixed alpha value.
		Color[] colors = new Color[] {
				new Color(0, 255, 255, 125),  // Teal
				new Color(0, 255, 125, 125),  // Green
				new Color(0, 0, 255, 125),    // Blue
				new Color(255, 0, 255, 125),  // Pink
				new Color(255, 0, 0, 125)     // Red
		};

		// Cycle period to speed up color changes.
		float period = 0.05f;
		// Get total elapsed time in seconds.
		float totalTime = Global.getCombatEngine().getTotalElapsedTime(true);

		// Normalize the remainder to a 0-1 range.
		float fractionThroughPeriod = (totalTime % period) / period;
		// Scale to the number of colors.
		float cycleTime = fractionThroughPeriod * colors.length;

		int index1 = (int) cycleTime;
		int index2 = (index1 + 1) % colors.length;
		float fraction = cycleTime - index1;

		return lerpColor(colors[index1], colors[index2], fraction);
	}

	// Linearly interpolates between two colors.
	private Color lerpColor(Color c1, Color c2, float fraction) {
		int r = (int) (c1.getRed() + fraction * (c2.getRed() - c1.getRed()));
		int g = (int) (c1.getGreen() + fraction * (c2.getGreen() - c1.getGreen()));
		int b = (int) (c1.getBlue() + fraction * (c2.getBlue() - c1.getBlue()));
		int a = (int) (c1.getAlpha() + fraction * (c2.getAlpha() - c1.getAlpha()));
		return new Color(r, g, b, a);
	}
}