package levianeer.draconis.data.scripts.world;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import levianeer.draconis.data.campaign.econ.XLII_MarketTransfer;
import levianeer.draconis.data.campaign.ids.Factions;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeSystem;
import levianeer.draconis.data.scripts.world.systems.XLII_System;

public class XLII_WorldGen implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {
        new XLII_System().generate(sector);
        new XLII_OfficeSystem().generate(sector);

        // Tags every market Draconis starts the game owning - see
        // XLII_MarketTransfer.ORIGINAL_DRACONIS_MARKET_FLAG's own doc for why. Done here, once, right
        // after generation rather than hardcoding market ids, so it stays correct regardless of which
        // systems/markets actually start under Draconis control.
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (Factions.DRACONIS.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().set(XLII_MarketTransfer.ORIGINAL_DRACONIS_MARKET_FLAG, true);
            }
        }
    }
}
