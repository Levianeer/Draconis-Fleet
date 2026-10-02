package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import levianeer.draconis.data.campaign.companion.KorrinTopicQueue;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import levianeer.draconis.data.campaign.intel.events.crisis.deal.DraconisAIOPaymentDealIntel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes the dynamic values the AIO payment-deal bar offer's rules.csv text depends on
 * ($aio_monthly, $aio_coreDesc, $aio_totalCores, $aio_months) and queues the Korrin
 * reaction topic. Called once, from the {@code XLII_aioBar_offer} row's script, before that
 * row's own text renders - conditions/script run before text within one row, so the values
 * are already set by the time the text's token substitution happens (see SKILL.md's
 * "Execution Order" section).
 * <p>
 * Usage in rules.csv script column: {@code XLII_ComputeAIOOfferVars}
 */
@SuppressWarnings("unused")
public class XLII_ComputeAIOOfferVars extends BaseCommandPlugin {

    private static final String MEM_KEY_KORRIN_OFFER_QUEUED = "$XLII_korrin_aio_offer_queued";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        if (local == null) return false;

        float monthly = DraconisAIOPaymentDealIntel.computeCurrentMonthlyPayment();
        int[] cores = countPlayerAICores();
        String coreDesc = formatCoreDescription(cores[0], cores[1], cores[2]);
        int totalCores = cores[0] + cores[1] + cores[2];

        DraconisAIOTracker tracker = DraconisAIOTracker.get();
        int months = (tracker != null) ? tracker.getMonthsWatched() : 14;

        local.set("$aio_monthly", Misc.getWithDGS((long) monthly), 0f);
        local.set("$aio_coreDesc", coreDesc, 0f);
        local.set("$aio_totalCores", (float) totalCores, 0f);
        local.set("$aio_months", formatWatchDuration(months), 0f);

        // No rules.csv rule fires ahead of this beat, so this is queued directly here - see the
        // Quickstart's one Java-call exception in .claude/systems/korrin-companion.md. Fires on
        // the offer itself, regardless of what the player goes on to choose.
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(MEM_KEY_KORRIN_OFFER_QUEUED)) {
            Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY_KORRIN_OFFER_QUEUED, true);
            KorrinTopicQueue.queue("korrin_aio_ancker");
        }

        return true;
    }

    /** Returns {alphaCount, betaCount, gammaCount} across all player-owned markets (industries + administrator). */
    private static int[] countPlayerAICores() {
        int alpha = 0, beta = 0, gamma = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(m.getFactionId())) continue;
            if (m.getAdmin() != null && m.getAdmin().getAICoreId() != null) alpha++;
            for (Industry industry : m.getIndustries()) {
                if (industry == null) continue;
                String coreId = industry.getAICoreId();
                if (coreId == null || coreId.isEmpty()) continue;
                if (Commodities.ALPHA_CORE.equals(coreId)) alpha++;
                else if (Commodities.BETA_CORE.equals(coreId)) beta++;
                else gamma++;
            }
        }
        return new int[]{alpha, beta, gamma};
    }

    /**
     * Builds a natural-language list of installed core types, e.g. "two alpha-class, one beta-class".
     * Returns "AI cores" as a fallback if all counts are zero.
     */
    private static String formatCoreDescription(int alpha, int beta, int gamma) {
        List<String> parts = new ArrayList<>();
        if (alpha > 0) parts.add(toWord(alpha) + " alpha-class");
        if (beta  > 0) parts.add(toWord(beta)  + " beta-class");
        if (gamma > 0) parts.add(toWord(gamma) + " gamma-class");
        if (parts.isEmpty()) return "AI cores";
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + " and " + parts.get(1);
        return parts.get(0) + ", " + parts.get(1) + ", and " + parts.get(2);
    }

    private static String formatWatchDuration(int months) {
        if (months <= 1) return "One month";
        String word = months < 10 ? capitalize(toWord(months)) : String.valueOf(months);
        return word + " months";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String toWord(int n) {
        return switch (n) {
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            case 5 -> "five";
            case 6 -> "six";
            case 7 -> "seven";
            case 8 -> "eight";
            case 9 -> "nine";
            default -> String.valueOf(n);
        };
    }
}
