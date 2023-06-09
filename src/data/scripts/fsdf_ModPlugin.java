package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import data.scripts.world.fsdf_WorldGen;

public class fsdf_ModPlugin extends BaseModPlugin {
    @Override
    public void onNewGame() {
        new fsdf_WorldGen().generate(Global.getSector());
    }
}