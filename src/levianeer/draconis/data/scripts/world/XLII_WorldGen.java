package levianeer.draconis.data.scripts.world;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;

import levianeer.draconis.data.scripts.world.systems.XLII_System;

public class XLII_WorldGen implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {
        new XLII_System().generate(sector);
    }
}