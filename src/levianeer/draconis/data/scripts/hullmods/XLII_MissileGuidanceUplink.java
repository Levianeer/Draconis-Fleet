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

    // Track reload timers for each large missile weapon per ship
    private final Map<String, Map<WeaponAPI, Float>> shipReloadTimers = new HashMap<>();

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Add 50% bonus to missile health
        stats.getMissileHealthBonus().modifyPercent(id, MISSILE_HEALTH_BONUS);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) {
            return;
        }

        // Get or create reload timer map for this ship
        String shipKey = ship.getId();
        if (!shipReloadTimers.containsKey(shipKey)) {
            shipReloadTimers.put(shipKey, new HashMap<WeaponAPI, Float>());
        }
        Map<WeaponAPI, Float> reloadTimers = shipReloadTimers.get(shipKey);

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

            // Initialize reload timer for this weapon if not already done
            if (!reloadTimers.containsKey(weapon)) {
                // Calculate reload interval based on weapon RoF and max ammo
                // Formula: BASE_MULTIPLIER / (rof × (1 + maxAmmo × AMMO_SCALING_FACTOR))
                // This ensures: more ammo = faster reload, but always slower than fire rate
                float rof = weapon.getSpec().getDerivedStats().getRoF();
                float maxAmmo = weapon.getMaxAmmo();
                float reloadInterval = BASE_MULTIPLIER / (rof * (1f + maxAmmo * AMMO_SCALING_FACTOR));

                reloadTimers.put(weapon, reloadInterval);
            }

            // Current ammo count
            int currentAmmo = weapon.getAmmo();
            int maxAmmo = weapon.getMaxAmmo();

            // Only reload if not at max ammo
            if (currentAmmo < maxAmmo) {
                // Decrement timer
                float timer = reloadTimers.get(weapon);
                timer -= amount;

                if (timer <= 0f) {
                    // Reload one missile
                    weapon.setAmmo(currentAmmo + 1);

                    // Reset timer - recalculate based on weapon RoF and ammo
                    float rof = weapon.getSpec().getDerivedStats().getRoF();
                    float reloadInterval = BASE_MULTIPLIER / (rof * (1f + maxAmmo * AMMO_SCALING_FACTOR));
                    reloadTimers.put(weapon, reloadInterval);
                } else {
                    // Update timer
                    reloadTimers.put(weapon, timer);
                }
            }
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) MISSILE_HEALTH_BONUS + "%";
        return null;
    }
}