package levianeer.draconis;

import com.fs.starfarer.api.Global;

/**
 * Reads a setting from LunaLib's in-game menu when LunaLib is enabled and the key is listed in
 * data/config/LunaSettings.csv, otherwise from settings.json. Throws like
 * {@code Global.getSettings()} on a missing key, so existing fallbacks still apply.
 */
public final class DraconisSettings {

    private DraconisSettings() {}

    public static float getFloat(String key) {
        if (lunaHas(key)) {
            Float value = DraconisLunaSettings.getFloat(key);
            if (value != null) return value;
        }
        return Global.getSettings().getFloat(key);
    }

    public static int getInt(String key) {
        if (lunaHas(key)) {
            Integer value = DraconisLunaSettings.getInt(key);
            if (value != null) return value;
        }
        return Global.getSettings().getInt(key);
    }

    public static boolean getBoolean(String key) {
        if (lunaHas(key)) {
            Boolean value = DraconisLunaSettings.getBoolean(key);
            if (value != null) return value;
        }
        return Global.getSettings().getBoolean(key);
    }

    // For values that live inside a nested settings.json object: the Luna field gets a flat key,
    // and the caller passes the nested value as the fallback.
    public static float getFloat(String key, float fallback) {
        try {
            return getFloat(key);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static int getInt(String key, int fallback) {
        try {
            return getInt(key);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean getBoolean(String key, boolean fallback) {
        try {
            return getBoolean(key);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean lunaHas(String key) {
        return XLII_ModPlugin.hasLunaLib() && DraconisLunaSettings.has(key);
    }
}
