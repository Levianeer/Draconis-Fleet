package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Fires one shot of the flare burst (from all SYSTEM slots) after a configurable delay.
 * Registered as a combat engine plugin so it fires independently of the ship system state —
 * the burst completes correctly even if the system has already wound down.
 */
public class XLII_DelayedFlareShot extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(XLII_DelayedFlareShot.class);

    // ==================== FLARE WEAPON CONSTANTS ====================
    // These mirror the corresponding fields in weapon_data.csv for XLII_flarelauncher.
    // Update here if the CSV values change.

    static final String WEAPON_ID = "XLII_flarelauncher";
    private static final String FIRE_SOUND_ID = "XLII_chaff_activate";

    /**
     * Half-spread of the flare fan in degrees.
     * Mirrors weapon_data.csv min spread / max spread (both 40°).
     */
    static final float FLARE_HALF_SPREAD = 20f;

    /**
     * Delay between consecutive shots in the burst, in seconds.
     * Mirrors weapon_data.csv burst delay (1 second).
     */
    public static final float BURST_DELAY = 0.5f;

    // ==================== INSTANCE STATE ====================

    private final ShipAPI ship;
    private final int shotIndex;
    private final int burstSize;
    private float timer;

    public XLII_DelayedFlareShot(ShipAPI ship, float delay, int shotIndex, int burstSize) {
        this.ship = ship;
        this.timer = delay;
        this.shotIndex = shotIndex;
        this.burstSize = burstSize;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        timer -= amount;
        if (timer <= 0f) {
            if (ship.isAlive() && !ship.isHulk()) {
                fire(engine);
            }
            engine.removePlugin(this);
        }
    }

    // ==================== STATIC HELPERS ====================

    /**
     * Returns the burst size declared by the flare launcher weapon spec.
     * Falls back to 1 if the spec is unavailable or not a projectile weapon.
     */
    public static int getBurstSize() {
        if (Global.getSettings().getWeaponSpec(WEAPON_ID) instanceof ProjectileWeaponSpecAPI spec) {
            int size = spec.getBurstSize();
            return size >= 1 ? size : 1;
        }
        return 1;
    }

    // ==================== PRIVATE ====================

    private void fire(CombatEngineAPI engine) {
        List<WeaponSlotAPI> systemSlots = new ArrayList<>();
        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.isSystemSlot()) systemSlots.add(slot);
        }

        if (systemSlots.isEmpty()) {
            log.warn("Draconis: Evasion Protocol - No SYSTEM weapon slots found on "
                    + ship.getHullSpec().getHullId());
            return;
        }

        // Fan shots evenly across [-FLARE_HALF_SPREAD, +FLARE_HALF_SPREAD]
        float spreadOffset = (burstSize == 1)
                ? 0f
                : -FLARE_HALF_SPREAD + (float) shotIndex / (burstSize - 1) * (FLARE_HALF_SPREAD * 2f);

        // One fake weapon shared across all slots — they all use the same spec
        WeaponAPI weapon = engine.createFakeWeapon(ship, WEAPON_ID);

        Global.getSoundPlayer().playSound(FIRE_SOUND_ID, 1f, 1f, ship.getLocation(), ship.getVelocity());

        for (WeaponSlotAPI slot : systemSlots) {
            Vector2f slotPos = slot.computePosition(ship);
            float slotAngle = slot.computeMidArcAngle(ship);

            engine.spawnProjectile(
                ship,
                weapon,
                WEAPON_ID,
                slotPos,
                slotAngle + spreadOffset,
                new Vector2f()
            );
        }

        log.debug("Draconis: Evasion Protocol - Burst shot " + (shotIndex + 1) + "/" + burstSize
                + " fired from " + ship.getHullSpec().getHullName());
    }
}