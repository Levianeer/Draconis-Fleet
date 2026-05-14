package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.XLII_TransverseJumpScript;
import levianeer.draconis.data.scripts.XLII_WarpInScript;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

public class XLII_DraconisHull extends BaseHullMod {

    // Stats
    public static final float DEGRADE_INCREASE_PERCENT = 25f;
    private static final float CR_LOSS_ON_WARP = 1f;

    private static final Map<HullSize, Float> CHARGE_TIMES = new EnumMap<>(HullSize.class);
    static {
        CHARGE_TIMES.put(HullSize.FRIGATE, 5f);
        CHARGE_TIMES.put(HullSize.DESTROYER, 6f);
        CHARGE_TIMES.put(HullSize.CRUISER, 7f);
        CHARGE_TIMES.put(HullSize.CAPITAL_SHIP, 9f);
    }

    private static final String MOD_ID = "draconisHull";

    public static float getChargeTime(HullSize hullSize) {
        return CHARGE_TIMES.getOrDefault(hullSize, 8f);
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
    }

    public static class WarpDriveScript extends XLII_TransverseJumpScript {

        private final IntervalUtil overloadArcInterval = new IntervalUtil(0.075f, 0.125f);
        private int arcCounter = 0;

        public WarpDriveScript(ShipAPI ship) {
            super(ship, getChargeTime(ship.getHullSize()), MOD_ID + "_" + ship.getId());
        }

        @Override
        protected boolean checkShouldActivate() {
            return ship.isDirectRetreat();
        }

        @Override
        protected void onWarpComplete() {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null) {
                CombatFleetManagerAPI fleetManager = engine.getFleetManager(ship.getOwner());
                if (fleetManager != null) {
                    fleetManager.removeDeployed(ship, true);
                }
            }
        }

        @Override
        protected void onAdvance(float amount) {
            boolean isOverloadedOrVenting = ship.getFluxTracker().isOverloaded() || ship.getFluxTracker().isVenting();
            if (isOverloadedOrVenting) {
                overloadArcInterval.advance(amount * 0.125f);
                if (overloadArcInterval.intervalElapsed()) {
                    spawnOverloadArc();
                }
            }
        }

        private void spawnOverloadArc() {
            CombatEngineAPI engine = Global.getCombatEngine();
            Vector2f shipLoc = ship.getLocation();

            float maxOffset = 150f;

            float sizeMultiplier = 0.5f;
            HullSize hullSize = ship.getHullSize();
            if (hullSize != null) {
                sizeMultiplier = switch (hullSize) {
                    case FRIGATE -> 0.33f;
                    case DESTROYER -> 0.44f;
                    case CRUISER -> 0.66f;
                    case CAPITAL_SHIP -> 1.0f;
                    default -> sizeMultiplier;
                };
            }

            EmpArcEntityAPI.EmpArcParams params = new EmpArcEntityAPI.EmpArcParams();
            params.segmentLengthMult = 4f;
            params.glowSizeMult = (5f + ship.getFluxLevel() * 2f) * sizeMultiplier;
            params.flickerRateMult = 0.5f + (float) Math.random() * 0.5f;

            boolean useBlue = (arcCounter % 2 == 0);
            Color fringeColor = useBlue ? BLUE_FRINGE_COLOR : EMP_FRINGE_COLOR;
            Color coreColor = useBlue ? BLUE_CORE_COLOR : EMP_CORE_COLOR;
            arcCounter++;

            float thickness = 40f;
            float coreThickness = 20f;

            float r = maxOffset * (0.5f + 0.5f * (float) Math.random());
            Vector2f from = Misc.getPointAtRadius(shipLoc, r);

            float angle = Misc.getAngleInDegrees(from, shipLoc);
            angle = angle + 90f * ((float) Math.random() - 0.5f);
            Vector2f dir = Misc.getUnitVectorAtDegreeAngle(angle);

            float dist = maxOffset * (0.5f + 0.5f * (float) Math.random()) * 1.5f;
            dir.scale(dist);
            Vector2f to = Vector2f.add(from, dir, new Vector2f());

            EmpArcEntityAPI arc = engine.spawnEmpArcVisual(
                    from, ship, to, ship,
                    thickness,
                    fringeColor,
                    coreColor,
                    params
            );

            arc.setCoreWidthOverride(coreThickness);

            Global.getSoundPlayer().playSound(EMP_IMPACT_ID, 1f, 1f, to, ship.getVelocity());
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship.getListeners(WarpDriveScript.class).isEmpty()) {
            ship.addListener(new WarpDriveScript(ship));
        }
        if (ship.getHullSize() == HullSize.CAPITAL_SHIP
                && ship.getListeners(XLII_WarpInScript.class).isEmpty()) {
            ship.addListener(new XLII_WarpInScript(ship));
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return switch (index) {
            case 0 -> CHARGE_TIMES.get(HullSize.FRIGATE).intValue() + "s";
            case 1 -> CHARGE_TIMES.get(HullSize.DESTROYER).intValue() + "s";
            case 2 -> CHARGE_TIMES.get(HullSize.CRUISER).intValue() + "s";
            case 3 -> CHARGE_TIMES.get(HullSize.CAPITAL_SHIP).intValue() + "s";
            case 4 -> (int) CR_LOSS_ON_WARP + Strings.X;
            default -> null;
        };
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Draconis-built hulls have a number of shared properties.", opad);

        tooltip.addSectionHeading("Transverse Jump", Alignment.MID, opad);
        tooltip.addPara("When Draconis ships receive %s orders, they will initiate an emergency Transverse Jump. This takes %s/%s/%s/%s to fully charge, depending on hull size.",
                opad, h,
                "Direct Retreat",
                CHARGE_TIMES.get(HullSize.FRIGATE).intValue() + "s",
                CHARGE_TIMES.get(HullSize.DESTROYER).intValue() + "s",
                CHARGE_TIMES.get(HullSize.CRUISER).intValue() + "s",
                CHARGE_TIMES.get(HullSize.CAPITAL_SHIP).intValue() + "s");

        tooltip.addPara("Ship counts as retreated upon completion and emerges post-combat, suffering CR penalty equal to %s of deployment cost. Once started, this cannot be aborted.",
                opad, h,
                (int) CR_LOSS_ON_WARP + Strings.X);

        tooltip.addSectionHeading("Maintenance", new Color(255,100,0) , new Color(105,40,0,175) , Alignment.MID, opad);
        tooltip.addPara("Draconis-built hulls require excessive maintenance and do not stand up well under the rigours of prolonged engagements.", opad);

        tooltip.addPara("Increases the rate of in-combat CR decay after peak performance time runs out by %s.",
                opad, h,
                (int) DEGRADE_INCREASE_PERCENT + "%");
    }

    @Override
    public int getDisplaySortOrder() {
        return 0;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}
