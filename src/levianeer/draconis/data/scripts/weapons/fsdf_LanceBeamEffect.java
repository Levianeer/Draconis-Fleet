package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class fsdf_LanceBeamEffect implements BeamEffectPlugin {	
    
    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine.isPaused()) return;
        if(beam.getBrightness() < 0.99f) return;
        float frameTime = engine.getElapsedInLastFrame();
        
        //Get the beam's target
        CombatEntityAPI target = beam.getDamageTarget();

        //If we have a target, target is a Ship, and shields are being hit.    
        if (target != null && target instanceof ShipAPI && target.getShield() != null && target.getShield().isWithinArc(beam.getTo())) {
            //Now that we have the target, get the weapon ID and get the DPS
            WeaponAPI weapon = beam.getWeapon();
            DamageType damType = weapon.getDamageType();
            float dps = weapon.getDerivedStats().getDps();

            engine.applyDamage(
                target, //enemy Ship
                beam.getTo(), //Our 2D vector to the exact world-position
                (dps * frameTime * 0.75f), //We're dividing the DPS by the time that's passed here.
                damType, //Using the damage type here.
                0f, //No EMP, as EMP already has specific rules.  However EMP could go through shields this way if we wanted it to.
                false, //Does not bypass shields.
                false, //Does not do Soft Flux damage (would kind've defeat the whole point, eh?
                beam.getSource()  //Who owns this beam?
             );
        }
    }
}