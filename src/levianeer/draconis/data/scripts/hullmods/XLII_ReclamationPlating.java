package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class XLII_ReclamationPlating extends BaseHullMod {

    private static final float DECREASE_EPSILON = 0.5f; // minimum drop to count as "took armor damage", not float noise
    private static final float LERP_DURATION = 8f;       // seconds to redistribute armor once triggered
    private static final float COOLDOWN_DURATION = 20f;  // seconds of downtime after a redistribution finishes

    private enum Phase { IDLE, LERPING, COOLDOWN }

    private static CombatEngineAPI lastEngine_ReclamationPlating;
    private static final Map<ShipAPI, PlatingState> states = new HashMap<>();

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_ReclamationPlating) {
            lastEngine_ReclamationPlating = engine;
            states.clear();
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        checkClearState();

        if (ship == null || !ship.isAlive()) {
            states.remove(ship);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        ArmorGridAPI grid = ship.getArmorGrid();
        if (grid == null) return;

        PlatingState state = states.get(ship);
        if (state == null) {
            state = new PlatingState();
            computeValidCells(state, grid);
            states.put(ship, state);
        }

        if (state.validCellCount == 0) return;

        switch (state.phase) {
            case IDLE: {
                float total = sumArmor(grid, state);
                if (state.lastTotal < 0f) {
                    // First measurement ever for this ship - just establish the baseline.
                    state.lastTotal = total;
                } else if (total < state.lastTotal - DECREASE_EPSILON) {
                    // Snapshot current per-cell values and the mean they're converging toward,
                    // then let LERPING play that out - further hits during the lerp or the
                    // cooldown that follows are not smoothed until the next trigger.
                    state.snapshotValues = snapshotValues(grid, state);
                    state.targetMean = total / state.validCellCount;
                    state.phaseTimer = 0f;
                    state.phase = Phase.LERPING;
                }
                // Deliberately NOT updating lastTotal here when nothing triggers - the baseline
                // only resets on entering IDLE (above/COOLDOWN exit). A slow, continuous decline
                // (DOT weapons, sustained beams) that never drops more than DECREASE_EPSILON in a
                // single frame still accumulates against this same baseline across many frames,
                // instead of being invisibly re-absorbed into a frame-to-frame comparison that
                // never individually crosses the threshold - the bug that let a section get fully
                // ground down by gradual fire without ever triggering a patch.
                break;
            }
            case LERPING: {
                state.phaseTimer += amount;
                float progress = Math.min(state.phaseTimer / LERP_DURATION, 1f);
                applyLerp(grid, state, progress);
                if (progress >= 1f) {
                    state.phase = Phase.COOLDOWN;
                    state.phaseTimer = 0f;
                }
                break;
            }
            case COOLDOWN: {
                state.phaseTimer += amount;
                if (state.phaseTimer >= COOLDOWN_DURATION) {
                    state.phase = Phase.IDLE;
                    state.lastTotal = sumArmor(grid, state); // rebaseline fresh, don't trigger off old history
                }
                break;
            }
        }
    }

    /**
     * Builds the valid-cell mask using a reverse flood-fill from the grid border, same approach
     * as identifying a ship's silhouette from its (rectangular, zero-padded) armor grid: every
     * zero-value cell reachable from the border is exterior padding, not real armor. Computed once
     * per ship, on the first combat frame it's seen - armor is always full at the start of a fresh
     * deployment, so no interior cell reads zero yet and the flood-fill can't misidentify real,
     * fully-destroyed armor as exterior.
     */
    private void computeValidCells(PlatingState state, ArmorGridAPI armorGrid) {
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

        state.validCells = new boolean[width][height];
        state.validCellCount = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!exterior[x][y]) {
                    state.validCells[x][y] = true;
                    state.validCellCount++;
                }
            }
        }
    }

    private float sumArmor(ArmorGridAPI grid, PlatingState state) {
        float total = 0f;
        boolean[][] valid = state.validCells;
        for (int x = 0; x < valid.length; x++) {
            for (int y = 0; y < valid[0].length; y++) {
                if (valid[x][y]) total += grid.getArmorValue(x, y);
            }
        }
        return total;
    }

    private float[][] snapshotValues(ArmorGridAPI grid, PlatingState state) {
        boolean[][] valid = state.validCells;
        float[][] values = new float[valid.length][valid[0].length];
        for (int x = 0; x < valid.length; x++) {
            for (int y = 0; y < valid[0].length; y++) {
                if (valid[x][y]) values[x][y] = grid.getArmorValue(x, y);
            }
        }
        return values;
    }

    /** Linearly interpolates each valid cell from its snapshot value to the target mean. */
    private void applyLerp(ArmorGridAPI grid, PlatingState state, float progress) {
        boolean[][] valid = state.validCells;
        float[][] snapshot = state.snapshotValues;
        for (int x = 0; x < valid.length; x++) {
            for (int y = 0; y < valid[0].length; y++) {
                if (!valid[x][y]) continue;
                float value = snapshot[x][y] + (state.targetMean - snapshot[x][y]) * progress;
                grid.setArmorValue(x, y, Math.max(0f, value));
            }
        }
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;

        tooltip.addPara(
                "Self-reorganizing plating, built from a swarm of reclamation fabricators too small to see " +
                        "individually. The first time the ship takes armor damage, remaining armor is gradually " +
                        "redistributed evenly across the hull over %s - there is no hole, only less armor everywhere.",
                opad, Misc.getHighlightColor(), Math.round(LERP_DURATION) + "s");

        tooltip.addPara(
                "This does not restore lost armor, only spreads what remains. The swarm then needs %s to " +
                        "recover before it can do this again - further damage in that time is not smoothed.",
                opad, Misc.getHighlightColor(), Math.round(COOLDOWN_DURATION) + "s");
    }

    static class PlatingState {
        boolean[][] validCells;
        int validCellCount = 0;
        float lastTotal = -1f; // sentinel: not yet measured

        Phase phase = Phase.IDLE;
        float phaseTimer = 0f;
        float[][] snapshotValues;
        float targetMean;
    }
}
