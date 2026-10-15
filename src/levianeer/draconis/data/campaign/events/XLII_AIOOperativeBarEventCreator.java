package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/**
 * Creator for the AIO Operative reveal bar event.
 * <p>
 * Retired (author request): Ancker's reveal at Ring-Port duplicated his real introduction at the
 * AIO bastion ({@code XLII_ancker_escort_*} rows, rules.csv, reached via
 * {@code XLII_monroe_vetting_open_unmet}) - that escort scene is now the only canonical "reveal
 * beat", so {@link #isTriggerConditionMet} is permanently disabled below rather than this class
 * being deleted outright, which would break deserialization for any existing save that already
 * has an {@link XLII_AIOOperativeBarEvent} instance serialized into {@code PortsideBarData} (see
 * {@code XLII_MissionBarEventWatchdog}'s own doc comment on how these events get added). Ancker
 * has no market/comm-directory placement left at all now (see
 * {@code XLII_PersonDanielAncker}'s doc comment) - the AIO crisis payment deal
 * ({@code XLII_CreateAIOPaymentDeal}) only ever needed the reveal flag for bastion-escort text
 * branching, not this bar event or any comm-directory visibility.
 */
public class XLII_AIOOperativeBarEventCreator extends BaseBarEventCreator {

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

    static boolean isTriggerConditionMet() {
        return false;
    }
}
