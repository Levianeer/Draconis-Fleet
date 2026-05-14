package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Creator for the AIO Operative reveal bar event.
 * Fires at Ring-Port after the assault when Vasner has not yet been met.
 */
public class XLII_AIOOperativeBarEventCreator extends BaseBarEventCreator {

    static final String MEM_RING_PORT_TAKEN = "$XLII_ringPortTaken";
    static final String MEM_OPERATIVE_REVEALED = "$XLII_aio_operative_revealed";

    @Override
    public float getBarEventFrequencyWeight() {
        if (!isTriggerConditionMet()) return 0f;
        return super.getBarEventFrequencyWeight();
    }

    @Override
    public PortsideBarEvent createBarEvent() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
        return new XLII_AIOOperativeBarEvent();
    }

    @Override
    public String getBarEventId() {
        return "XLII_aio_operative_reveal";
    }

    @Override
    public boolean isPriority() {
        return false;
    }

    @Override
    public float getBarEventTimeoutDuration() {
        return 10000000000f;
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 10000000000f;
    }

    static boolean isTriggerConditionMet() {
        var mem = Global.getSector().getMemoryWithoutUpdate();
        if (!mem.getBoolean(MEM_RING_PORT_TAKEN)) return false;
        if (mem.getBoolean(MEM_OPERATIVE_REVEALED)) return false;
        if (Global.getSector().getPlayerFaction().getRelationship(DRACONIS) < 0f) return false;
        return true;
    }
}
