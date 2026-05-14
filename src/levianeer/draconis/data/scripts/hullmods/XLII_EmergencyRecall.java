package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * When a fighter would take lethal hull damage, the damage is negated,
 * the fighter is fully repaired, and it phases out before landing back in its carrier bay. The effect resets
 * after the fighter dies for real (not after a recall). If the carrier is dead, the recall does not activate.
 */
public class XLII_EmergencyRecall extends BaseHullMod {

    private static final Color JITTER_COLOR = new Color(100, 165, 255, 155);
    private static final float PHASE_DURATION = 0.5f;

    // ==================== PER-COMBAT STATE ====================

    /** Tracks which ship IDs already have a listener managed by this hullmod. */
    private static final Map<String, RecallListener> listeners = new HashMap<>();

    /**
     * Wing slot keys that used recall last life. The next fighter deployed
     * in that slot will NOT get a recall; the key is consumed on that deploy.
     * After that fighter dies for real, the slot is clean and the next spawn
     * gets recall again.
     */
    private static final Set<String> usedRecallSlots = new HashSet<>();

    private static CombatEngineAPI lastEngine;

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine) {
            lastEngine = engine;
            listeners.clear();
            usedRecallSlots.clear();
        }
    }

    /**
     * Returns a key that identifies this fighter's wing slot, stable across
     * respawns (different ShipAPI instances in the same slot get the same key).
     */
    private static String getSlotKey(ShipAPI fighter) {
        FighterWingAPI wing = fighter.getWing();
        if (wing == null) return null;
        int index = wing.getWingMembers().indexOf(fighter);
        if (index < 0) return null;
        return System.identityHashCode(wing) + "_" + index;
    }

    // ==================== COMBAT LOOP ====================

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        if (ship == null || !ship.isAlive()) return;

        checkClearState();

        String shipKey = ship.getId();
        if (listeners.containsKey(shipKey)) return; // already handled this instance

        // New fighter instance — check if this slot used recall last life
        String slotKey = getSlotKey(ship);
        if (slotKey != null && usedRecallSlots.remove(slotKey)) {
            // Redeployed after recall — no recall this life, store sentinel
            listeners.put(shipKey, new RecallListener(ship, true));
        } else {
            // Fresh life — recall available
            RecallListener listener = new RecallListener(ship, false);
            ship.addListener(listener);
            listeners.put(shipKey, listener);
        }
    }

    // ==================== DAMAGE LISTENER ====================

    public static class RecallListener implements HullDamageAboutToBeTakenListener {

        private final ShipAPI ship;
        private final boolean used;

        public RecallListener(ShipAPI ship, boolean preUsed) {
            this.ship = ship;
            this.used = preUsed;
        }

        public boolean isUsed() {
            return used;
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            if (used || ship != this.ship || ship.isHulk()) return false;

            // Not lethal — let it through
            if (ship.getHitpoints() - damageAmount > 0f) return false;

            // No carrier to recall to — don't activate
            if (ship.getWing() == null || ship.getWing().getSource() == null) return false;
            ShipAPI carrier = ship.getWing().getSourceShip();
            if (carrier == null || !carrier.isAlive()) return false;

            // Mark this wing slot so the next deploy skips recall
            String slotKey = getSlotKey(ship);
            if (slotKey != null) {
                usedRecallSlots.add(slotKey);
            }

            // Restore hull
            ship.setHitpoints(ship.getMaxHitpoints());

            // Restore all armor cells
            ArmorGridAPI armor = ship.getArmorGrid();
            float maxCell = armor.getMaxArmorInCell();
            float[][] grid = armor.getGrid();
            for (int x = 0; x < grid.length; x++) {
                for (int y = 0; y < grid[x].length; y++) {
                    armor.setArmorValue(x, y, maxCell);
                }
            }

            // Sound
            Global.getSoundPlayer().playSound(
                    "system_phase_skimmer", 1f, 0.5f,
                    ship.getLocation(), ship.getVelocity()
            );

            // Phase out and land via FX plugin
            ship.setPhased(true);
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null) {
                engine.addPlugin(new RecallFXPlugin(ship));
            }

            return true; // negate the lethal damage
        }
    }

    // ==================== PHASE-OUT FX ====================

    /**
     * Brief phase-out effect: jitter + fade over PHASE_DURATION, then land
     * the fighter back in its carrier bay.
     */
    public static class RecallFXPlugin extends BaseEveryFrameCombatPlugin {

        private final ShipAPI ship;
        private float elapsed = 0f;

        public RecallFXPlugin(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            elapsed += amount;
            float progress = Math.min(elapsed / PHASE_DURATION, 1f);

            // Jitter: builds with progress
            float jitterLevel = progress;
            float jitterRangeBonus = 5f + jitterLevel * ship.getCollisionRadius();
            ship.setJitter(this, JITTER_COLOR, jitterLevel, 10, 0f, jitterRangeBonus);

            // Fade out
            float alpha = 1f - progress * 0.5f;
            ship.setExtraAlphaMult(alpha);

            if (progress >= 1f) {
                // Land the fighter
                if (ship.getWing() != null && ship.getWing().getSource() != null) {
                    ship.getWing().getSource().makeCurrentIntervalFast();
                    ship.getWing().getSource().land(ship);
                } else {
                    // Carrier was destroyed during phase-out — restore visibility
                    ship.setExtraAlphaMult(1f);
                    ship.setPhased(false);
                }

                engine.removePlugin(this);
            }
        }
    }
}