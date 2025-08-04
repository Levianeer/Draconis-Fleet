//IDK who made this script but thank you!
package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class fsdf_HardFluxBeamEffect implements BeamEffectPlugin {

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {

        float frameTime = engine.getElapsedInLastFrame();
        float hardFluxRatio = 0.25f;    // Hard Flux to total Flux generation
        CombatEntityAPI target = beam.getDamageTarget();    // Gets the beam's target

        // Check if a target exist, if it is a Ship, and if shields are being hit.
        if (target instanceof ShipAPI ship && target.getShield() != null && target.getShield().isWithinArc(beam.getTo()))
        {

            WeaponAPI weapon = beam.getWeapon();    //Get beam's DPS
            float dps = weapon.getDerivedStats().getDps();

            // Get target's shield efficiency
            float absorption = ship.getShield().getFluxPerPointOfDamage();

            // Modifies the target's flux stats removing part of the beam's soft flux replacing it with hard flux
            ship.getFluxTracker().decreaseFlux((dps*absorption*frameTime*(hardFluxRatio)));
            ship.getFluxTracker().increaseFlux((dps*absorption*frameTime*(hardFluxRatio)),true);
        }
    }
}