package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XLII_EvasionProtocolStats extends BaseShipSystemScript {

    private static final Logger log = Global.getLogger(XLII_EvasionProtocolStats.class);

    // ==================== TUNING PARAMETERS ====================

    private static final float VELOCITY = 200f;
    private static final float DELTA = 2000f;

    // Flare launcher weapon
    private static final String WEAPON_ID = "XLII_flarelauncher";

    // Track which ships have fired flares during current activation
    private static final HashMap<String, Boolean> firedThisActivation = new HashMap<>();

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        String shipId = ship.getId();

        // Apply stat modifiers
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);

            // Reset fired flag when system deactivates
            firedThisActivation.put(shipId, false);
        } else {
            // Apply movement bonuses
            stats.getMaxSpeed().modifyFlat(id, VELOCITY * effectLevel);
            stats.getAcceleration().modifyFlat(id, DELTA * effectLevel);
            stats.getDeceleration().modifyFlat(id, DELTA * effectLevel);

            // Fire flares once per activation when entering ACTIVE state
            if (state == ShipSystemStatsScript.State.ACTIVE && !engine.isPaused()) {
                boolean hasFired = firedThisActivation.getOrDefault(shipId, false);

                if (!hasFired) {
                    // Launch all flares from SYSTEM slots
                    launchFlaresFromAllSlots(ship, engine);

                    // Mark as fired for this activation
                    firedThisActivation.put(shipId, true);
                }
            }
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        return switch (index) {
            case 0 -> new StatusData("increased RCS power", false);
            case 1 -> new StatusData("deploying countermeasures", false);
            default -> null;
        };
    }

    // ==================== FLARE LAUNCH LOGIC ====================

    /**
     * Launches flares from all SYSTEM weapon slots simultaneously.
     */
    private void launchFlaresFromAllSlots(ShipAPI ship, CombatEngineAPI engine) {
        // Get all SYSTEM weapon slots
        List<WeaponSlotAPI> systemSlots = getSystemWeaponSlots(ship);

        if (systemSlots.isEmpty()) {
            log.warn("Draconis: Evasion Protocol - No SYSTEM weapon slots found on " + ship.getHullSpec().getHullId());
            return;
        }

        log.info("Draconis: Evasion Protocol - Firing " + systemSlots.size() + " flares from " + ship.getHullSpec().getHullName());

        // Fire all flares simultaneously
        for (WeaponSlotAPI slot : systemSlots) {
            launchFlareFromSlot(ship, slot, engine);
        }
    }

    /**
     * Launches a single flare from a weapon slot.
     * Weapon spec handles velocity, visual effects, and flux costs automatically.
     */
    private void launchFlareFromSlot(ShipAPI ship, WeaponSlotAPI slot, CombatEngineAPI engine) {
        Vector2f slotPos = slot.computePosition(ship);
        float slotAngle = slot.computeMidArcAngle(ship);

        // Create a fresh fake weapon for this flare
        WeaponAPI weapon = engine.createFakeWeapon(ship, WEAPON_ID);

        // Spawn the flare projectile - weapon spec handles everything else
        engine.spawnProjectile(
            ship,
            weapon,
            WEAPON_ID,
            slotPos,
            slotAngle,
            new Vector2f()
        );
    }

    /**
     * Gets all SYSTEM type weapon slots from the ship.
     */
    private List<WeaponSlotAPI> getSystemWeaponSlots(ShipAPI ship) {
        List<WeaponSlotAPI> systemSlots = new ArrayList<>();

        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.isSystemSlot()) {
                systemSlots.add(slot);
            }
        }

        return systemSlots;
    }
}