package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;

public class DraconisHostileActivityManager implements EveryFrameScript {

    private boolean added = false;

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
        if (!added) {
            HostileActivityEventIntel intel = HostileActivityEventIntel.get();

            if (intel != null) {
                DraconisFleetHostileActivityFactor existingFactor = (DraconisFleetHostileActivityFactor) intel.getFactorOfClass(DraconisFleetHostileActivityFactor.class);

                if (existingFactor == null) {
                    DraconisFleetHostileActivityFactor factor = new DraconisFleetHostileActivityFactor(intel);
                    DraconisFleetStandardActivityCause cause = new DraconisFleetStandardActivityCause(intel);
                    factor.addCause(cause);
                    intel.addFactor(factor);

                    Global.getLogger(this.getClass()).info("Draconis hostile activity factor registered");
                }
                added = true;
            }
        }
    }
}