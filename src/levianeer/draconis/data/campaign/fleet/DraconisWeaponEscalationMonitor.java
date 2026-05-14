package levianeer.draconis.data.campaign.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
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
 * Swap mappings (slot-for-slot, applied at fleet interaction time):
 *   XLII_halberd       -> XLII_hankyu_torpedo          (small)
 *   XLII_halberd_pod   -> XLII_hankyu_torpedo_pod      (medium)
 *   XLII_naginata      -> XLII_daikyu_torpedo_large    (large)
 *   XLII_shangshu_wing -> XLII_shangshu_heavy_wing     (fighter wing)
 */
public class DraconisWeaponEscalationMonitor {

    private static final Logger log = Global.getLogger(DraconisWeaponEscalationMonitor.class);

    private static final String WHITELIST_PATH = "data/config/modFiles/draconis_nuclear_whitelist.csv";
    private static final String SETTING_ENABLED = "draconisEnableWeaponEscalation";

    // Weapons to replace
    private static final String HALBERD         = "XLII_halberd";
    private static final String HALBERD_POD     = "XLII_halberd_pod";
    private static final String NAGINATA        = "XLII_naginata";

    // WMD to override
    private static final String HANKYU_SMALL    = "XLII_hankyu_torpedo";
    private static final String HANKYU_MEDIUM   = "XLII_hankyu_torpedo_pod";
    private static final String DAIKYU_LARGE    = "XLII_daikyu_torpedo_large";

    // Fighter wing swap
    private static final String SHANGSHU_WING       = "XLII_shangshu_wing";
    private static final String SHANGSHU_HEAVY_WING = "XLII_shangshu_heavy_wing";

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
     * Replaces Halberd and Naginata mounts on all ships in the given fleet
     * with their equivalents.
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
                if (HALBERD.equals(weaponId)) {
                    mutable.addWeapon(slot, HANKYU_SMALL);
                    modified = true;
                    swapCount++;
                    log.debug("Draconis: Escalation swap: " + HALBERD + " -> " + HANKYU_SMALL
                            + " on " + member.getShipName());
                } else if (HALBERD_POD.equals(weaponId)) {
                    mutable.addWeapon(slot, HANKYU_MEDIUM);
                    modified = true;
                    swapCount++;
                    log.debug("Draconis: Escalation swap: " + HALBERD_POD + " -> " + HANKYU_MEDIUM
                            + " on " + member.getShipName());
                } else if (NAGINATA.equals(weaponId)) {
                    mutable.addWeapon(slot, DAIKYU_LARGE);
                    modified = true;
                    swapCount++;
                    log.debug("Draconis: Escalation swap: " + NAGINATA + " -> " + DAIKYU_LARGE
                            + " on " + member.getShipName());
                }
            }

            List<String> wings = mutable.getWings();
            for (int i = 0; i < wings.size(); i++) {
                if (SHANGSHU_WING.equals(wings.get(i))) {
                    mutable.setWingId(i, SHANGSHU_HEAVY_WING);
                    modified = true;
                    swapCount++;
                    log.debug("Draconis: Escalation swap: " + SHANGSHU_WING + " -> " + SHANGSHU_HEAVY_WING
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