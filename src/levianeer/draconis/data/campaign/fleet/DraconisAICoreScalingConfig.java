package levianeer.draconis.data.campaign.fleet;

import com.fs.starfarer.api.Global;
import levianeer.draconis.DraconisSettings;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * Configuration manager for the Draconis AI Core Fleet Inflater.
 * Handles loading settings from {@code settings.json}.
 */
public class DraconisAICoreScalingConfig {
    private static final Logger log = Global.getLogger(DraconisAICoreScalingConfig.class);

    // System settings
    private boolean enabled = true;
    private float recheckIntervalDays = 35f; // How often to recheck fleets for new empty slots

    private static DraconisAICoreScalingConfig instance;

    public static DraconisAICoreScalingConfig getInstance() {
        if (instance == null) {
            instance = new DraconisAICoreScalingConfig();
            instance.loadSettings();
        }
        return instance;
    }

    /** Reset the singleton so settings are re-read on next game load. */
    public static void reset() {
        instance = null;
    }

    private void loadSettings() {
        try {
            JSONObject settings = Global.getSettings().getJSONObject("draconisAICoreScaling");

            enabled = DraconisSettings.getBoolean("draconisAICoreScalingEnabled", settings.optBoolean("enabled", true));
            recheckIntervalDays = (float) settings.optDouble("recheckIntervalDays", 120.0);

            log.info("Draconis: AI Core Fleet Inflater config loaded successfully - Enabled: " + enabled);

        } catch (Exception e) {
            log.error("Draconis: Failed to load AI Core Fleet Inflater settings from JSON - SYSTEM DISABLED", e);
            enabled = false; // Disable system instead of using unreliable defaults
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getRecheckIntervalDays() {
        return recheckIntervalDays;
    }
}
