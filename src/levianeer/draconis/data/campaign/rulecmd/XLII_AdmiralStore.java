package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dynamic paginated store for Fleet Admiral Emil August.
 * Automatically discovers all ship variants and fighter wings tagged
 * with XLII_fortysecond_bp and presents them 4 per page.
 * <p>
 * Commands (param 0):
 *   init  - open store at page 0
 *   next  - advance one page
 *   prev  - go back one page
 * <p>
 * Memory variables set (all session-only, expiry = 0):
 *   $XLII_store_page      - current page index
 *   $XLII_store_item_1..4 - variant/wing ID for each visible slot
 * <p>
 * rules.csv option IDs produced:
 *   store_item_1..4  - purchase a slot item
 *   store_next       - next page (only if more pages exist)
 *   store_prev       - previous page (only if not on page 0)
 *   decline          - leave the store
 */
@SuppressWarnings("unused")
public class XLII_AdmiralStore extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_AdmiralStore.class);
    private static final String STORE_BP_TAG = "XLII_fortysecond_bp";
    private static final int PAGE_SIZE = 4;
    private static final float CRUISER_GATE_REP = 0.25f;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            log.warn("XLII_AdmiralStore: no action param provided");
            return false;
        }

        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory == null) {
            log.warn("XLII_AdmiralStore: could not access local memory");
            return false;
        }

        List<String> items = buildStoreItems();
        if (items.isEmpty()) {
            log.warn("XLII_AdmiralStore: no store items found with tag " + STORE_BP_TAG);
            return false;
        }

        int maxPage = Math.max(0, (items.size() - 1) / PAGE_SIZE);
        int page = 0;
        Object pageObj = memory.get("$XLII_store_page");
        if (pageObj instanceof Number) {
            page = ((Number) pageObj).intValue();
        }

        switch (action) {
            case "next": page = Math.min(page + 1, maxPage); break;
            case "prev": page = Math.max(page - 1, 0); break;
            case "select": {
                // XLII_AdmiralStore select N - copies $XLII_store_item_N into $XLII_store_selected
                // so slot-handler scripts can call XLII_BuyShip $XLII_store_selected afterwards.
                if (params.size() < 2) {
                    log.warn("XLII_AdmiralStore select: missing slot number param");
                    return false;
                }
                String slotStr = params.get(1).getString(memoryMap);
                String slotKey = "$XLII_store_item_" + slotStr;
                Object variantObj = memory.get(slotKey);
                if (variantObj != null) {
                    memory.set("$XLII_store_selected", variantObj.toString(), 0);
                    log.info("XLII_AdmiralStore select: slot " + slotStr + " -> " + variantObj);
                } else {
                    log.warn("XLII_AdmiralStore select: no item in slot " + slotStr);
                }
                return true;
            }
            default: page = 0; // "init" or any unknown value
        }

        memory.set("$XLII_store_page", page, 0);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());
        List<String> pageItems = items.subList(start, end);

        // Write slot memory and clear stale slots
        for (int i = 0; i < PAGE_SIZE; i++) {
            String key = "$XLII_store_item_" + (i + 1);
            if (i < pageItems.size()) {
                memory.set(key, pageItems.get(i), 0);
            } else {
                memory.unset(key);
            }
        }

        // Build option panel
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        for (int i = 0; i < pageItems.size(); i++) {
            String variantId = pageItems.get(i);
            String displayName = getDisplayName(variantId);
            int credits = calculateCredits(variantId);
            int repPoints = calculateRepPoints(variantId);
            String text = "Purchase " + displayName
                    + " (" + Misc.getWithDGS(credits) + " credits, " + repPoints + " rep)";
            opts.addOption(text, "store_item_" + (i + 1));
        }

        if (end < items.size()) {
            opts.addOption("Next page", "store_next");
        }
        if (page > 0) {
            opts.addOption("Previous page", "store_prev");
        }
        opts.addOption("Perhaps another time.", "decline");

        log.info("XLII_AdmiralStore: showing page " + page + "/" + maxPage
                + " (" + pageItems.size() + " items)");
        return true;
    }

    /**
     * Builds the ordered list of store items:
     * fighter wings first (by wing name), then ships (by hull size ascending, then name).
     */
    private List<String> buildStoreItems() {
        List<String> wings = new ArrayList<>();
        List<String> ships = new ArrayList<>();

        // Fighter wings: scan all wing specs for the store tag
        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (spec.hasTag(STORE_BP_TAG)) {
                wings.add(spec.getId());
            }
        }
        wings.sort(null);

        // Ships: scan all hull specs for the store tag - one entry per hull, no deduplication needed.
        // To add a ship to the store, add the XLII_fortysecond_bp tag to its skin spec.
        float augustRel = getAugustRel();
        for (ShipHullSpecAPI hull : Global.getSettings().getAllShipHullSpecs()) {
            if (!hull.hasTag(STORE_BP_TAG)) continue;
            ShipAPI.HullSize size = hull.getHullSize();
            if ((size == ShipAPI.HullSize.CRUISER || size == ShipAPI.HullSize.CAPITAL_SHIP)
                    && augustRel < CRUISER_GATE_REP) continue;
            ships.add(hull.getHullId());
        }

        // Sort ships by hull size ascending, then alphabetically
        ships.sort((a, b) -> {
            int sizeA = hullSizeOrdinal(a);
            int sizeB = hullSizeOrdinal(b);
            if (sizeA != sizeB) return Integer.compare(sizeA, sizeB);
            return a.compareTo(b);
        });

        List<String> result = new ArrayList<>();
        result.addAll(wings);
        result.addAll(ships);
        return result;
    }

    private float getAugustRel() {
        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson("XLII_fleet_admiral_emil");
        return admiral != null ? admiral.getRelToPlayer().getRel() : 0f;
    }

    private int hullSizeOrdinal(String hullId) {
        try {
            ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
            if (hull == null) return 99;
            return switch (hull.getHullSize()) {
                case FIGHTER -> 0;
                case FRIGATE -> 1;
                case DESTROYER -> 2;
                case CRUISER -> 3;
                case CAPITAL_SHIP -> 4;
                default -> 5;
            };
        } catch (Exception e) {
            return 99;
        }
    }

    private String getDisplayName(String id) {
        // Try as fighter wing first
        try {
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
            if (spec != null) {
                ShipVariantAPI variant = spec.getVariant();
                if (variant != null) {
                    String hullName = variant.getHullSpec().getHullName();
                    String variantName = variant.getDisplayName();
                    if (variantName != null && !variantName.isEmpty()) {
                        return hullName + " " + variantName + " Wing";
                    }
                    return hullName + " Wing";
                }
                return spec.getWingName();
            }
        } catch (Exception ignored) {}

        // Fall back to hull spec (ships are stored by hull ID)
        try {
            ShipHullSpecAPI hull = Global.getSettings().getHullSpec(id);
            if (hull != null) return hull.getHullName() + " " + hull.getDesignation();
        } catch (Exception ignored) {}

        return id;
    }

    private int calculateCredits(String id) {
        try {
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
            if (spec != null) return (int) spec.getBaseValue();
        } catch (Exception ignored) {}

        try {
            ShipHullSpecAPI hull = Global.getSettings().getHullSpec(id);
            if (hull != null) return (int) hull.getBaseValue();
        } catch (Exception e) {
            log.warn("XLII_AdmiralStore: could not calculate credits for " + id, e);
        }
        return 0;
    }

    private int calculateRepPoints(String id) {
        // Wings cost 1 rep point
        try {
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
            if (spec != null) return 1;
        } catch (Exception ignored) {}

        // Ships scale by hull size
        try {
            ShipHullSpecAPI hull = Global.getSettings().getHullSpec(id);
            if (hull != null) return switch (hull.getHullSize()) {
                case FRIGATE -> 2;
                case DESTROYER -> 3;
                case CRUISER -> 4;
                case CAPITAL_SHIP -> 5;
                default -> 1;
            };
        } catch (Exception e) {
            log.warn("XLII_AdmiralStore: could not calculate rep for " + id, e);
        }
        return 1;
    }
}
