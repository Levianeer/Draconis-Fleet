package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.util.IntervalUtil;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import levianeer.draconis.data.campaign.intel.events.crisis.factors.DraconisFleetHostileActivityFactor;

/**
 * Ensures the mission-starting bar events (Fafnir TT Courier, Fafnir Ring-Port,
 * AIO Operative reveal, AIO payment negotiation) are created deterministically as soon
 * as their trigger condition is met, instead of waiting on BarEventManager's random,
 * capacity-limited sector-wide pick.
 * <p>
 * Waste Not's bar hook/report-back and the Stellar Mirror offer/payoff do NOT use this mechanism -
 * they're built on vanilla's
 * {@code AddBarEvents}/{@code AddBarEvent} rules.csv trigger instead (confirmed against the base
 * game's own rules.csv, e.g. the Luddic Path and League questline bar hooks), which evaluates
 * live every time the bar screen populates rather than needing a pre-registered Java event
 * instance at all. Simpler and more reliable than this class' own approach for a one-shot
 * narrative hook: it evaluates live on every bar visit, so it needs no pre-registered Java event,
 * is immune to the shown-events cache below, and has none of this class' tick latency. Prefer it
 * for new bar content - see the {@code # [WASTE NOT] Quest} and {@code # [STELLAR MIRROR] Quest}
 * sections of rules.csv.
 * <p>
 * Adds each event directly to {@link PortsideBarData}, mirroring vanilla's own approach
 * for guaranteed one-off bar events (see {@code PirateBaseIntel.addPlayerFleetToLocation}
 * calling {@code PortsideBarData.getInstance().addEvent(...)} directly). Once added, an
 * event is never removed (matches {@code BaseBarEvent.shouldRemoveEvent()} default) and
 * self-gates visibility via its own {@code shouldShowAtMarket()} check.
 * <p>
 * Creating each event once is necessary but <b>not sufficient</b> for it to actually appear - see
 * {@link #addEvent}, which also has to invalidate {@code BarCMD}'s shown-events cache. An event
 * added while that cache is live is silently never offered until it lapses, which is the failure
 * this watchdog exists to prevent in the first place.
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
            addEvent(data, new XLII_FafnirTTBarEvent());
        }

        if (XLII_FafnirRingPortBarEventCreator.isTriggerConditionMet() && !hasActive(data, XLII_FafnirRingPortBarEvent.class)) {
            addEvent(data, new XLII_FafnirRingPortBarEvent());
        }

        if (XLII_AIOOperativeBarEventCreator.isTriggerConditionMet() && !hasActive(data, XLII_AIOOperativeBarEvent.class)) {
            addEvent(data, new XLII_AIOOperativeBarEvent());
        }

        if (DraconisAIOTracker.get() != null && !DraconisFleetHostileActivityFactor.isCommissioned()
                && !hasActive(data, DraconisAIOPaymentBarEvent.class)) {
            addEvent(data, new DraconisAIOPaymentBarEvent());
        }
    }

    /**
     * Adds the event, then invalidates the shown-events cache anywhere it wants to appear.
     * <p>
     * {@code BarCMD} snapshots the ids it offered into {@code $BarCMD_shownEvents} on the market,
     * for 20-40 days ({@code BAR_EVENT_MIN/MAX_TIME_BEFORE_CHANGING}). While that snapshot exists
     * it is the <i>only</i> source of candidate events - {@code BarCMD} rebuilds its list purely by
     * matching saved ids against {@code PortsideBarData}, so an event added afterwards is never
     * even considered, regardless of {@code isAlwaysShow()} or {@code shouldShowAtMarket()}
     * (BarCMD.java:199-214). An event this watchdog adds therefore stays invisible until the
     * snapshot lapses, which defeats the entire point of adding it deterministically.
     * <p>
     * Only markets the new event actually wants to appear at are cleared, so this does not reshuffle
     * bar content sector-wide. Note this is narrower than {@code XLII_ClearBarSnapshots}, which
     * clears player-owned markets only and so never touches Ring-Port.
     */
    private static void addEvent(PortsideBarData data, PortsideBarEvent event) {
        data.addEvent(event);

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (event.shouldShowAtMarket(market)) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
    }

    private static boolean hasActive(PortsideBarData data, Class<? extends PortsideBarEvent> clazz) {
        for (PortsideBarEvent event : data.getEvents()) {
            if (clazz.isInstance(event)) return true;
        }
        return false;
    }
}
