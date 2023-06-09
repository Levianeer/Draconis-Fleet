package data.scripts.world;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;

import data.scripts.world.systems.fsdf_System;

public class fsdf_WorldGen implements SectorGeneratorPlugin {

    @Override
    public void generate(SectorAPI sector) {
        new fsdf_System().generate(sector);
    }
}
