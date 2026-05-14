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

    private static final float HULL_REPAIR_PERCENT = 0.4f;     // fraction of max HP repaired over full duration
    private static final float ACTIVE_DURATION = 10f;           // must match the .system active time
    private static final float SPEED_MULT = 0.75f;              // 25% speed penalty while active
    // ==================== INSTANCE STATE ====================

    private boolean activated = false;
    private boolean[][] validCells = null;   // cells eligible for armor redistribution
    private float[][] snapshotValues = null; // armor values at moment of activation
    private float snapshotMean = 0f;         // target each cell converges to
    private int validCellCount = 0;
    private float elapsed = 0f;

    // ==================== APPLY ====================

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        // Speed penalty throughout all active states (removed in unapply)
        stats.getMaxSpeed().modifyMult(id, SPEED_MULT);

        if (state == State.OUT) return; // no repairs during wind-down

        // Snapshot on first active frame
        if (!activated) {
            snapshotValidCells(ship.getArmorGrid());
            activated = true;
        }

        float dt = Global.getCombatEngine().getElapsedInLastFrame();
        elapsed += dt;

        // Hull repair - linear over ACTIVE_DURATION
        float repairThisFrame = (HULL_REPAIR_PERCENT / ACTIVE_DURATION) * ship.getMaxHitpoints() * dt;
        ship.setHitpoints(Math.min(ship.getHitpoints() + repairThisFrame, ship.getMaxHitpoints()));

        // Armor redistribution - lerp from snapshot values to mean, reaching it exactly at ACTIVE_DURATION
        if (validCells != null && validCellCount > 0) {
            redistributeArmor(ship.getArmorGrid());
        }
    }

    // ==================== UNAPPLY ====================

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        // Reset so the next activation takes a fresh snapshot
        activated = false;
        validCells = null;
        snapshotValues = null;
        snapshotMean = 0f;
        validCellCount = 0;
        elapsed = 0f;
    }

    // ==================== ARMOR HELPERS ====================

    /**
     * Builds the valid-cell mask using a reverse flood-fill from the grid border.
     *
     * Every zero-value cell on the perimeter is seeded as "exterior" and the BFS expands
     * 4-connectedly through adjacent zero-value cells. Anything reached is outside the ship's
     * silhouette or part of a destroyed section open to the edge. Anything NOT reached is a
     * valid interior cell — either an armor-bearing cell or an enclosed destroyed region.
     */
    private void snapshotValidCells(ArmorGridAPI armorGrid) {
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
        snapshotValues = new float[width][height];
        validCellCount = 0;
        float total = 0f;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!exterior[x][y]) {
                    validCells[x][y] = true;
                    snapshotValues[x][y] = armorGrid.getArmorValue(x, y);
                    total += snapshotValues[x][y];
                    validCellCount++;
                }
            }
        }

        snapshotMean = validCellCount > 0 ? total / validCellCount : 0f;
    }

    /**
     * Linearly interpolates each valid cell from its snapshot value to the snapshot mean.
     * At elapsed == ACTIVE_DURATION every cell lands exactly on the mean.
     */
    private void redistributeArmor(ArmorGridAPI armorGrid) {
        float progress = Math.min(elapsed / ACTIVE_DURATION, 1f);
        int width = validCells.length;
        int height = width > 0 ? validCells[0].length : 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!validCells[x][y]) continue;
                float value = snapshotValues[x][y] + (snapshotMean - snapshotValues[x][y]) * progress;
                armorGrid.setArmorValue(x, y, Math.max(0f, value));
            }
        }
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
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