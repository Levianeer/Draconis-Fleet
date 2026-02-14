package levianeer.draconis;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.combat.ShipAPI;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import levianeer.draconis.data.campaign.intel.aicore.donation.DraconisAICoreDonationListener;
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisTargetedRaidMonitor;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantRaidListener;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantRaidManager;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.raids.DraconisAICoreRaidManager;
import levianeer.draconis.data.campaign.intel.events.crisis.DraconisHostileActivityManager;
import levianeer.draconis.data.campaign.econ.conditions.DraconConfig;
import levianeer.draconis.data.campaign.econ.conditions.DraconManager;
import levianeer.draconis.data.campaign.econ.conditions.DraconisSteelCurtainMonitor;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreFleetInflater;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreScalingConfig;
import levianeer.draconis.data.scripts.ai.XLII_antiMissileAI;
import levianeer.draconis.data.scripts.ai.XLII_magicMissileAI;
import levianeer.draconis.data.scripts.ai.XLII_PhaseTorpedoAI;
import levianeer.draconis.data.scripts.ai.XLII_SabreAI;
import levianeer.draconis.data.scripts.ai.XLII_SlapERMissileAI;
import levianeer.draconis.data.scripts.world.XLII_WorldGen;

@SuppressWarnings("unused")
public class XLII_ModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(XLII_ModPlugin.class);
    public static final String PD_MISSILE_ID = "XLII_swordbreaker_shot";
    public static final String SWARM_MISSILE_ID = "XLII_bardiche_shot";
    public static final String PHASE_TORPEDO_ID = "XLII_phasetorp";
    public static final String SABRE_MISSILE_ID = "XLII_sabre_torp";
    public static final String SLAP_ER_MISSILE_ID = "XLII_SLAP-ER_torp";
    private static final String NEXERELIN_MOD_ID = "nexerelin";
    private static boolean hasNexerelin = false;

    // Raid system toggles
    private static boolean enableAICoreRaids = true;
    private static boolean enableRemnantRaids = true;

    // AI Core Fleet Scaling toggle
    private static boolean enableAICoreFleetScaling = true;

    @Override
    public void onApplicationLoad() {
        log.info("Draconis: === Mod Loading ===");
        hasNexerelin = Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID);

        // Load raid system toggles from settings
        try {
            enableAICoreRaids = Global.getSettings().getBoolean("draconisEnableAICoreRaids");
            log.info("Draconis: AI Core Raids: " + (enableAICoreRaids ? "ENABLED" : "DISABLED"));
        } catch (Exception e) {
            log.warn("Draconis: Failed to load draconisEnableAICoreRaids setting, defaulting to true", e);
            enableAICoreRaids = true;
        }

        try {
            enableRemnantRaids = Global.getSettings().getBoolean("draconisEnableRemnantRaids");
            log.info("Draconis: Remnant Raids: " + (enableRemnantRaids ? "ENABLED" : "DISABLED"));
        } catch (Exception e) {
            log.warn("Draconis: Failed to load draconisEnableRemnantRaids setting, defaulting to true", e);
            enableRemnantRaids = true;
        }

        // Load AI Core Fleet Scaling config (initializes singleton)
        DraconisAICoreScalingConfig config = DraconisAICoreScalingConfig.getInstance();
        enableAICoreFleetScaling = config.isEnabled();
        log.info("Draconis: AI Core Fleet Scaling: " + (enableAICoreFleetScaling ? "ENABLED" : "DISABLED"));

        if (hasNexerelin) {
            log.info("Draconis: Nexerelin detected - AI core acquisition system will be enabled");
            log.info("Draconis: Story mission system enabled - 'The Nanoforge Gambit' available at Ring-Port");
        } else {
            log.info("Draconis: Nexerelin not detected - AI core system will be disabled");
            log.info("Draconis: Story mission system disabled (requires Nexerelin)");
        }
    }

    @Override
    public void onNewGame() {
        log.info("Draconis: === onNewGame() ===");

        boolean skipGeneration = false;

        if (hasNexerelin) {
            boolean isRandomSector = Global.getSector().getMemoryWithoutUpdate()
                    .getBoolean("$nex_randomSector");

            if (isRandomSector) {
                skipGeneration = true;
                log.info("Draconis: Nexerelin random sector detected - skipping custom sector generation");
            }
        }

        if (!skipGeneration) {
            log.info("Draconis: Generating custom sector");
            new XLII_WorldGen().generate(Global.getSector());
            log.info("Draconis: Sector generation complete");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);
        log.info("Draconis: === onGameLoad() ===");
        log.info("Draconis: New game: " + newGame);

        // Remove old script instances from previous save/load cycles to prevent accumulation
        // Scripts are serialized into saves, so without cleanup they stack on each game load
        cleanupOldScripts();

        // Initialize Draconis characters
        log.info("Draconis: Initializing characters");
        XLII_Characters.initializeAllCharacters();

        // Add the crisis event system
        log.info("Draconis: Registering crisis systems");
        Global.getSector().addScript(new DraconisHostileActivityManager());
        log.info("Draconis:   - Hostile Activity Manager");

        // Add AI Core Fleet Scaling system
        if (enableAICoreFleetScaling) {
            log.info("Draconis: Registering AI Core Fleet Scaling");
            Global.getSector().addScript(new DraconisAICoreFleetInflater());
            log.info("Draconis:   - AI Core Fleet Inflater");
        } else {
            log.info("Draconis: AI Core Fleet Scaling DISABLED by settings");
        }

        // If Nexerelin is present, add DRACON system and AI core acquisition
        if (hasNexerelin) {
            // DRACON (Draconis Readiness Condition) - replaces Steel Curtain
            DraconConfig draconConfig = DraconConfig.getInstance();
            if (draconConfig.isEnabled()) {
                Global.getSector().addScript(new DraconManager());
                log.info("Draconis:   - DRACON Manager (Draconis Readiness Condition)");
            } else {
                Global.getSector().addScript(new DraconisSteelCurtainMonitor());
                log.info("Draconis:   - Steel Curtain Monitor (DRACON disabled by settings)");
            }
            log.info("Draconis: === Registering AI Core Acquisition System ===");

            // AI Core Raids on Faction Markets
            if (enableAICoreRaids) {
                log.info("Draconis: === Registering AI Core Raid System ===");
                // Scanner - finds high-value AI core targets
                Global.getSector().addScript(new DraconisSingleTargetScanner());
                log.info("Draconis:   - AI Core Target Scanner");
                // Raid Manager - triggers Shadow Fleet raids on high-value targets
                Global.getSector().addScript(new DraconisAICoreRaidManager());
                log.info("Draconis:   - AI Core Raid Manager");
                // Monitor - watches for successful raids and steals AI cores
                Global.getSector().addScript(new DraconisTargetedRaidMonitor());
                log.info("Draconis:   - Targeted Raid Monitor");
            } else {
                log.info("Draconis: AI Core Raids DISABLED by settings");
            }

            // Donation Listener - processes player AI core donations (always enabled)
            Global.getSector().addScript(new DraconisAICoreDonationListener());
            log.info("Draconis:   - AI Core Donation Listener");

            // Remnant Raid System - hunts Remnant installations for AI cores
            if (enableRemnantRaids) {
                log.info("Draconis: === Registering Remnant Raid System ===");
                Global.getSector().addScript(new DraconisRemnantTargetScanner());
                log.info("Draconis:   - Remnant Target Scanner");
                Global.getSector().addScript(new DraconisRemnantRaidManager());
                log.info("Draconis:   - Remnant Raid Manager");
                Global.getSector().addScript(new DraconisRemnantRaidListener());
                log.info("Draconis:   - Remnant Raid Listener");
            } else {
                log.info("Draconis: Remnant Raids DISABLED by settings");
            }

            log.info("Draconis: === AI Core Acquisition System Configuration Complete ===");
        } else {
            log.info("Draconis: Nexerelin not present - AI core acquisition system disabled");
            // Without Nex, fall back to Steel Curtain (static condition)
            Global.getSector().addScript(new DraconisSteelCurtainMonitor());
            log.info("Draconis:   - Steel Curtain Monitor (Nexerelin not present)");
        }

        // Clean up any old intel that wasn't properly expired (save compatibility fix)
        cleanupOldIntel();

        log.info("Draconis: === Game load complete ===");
    }

    /**
     * Removes all Draconis EveryFrameScript instances from the sector before re-adding them.
     * Prevents script accumulation across save/load cycles (scripts are serialized into saves,
     * so without cleanup each onGameLoad() would add duplicates).
     * Also handles 0.6.0 -> 0.6.1 migration: removes old DraconisSteelCurtainMonitor instances
     * that would conflict with the new DraconManager.
     */
    private void cleanupOldScripts() {
        List<EveryFrameScript> toRemove = new ArrayList<>();

        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof DraconisHostileActivityManager
                    || script instanceof DraconisAICoreFleetInflater
                    || script instanceof DraconisSteelCurtainMonitor
                    || script instanceof DraconManager
                    || script instanceof DraconisSingleTargetScanner
                    || script instanceof DraconisAICoreRaidManager
                    || script instanceof DraconisTargetedRaidMonitor
                    || script instanceof DraconisAICoreDonationListener
                    || script instanceof DraconisRemnantTargetScanner
                    || script instanceof DraconisRemnantRaidManager
                    || script instanceof DraconisRemnantRaidListener) {
                toRemove.add(script);
            }
        }

        for (EveryFrameScript script : toRemove) {
            Global.getSector().removeScript(script);
        }

        if (!toRemove.isEmpty()) {
            log.info("Draconis: Cleaned up " + toRemove.size() + " old script instance(s) from save");
        }
    }

    /**
     * Cleanup old AI core theft intel that may not have expired properly in previous versions
     * This is save-compatible and runs on every game load
     * NOTE TO SELF: Remove this at some point!!
     */
    private void cleanupOldIntel() {
        try {
            log.info("Draconis: === Starting Intel Cleanup ===");

            int removedCount = 0;
            int foundCount = 0;

            // Defensive copy to prevent ConcurrentModificationException
            java.util.List<com.fs.starfarer.api.campaign.comm.IntelInfoPlugin> allIntel =
                new java.util.ArrayList<>(Global.getSector().getIntelManager().getIntel());

            log.info("Draconis: Checking " + allIntel.size() + " total intel items");

            for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin intel : allIntel) {
                if (intel == null) continue;

                // Use class name matching for better save compatibility
                String className = intel.getClass().getName();

                if (className.contains("DraconisAICoreTheftIntel")) {
                    foundCount++;
                    log.info("Draconis: Found AI core theft intel: " + className);

                    try {
                        // Cast to our intel type
                        levianeer.draconis.data.campaign.intel.aicore.intel.DraconisAICoreTheftIntel theftIntel =
                            (levianeer.draconis.data.campaign.intel.aicore.intel.DraconisAICoreTheftIntel) intel;

                        // Check if intel is expired (no reflection needed!)
                        if (theftIntel.isExpired()) {
                            // Use the proper intel lifecycle method
                            theftIntel.endImmediately();
                            removedCount++;
                            log.info("Draconis:   >>> REMOVED expired intel");
                        } else {
                            log.info("Draconis:   Intel not expired yet, keeping");
                        }
                    } catch (ClassCastException e) {
                        log.warn("Draconis: Could not cast intel (save compatibility issue): " + e.getMessage());
                    } catch (Exception e) {
                        log.warn("Draconis: Could not process intel: " + e.getMessage());
                    }
                }
            }

            log.info("Draconis: Intel cleanup complete - Found: " + foundCount + ", Removed: " + removedCount);

            if (removedCount > 0) {
                log.info("Draconis: Successfully cleaned up " + removedCount + " expired AI core theft intel notifications");
            } else if (foundCount > 0) {
                log.info("Draconis: Found " + foundCount + " AI core theft intel items but none were expired");
            } else {
                log.info("Draconis: No AI core theft intel found to clean up");
            }

            log.info("Draconis: === Intel Cleanup Complete ===");
        } catch (Exception e) {
            log.error("Draconis: Error during intel cleanup (non-critical): " + e.getMessage(), e);
        }
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (missile.getProjectileSpecId().equals(PD_MISSILE_ID)) {
            return new PluginPick<>(new XLII_antiMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(SWARM_MISSILE_ID)) {
            return new PluginPick<>(new XLII_magicMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(PHASE_TORPEDO_ID)) {
            // Phase torpedoes launched from weapons (not ship system) start unphased
            // Ship system handles its own AI creation with proper phase state
            boolean startedPhased = false;
            return new PluginPick<>(new XLII_PhaseTorpedoAI(missile, startedPhased), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(SABRE_MISSILE_ID)) {
            return new PluginPick<>(new XLII_SabreAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }

        if (missile.getProjectileSpecId().equals(SLAP_ER_MISSILE_ID)) {
            return new PluginPick<>(new XLII_SlapERMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        return null;
    }
}