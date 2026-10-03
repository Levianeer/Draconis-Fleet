package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pool of randomized Talk-greeting flavor lines ("where he is, what he's doing right now"),
 * loaded from {@code data/config/korrin_idle_lines.csv} the same way {@link KorrinGiftRegistry}
 * loads its gifts - a plain data row per line, mergeable by other mods via
 * {@code getMergedSpreadsheetDataForMod}.
 * <p>
 * Replaces the old rules.csv {@code OR}-chain for this content: an {@code OR} chain only
 * randomizes within one quoted cell, which doesn't scale past a handful of variants. Adding
 * line #200 is now a CSV row, not an edit to a huge multiline cell.
 */
public class KorrinIdleLines {

    private static final Logger log = Global.getLogger(KorrinIdleLines.class);

    private static final String IDLE_LINES_CSV = "data/config/korrin_idle_lines.csv";
    private static final String MOD_ID = "levianeer_draconis";

    private static Map<String, List<String>> linesByBucket;

    private KorrinIdleLines() {}

    /** A random line for the given bucket, or null if the bucket has none registered. */
    public static String pickRandom(String bucket) {
        if (bucket == null) return null;
        List<String> lines = getLines().get(bucket);
        if (lines == null || lines.isEmpty()) return null;
        return lines.get((int) (Math.random() * lines.size()));
    }

    private static Map<String, List<String>> getLines() {
        if (linesByBucket != null) return linesByBucket;

        linesByBucket = new LinkedHashMap<>();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", IDLE_LINES_CSV, MOD_ID);
            int count = 0;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String bucket = row.optString("bucket", "").trim();
                String text = row.optString("text", "").trim();
                if (bucket.isEmpty() || text.isEmpty()) continue;

                List<String> lines = linesByBucket.get(bucket);
                if (lines == null) {
                    lines = new ArrayList<>();
                    linesByBucket.put(bucket, lines);
                }
                lines.add(text);
                count++;
            }
            log.info("Draconis: loaded " + count + " Korrin idle line(s)");
        } catch (Exception e) {
            log.error("Draconis: failed to load " + IDLE_LINES_CSV, e);
        }
        return linesByBucket;
    }
}
