package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of items the player can hand Korrin as a one-time personal gift, loaded from
 * {@code data/config/korrin_gifts.csv} the same way {@link KorrinTopicQueue} loads its topics -
 * so another mod can register its own giftable item via {@code getMergedSpreadsheetDataForMod}
 * without touching Java.
 * <p>
 * Each row maps a plain commodity or a special item (see {@link ItemType}) to a one-time
 * reaction. Given-once state is tracked on sector memory as {@code $XLII_korrin_gifted_<id>}
 * (visible to rules.csv as {@code $global.XLII_korrin_gifted_<id>}), not on Korrin himself -
 * consistent with every other one-shot flag this system uses
 * ({@code $global.XLII_korrin_asked_<topic>}, etc.). A gift, once given, stays given for the rest
 * of the playthrough; there is no un-give.
 */
public class KorrinGiftRegistry {

    private static final Logger log = Global.getLogger(KorrinGiftRegistry.class);

    private static final String GIFT_CSV = "data/config/korrin_gifts.csv";
    private static final String MOD_ID = "levianeer_draconis";
    private static final String GIFTED_FLAG_PREFIX = "$XLII_korrin_gifted_";

    private static Map<String, Gift> gifts;

    private KorrinGiftRegistry() {}

    /** Which cargo API a gift's {@code itemId} is checked and removed through. */
    public enum ItemType {
        /** {@code CargoAPI.getCommodityQuantity}/{@code removeCommodity} - e.g. lobster. */
        COMMODITY,
        /** A stack-based special item (e.g. Topographic Data) - checked via cargo stacks, removed
         *  via {@code CargoAPI.CargoItemType.SPECIAL}, the same route XLII_NanoforgeExchange uses
         *  for the Pristine Nanoforge. */
        SPECIAL
    }

    /** One row of korrin_gifts.csv. */
    public static class Gift {
        public final String id;
        public final String itemId;
        public final ItemType itemType;
        public final float quantity;
        /** Shown as the option text in the gift-picker submenu. */
        public final String label;
        /** Sort order in the gift-picker submenu - lower sorts first. */
        public final int priority;

        Gift(String id, String itemId, ItemType itemType, float quantity, String label, int priority) {
            this.id = id;
            this.itemId = itemId;
            this.itemType = itemType;
            this.quantity = quantity;
            this.label = label;
            this.priority = priority;
        }
    }

    // --- Registry ------------------------------------------------------------------------------

    public static Gift getGift(String id) {
        return getGifts().get(id);
    }

    public static boolean isKnownGift(String id) {
        return id != null && getGifts().containsKey(id);
    }

    /** All registered gifts, ascending by priority - the order they appear in the picker. */
    public static List<Gift> getGiftsSorted() {
        List<Gift> out = new ArrayList<>(getGifts().values());
        out.sort((a, b) -> Integer.compare(a.priority, b.priority));
        return out;
    }

    private static Map<String, Gift> getGifts() {
        if (gifts != null) return gifts;

        gifts = new LinkedHashMap<>();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", GIFT_CSV, MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.optString("id", "").trim();
                String itemId = row.optString("itemId", "").trim();
                if (id.isEmpty() || itemId.isEmpty()) continue;

                gifts.put(id, new Gift(
                        id,
                        itemId,
                        itemTypeOf(row.optString("itemType", null), id),
                        (float) row.optDouble("quantity", 1),
                        row.optString("label", id),
                        row.optInt("priority", 0)));
            }
            log.info("Draconis: loaded " + gifts.size() + " Korrin gift(s)");
        } catch (Exception e) {
            log.error("Draconis: failed to load " + GIFT_CSV, e);
        }
        return gifts;
    }

    /** Unknown or missing itemType falls back to COMMODITY, the original and more common case. */
    private static ItemType itemTypeOf(String raw, String giftId) {
        if (raw == null || raw.trim().isEmpty()) return ItemType.COMMODITY;
        try {
            return ItemType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Draconis: Korrin gift '" + giftId + "' has unknown itemType '" + raw + "'");
            return ItemType.COMMODITY;
        }
    }

    // --- Eligibility and state -------------------------------------------------------------------

    public static boolean isGiven(String id) {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(giftedFlag(id));
    }

    /** True if the player has enough of the gift's item in cargo and hasn't given it yet. */
    public static boolean isEligible(Gift gift) {
        if (gift == null || isGiven(gift.id)) return false;
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        return quantityInCargo(cargo, gift) >= gift.quantity;
    }

    /** True if any registered gift is currently eligible - gates the "Give him something." row. */
    public static boolean hasAnyEligible() {
        for (Gift gift : getGifts().values()) {
            if (isEligible(gift)) return true;
        }
        return false;
    }

    /** Removes the gift's item from cargo and marks it given. No-op if already given. */
    public static void give(String id) {
        Gift gift = getGift(id);
        if (gift == null || isGiven(id)) return;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (gift.itemType == ItemType.SPECIAL) {
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(gift.itemId, null), gift.quantity);
        } else {
            cargo.removeCommodity(gift.itemId, gift.quantity);
        }
        Global.getSector().getMemoryWithoutUpdate().set(giftedFlag(id), true);
    }

    private static float quantityInCargo(CargoAPI cargo, Gift gift) {
        if (gift.itemType != ItemType.SPECIAL) {
            return cargo.getCommodityQuantity(gift.itemId);
        }
        float total = 0f;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (!stack.isSpecialStack()) continue;
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data != null && gift.itemId.equals(data.getId())) total += stack.getSize();
        }
        return total;
    }

    private static String giftedFlag(String id) {
        return GIFTED_FLAG_PREFIX + id;
    }
}
