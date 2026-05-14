package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;

import java.util.HashMap;
import java.util.Map;

public class XLII_MissileGuidanceUplink extends BaseHullMod {

    public static final float MISSILE_HEALTH_BONUS = 50f;

    // Base multiplier for reload time - ensures reload is always slower than fire rate
    // Reload interval = BASE_MULTIPLIER / (rof × (1 + maxAmmo × AMMO_SCALING_FACTOR))
    private static final float BASE_MULTIPLIER = 3.5f;

    // Scaling factor - higher max ammo = faster individual missile reload
    // Lower values = more significant scaling with ammo count
    private static final float AMMO_SCALING_FACTOR = 0.005f;

    // Track reload timers for each large missile weapon per ship.
    // float[0] = current countdown, float[1] = cached interval (avoids re-computing RoF)
    private final Map<String, Map<WeaponAPI, float[]>> shipReloadTimers = new HashMap<>();

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Add 50% bonus to missile health
        stats.getMissileHealthBonus().modifyPercent(id, MISSILE_HEALTH_BONUS);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;

        String shipKey = ship.getId();
        if (!ship.isAlive()) {
            shipReloadTimers.remove(shipKey);
            return;
        }

        // Get or create reload timer map for this ship
        Map<WeaponAPI, float[]> reloadTimers = shipReloadTimers.computeIfAbsent(shipKey, k -> new HashMap<>());

        // Process each weapon on the ship
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            // Only process large missile weapons
            if (weapon.getSize() != WeaponSize.LARGE) {
                continue;
            }
            if (weapon.getType() != WeaponType.MISSILE) {
                continue;
            }

            // Skip if weapon has no ammo system or unlimited ammo
            if (weapon.getMaxAmmo() <= 0) {
                continue;
            }

            // Initialize reload timer for this weapon if not already done.
            // float[0] = current countdown, float[1] = cached reload interval
            if (!reloadTimers.containsKey(weapon)) {
                // Formula: BASE_MULTIPLIER / (rof × (1 + maxAmmo × AMMO_SCALING_FACTOR))
                // This ensures: more ammo = faster reload, but always slower than fire rate
                float rof = weapon.getSpec().getDerivedStats().getRoF();
                float maxAmmo = weapon.getMaxAmmo();
                float reloadInterval = BASE_MULTIPLIER / (rof * (1f + maxAmmo * AMMO_SCALING_FACTOR));
                reloadTimers.put(weapon, new float[]{ reloadInterval, reloadInterval });
            }

            // Current ammo count
            int currentAmmo = weapon.getAmmo();
            int maxAmmo = weapon.getMaxAmmo();

            // Only reload if not at max ammo
            if (currentAmmo < maxAmmo) {
                float[] timerData = reloadTimers.get(weapon);
                timerData[0] -= amount;

                if (timerData[0] <= 0f) {
                    // Reload one missile and reset to cached interval (no RoF recalculation)
                    weapon.setAmmo(currentAmmo + 1);
                    timerData[0] = timerData[1];
                }
                // timerData mutated in-place; no put() needed
            }
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) MISSILE_HEALTH_BONUS + "%";
        return null;
    }
}