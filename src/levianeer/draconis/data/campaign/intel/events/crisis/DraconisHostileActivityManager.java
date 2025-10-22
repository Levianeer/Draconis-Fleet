package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import levianeer.draconis.data.campaign.intel.events.aicore.DraconisAICoreActivityCause;

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

                    // Add standard fleet activity cause
                    DraconisFleetStandardActivityCause standardCause = new DraconisFleetStandardActivityCause(intel);
                    factor.addCause(standardCause);

                    // Add AI core acquisition cause
                    DraconisAICoreActivityCause aiCoreCause = new DraconisAICoreActivityCause(intel);
                    factor.addCause(aiCoreCause);

                    intel.addFactor(factor);

                    Global.getLogger(this.getClass()).info("Draconis hostile activity factor registered with AI core cause");
                }
                added = true;
            }
        }
    }
}