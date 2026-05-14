package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisFleetHostileActivityFactor;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Creator for the DDA Intelligence Office payment bar event.
 * Uses direct instantiation (not getInstanceOfScript)
 * for non-mission BaseBarEvent subclasses (same approach as vanilla PlanetaryShieldBarEvent).
 * Registered programmatically in XLII_ModPlugin.onGameLoad().
 */
public class DraconisAIOPaymentBarEventCreator extends BaseBarEventCreator {

    @Override
    public float getBarEventFrequencyWeight() {
        if (DraconisAIOTracker.get() == null) return 0f;
        if (DraconisFleetHostileActivityFactor.isCommissioned()) return 0f;
        return super.getBarEventFrequencyWeight();
    }

    @Override
    public PortsideBarEvent createBarEvent() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
        return new DraconisAIOPaymentBarEvent();
    }

    @Override
    public String getBarEventId() {
        return "XLII_dda_aio_payment";
    }

    @Override
    public boolean isPriority() {
        return true;
    }

    @Override
    public float getBarEventTimeoutDuration() {
        return 10000000000f;
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 10000000000f;
    }
}