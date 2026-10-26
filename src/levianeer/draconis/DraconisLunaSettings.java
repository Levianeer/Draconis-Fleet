package levianeer.draconis;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import org.apache.log4j.Logger;
import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

/**
 * LunaLib is optional: only reach this class through {@link DraconisSettings}, after
 * {@link XLII_ModPlugin#hasLunaLib()}. Anything that names a LunaLib type outside this class would
 * fail to load without it.
 */
final class DraconisLunaSettings {

    private static final Logger log = Global.getLogger(DraconisLunaSettings.class);
    private static final String MOD_ID = "levianeer_draconis";
    private static final String CSV = "data/config/LunaSettings.csv";

    // LunaSettings logs an error for every lookup of a field it doesn't know, and most settings
    // are read on every call, so only ask it about the fields our CSV declares.
    private static Set<String> keys;

    private DraconisLunaSettings() {}

    static boolean has(String key) {
        if (keys == null) keys = loadKeys();
        return keys.contains(key);
    }

    static Float getFloat(String key) {
        return LunaSettings.getFloat(MOD_ID, key);
    }

    static Integer getInt(String key) {
        return LunaSettings.getInt(MOD_ID, key);
    }

    static Boolean getBoolean(String key) {
        return LunaSettings.getBoolean(MOD_ID, key);
    }

    private static Set<String> loadKeys() {
        Set<String> result = new HashSet<>();
        try {
            JSONArray rows = Global.getSettings().loadCSV(CSV, MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                String id = rows.getJSONObject(i).optString("fieldID", "");
                if (!id.isEmpty()) result.add(id);
            }
        } catch (Exception e) {
            log.warn("Draconis: Failed to load " + CSV + ", using settings.json only", e);
        }
        return result;
    }
}
