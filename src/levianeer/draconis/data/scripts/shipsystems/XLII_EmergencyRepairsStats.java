package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.util.ArrayDeque;
import java.util.Deque;

public class XLII_EmergencyRepairsStats extends BaseShipSystemScript {

    // ==================== TUNING PARAMETERS ====================

    private static final float HULL_REPAIR_PERCENT = 0.4f;   // fraction of max HP repaired over the active window
    private static final float SPEED_MULT = 0.75f;
    // Only a fallback - the real duration is read live from the ship's own system spec
    // (getChargeActiveDur()) every activation, so it can never drift out of sync with
    // XLII_emergency_repairs.system's "active" column the way a hardcoded constant did before.
    private static final float ACTIVE_DURATION_FALLBACK = 9f;

    // ==================== INSTANCE STATE ====================
    // (one instance per ship - unlike BaseHullMod, ship system scripts are not shared, per the
    // rest of this codebase's shipsystems/ scripts, which all use plain instance fields the same way)

    private boolean validCellsComputed = false;
    private boolean[][] validCells = null;   // cells eligible for armor redistribution
    private int validCellCount = 0;

    private boolean wasEngaged = false;      // true while the previous frame was IN or ACTIVE
    private float phaseTimer = 0f;
    private float lerpDuration = ACTIVE_DURATION_FALLBACK;
    private float[][] snapshotValues = null; // per-cell armor at the start of this activation
    private float targetMean = 0f;           // value every cell converges to by lerpDuration

    // ==================== APPLY ====================

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        // Computed once, before the very first activation (at full armor, so the border
        // flood-fill can't mistake real destroyed cells for exterior padding) - same reasoning
        // as XLII_ReclamationPlating's valid-cell cache.
        if (!validCellsComputed) {
            computeValidCells(ship.getArmorGrid());
            validCellsComputed = true;
        }

        // Speed penalty throughout all active states (removed in unapply)
        stats.getMaxSpeed().modifyMult(id, SPEED_MULT);

        // BaseShipSystemScript.State has five values - IN, ACTIVE, OUT, COOLDOWN, IDLE - and
        // apply() is called every frame regardless of which one is current. The previous version
        // only excluded OUT, so repair and armor redistribution kept running (with elapsed already
        // past ACTIVE_DURATION) through the entire COOLDOWN and IDLE stretch after every use -
        // armor was pinned to the activation-time mean and hull kept healing for the rest of the
        // battle, not just the intended ~9s burst. Both non-engaged states must be excluded here.
        boolean engaged = (state == State.IN || state == State.ACTIVE);
        if (!engaged) {
            wasEngaged = false;
            return;
        }

        if (!wasEngaged) {
            // Fresh activation - snapshot current (possibly already damaged) armor and start a
            // new redistribution cycle toward its current mean. Starting fresh every use (rather
            // than only once, like the old `activated` flag that was never reset) is what makes a
            // second or third use actually respond to damage taken since the last one.
            startCycle(ship);
            wasEngaged = true;
        }

        float dt = Global.getCombatEngine().getElapsedInLastFrame();

        float repairThisFrame = (HULL_REPAIR_PERCENT / lerpDuration) * ship.getMaxHitpoints() * dt;
        ship.setHitpoints(Math.min(ship.getHitpoints() + repairThisFrame, ship.getMaxHitpoints()));

        if (validCellCount > 0) {
            phaseTimer += dt;
            float progress = Math.min(phaseTimer / lerpDuration, 1f);
            applyLerp(ship.getArmorGrid(), progress);
        }
    }

    private void startCycle(ShipAPI ship) {
        phaseTimer = 0f;
        lerpDuration = ACTIVE_DURATION_FALLBACK;
        ShipSystemAPI system = ship.getSystem();
        if (system != null && system.getChargeActiveDur() > 0f) {
            lerpDuration = system.getChargeActiveDur();
        }

        if (validCellCount > 0) {
            ArmorGridAPI grid = ship.getArmorGrid();
            snapshotValues = snapshotValues(grid);
            targetMean = sumArmor(grid) / validCellCount;
        }
    }

    // ==================== UNAPPLY ====================

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        wasEngaged = false;
    }

    // ==================== ARMOR HELPERS ====================

    /**
     * Builds the valid-cell mask using a reverse flood-fill from the grid border.
     *
     * Every zero-value cell on the perimeter is seeded as "exterior" and the BFS expands
     * 4-connectedly through adjacent zero-value cells. Anything reached is outside the ship's
     * silhouette or part of a destroyed section open to the edge. Anything NOT reached is a
     * valid interior cell - either an armor-bearing cell or an enclosed destroyed region.
     */
    private void computeValidCells(ArmorGridAPI armorGrid) {
        float[][] grid = armorGrid.getGrid();
        int width = grid.length;
        int height = width > 0 ? grid[0].length : 0;

        boolean[][] exterior = new boolean[width][height];
        Deque<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if ((x == 0 || x == width - 1 || y == 0 || y == height - 1) && grid[x][y] == 0f) {
                    exterior[x][y] = true;
                    queue.add(new int[]{x, y});
                }
            }
        }

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height
                        && !exterior[nx][ny] && grid[nx][ny] == 0f) {
                    exterior[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        validCells = new boolean[width][height];
        validCellCount = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!exterior[x][y]) {
                    validCells[x][y] = true;
                    validCellCount++;
                }
            }
        }
    }

    private float sumArmor(ArmorGridAPI grid) {
        float total = 0f;
        for (int x = 0; x < validCells.length; x++) {
            for (int y = 0; y < validCells[0].length; y++) {
                if (validCells[x][y]) total += grid.getArmorValue(x, y);
            }
        }
        return total;
    }

    private float[][] snapshotValues(ArmorGridAPI grid) {
        float[][] values = new float[validCells.length][validCells[0].length];
        for (int x = 0; x < validCells.length; x++) {
            for (int y = 0; y < validCells[0].length; y++) {
                if (validCells[x][y]) values[x][y] = grid.getArmorValue(x, y);
            }
        }
        return values;
    }

    /** Linearly interpolates each valid cell from its snapshot value to the target mean. */
    private void applyLerp(ArmorGridAPI grid, float progress) {
        for (int x = 0; x < validCells.length; x++) {
            for (int y = 0; y < validCells[0].length; y++) {
                if (!validCells[x][y]) continue;
                float value = snapshotValues[x][y] + (targetMean - snapshotValues[x][y]) * progress;
                grid.setArmorValue(x, y, Math.max(0f, value));
            }
        }
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        // isUsable() is polled every frame from deployment onward regardless of whether the
        // system has ever been activated (the HUD needs to know in real time whether to grey the
        // icon out), unlike apply(), which only starts running once the system is actually
        // engaged. Snapshotting the valid-cell mask here instead catches the ship while armor is
        // still pristine, rather than risking the player's first activation happening after a
        // section has already been fully ground down - which the border flood-fill can't tell
        // apart from genuine exterior padding once that's happened.
        if (!validCellsComputed && ship != null) {
            computeValidCells(ship.getArmorGrid());
            validCellsComputed = true;
        }
        return ship.getHitpoints() < ship.getMaxHitpoints();
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.IN || state == State.ACTIVE) {
            if (index == 0) return new StatusData("hull repair active", false);
            if (index == 1) return new StatusData("armor redistributing", false);
            if (index == 2) return new StatusData("slowing for repairs", true);
        }
        return null;
    }
}
