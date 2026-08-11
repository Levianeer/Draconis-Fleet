package levianeer.draconis.data.campaign.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Reactive nuclear escalation for Draconis faction fleets.
 * <p>
 * A few mods introduce large-scale nuclear weapons that have no
 * equivalent in the base game. Rather than baking Daikyu-class torpedoes into
 * every Draconis loadout - which would skew vanilla balance - the DDA instead
 * responds in kind: if the player is carrying any of these weapons, Draconis
 * fleets 'upgrade' their torpedo mounts to match before the engagement begins.
 * The intent is an even playing field, not a unilateral advantage.
 * <p>
 * The qualifying weapon IDs are maintained externally in
 *   data/config/modFiles/draconis_nuclear_whitelist.csv
 * so the list can be extended without touching code. The system is toggled via
 * "draconisEnableWeaponEscalation" in settings.json.
 * <p>
 * Swap mapping (slot-for-slot, applied at fleet interaction time):
 *   XLII_naginata       -> XLII_daikyu_torpedo_large (large)
 *   XLII_bardiche_large -> XLII_daikyu_torpedo_large (large)
 *   XLII_pike           -> XLII_daikyu_torpedo_large (large)
 */
public class DraconisWeaponEscalationMonitor {

    private static final Logger log = Global.getLogger(DraconisWeaponEscalationMonitor.class);

    private static final String WHITELIST_PATH = "data/config/modFiles/draconis_nuclear_whitelist.csv";
    private static final String SETTING_ENABLED = "draconisEnableWeaponEscalation";

    // Weapons to replace
    private static final String NAGINATA        = "XLII_naginata";
    private static final String BARDICHE_LARGE  = "XLII_bardiche_large";
    private static final String PIKE            = "XLII_pike";

    // WMD to override
    private static final String DAIKYU_LARGE    = "XLII_daikyu_torpedo_large";

    // Lazily loaded
    private static Boolean cachedEnabled = null;
    private static Set<String> cachedWhitelist = null;

    private DraconisWeaponEscalationMonitor() {}

    public static boolean isEnabled() {
        if (cachedEnabled == null) {
            try {
                cachedEnabled = Global.getSettings().getBoolean(SETTING_ENABLED);
            } catch (Exception e) {
                log.warn("Draconis: Failed to read " + SETTING_ENABLED + ", defaulting to true", e);
                cachedEnabled = true;
            }
        }
        return cachedEnabled;
    }

    /** Clears cached state so settings and whitelist are re-read on next use. */
    public static void reset() {
        cachedEnabled = null;
        cachedWhitelist = null;
    }

    private static Set<String> getWhitelist() {
        if (cachedWhitelist != null) return cachedWhitelist;

        cachedWhitelist = new HashSet<>();
        try {
            JSONArray rows = Global.getSettings().loadCSV(WHITELIST_PATH);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.optString("weaponId", "").trim();
                if (!id.isEmpty() && !id.startsWith("#")) {
                    cachedWhitelist.add(id);
                }
            }
            log.info("Draconis: Loaded nuclear whitelist: " + cachedWhitelist.size() + " weapon(s)");
        } catch (Exception e) {
            log.warn("Draconis: Failed to load nuclear weapon whitelist from " + WHITELIST_PATH, e);
        }
        return cachedWhitelist;
    }

    /** Returns true if the player fleet has any weapon in the nuclear whitelist. */
    public static boolean playerHasNuclearWeapons() {
        Set<String> whitelist = getWhitelist();
        if (whitelist.isEmpty()) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        for (FleetMemberAPI member : player.getFleetData().getMembersListCopy()) {
            ShipVariantAPI variant = member.getVariant();
            if (variant == null) continue;
            for (String slot : variant.getNonBuiltInWeaponSlots()) {
                String weaponId = variant.getWeaponId(slot);
                if (weaponId != null && whitelist.contains(weaponId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces Naginata, Bardiche MLRS, and Pike MLRS mounts on all ships
     * in the given fleet with their equivalent.
     */
    public static void applyEscalationTo(CampaignFleetAPI fleet) {
        int swapCount = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            ShipVariantAPI variant = member.getVariant();
            if (variant == null) continue;

            ShipVariantAPI mutable = variant.clone();
            boolean modified = false;

            for (String slot : mutable.getNonBuiltInWeaponSlots()) {
                String weaponId = mutable.getWeaponId(slot);
                if (NAGINATA.equals(weaponId) || BARDICHE_LARGE.equals(weaponId) || PIKE.equals(weaponId)) {
                    mutable.addWeapon(slot, DAIKYU_LARGE);
                    modified = true;
                    swapCount++;
                    log.debug("Draconis: Escalation swap: " + weaponId + " -> " + DAIKYU_LARGE
                            + " on " + member.getShipName());
                }
            }

            if (modified) {
                member.setVariant(mutable, false, false);
            }
        }
        if (swapCount > 0) {
            log.info("Draconis: Applied " + swapCount + " Daikyu escalation swap(s) to fleet "
                    + fleet.getName());
        }
    }
}