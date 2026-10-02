package levianeer.draconis.data.campaign.econ.conditions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import levianeer.draconis.data.campaign.characters.XLII_Characters;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Monitors all markets and dynamically applies/removes Steel Curtain condition
 * based on Draconis ownership
 * <p>
 * Also updates character placements based on market ownership
 */
public class DraconisSteelCurtainMonitor implements EveryFrameScript {

    private static final String CONDITION_ID = "draconis_steel_curtain";
    private static final float CHECK_INTERVAL = 1f;

    private float daysElapsed = 0f;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;

        // Only check periodically to reduce performance impact
        if (daysElapsed < CHECK_INTERVAL) return;

        daysElapsed = 0f;

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.isHidden()) continue;

            boolean isDraconisOwned = DRACONIS.equals(market.getFactionId());
            boolean hasCondition = market.hasCondition(CONDITION_ID);

            if (isDraconisOwned && !hasCondition) {
                market.addCondition(CONDITION_ID);

                Global.getLogger(this.getClass()).info(
                        "Added Steel Curtain to " + market.getName() +
                                " (Draconis-controlled)"
                );
            }
            else if (!isDraconisOwned && hasCondition) {
                market.removeCondition(CONDITION_ID);

                Global.getLogger(this.getClass()).info(
                        "Removed Steel Curtain from " + market.getName() +
                                " (no longer Draconis-controlled)"
                );
            }
        }

        XLII_Characters.updateCharacterPlacements();
    }
}