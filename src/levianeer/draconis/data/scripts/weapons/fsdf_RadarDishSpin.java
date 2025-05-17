package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.WeaponAPI;

public class fsdf_RadarDishSpin implements EveryFrameWeaponEffectPlugin {
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (!engine.isPaused()) {
            ShipAPI ship = weapon.getShip();
            if (ship.isAlive()) {
                float angle = weapon.getCurrAngle();
                ShipAPI _ship = weapon.getShip();
                StatBonus sb = _ship.getMutableStats().getWeaponTurnRateBonus();
                if (sb.isUnmodified()) {
                    angle += weapon.getTurnRate();
                } else {
                    float result = weapon.getTurnRate();
                    float flatBonus = sb.flatBonus;
                    float mult = sb.mult;
                    float percentMod = sb.percentMod;
                    float baseValue = (result - flatBonus * mult) / ((1F + percentMod * mult / 100F) * mult);
                    angle += baseValue;
                }

                angle %= 360F;
                weapon.setCurrAngle(angle);
            }
        }
    }
}