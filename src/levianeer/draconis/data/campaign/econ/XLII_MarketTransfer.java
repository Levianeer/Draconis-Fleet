package levianeer.draconis.data.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import levianeer.draconis.data.campaign.ids.Factions;
import org.apache.log4j.Logger;

/**
 * Shared market-ownership-transfer utility - Nexerelin-aware when Nexerelin is present, falling
 * back to a manual recipe otherwise. Used by the Office Takeover crisis's capture outcome
 * (`XLII_LongsightBastionIntel`) and by `XLII_RingPortAssault`'s capture, which previously only
 * had the manual path and never told Nexerelin a market had changed hands - fine for a one-time
 * scripted capture, not for something that runs repeatedly at Sector scale.
 * <p>
 * <b>Why Nexerelin needs to be told at all:</b> its own invasion system and player-facing "transfer
 * market" command both go through {@code exerelin.campaign.SectorManager.transferMarket(...)} rather
 * than a raw {@code market.setFactionId()} call - that method is what notifies Nexerelin's
 * `DiplomacyManager`/`StatsTracker`, checks for faction elimination/respawn, and posts the
 * player-facing `MarketTransferIntel` Nexerelin players expect. Skipping it leaves the transfer
 * invisible to all of that.
 */
public class XLII_MarketTransfer {

    private static final Logger log = Global.getLogger(XLII_MarketTransfer.class);

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    /**
     * Transfers {@code market} to {@code newFactionId}. Uses Nexerelin's own transfer path when
     * Nexerelin is enabled, the manual recipe otherwise.
     */
    public static void transferMarket(MarketAPI market, String newFactionId) {
        if (Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID)) {
            try {
                transferViaNexerelin(market, newFactionId);
                return;
            } catch (Throwable t) {
                log.warn("Draconis: Nexerelin market transfer failed, falling back to manual recipe", t);
            }
        }
        transferManual(market, newFactionId);
    }

    /**
     * Reflection-free direct call, guarded at runtime by the Nexerelin mod-enabled check above and
     * a try/catch here - same idiom this codebase already uses for other optional Nexerelin calls
     * (e.g. {@code DraconisAIOTracker.isNexerelinAllied()}). Not player-involved and treated as a
     * capture (an NPC-driven hostile takeover, not a peaceful transfer) - matches how
     * {@code Nex_TransferMarket}'s own player-facing command calls the same method, just with
     * {@code playerInvolved=false} since the player didn't do this.
     */
    private static void transferViaNexerelin(MarketAPI market, String newFactionId) {
        FactionAPI newOwner = Global.getSector().getFaction(newFactionId);
        FactionAPI oldOwner = market.getFaction();

        exerelin.campaign.SectorManager.transferMarket(
                market, newOwner, oldOwner,
                false,  // playerInvolved
                true,   // isCapture
                null,   // factionsToNotify
                0f);    // repChangeStrength
    }

    /**
     * Generalized from `XLII_RingPortAssault.transferMarketDirect()` - same recipe, parameterized
     * by faction id instead of hardcoding Draconis. Deliberately skips creating named
     * admin/commander/portmaster NPCs the way Ring-Port's own capture does - that's a one-time
     * story beat's flourish, not something a repeating background crisis needs. The market is left
     * personnel-less afterward, like an ordinary NPC-vs-NPC capture.
     */
    private static void transferManual(MarketAPI market, String newFactionId) {
        market.setFactionId(newFactionId);

        // BaseSubmarketPlugin.isBlackMarket() checks market.getFaction().isHostileTo(submarket.getFaction()).
        // The open submarket's faction is set at creation and doesn't auto-update; left stale, it's
        // misidentified as a black market once the new owner is hostile to the old one.
        if (market.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
            market.getSubmarket(Submarkets.SUBMARKET_OPEN)
                  .setFaction(Global.getSector().getFaction(newFactionId));
        }

        SectorEntityToken entity = market.getPrimaryEntity();
        if (entity == null) return;

        entity.setFaction(newFactionId);

        // The OrbitalStation (BATTLESTATION) industry maintains a hidden stationFleet whose faction
        // is set at creation time and doesn't update automatically when the market faction changes.
        MemoryAPI entityMem = entity.getMemoryWithoutUpdate();
        Object stationFleetObj = entityMem.get(MemFlags.STATION_FLEET);
        if (stationFleetObj instanceof CampaignFleetAPI) {
            ((CampaignFleetAPI) stationFleetObj).setFaction(newFactionId, true);
        }
        Object baseFleetObj = entityMem.get(MemFlags.STATION_BASE_FLEET);
        if (baseFleetObj instanceof CampaignFleetAPI) {
            ((CampaignFleetAPI) baseFleetObj).setFaction(newFactionId, true);
        }

        market.setAdmin(null);
        for (PersonAPI person : market.getPeopleCopy()) {
            market.removePerson(person);
        }
        market.getCommDirectory().clear();
    }

    /**
     * True if Draconis and {@code factionId} are formally allied under Nexerelin, or if Nexerelin
     * isn't present at all (no alliance concept, so nothing to check - never blocks anything in
     * that case). Reflection-free direct call, same guarded idiom as
     * {@code transferViaNexerelin()}/{@code DraconisAIOTracker.isNexerelinAllied()}.
     * <p>
     * Without this check, the crisis would capture an ally's market and it would come right back to
     * the ally shortly after, repeatedly. Likely cause (not fully traced): Nexerelin pools an
     * alliance's markets ({@code AllianceManager.getAllianceMarkets()}), and its background
     * diplomacy/invasion AI treats an ally holding territory just taken from another ally as
     * something to correct - creating an endless tug-of-war as this crisis keeps re-targeting the
     * reclaimed market. Fixed by simply never offering allied markets as invasion targets.
     */
    public static boolean isAllyOfDraconis(String factionId) {
        if (!Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID)) return false;
        try {
            return exerelin.campaign.AllianceManager.areFactionsAllied(Factions.DRACONIS, factionId);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Reputation-based counterpart to {@link #isAllyOfDraconis}: protects any faction Draconis is
     * genuinely on good terms with, even without a formal Nexerelin alliance (or when Nexerelin
     * isn't installed at all, where the alliance concept doesn't exist). Applies whether or not
     * Nexerelin is present, so a good-standing non-allied faction is protected alongside formal
     * allies, not just as a non-Nexerelin fallback.
     */
    private static final RepLevel MIN_PROTECTED_REP_LEVEL = RepLevel.FRIENDLY;

    public static boolean isRepProtected(String factionId) {
        RepLevel level = Global.getSector().getFaction(Factions.DRACONIS).getRelationshipLevel(factionId);
        return level.compareTo(MIN_PROTECTED_REP_LEVEL) >= 0;
    }

    /**
     * True if {@code factionId} should never be offered as an Office Takeover invasion target -
     * either condition alone is enough. Single combined entry point so call sites don't need to
     * know there are two separate checks under the hood.
     */
    public static boolean isProtectedFromCrisisInvasion(String factionId) {
        return isAllyOfDraconis(factionId) || isRepProtected(factionId);
    }
}
