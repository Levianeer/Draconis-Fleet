package levianeer.draconis;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;

import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisRaidMonitor;
import levianeer.draconis.data.scripts.ai.XLII_antiMissileAI;
import levianeer.draconis.data.scripts.ai.XLII_magicMissileAI;
import levianeer.draconis.data.scripts.world.XLII_WorldGen;
import levianeer.draconis.data.campaign.intel.events.crisis.DraconisHostileActivityManager;
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisAICoreTargetingMonitor;
import levianeer.draconis.data.campaign.characters.XLII_Characters;

public class XLII_ModPlugin extends BaseModPlugin {

    public static final String PD_MISSILE_ID = "XLII_swordbreaker_shot";
    public static final String SWARM_MISSILE_ID = "XLII_bardiche_shot";

    // Nexerelin integration
    private static final String NEXERELIN_MOD_ID = "nexerelin";
    private static boolean hasNexerelin = false;

    @Override
    public void onApplicationLoad() {
        Global.getLogger(this.getClass()).info("=== Draconis Mod Loading ===");

        // Check if Nexerelin is installed
        hasNexerelin = Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID);

        if (hasNexerelin) {
            Global.getLogger(this.getClass()).info("Nexerelin detected - AI core theft and targeting systems will be active");
        } else {
            Global.getLogger(this.getClass()).info("Nexerelin not detected - using vanilla raid detection only");
        }
    }

    @Override
    public void onNewGame() {
        Global.getLogger(this.getClass()).info("=== Draconis Mod: onNewGame() ===");

        // Check if we should run custom sector generation
        // Skip if Nexerelin is using random sector mode
        boolean skipGeneration = false;

        if (hasNexerelin) {
            boolean isRandomSector = Global.getSector().getMemoryWithoutUpdate()
                    .getBoolean("$nex_randomSector");

            if (isRandomSector) {
                skipGeneration = true;
                Global.getLogger(this.getClass()).info(
                        "Nexerelin random sector detected - skipping custom sector generation"
                );
            }
        }

        if (!skipGeneration) {
            Global.getLogger(this.getClass()).info("Generating custom Draconis sector");
            new XLII_WorldGen().generate(Global.getSector());
            Global.getLogger(this.getClass()).info("Sector generation complete");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        Global.getLogger(this.getClass()).info("=== Draconis Mod: onGameLoad() ===");
        Global.getLogger(this.getClass()).info("New game: " + newGame);

        // Initialize Draconis characters
        Global.getLogger(this.getClass()).info("Initializing Draconis characters");
        XLII_Characters.initializeAllCharacters();

        // Add the persistent manager script for hostile activity
        Global.getLogger(this.getClass()).info("Registering DraconisHostileActivityManager");
        Global.getSector().addScript(new DraconisHostileActivityManager());

        // If Nexerelin is present, add the AI core targeting system
        if (hasNexerelin) {
            Global.getLogger(this.getClass()).info("Registering Nexerelin AI core targeting:");

            // AI core targeting system (monitors markets and flags them)
            Global.getSector().addScript(new DraconisAICoreTargetingMonitor());
            Global.getLogger(this.getClass()).info("  - AI core targeting monitor");

            // Add the raid monitor for AI core theft
            Global.getLogger(this.getClass()).info("Registering DraconisRaidMonitor");
            Global.getSector().addScript(new DraconisRaidMonitor());

            Global.getLogger(this.getClass()).info("Nexerelin Strategic AI integration complete");
        } else {
            Global.getLogger(this.getClass()).info("Skipping Nexerelin integration (mod not present)");
        }

        Global.getLogger(this.getClass()).info("=== Draconis Mod: Game load complete ===");
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (missile.getProjectileSpecId().equals(PD_MISSILE_ID)) {
            return new PluginPick<>(new XLII_antiMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(SWARM_MISSILE_ID)) {
            return new PluginPick<>(new XLII_magicMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        return null;
    }
}