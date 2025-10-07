package levianeer.draconis.data.campaign.intel.aicore.config;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

/**
 * Configuration for Draconis AI core theft system
 */
public class DraconisAICoreConfig {

    private static JSONObject config;
    private static boolean loaded = false;

    public static class FactionCoreChances {
        public float alphaChance;
        public float betaChance;
        public float gammaChance;
        public int minMarketSize;

        public FactionCoreChances(float alpha, float beta, float gamma, int minSize) {
            this.alphaChance = alpha;
            this.betaChance = beta;
            this.gammaChance = gamma;
            this.minMarketSize = minSize;
        }
    }

    private static void loadConfig() {
        if (loaded) return;

        try {
            config = Global.getSettings().getJSONObject("draconisAICoreTheft");
            loaded = true;
            Global.getLogger(DraconisAICoreConfig.class).info("Loaded Draconis AI core theft configuration");
        } catch (Exception e) {
            Global.getLogger(DraconisAICoreConfig.class).error("Failed to load AI core theft config, using defaults", e);
            config = new JSONObject();
            loaded = true;
        }
    }

    /**
     * Gets AI core generation chances for a faction
     */
    public static FactionCoreChances getFactionCoreChances(String factionId) {
        loadConfig();

        try {
            JSONObject factionChances = config.getJSONObject("factionCoreChances");

            // Try exact faction match first
            if (factionChances.has(factionId)) {
                JSONObject faction = factionChances.getJSONObject(factionId);
                return new FactionCoreChances(
                        (float) faction.optDouble("alphaChance", 0.0),
                        (float) faction.optDouble("betaChance", 0.0),
                        (float) faction.optDouble("gammaChance", 0.0),
                        faction.optInt("minMarketSize", 5)
                );
            }

            // Default: no fallback cores
            return new FactionCoreChances(0f, 0f, 0f, 99);

        } catch (Exception e) {
            Global.getLogger(DraconisAICoreConfig.class).error("Error reading faction core chances for " + factionId, e);
            return new FactionCoreChances(0f, 0f, 0f, 99);
        }
    }

    /**
     * Gets max cores that can be generated per raid
     */
    public static int getMaxCoresPerRaid() {
        loadConfig();
        try {
            return config.getJSONObject("fallbackBehavior").optInt("maxCoresPerRaid", 3);
        } catch (Exception e) {
            return 3;
        }
    }

    /**
     * Whether to prefer actual installed cores over generated ones
     */
    public static boolean preferActualCores() {
        loadConfig();
        try {
            return config.getJSONObject("fallbackBehavior").optBoolean("preferActualCores", true);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Whether larger markets should be preferred for stolen core installation
     */
    public static boolean preferLargeMarkets() {
        loadConfig();
        try {
            return config.getJSONObject("stolenCoreDestination").optBoolean("preferLargeMarkets", true);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Weight multiplier for market size when choosing installation target
     */
    public static float getMarketSizeWeight() {
        loadConfig();
        try {
            return (float) config.getJSONObject("stolenCoreDestination").optDouble("marketSizeWeight", 2.0);
        } catch (Exception e) {
            return 2.0f;
        }
    }
}