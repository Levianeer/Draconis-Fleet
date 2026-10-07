package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import org.apache.log4j.Logger;

/**
 * Non-destructive override point for testing/tuning carrier doctrine. An experimental harness
 * mod can supply its own replacement for {@link CarrierDoctrinePlugin} without Draconis-Fleet
 * ever referencing it at compile time - same mod-presence-detection-plus-reflection idiom this
 * codebase already uses for optional Nexerelin integration (isModEnabled check, no hard
 * dependency), just applied to swapping a plugin class instead of gating a feature.
 *
 * Contract an override mod must satisfy:
 * <ul>
 *   <li>Declare itself enabled under {@link #OVERRIDE_MOD_ID} (its own mod_info.json id).</li>
 *   <li>Provide a class named exactly {@link #OVERRIDE_PLUGIN_CLASS}, extending
 *       {@code BaseEveryFrameCombatPlugin}, with a public no-arg constructor.</li>
 *   <li>Load after Draconis-Fleet (declare it as a dependency in the override mod's own
 *       mod_info.json) so this class and {@code CarrierDoctrinePlugin} are already on the
 *       classpath when the override mod's own code runs.</li>
 * </ul>
 * Any failure resolving the override - mod not present, class missing, wrong constructor,
 * wrong supertype, anything - falls back to the real {@link CarrierDoctrinePlugin}. A broken or
 * missing harness mod must never be able to break the doctrine for a player who doesn't have it
 * installed; this is the whole reason this indirection exists as its own class rather than being
 * inlined into {@link levianeer.draconis.data.scripts.hullmods.XLII_FortySecond}.
 */
public class CarrierDoctrinePluginResolver {

    private static final Logger log = Global.getLogger(CarrierDoctrinePluginResolver.class);

    public static final String OVERRIDE_MOD_ID = "XLII_carrierDoctrineHarness";
    public static final String OVERRIDE_PLUGIN_CLASS = "levianeer.draconis.harness.ExperimentalCarrierDoctrinePlugin";

    /** Tracked independently of {@code CombatEngineAPI.hasPluginOfClass()} - that method's
     * matching semantics (exact class vs. isInstance) against a dynamically-loaded class aren't
     * verifiable from the decompiled API alone, so this uses its own guard instead of relying
     * on it to recognize either the real or the experimental plugin as "already installed". */
    private static CombatEngineAPI lastResolvedEngine;

    public static void installIfNeeded(CombatEngineAPI engine) {
        if (engine == lastResolvedEngine) return;
        lastResolvedEngine = engine;

        BaseEveryFrameCombatPlugin plugin = resolveOverride();
        if (plugin == null) {
            plugin = new CarrierDoctrinePlugin();
        }
        engine.addPlugin(plugin);
    }

    private static BaseEveryFrameCombatPlugin resolveOverride() {
        try {
            if (!Global.getSettings().getModManager().isModEnabled(OVERRIDE_MOD_ID)) {
                return null;
            }

            Object instance = Class.forName(OVERRIDE_PLUGIN_CLASS).getDeclaredConstructor().newInstance();
            if (!(instance instanceof BaseEveryFrameCombatPlugin)) {
                log.warn("Carrier doctrine override class " + OVERRIDE_PLUGIN_CLASS
                        + " does not extend BaseEveryFrameCombatPlugin - falling back to the built-in doctrine");
                return null;
            }

            log.info("Carrier doctrine override active: " + OVERRIDE_PLUGIN_CLASS);
            return (BaseEveryFrameCombatPlugin) instance;
        } catch (Throwable t) {
            // Deliberately catches everything, including Error subclasses like
            // NoClassDefFoundError - the override mod is untrusted/experimental by definition,
            // so this needs to be isolated from it completely rather than assuming it behaves.
            log.info("Carrier doctrine override not active (" + t.getClass().getSimpleName()
                    + (t.getMessage() != null ? ": " + t.getMessage() : "") + ") - using the built-in doctrine");
            return null;
        }
    }
}
