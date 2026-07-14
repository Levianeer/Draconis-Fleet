package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.util.IntervalUtil;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisFleetHostileActivityFactor;

/**
 * Ensures the four mission-starting bar events (Fafnir TT Courier, Fafnir Ring-Port,
 * AIO Operative reveal, AIO payment negotiation) are created deterministically as soon
 * as their trigger condition is met, instead of waiting on BarEventManager's random,
 * capacity-limited sector-wide pick.
 * <p>
 * Adds each event directly to {@link PortsideBarData}, mirroring vanilla's own approach
 * for guaranteed one-off bar events (see {@code PirateBaseIntel.addPlayerFleetToLocation}
 * calling {@code PortsideBarData.getInstance().addEvent(...)} directly). Once added, an
 * event is never removed (matches {@code BaseBarEvent.shouldRemoveEvent()} default) and
 * self-gates visibility via its own {@code shouldShowAtMarket()} check, so this watchdog
 * only needs to guarantee each event is created at most once.
 */
public class XLII_MissionBarEventWatchdog implements EveryFrameScript {

    private final IntervalUtil checkInterval = new IntervalUtil(10f, 10f);

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
        checkInterval.advance(amount);
        if (!checkInterval.intervalElapsed()) return;

        PortsideBarData data = PortsideBarData.getInstance();
        if (data == null) return;

        if (XLII_FafnirTTBarEventCreator.isTriggerConditionMet() && !hasActive(data, XLII_FafnirTTBarEvent.class)) {
            data.addEvent(new XLII_FafnirTTBarEvent());
        }

        if (XLII_FafnirRingPortBarEventCreator.isTriggerConditionMet() && !hasActive(data, XLII_FafnirRingPortBarEvent.class)) {
            data.addEvent(new XLII_FafnirRingPortBarEvent());
        }

        if (XLII_AIOOperativeBarEventCreator.isTriggerConditionMet() && !hasActive(data, XLII_AIOOperativeBarEvent.class)) {
            data.addEvent(new XLII_AIOOperativeBarEvent());
        }

        if (DraconisAIOTracker.get() != null && !DraconisFleetHostileActivityFactor.isCommissioned()
                && !hasActive(data, DraconisAIOPaymentBarEvent.class)) {
            data.addEvent(new DraconisAIOPaymentBarEvent());
        }
    }

    private static boolean hasActive(PortsideBarData data, Class<? extends PortsideBarEvent> clazz) {
        for (PortsideBarEvent event : data.getEvents()) {
            if (clazz.isInstance(event)) return true;
        }
        return false;
    }
}
