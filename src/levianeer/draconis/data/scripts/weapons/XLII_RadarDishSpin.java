package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.*;

public class XLII_RadarDishSpin implements EveryFrameWeaponEffectPlugin {

    private float baseTurnRate = -1f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || weapon.getShip() == null || !weapon.getShip().isAlive()) {
            return;
        }

        // Cache the spec's base turn rate once to avoid stat modifier interference.
        // This is a decorative spinning radar dish - it should spin at a constant rate.
        if (baseTurnRate < 0f) {
            baseTurnRate = weapon.getSpec().getTurnRate();
        }

        float angle = weapon.getCurrAngle();
        weapon.setCurrAngle((angle + baseTurnRate) % 360f);
    }
}