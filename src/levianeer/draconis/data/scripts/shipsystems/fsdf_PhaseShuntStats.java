package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class fsdf_PhaseShuntStats extends BaseShipSystemScript {

	// System configuration constants
	private static final float
		MAX_FLUX_SCALING = 2f,        // Max EMP multiplier at high flux
		MIN_FLUX_SCALING = 0.5f,     // Min EMP multiplier at low flux
		FLUX_SCALING_START = 0.2f,   // Flux level where scaling begins
		FLUX_SCALING_END = 0.7f;     // Flux level where scaling caps

	// Phase cloak parameters
	private static final float
		SHIP_ALPHA_MULT = 0.25f,     // Visibility when phased
		MAX_TIME_MULT = 3f,          // Maximum time flow alteration
		MIN_SPEED_MULT = 0.33f,      // Minimum speed when fluxed
		BASE_FLUX_LEVEL_FOR_MIN_SPEED = 0.5f;

	private static final boolean
		FLUX_LEVEL_AFFECTS_SPEED = true;

	// EMP effect parameters
	private static final float
		BASE_EMP_RANGE = 1500f,
		BASE_EMP_AMOUNT = 250f,
		EMP_THICKNESS = 10f,
		ARC_SPAWN_RADIUS = 100f,     // Distance from ship where arcs start
		ARC_VARIABILITY = 0.7f;      // How erratic the arcs are (0-1)

	private static final int
		SHIP_EMP_ARCS = 6,             // EMP arcs per ship
		VISUAL_EMP_ARCS = 12,          // Visual-only arcs for storm effect
		MAX_PROJ_AFFECTED = 32;        // Performance limiter

	private static final Color EMP_CORE_COLOR = new Color(200, 150, 255, 200);
	private static final Color EMP_FRINGE_COLOR = new Color(180, 100, 255, 150);

	private static final String EMP_SOUND_ID = "system_tenebrous_expulsion_activate";
	private static final String EMP_IMPACT_ID = "system_emp_emitter_impact";

	// Status keys for UI elements
	private final Object STATUSKEY1 = new Object();
	private final Object STATUSKEY2 = new Object();
	private final Object STATUSKEY3 = new Object();

	private State prevState = State.IDLE;

	@Override
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		// Get ship reference and verify it's valid
		ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
		if (ship == null) return;

		// Update ID to be ship-specific
		id = id + "_" + ship.getId();
		boolean isPlayer = ship == Global.getCombatEngine().getPlayerShip();

		// Spawn EMP effect when transitioning from OUT to COOLDOWN
		if (state == State.COOLDOWN && prevState == State.OUT) {
			spawnEmpEffects(ship);
		}

		// Update player status display
		if (isPlayer) {
			maintainStatus(ship, state, effectLevel);
		}

		// Handle paused game state
		if (Global.getCombatEngine().isPaused()) {
			prevState = state;
			return;
		}

		// Get phase cloak system reference
		ShipSystemAPI cloak = ship.getPhaseCloak();
		if (cloak == null) cloak = ship.getSystem();
		if (cloak == null) {
			prevState = state;
			return;
		}

		// Apply speed reduction based on flux level
		if (FLUX_LEVEL_AFFECTS_SPEED && (state == State.ACTIVE || state == State.OUT || state == State.IN)) {
			applySpeedReduction(stats, id, ship, effectLevel, cloak);
		}

		// Handle system states
		if (state == State.COOLDOWN || state == State.IDLE) {
			unapply(stats, id);
			prevState = state;
			return;
		}

		// Apply standard phase cloak modifiers
		applyPhaseModifiers(stats, id, effectLevel, state, ship, isPlayer);

		prevState = state;
	}

	@Override
	public void unapply(MutableShipStatsAPI stats, String id) {
		if (!(stats.getEntity() instanceof ShipAPI ship)) return;

        // Reset all modified stats
		Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getTimeMult().unmodify(id);
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxSpeed().unmodifyMult(id + "_2");
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);

		// Reset ship visuals
		ship.setPhased(false);
		ship.setExtraAlphaMult(1f);

		// Reset cloak jitter if applicable
		ShipSystemAPI cloak = ship.getPhaseCloak();
		if (cloak == null) cloak = ship.getSystem();
		if (cloak instanceof PhaseCloakSystemAPI) {
			((PhaseCloakSystemAPI)cloak).setMinCoilJitterLevel(0f);
		}
	}

	private void spawnEmpEffects(ShipAPI ship) {
		float fluxLevel = ship.getCurrFlux() / ship.getMaxFlux();
		float fluxScaling = calculateFluxScaling(fluxLevel);

		// Spawn visual storm effect
		spawnEmpStorm(ship, fluxScaling);

		// Spawn damaging EMP arcs
		spawnEmpArcs(ship, fluxScaling);

		// Play EMP sound
		Global.getSoundPlayer().playSound(
				EMP_SOUND_ID,
				1.0f,
				1.0f,
				ship.getLocation(),
				new Vector2f()
		);
	}

	private void spawnEmpStorm(ShipAPI ship, float fluxScaling) {
		CombatEngineAPI engine = Global.getCombatEngine();
		Vector2f loc = ship.getLocation();
		float empRange = BASE_EMP_RANGE * (0.5f + 0.5f * fluxScaling);
		float arcThickness = EMP_THICKNESS * (0.5f + fluxScaling);

		// Create a circle of visual EMP arcs
		for (int i = 0; i < VISUAL_EMP_ARCS; i++) {
			// Calculate random end point around the ship
			float angle = (float) (Math.random() * Math.PI * 2);
			float distance = empRange * (0.7f + 0.3f * (float) Math.random());
			Vector2f endPoint = new Vector2f(
					loc.x + (float) Math.cos(angle) * distance,
					loc.y + (float) Math.sin(angle) * distance
			);

			// Create a start point near the ship's edge
			Vector2f startPoint = new Vector2f(
					loc.x + (float) Math.cos(angle) * ARC_SPAWN_RADIUS,
					loc.y + (float) Math.sin(angle) * ARC_SPAWN_RADIUS
			);

			// Add some randomness to the path
			Vector2f midPoint = Vector2f.add(
					Vector2f.add(startPoint, endPoint, null),
					new Vector2f(
							empRange * ARC_VARIABILITY * ((float) Math.random() - 0.5f),
							empRange * ARC_VARIABILITY * ((float) Math.random() - 0.5f)
					),
					null
			);
			midPoint.scale(0.5f);

			// Spawn the visual arc
			engine.spawnEmpArcVisual(
					startPoint,
					ship,
					midPoint,
					null,
					arcThickness,
					EMP_FRINGE_COLOR,
					EMP_CORE_COLOR
			);

			// Second segment of the arc
			engine.spawnEmpArcVisual(
					midPoint,
					ship,
					endPoint,
					null,
					arcThickness * 0.8f,
					EMP_FRINGE_COLOR,
					EMP_CORE_COLOR
			);
		}

		// Add a pulsing glow at the center
		engine.addHitParticle(
				loc,
				new Vector2f(),
				empRange * 1.5f,
				0.5f,
				0.5f,
				new Color(200, 180, 255, 100)
		);
	}

	private void spawnEmpArcs(ShipAPI ship, float fluxScaling) {
		CombatEngineAPI engine = Global.getCombatEngine();
		Vector2f loc = ship.getLocation();

		float empRange = BASE_EMP_RANGE * (0.5f + 0.5f * fluxScaling);
		float arcDamage = BASE_EMP_AMOUNT * fluxScaling;
		float arcThickness = EMP_THICKNESS * (0.75f + 0.5f * fluxScaling);

		// Damage nearby enemy ships
		for (ShipAPI target : engine.getShips()) {
			if (target.isHulk() || target.getOwner() == ship.getOwner()) continue;
			if (Misc.getDistance(loc, target.getLocation()) > empRange) continue;

			for (int i = 0; i < SHIP_EMP_ARCS; i++) {
				engine.spawnEmpArc(
						ship, loc, ship, target,
						DamageType.ENERGY,
						arcDamage / 2,
						arcDamage,
						empRange / 2,
						EMP_IMPACT_ID,
						arcThickness,
						EMP_FRINGE_COLOR,
						EMP_CORE_COLOR
				);
			}
		}

		// Disrupt nearby enemy projectiles (limited for performance)
		int projCount = 0;
		for (CombatEntityAPI projectile : engine.getProjectiles()) {
			if (projCount >= MAX_PROJ_AFFECTED) break;
			if (projectile.getOwner() == ship.getOwner()) continue;
			if (Misc.getDistance(loc, projectile.getLocation()) > empRange) continue;

			engine.spawnEmpArc(
					ship, loc, ship, projectile,
					DamageType.ENERGY,
					arcDamage / 2,
					arcDamage,
					empRange / 2,
					EMP_IMPACT_ID,
					arcThickness,
					EMP_FRINGE_COLOR,
					EMP_CORE_COLOR
			);
			projCount++;
		}
	}

	// ==================== HELPER METHODS ====================

	private void applyPhaseModifiers(MutableShipStatsAPI stats, String id,
									 float effectLevel, State state, ShipAPI ship, boolean isPlayer) {

		// Apply speed/acceleration modifiers
		float speedPercentMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_SPEED_MOD).computeEffective(0f);
		float accelPercentMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_ACCEL_MOD).computeEffective(0f);

		stats.getMaxSpeed().modifyPercent(id, speedPercentMod * effectLevel);
		stats.getAcceleration().modifyPercent(id, accelPercentMod * effectLevel);
		stats.getDeceleration().modifyPercent(id, accelPercentMod * effectLevel);

		float speedMultMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_SPEED_MOD).getMult();
		float accelMultMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_ACCEL_MOD).getMult();

		stats.getMaxSpeed().modifyMult(id, speedMultMod * effectLevel);
		stats.getAcceleration().modifyMult(id, accelMultMod * effectLevel);
		stats.getDeceleration().modifyMult(id, accelMultMod * effectLevel);

		// Handle phased state
		if (state == State.IN || state == State.ACTIVE) {
			ship.setPhased(true);
		} else if (state == State.OUT) {
			ship.setPhased(effectLevel > 0.5f);
		}

		// Apply visual effects
		ship.setExtraAlphaMult(1f - (1f - SHIP_ALPHA_MULT) * effectLevel);
		ship.setApplyExtraAlphaToEngines(true);

		// Apply time flow alteration
		float shipTimeMult = 1f + (getMaxTimeMult(stats) - 1f) * effectLevel;
		stats.getTimeMult().modifyMult(id, shipTimeMult);
		if (isPlayer) {
			Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
		} else {
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}
	}

	private void applySpeedReduction(MutableShipStatsAPI stats, String id,
									 ShipAPI ship, float effectLevel, ShipSystemAPI cloak) {

		float mult = getSpeedMult(ship, effectLevel);
		if (mult < 1f) {
			stats.getMaxSpeed().modifyMult(id + "_2", mult);
		} else {
			stats.getMaxSpeed().unmodifyMult(id + "_2");
		}

		if (cloak instanceof PhaseCloakSystemAPI) {
			((PhaseCloakSystemAPI)cloak).setMinCoilJitterLevel(getDisruptionLevel(ship));
		}
	}

	private void maintainStatus(ShipAPI playerShip, State state, float effectLevel) {
		ShipSystemAPI cloak = playerShip.getPhaseCloak();
		if (cloak == null) cloak = playerShip.getSystem();
		if (cloak == null) return;

		// Phase cloak active status
		if (effectLevel > 0f) {
			Global.getCombatEngine().maintainStatusForPlayerShip(
					STATUSKEY1,
					cloak.getSpecAPI().getIconSpriteName(),
					"phase cloak active",
					"time flow altered",
					false
			);
		}

		// Speed reduction status
		if (FLUX_LEVEL_AFFECTS_SPEED && effectLevel > 0f) {
			if (getDisruptionLevel(playerShip) <= 0f) {
				Global.getCombatEngine().maintainStatusForPlayerShip(
						STATUSKEY2,
						cloak.getSpecAPI().getIconSpriteName(),
						"phase coils stable",
						"top speed at 100%",
						false
				);
			} else {
				String speedPercentStr = Math.round(getSpeedMult(playerShip, effectLevel) * 100f) + "%";
				Global.getCombatEngine().maintainStatusForPlayerShip(
						STATUSKEY2,
						cloak.getSpecAPI().getIconSpriteName(),
						"phase coil stress",
						"top speed at " + speedPercentStr,
						true
				);
			}
		}

		// EMP charge status (only when active)
		if (state == State.ACTIVE) {
			float fluxLevel = playerShip.getCurrFlux() / playerShip.getMaxFlux();
			float scaling = calculateFluxScaling(fluxLevel);
			String scalingStr = String.format("%.1fx", scaling);

			Global.getCombatEngine().maintainStatusForPlayerShip(
					STATUSKEY3,
					"graphics/icons/hullsys/phase_cloak.png",
					"EMP Discharge",
					"Flux-powered discharge: " + scalingStr,
					false
			);
		}
	}

	// ==================== UTILITY METHODS ====================

	private float getSpeedMult(ShipAPI ship, float effectLevel) {
		if (getDisruptionLevel(ship) <= 0f) return 1f;
		return MIN_SPEED_MULT + (1f - MIN_SPEED_MULT) * (1f - getDisruptionLevel(ship) * effectLevel);
	}

	private float getDisruptionLevel(ShipAPI ship) {
		if (!FLUX_LEVEL_AFFECTS_SPEED) return 0f;

		float threshold = ship.getMutableStats()
				.getDynamic()
				.getMod(Stats.PHASE_CLOAK_FLUX_LEVEL_FOR_MIN_SPEED_MOD)
				.computeEffective(BASE_FLUX_LEVEL_FOR_MIN_SPEED);

		if (threshold <= 0f) return 1f;

		float level = ship.getHardFluxLevel() / threshold;
		return Math.min(level, 1f);
	}

	private static float getMaxTimeMult(MutableShipStatsAPI stats) {
		return 1f + (MAX_TIME_MULT - 1f) * stats.getDynamic().getValue(Stats.PHASE_TIME_BONUS_MULT);
	}

	private float calculateFluxScaling(float fluxLevel) {
		fluxLevel = Math.max(0f, Math.min(fluxLevel, 1f));

		// Normalize flux level between scaling thresholds
		float t = (fluxLevel - FLUX_SCALING_START) / (FLUX_SCALING_END - FLUX_SCALING_START);
		t = Math.max(0f, Math.min(t, 1f));

		// Smoothstep interpolation for natural progression
		float easedT = t * t * (3f - 2f * t);

		return MIN_FLUX_SCALING + (MAX_FLUX_SCALING - MIN_FLUX_SCALING) * easedT;
	}
}