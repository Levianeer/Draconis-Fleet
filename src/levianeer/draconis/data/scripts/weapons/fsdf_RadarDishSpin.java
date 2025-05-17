package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.*;

public class fsdf_RadarDishSpin implements EveryFrameWeaponEffectPlugin {

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || weapon.getShip() == null || !weapon.getShip().isAlive()) {
            return;
        }

        float angle = weapon.getCurrAngle();
        float turnRate = getEffectiveTurnRate(weapon);
        weapon.setCurrAngle((angle + turnRate) % 360f);
    }

    private float getEffectiveTurnRate(WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        StatBonus turnRateBonus = ship.getMutableStats().getWeaponTurnRateBonus();

        if (turnRateBonus.isUnmodified()) {
            return weapon.getTurnRate();
        }

        float baseTurnRate = weapon.getTurnRate();
        float flat = turnRateBonus.flatBonus;
        float mult = turnRateBonus.mult;
        float percent = turnRateBonus.percentMod;

        return (baseTurnRate - flat * mult) / ((1f + percent * mult / 100f) * mult);
    }
}