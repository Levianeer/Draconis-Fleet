//Idk who made this script but thank you!
package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class fsdf_HardFluxBeamEffect implements BeamEffectPlugin
{
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        //Time per Frame
        float frameTime = engine.getElapsedInLastFrame();
        //Hard Flux to total Flux generation
        float hFluxRatio = 0.5f;
        //Gets the beam's target
        CombatEntityAPI target = beam.getDamageTarget();

        //Checks if a target exist, if it is a Ship, and if shields are being hit.
        if (target != null && target instanceof ShipAPI && target.getShield() != null && target.getShield().isWithinArc(beam.getTo()))
        {
            //Gets the beam's DPS
            WeaponAPI weapon = beam.getWeapon();
            float dps = weapon.getDerivedStats().getDps();

            //Gets the target ship's shield efficiency
            ShipAPI ship = (ShipAPI)target;
            float absorption = ship.getShield().getFluxPerPointOfDamage();

            //Modifies the target's flux stats removing part of the beam's soft flux replacing it with hard flux
            ship.getFluxTracker().decreaseFlux((dps*absorption*frameTime*(hFluxRatio)));
            ship.getFluxTracker().increaseFlux((dps*absorption*frameTime*(hFluxRatio)),true);
        }
    }
}