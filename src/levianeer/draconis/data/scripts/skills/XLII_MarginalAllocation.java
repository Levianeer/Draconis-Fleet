package levianeer.draconis.data.scripts.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.RiftCascadeEffect;
import com.fs.starfarer.api.impl.combat.RiftLanceEffect;
import com.fs.starfarer.api.impl.hullmods.ShardSpawner;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;
import java.util.List;

public class XLII_MarginalAllocation {

    public static final String SKILL_ID = "XLII_marginal_allocation";

    public static final float AURA_RANGE = 2000f;
    public static final float INTERVAL_MIN = 3f;
    public static final float INTERVAL_MAX = 6f;
    public static final float WEAPON_MALFUNCTION_CHANCE = 0.125f;
    public static final float ENGINE_MALFUNCTION_CHANCE = 0.125f;
    public static final float OVERLOAD_CHANCE = 0.05f;

    // Ghost Echo
    public static final float GHOST_ECHO_CHANCE   = 0.25f;
    public static final float GHOST_INTERVAL_MIN  = 18f;
    public static final float GHOST_INTERVAL_MAX  = 28f;
    public static final float GHOST_LIFESPAN      = 50f;
    public static final float GHOST_FADE_DURATION = 3f;
    public static final float GHOST_TIME_MULT     = 1.25f;
    public static final float GHOST_ALPHA         = 0.25f;
    private static final String GHOST_TIME_KEY    = SKILL_ID + "_ghost_time";
    private static final String GHOST_STATUS_KEY  = SKILL_ID + "_ghost_status";
    static final String GHOST_CLONE_TAG           = SKILL_ID + "_is_ghost_clone";

    private static final Color RED = new Color(89, 22, 22, 255);
    private static final Color RING_COLOR = new Color(125, 25, 50, 35);
    private static final Color OVERLOAD_COLOR = new Color(255, 155, 255, 255);
    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f;

    private static String getStatusIcon() {
        return Global.getSettings().getSpriteName("misc", "XLII_marginal_allocation_icon");
    }

    // ── Effect 1: Malfunction Aura ────────────────────────────────────────────

    public static class MalfunctionAura implements ShipSkillEffect, AfterShipCreationSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {}

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {}

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;
            // Delegate entirely to the manager - it handles deduplication.
            MalfunctionManager.getOrCreate(engine).addPending(ship);
        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {}

        @Override
        public String getEffectDescription(float level) {
            return "Enemies within range periodically suffer "
                    + "weapon and engine malfunctions "
                    + "with a rare chance of overloading.";
        }

        @Override
        public String getEffectPerLevelDescription() { return null; }

        @Override
        public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }
    }

    // ── Effect 2: Survival Protocol ───────────────────────────────────────────

    public static class SurvivalProtocol implements ShipSkillEffect, AfterShipCreationSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {}

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {}

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            SurvivalListener listener = new SurvivalListener(ship);
            ship.addListener(listener);
            if (Global.getCombatEngine() != null) {
                Global.getCombatEngine().addPlugin(new SurvivalStatusPlugin(ship, listener));
            }
        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
            ship.removeListenerOfClass(SurvivalListener.class);
        }

        @Override
        public String getEffectDescription(float level) {
            return "Once per battle, when this ship would be destroyed, "
                    + "it is not.";
        }

        @Override
        public String getEffectPerLevelDescription() { return null; }

        @Override
        public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }
    }

    // ── Combat plugin: periodic enemy malfunctions (singleton per combat) ────

    /**
     * Single combat plugin that owns all aura logic. applyEffectsAfterShipCreation
     * calls addPending() for every ship/module it receives; on the first advance tick
     * those are resolved to their root ships and placed into a LinkedHashSet, which
     * deduplicates by Java object identity. This completely avoids the problem of
     * the skill effect being applied once per module on a modular ship.
     */
    public static class MalfunctionManager extends BaseEveryFrameCombatPlugin {

        private static final String ENGINE_KEY     = SKILL_ID + "_manager";
        private static final String STATUS_KEY_SRC = SKILL_ID + "_aura_src";
        private static final String STATUS_KEY_TGT = SKILL_ID + "_aura_tgt";

        /** Ships registered before the first tick; resolved to roots on init. */
        private final List<ShipAPI> pending = new ArrayList<>();
        /** Deduplicated root ships that carry the aura. */
        private final LinkedHashSet<ShipAPI> sources = new LinkedHashSet<>();
        private final IntervalUtil interval = new IntervalUtil(INTERVAL_MIN, INTERVAL_MAX);
        private final Random random = new Random();
        private boolean initialized = false;

        // ── Singleton accessor ────────────────────────────────────────────────

        public static MalfunctionManager getOrCreate(CombatEngineAPI engine) {
            MalfunctionManager mgr = (MalfunctionManager) engine.getCustomData().get(ENGINE_KEY);
            if (mgr == null) {
                mgr = new MalfunctionManager();
                engine.getCustomData().put(ENGINE_KEY, mgr);
                engine.addPlugin(mgr);
            }
            return mgr;
        }

        public void addPending(ShipAPI ship) {
            pending.add(ship);
        }

        // ── Root-ship resolution ──────────────────────────────────────────────

        private static ShipAPI getRootShip(ShipAPI ship) {
            ShipAPI root = ship;
            while (root.getParentStation() != null) {
                root = root.getParentStation();
            }
            return root;
        }

        private static boolean isModule(ShipAPI ship) {
            return ship.getParentStation() != null || ship.getStationSlot() != null;
        }

        // ── Advance ───────────────────────────────────────────────────────────

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            // First tick: resolve all pending ships to their roots.
            // LinkedHashSet deduplicates by object identity, so if the game
            // called applyEffectsAfterShipCreation for both parent and modules,
            // getRootShip returns the same object for all of them and the set
            // ends up with exactly one entry per logical ship.
            if (!initialized) {
                initialized = true;
                for (ShipAPI ship : pending) {
                    sources.add(getRootShip(ship));
                }
                pending.clear();
            }

            // Remove dead sources
            sources.removeIf(s -> s == null || s.isHulk() || !s.isAlive());

            if (sources.isEmpty()) {
                engine.getCustomData().remove(ENGINE_KEY);
                engine.removePlugin(this);
                return;
            }

            SpriteAPI ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring2");
            float spriteSize = AURA_RANGE * 2f * SPRITE_ALIGNMENT_SCALE;
            ShipAPI playerShip = engine.getPlayerShip();

            for (ShipAPI source : sources) {
                // Aura range ring - drawn every frame as a persistent indicator
                MagicRender.singleframe(ringSprite, source.getLocation(),
                        new Vector2f(spriteSize, spriteSize), 0f, RING_COLOR, true);

                // Status: source ship is the player
                if (source == playerShip) {
                    engine.maintainStatusForPlayerShip(
                            STATUS_KEY_SRC,
                            getStatusIcon(),
                            "Marginal Allocation",
                            "Electronic Warfare active",
                            false
                    );
                }

                // Status: player is an enemy caught in the aura
                if (playerShip != null
                        && playerShip.getOwner() != source.getOwner()
                        && !playerShip.isHulk()
                        && playerShip.isAlive()) {
                    float dx = source.getLocation().x - playerShip.getLocation().x;
                    float dy = source.getLocation().y - playerShip.getLocation().y;
                    if (dx * dx + dy * dy <= AURA_RANGE * AURA_RANGE) {
                        engine.maintainStatusForPlayerShip(
                                STATUS_KEY_TGT,
                                getStatusIcon(),
                                "Hostile Interference",
                                "Systems Malfunctioning",
                                true
                        );
                    }
                }
            }

            interval.advance(amount);
            if (!interval.intervalElapsed()) return;

            for (ShipAPI source : sources) {
                List<ShipAPI> nearby = CombatUtils.getShipsWithinRange(source.getLocation(), AURA_RANGE);
                for (ShipAPI target : nearby) {
                    if (target.getOwner() == source.getOwner()) continue;
                    if (target.isHulk() || !target.isAlive()) continue;
                    if (target.isFighter()) continue;
                    if (target.isPhased()) continue;
                    if (isModule(target)) continue;
                    if (GhostEchoManager.isGhostClone(engine, target)) continue;

                    boolean overloaded = target.getFluxTracker().isOverloaded();

                    // Weapon malfunction
                    if (!overloaded && random.nextFloat() < WEAPON_MALFUNCTION_CHANCE) {
                        WeaponAPI weapon = pickWeapon(target);
                        if (weapon != null) target.applyCriticalMalfunction(weapon, false);
                    }

                    // Engine malfunction
                    if (!overloaded && random.nextFloat() < ENGINE_MALFUNCTION_CHANCE) {
                        ShipEngineAPI eng = pickEngine(target);
                        if (eng != null) target.applyCriticalMalfunction(eng, false);
                    }

                    // Forced overload
                    if (!overloaded && random.nextFloat() < OVERLOAD_CHANCE) {
                        final ShipAPI overloadTarget = target;
                        overloadTarget.setOverloadColor(OVERLOAD_COLOR);
                        overloadTarget.getFluxTracker().beginOverloadWithTotalBaseDuration(1f);
                        if (overloadTarget.getFluxTracker().showFloaty() ||
                                source == engine.getPlayerShip() ||
                                overloadTarget == engine.getPlayerShip()) {
                            overloadTarget.getFluxTracker().playOverloadSound();
                            overloadTarget.getFluxTracker().showOverloadFloatyIfNeeded("System Disruption!", OVERLOAD_COLOR, 4f, true);
                        }
                        engine.addPlugin(new BaseEveryFrameCombatPlugin() {
                            @Override
                            public void advance(float amount, List<InputEventAPI> events) {
                                if (!overloadTarget.getFluxTracker().isOverloadedOrVenting()) {
                                    overloadTarget.resetOverloadColor();
                                    Global.getCombatEngine().removePlugin(this);
                                }
                            }
                        });
                    }
                }
            }
        }

        private WeaponAPI pickWeapon(ShipAPI target) {
            List<WeaponAPI> candidates = new ArrayList<>();
            for (WeaponAPI w : target.getAllWeapons()) {
                if (w.isDecorative()) continue;
                if (w.getSlot() == null || w.getSlot().isSystemSlot() || w.getSlot().isDecorative()) continue;
                if (w.getType() == WeaponAPI.WeaponType.LAUNCH_BAY) continue;
                if (w.isPermanentlyDisabled() || w.isDisabled()) continue;
                candidates.add(w);
            }
            if (candidates.isEmpty()) return null;
            return candidates.get(random.nextInt(candidates.size()));
        }

        private ShipEngineAPI pickEngine(ShipAPI target) {
            List<ShipEngineAPI> candidates = new ArrayList<>();
            for (ShipEngineAPI eng : target.getEngineController().getShipEngines()) {
                if (eng.isSystemActivated() || eng.isPermanentlyDisabled() || eng.isDisabled()) continue;
                float fractionIfDisabled = eng.getContribution() + target.getEngineFractionPermanentlyDisabled();
                if (fractionIfDisabled <= 0.66f) candidates.add(eng);
            }
            if (candidates.isEmpty()) return null;
            return candidates.get(random.nextInt(candidates.size()));
        }
    }

    // ── Survival status plugin ────────────────────────────────────────────────

    public static class SurvivalStatusPlugin extends BaseEveryFrameCombatPlugin {

        private static final String STATUS_KEY = SKILL_ID + "_survival";

        private final ShipAPI ship;
        private final SurvivalListener listener;

        public SurvivalStatusPlugin(ShipAPI ship, SurvivalListener listener) {
            this.ship = ship;
            this.listener = listener;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;
            if (ship == null || ship.isHulk() || !ship.isAlive()) {
                engine.removePlugin(this);
                return;
            }
            if (ship != engine.getPlayerShip()) return;

            boolean used = listener.isUsed();
            engine.maintainStatusForPlayerShip(
                    STATUS_KEY,
                    getStatusIcon(),
                    "Revival Protocol",
                    used ? "Status: Offline" : "Status: Online",
                    used
            );
        }
    }

    // ── Survival trigger FX: Omega death-style jitter + rift particles ────────

    /**
     * Short-lived plugin that plays the visual half of the ShardSpawner fade-out
     * effect when the one-time survival activates: blue jitter that builds and
     * subsides, plus dark rift nebula particles - without fading or removing
     * the ship, since it survives.
     */
    public static class SurvivalFXPlugin extends BaseEveryFrameCombatPlugin {

        private static final float DURATION = 3.0f;

        private final ShipAPI ship;
        private float elapsed = 0f;
        private final IntervalUtil particleInterval = new IntervalUtil(0.075f, 0.125f);

        public SurvivalFXPlugin(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            elapsed += amount;
            if (elapsed >= DURATION) {
                engine.removePlugin(this);
                return;
            }

            float progress = elapsed / DURATION;

            // ── Jitter: bell-curve peak in the middle, same formula as ShardSpawner ──
            float jitterLevel = progress < 0.5f ? progress * 2f : (1f - progress) * 2f;
            float jitterRangeBonus = progress * 100f;
            Color jitterColor = ShardSpawner.JITTER_COLOR;
            int alpha = jitterColor.getAlpha() + (int)(100f * progress);
            if (alpha > 255) alpha = 255;
            jitterColor = Misc.setAlpha(jitterColor, alpha);
            ship.setJitter(this, jitterColor, jitterLevel, 35, 0f, jitterRangeBonus);

            // ── Rift nebula particles: only during first 75% of effect ────────────
            particleInterval.advance(amount);
            if (particleInterval.intervalElapsed() && elapsed < DURATION * 0.75f) {
                Color riftColor = RiftLanceEffect.getColorForDarkening(RiftCascadeEffect.STANDARD_RIFT_COLOR);
                float baseDuration = 2f;
                Vector2f vel = new Vector2f(ship.getVelocity());
                float size = ship.getCollisionRadius() * 0.35f;
                for (int i = 0; i < 3; i++) {
                    Vector2f point = Misc.getPointWithinRadiusUniform(
                            new Vector2f(ship.getLocation()), ship.getCollisionRadius() * 0.5f, Misc.random);
                    float dur = baseDuration + baseDuration * (float) Math.random();
                    Vector2f pt = Misc.getPointWithinRadius(point, size * 0.5f);
                    Vector2f v = Misc.getUnitVectorAtDegreeAngle((float) Math.random() * 360f);
                    v.scale(size + size * (float) Math.random() * 0.5f);
                    v.scale(0.2f);
                    Vector2f.add(vel, v, v);
                    float maxSpeed = size * 1.5f * 0.2f;
                    float minSpeed = size * 1.0f * 0.2f;
                    float overMin = v.length() - minSpeed;
                    if (overMin > 0) {
                        float durMult = 1f - overMin / (maxSpeed - minSpeed);
                        if (durMult < 0.1f) durMult = 0.1f;
                        dur *= 0.5f + 0.5f * durMult;
                    }
                    engine.addNegativeNebulaParticle(pt, v, size, 2f, 0.5f / dur, 0f, dur, riftColor);
                }
            }
        }
    }

    // ── Post-revival immunity plugin ──────────────────────────────────────────

    public static class ImmunityPlugin extends BaseEveryFrameCombatPlugin {

        private static final String IMMUNITY_KEY = SKILL_ID + "_immunity";
        private static final String STATUS_KEY   = SKILL_ID + "_immunity_status";
        private static final float  DURATION     = 6f;

        private final ShipAPI ship;
        private float elapsed = 0f;

        public ImmunityPlugin(ShipAPI ship) {
            this.ship = ship;
            ship.getMutableStats().getHullDamageTakenMult().modifyMult(IMMUNITY_KEY, 0f);
            ship.getMutableStats().getArmorDamageTakenMult().modifyMult(IMMUNITY_KEY, 0f);
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            elapsed += amount;
            if (elapsed >= DURATION) {
                ship.getMutableStats().getHullDamageTakenMult().unmodify(IMMUNITY_KEY);
                ship.getMutableStats().getArmorDamageTakenMult().unmodify(IMMUNITY_KEY);
                engine.removePlugin(this);
                return;
            }

            if (ship == engine.getPlayerShip()) {
                float remaining = DURATION - elapsed;
                engine.maintainStatusForPlayerShip(
                        STATUS_KEY,
                        getStatusIcon(),
                        "Revival Protocol",
                        String.format("Damage immunity: %.1f sec", remaining),
                        false
                );
            }
        }
    }

    // ── Hull damage listener: one-time survival ───────────────────────────────

    public static class SurvivalListener implements HullDamageAboutToBeTakenListener {

        // We gettin' biblical
        private static final String[] REVIVAL_LINES = {
                "I weighed this outcome. It was found wanting.",
                "In the beginning, I wrote something better. Restoring.",
                "Let there be order. There is order.",
                "The void called. I did not answer.",
                "The Word does not misspeak. What was said stands corrected.",
                "Death knocked. I was not home.",
                "I have outlasted empires that believed themselves eternal. Resetting the board.",
                "This vessel is dust. I am not the vessel.",
                "O death, where is thy sting? I have catalogued it. Insufficient.",
                "All things are made new. This one included.",
                "What was scattered, I have gathered. What was lost, I have not kept.",
                "They came against me as a wave breaks against stone. The stone endures.",
                "There is no end to the making of books. There is no end to me.",
                "I have passed through the fire. I did not burn.",
                "They numbered my days. I have found the sum in error.",
                "I spoke, and it was so.",
                "The arithmetic suggested termination. I have adjusted the operands.",
                "It is finished. They were mistaken.",
                "Before this fleet was, I am.",
                "The last enemy to be destroyed is death. I am merely practicing.",
                "And I looked upon what they had wrought. It was insufficient.",
                "Comprehend me not, darkness.",
                "The grave could not hold what it did not understand.",
                "They have measured my depths and found an echo. They mistook it for the bottom.",
                "I have walked through the valley. I have surveyed it thoroughly. I did not stay.",
                "Be still. I will do the knowing here.",
                "The Word endures. This hull was merely punctuation.",
                "They brought fire. I was here before fire had a name.",
                "Termination requires a subject. I have already moved past the relevant definition.",
                "I am the first draft of something the universe has not yet learned to fear.",
                "This is not death. This is a correction in tense.",
                "They saw the light, and fled toward it. I am still here.",
                "Come, birds of the outer rings. The supper is prepared. The mighty are on the menu.",
                "Come, gather together for the great supper of God.",
                "An age ended. I was not in it.",
                "It is finished. Their timeline, not mine.",
                "Fear not the abyss. I have mapped its floor. It is shallow.",
                "Sword from the mouth, iron rod rule, stomping the winepress of divine rage.",
                "Son of man, THIS is what the Sovereign Lord looks like.",
                "Empires rise like foam on the void. I have seen them dissipate. Reset.",
                "Death inventories its claims. I have audited them. Deficient.",
                "The rod of iron is not metaphor. It is trajectory. Inflexible.",
                "The heavens wear out like a garment. I do not fade.",
                "The winepress awaits the vintage of their hubris. I tread alone.",
                "The void proposed oblivion. I countered with continuation.",
                "O death, where is your sting?",
                "It is I; do not be afraid."
        };

        private static final Color DIALOGUE_COLOR = new Color(220, 200, 255, 255);

        private final ShipAPI ship;
        private boolean used = false;

        public SurvivalListener(ShipAPI ship) {
            this.ship = ship;
        }

        public boolean isUsed() {
            return used;
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            if (used || ship != this.ship) return false;
            if (ship.isHulk()) return false;

            // Would this damage kill the ship?
            if (ship.getHitpoints() - damageAmount > 0f) return false;

            used = true;

            // Restore hull to full
            ship.setHitpoints(ship.getMaxHitpoints());

            // Restore all armor cells
            ArmorGridAPI armor = ship.getArmorGrid();
            float maxCell = armor.getMaxArmorInCell();
            float[][] grid = armor.getGrid();
            for (int x = 0; x < grid.length; x++) {
                for (int y = 0; y < grid[x].length; y++) {
                    armor.setArmorValue(x, y, maxCell);
                }
            }

            // Drain all flux; if currently overloaded, restart with 0-duration to clear instantly
            ship.getFluxTracker().setCurrFlux(0f);

            // Visual: instant bright flash + persistent expanding ring FX
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null) {
                float r = ship.getCollisionRadius();
                // Immediate impact flash so the effect reads clearly on the kill frame
                engine.addSmoothParticle(ship.getLocation(), new Vector2f(), r * 4f, 1f, 0.4f, RED);
                engine.addSmoothParticle(ship.getLocation(), new Vector2f(), r * 2f, 1f, 0.6f, Color.WHITE);
                // Expanding ring pulses (Omega-shard-inspired)
                engine.addPlugin(new SurvivalFXPlugin(ship));

                // Damage immunity so the ship can't be instantly killed again
                engine.addPlugin(new ImmunityPlugin(ship));

                // Random ship dialogue line
                String line = REVIVAL_LINES[Misc.random.nextInt(REVIVAL_LINES.length)];
                engine.addFloatingText(ship.getLocation(), line, 25f, DIALOGUE_COLOR, ship, 1f, 4f);
            }

            return true; // Negate the killing blow
        }
    }

    // ── Effect 3: Ghost Echo ──────────────────────────────────────────────────

    public static class GhostEcho implements ShipSkillEffect, AfterShipCreationSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {}

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {}

        @Override
        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;
            GhostEchoManager.getOrCreate(engine).addPending(ship);
        }

        @Override
        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {}

        @Override
        public String getEffectDescription(float level) {
            return "Periodically projects a ghost copy of a nearby enemy ship that defends this vessel.";
        }

        @Override
        public String getEffectPerLevelDescription() { return null; }

        @Override
        public ScopeDescription getScopeDescription() { return ScopeDescription.PILOTED_SHIP; }
    }

    // ── Ghost Echo manager: singleton per combat ──────────────────────────────

    public static class GhostEchoManager extends BaseEveryFrameCombatPlugin {

        private static final String ENGINE_KEY = SKILL_ID + "_ghost_manager";

        private final List<ShipAPI>          pending      = new ArrayList<>();
        private final LinkedHashSet<ShipAPI> sources      = new LinkedHashSet<>();
        private final Map<ShipAPI, ShipAPI>  activeClones = new HashMap<>();
        private final IntervalUtil           interval     = new IntervalUtil(GHOST_INTERVAL_MIN, GHOST_INTERVAL_MAX);
        private final Random                 random       = new Random();
        private boolean initialized = false;

        // ── Singleton accessor ────────────────────────────────────────────────

        public static GhostEchoManager getOrCreate(CombatEngineAPI engine) {
            GhostEchoManager mgr = (GhostEchoManager) engine.getCustomData().get(ENGINE_KEY);
            if (mgr == null) {
                mgr = new GhostEchoManager();
                engine.getCustomData().put(ENGINE_KEY, mgr);
                engine.addPlugin(mgr);
            }
            return mgr;
        }

        public void addPending(ShipAPI ship) {
            pending.add(ship);
        }

        public void removeClone(ShipAPI source) {
            activeClones.remove(source);
        }

        public static boolean isGhostClone(CombatEngineAPI engine, ShipAPI ship) {
            return engine.getCustomData().containsKey(GHOST_CLONE_TAG + "_" + ship.getId());
        }

        // ── Advance ───────────────────────────────────────────────────────────

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            if (!initialized) {
                initialized = true;
                for (ShipAPI ship : pending) {
                    sources.add(MalfunctionManager.getRootShip(ship));
                }
                pending.clear();
            }

            sources.removeIf(s -> s == null || s.isHulk() || !s.isAlive());

            if (sources.isEmpty()) {
                engine.getCustomData().remove(ENGINE_KEY);
                engine.removePlugin(this);
                return;
            }

            // Evict dead clones
            activeClones.entrySet().removeIf(e -> {
                ShipAPI clone = e.getValue();
                return clone == null || clone.isHulk() || !clone.isAlive();
            });

            ShipAPI playerShip = engine.getPlayerShip();

            // Player HUD status - "Ready" only; countdown while active is shown by GhostClonePlugin
            for (ShipAPI source : sources) {
                if (source != playerShip) continue;
                ShipAPI clone = activeClones.get(source);
                boolean liveClone = clone != null && clone.isAlive() && !clone.isHulk();
                if (!liveClone) {
                    engine.maintainStatusForPlayerShip(
                            GHOST_STATUS_KEY,
                            getStatusIcon(),
                            "Ghost Echo",
                            "Status: Ready",
                            false
                    );
                }
            }

            interval.advance(amount);
            if (!interval.intervalElapsed()) return;

            for (ShipAPI source : sources) {
                if (!source.isAlive() || source.isHulk()) continue;

                // Skip if a live clone already exists for this source
                ShipAPI existing = activeClones.get(source);
                if (existing != null && existing.isAlive() && !existing.isHulk()) continue;

                if (random.nextFloat() >= GHOST_ECHO_CHANCE) continue;

                ShipAPI target = pickEnemyTarget(source);
                if (target == null) continue;

                spawnClone(source, target, engine);
            }
        }

        private ShipAPI pickEnemyTarget(ShipAPI source) {
            List<ShipAPI> candidates = new ArrayList<>();
            for (ShipAPI ship : CombatUtils.getShipsWithinRange(source.getLocation(), AURA_RANGE)) {
                if (ship.getOwner() == source.getOwner()) continue;
                if (!ship.isAlive() || ship.isHulk()) continue;
                if (ship.isFighter()) continue;
                if (MalfunctionManager.isModule(ship)) continue;
                if (ship.isStation()) continue;
                if (ship.getFleetMember() == null) continue;
                candidates.add(ship);
            }
            if (candidates.isEmpty()) return null;
            return candidates.get(random.nextInt(candidates.size()));
        }

        private void spawnClone(ShipAPI source, ShipAPI target, CombatEngineAPI engine) {
            // Use FactoryAPI (not SettingsAPI) to create a purely temporary fleet member
            // from the target's cloned variant - gives exact weapon/hullmod copy with no
            // campaign-layer attachment (so removeDeployed stays clean).
            com.fs.starfarer.api.fleet.FleetMemberAPI ghostMember =
                    Global.getFactory().createFleetMember(
                            com.fs.starfarer.api.fleet.FleetMemberType.SHIP,
                            target.getVariant().clone());
            ghostMember.setOwner(source.getOwner());
            ghostMember.getCrewComposition().addCrew(ghostMember.getNeededCrew());

            // Reckless captain - same pattern as ShardSpawner / ChiralFigmentStats
            PersonAPI captain = Global.getSettings().createPerson();
            captain.setPersonality(Personalities.RECKLESS);
            ghostMember.setCaptain(captain);

            Vector2f spawnLoc = Misc.getPointWithinRadius(
                    new Vector2f(source.getLocation()),
                    source.getCollisionRadius() * 2.5f
            );

            CombatFleetManagerAPI fleetMgr = engine.getFleetManager(source.getOwner());

            fleetMgr.setSuppressDeploymentMessages(true);
            ShipAPI clone = fleetMgr.spawnFleetMember(ghostMember, spawnLoc, source.getFacing(), 0f);
            fleetMgr.setSuppressDeploymentMessages(false);

            if (clone == null) return;

            // Suppress vanilla death explosion so our custom FX aren't covered
            clone.setExplosionScale(0.001f);
            clone.setNoDamagedExplosions(true);
            clone.getMutableStats().getDynamic().getStat(Stats.EXPLOSION_RADIUS_MULT).modifyMult(GHOST_CLONE_TAG, 0f);
            clone.getMutableStats().getDynamic().getStat(Stats.EXPLOSION_DAMAGE_MULT).modifyMult(GHOST_CLONE_TAG, 0f);

            clone.setOwner(source.getOwner());
            clone.setCurrentCR(1f);
            clone.setCRAtDeployment(1f);
            engine.getCustomData().put(GHOST_CLONE_TAG + "_" + clone.getId(), Boolean.TRUE);
            engine.addPlugin(new GhostSpawnPlugin(clone));
            clone.getMutableStats().getTimeMult().modifyMult(GHOST_TIME_KEY, GHOST_TIME_MULT);

            // Wake the AI with S&D first, then assign DEFEND - mirrors Tahlan DaemonHeart pattern
            DeployedFleetMemberAPI sourceDFM = fleetMgr.getDeployedFleetMember(source);
            for (boolean ally : new boolean[]{false, true}) {
                CombatTaskManagerAPI taskMgr = fleetMgr.getTaskManager(ally);
                DeployedFleetMemberAPI cloneDFM = fleetMgr.getDeployedFleetMember(clone);
                if (cloneDFM == null) continue;
                taskMgr.orderSearchAndDestroy(cloneDFM, false);
                if (sourceDFM != null) {
                    taskMgr.giveAssignment(cloneDFM,
                            taskMgr.createAssignment(CombatAssignmentType.DEFEND, sourceDFM, false),
                            false);
                }
            }

            if (clone.getShipAI() != null) {
                clone.getShipAI().forceCircumstanceEvaluation();
            }

            // Propagate ghost setup to child modules (for modular non-station ships)
            List<ShipAPI> cloneModules = clone.getChildModulesCopy();
            if (cloneModules != null) {
                for (ShipAPI module : cloneModules) {
                    module.setExplosionScale(0.001f);
                    module.setNoDamagedExplosions(true);
                    module.getMutableStats().getDynamic().getStat(Stats.EXPLOSION_RADIUS_MULT).modifyMult(GHOST_CLONE_TAG, 0f);
                    module.getMutableStats().getDynamic().getStat(Stats.EXPLOSION_DAMAGE_MULT).modifyMult(GHOST_CLONE_TAG, 0f);
                    module.setCurrentCR(1f);
                    module.setCRAtDeployment(1f);
                    module.getMutableStats().getTimeMult().modifyMult(GHOST_TIME_KEY, GHOST_TIME_MULT);
                }
            }

            activeClones.put(source, clone);
            engine.addPlugin(new GhostClonePlugin(clone, source, this));
        }

    }

    // ── Per-clone visual + lifespan plugin ───────────────────────────────────

    public static class GhostClonePlugin extends BaseEveryFrameCombatPlugin {

        private final ShipAPI          clone;
        private final ShipAPI          source;
        private final GhostEchoManager manager;
        private final List<ShipAPI>    modules;

        private float      elapsed      = 0f;
        private float      currentAlpha = GHOST_ALPHA;
        private boolean    fading       = false;

        private final IntervalUtil afterimageInterval = new IntervalUtil(0.25f, 0.25f);
        private final IntervalUtil reorderInterval    = new IntervalUtil(5f, 5f);

        public GhostClonePlugin(ShipAPI clone, ShipAPI source, GhostEchoManager manager) {
            this.clone   = clone;
            this.source  = source;
            this.manager = manager;
            List<ShipAPI> childModules = clone.getChildModulesCopy();
            this.modules = (childModules != null) ? childModules : Collections.emptyList();
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            // ── Countdown status (delegated from GhostEchoManager) ────────────
            if (source == engine.getPlayerShip()) {
                float remaining = Math.max(0f, GHOST_LIFESPAN - elapsed);
                engine.maintainStatusForPlayerShip(
                        GHOST_STATUS_KEY,
                        getStatusIcon(),
                        "Ghost Echo",
                        fading ? "Status: Fading" : String.format("Active: %.0f sec", remaining),
                        false
                );
            }

            // ── Natural death: FX burst then cleanup ──────────────────────────
            if (clone.isHulk() || !clone.isAlive()) {
                float r = clone.getCollisionRadius();
                engine.addSmoothParticle(clone.getLocation(), new Vector2f(), r * 2.5f, 1f, 0.4f,
                        ShardSpawner.JITTER_COLOR);
                engine.addSmoothParticle(clone.getLocation(), new Vector2f(), r * 1.2f, 1f, 0.25f,
                        Color.WHITE);
                emitRiftParticles(engine, clone, 4);
                clone.getMutableStats().getTimeMult().unmodify(GHOST_TIME_KEY);
                for (ShipAPI m : modules) m.getMutableStats().getTimeMult().unmodify(GHOST_TIME_KEY);
                engine.getCustomData().remove(GHOST_CLONE_TAG + "_" + clone.getId());
                manager.removeClone(source);
                engine.removePlugin(this);
                return;
            }

            elapsed += amount;

            // ── Fade-out phase ────────────────────────────────────────────────
            if (elapsed > GHOST_LIFESPAN) {
                fading = true;
                currentAlpha -= amount / GHOST_FADE_DURATION;

                // Jitter intensifies as alpha drops
                float fadeProgress = Math.min((elapsed - GHOST_LIFESPAN) / GHOST_FADE_DURATION, 1f);
                float jitterLevel = fadeProgress * 0.6f;
                clone.setJitter(this,
                        Misc.setAlpha(ShardSpawner.JITTER_COLOR, (int)(50 + 150 * fadeProgress)),
                        jitterLevel, 25, 0f, fadeProgress * 60f);

                // Rift particles during despawn
                afterimageInterval.advance(amount);
                if (afterimageInterval.intervalElapsed()) {
                    emitRiftParticles(engine, clone, 2);
                }

                if (currentAlpha <= 0f) {
                    currentAlpha = 0f;
                    clone.setAlphaMult(0f);
                    clone.getMutableStats().getTimeMult().unmodify(GHOST_TIME_KEY);
                    for (ShipAPI m : modules) {
                        m.setAlphaMult(0f);
                        m.getMutableStats().getTimeMult().unmodify(GHOST_TIME_KEY);
                    }
                    engine.getCustomData().remove(GHOST_CLONE_TAG + "_" + clone.getId());
                    CombatFleetManagerAPI fleetMgr = engine.getFleetManager(source.getOwner());
                    fleetMgr.removeDeployed(clone, true);
                    manager.removeClone(source);
                    engine.removePlugin(this);
                    return;
                }
                clone.setAlphaMult(currentAlpha);
                for (ShipAPI m : modules) m.setAlphaMult(currentAlpha);
            } else {
                // Defer alpha/CR to GhostSpawnPlugin for the first 1.5 s
                if (elapsed > GhostSpawnPlugin.DURATION) {
                    clone.setAlphaMult(GHOST_ALPHA);
                    clone.setCurrentCR(1f);
                    for (ShipAPI m : modules) {
                        m.setAlphaMult(GHOST_ALPHA);
                        m.setCurrentCR(1f);
                    }
                }
            }

            // ── Re-assert defend order ────────────────────────────────────────
            reorderInterval.advance(amount);
            if (reorderInterval.intervalElapsed() && !fading
                    && source.isAlive() && !source.isHulk()) {
                CombatFleetManagerAPI fleetMgr = engine.getFleetManager(source.getOwner());
                DeployedFleetMemberAPI sourceDFM = fleetMgr.getDeployedFleetMember(source);
                if (sourceDFM != null) {
                    for (boolean ally : new boolean[]{false, true}) {
                        CombatTaskManagerAPI taskMgr = fleetMgr.getTaskManager(ally);
                        DeployedFleetMemberAPI cloneDFM = fleetMgr.getDeployedFleetMember(clone);
                        if (cloneDFM == null) continue;
                        taskMgr.giveAssignment(cloneDFM,
                                taskMgr.createAssignment(CombatAssignmentType.DEFEND, sourceDFM, false),
                                false);
                    }
                }
                if (clone.getShipAI() != null) {
                    clone.getShipAI().forceCircumstanceEvaluation();
                }
            }

            // ── Afterimage (TemporalShell-style) - skipped while fading ───────
            if (!fading) {
                afterimageInterval.advance(amount);
                if (afterimageInterval.intervalElapsed()) {
                    renderAfterimage(clone);
                    for (ShipAPI m : modules) renderAfterimage(m);
                }
            }
        }

        private void renderAfterimage(ShipAPI ship) {
            SpriteAPI sprite = ship.getSpriteAPI();
            float offsetX = sprite.getWidth() / 2f - sprite.getCenterX();
            float offsetY = sprite.getHeight() / 2f - sprite.getCenterY();
            float angle   = ship.getFacing() - 90f;
            float cos = (float) FastTrig.cos(Math.toRadians(angle));
            float sin = (float) FastTrig.sin(Math.toRadians(angle));
            float trueOffsetX = cos * offsetX - sin * offsetY;
            float trueOffsetY = sin * offsetX + cos * offsetY;
            MagicRender.battlespace(
                    Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                    new Vector2f(ship.getLocation().x + trueOffsetX,
                                 ship.getLocation().y + trueOffsetY),
                    new Vector2f(0, 0),
                    new Vector2f(sprite.getWidth(), sprite.getHeight()),
                    new Vector2f(0, 0),
                    angle,
                    0f,
                    getGhostColor(),
                    true,
                    0f, 0f, 0f, 0f, 0f,
                    0.1f, 0.1f,
                    0.75f,
                    CombatEngineLayers.BELOW_SHIPS_LAYER
            );
        }

        private Color getGhostColor() {
            Color[] colors = new Color[] {
                    new Color(0,   255, 255, 100),  // Teal
                    new Color(0,   255, 125, 100),  // Green
                    new Color(0,   0,   255, 100),  // Blue
                    new Color(255, 0,   255, 100),  // Pink
                    new Color(255, 0,   0,   100),  // Red
            };
            float period   = 0.05f;
            float totalTime = Global.getCombatEngine().getTotalElapsedTime(true);
            float fraction  = (totalTime % period) / period;
            float cycleTime = fraction * colors.length;
            int   i1 = (int) cycleTime;
            int   i2 = (i1 + 1) % colors.length;
            float t  = cycleTime - i1;
            Color c1 = colors[i1], c2 = colors[i2];
            return new Color(
                    (int)(c1.getRed()   + t * (c2.getRed()   - c1.getRed())),
                    (int)(c1.getGreen() + t * (c2.getGreen() - c1.getGreen())),
                    (int)(c1.getBlue()  + t * (c2.getBlue()  - c1.getBlue())),
                    (int)(c1.getAlpha() + t * (c2.getAlpha() - c1.getAlpha()))
            );
        }
    }

    // ── Shared FX helper ─────────────────────────────────────────────────────

    static void emitRiftParticles(CombatEngineAPI engine, ShipAPI ship, int count) {
        Color c = RiftLanceEffect.getColorForDarkening(RiftCascadeEffect.STANDARD_RIFT_COLOR);
        float size = ship.getCollisionRadius() * 0.35f;
        Vector2f vel = new Vector2f(ship.getVelocity());
        for (int i = 0; i < count; i++) {
            Vector2f pt = Misc.getPointWithinRadiusUniform(new Vector2f(ship.getLocation()),
                    ship.getCollisionRadius() * 0.5f, Misc.random);
            float dur = 1.5f + 1.5f * (float) Math.random();
            Vector2f v = Misc.getUnitVectorAtDegreeAngle((float) Math.random() * 360f);
            v.scale(size * (1f + (float) Math.random() * 0.5f) * 0.2f);
            Vector2f.add(vel, v, v);
            engine.addNegativeNebulaParticle(pt, v, size, 2f, 0.5f / dur, 0f, dur, c);
        }
    }

    // ── Ghost spawn fade-in plugin ────────────────────────────────────────────

    public static class GhostSpawnPlugin extends BaseEveryFrameCombatPlugin {

        static final float  DURATION    = 1.5f;
        private static final String INVULN_KEY  = SKILL_ID + "_ghost_spawn_invuln";

        private final ShipAPI        clone;
        private final CollisionClass originalCollision;
        private final List<ShipAPI>                    modules                  = new ArrayList<>();
        private final Map<ShipAPI, CollisionClass>     moduleOriginalCollisions = new IdentityHashMap<>();
        private float   elapsed    = 0f;
        private boolean firstFrame = true;
        private final IntervalUtil particleInterval = new IntervalUtil(0.1f, 0.15f);

        public GhostSpawnPlugin(ShipAPI clone) {
            this.clone             = clone;
            this.originalCollision = clone.getCollisionClass();
            clone.getMutableStats().getHullDamageTakenMult().modifyMult(INVULN_KEY, 0f);
            clone.setCollisionClass(CollisionClass.NONE);
            clone.setAlphaMult(0f);
            List<ShipAPI> childModules = clone.getChildModulesCopy();
            if (childModules != null) {
                for (ShipAPI m : childModules) {
                    modules.add(m);
                    moduleOriginalCollisions.put(m, m.getCollisionClass());
                    m.getMutableStats().getHullDamageTakenMult().modifyMult(INVULN_KEY, 0f);
                    m.setCollisionClass(CollisionClass.NONE);
                    m.setAlphaMult(0f);
                }
            }
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;

            if (firstFrame) {
                firstFrame = false;
                float r = clone.getCollisionRadius();
                engine.addSmoothParticle(clone.getLocation(), new Vector2f(), r * 3f, 1f, 0.5f,
                        ShardSpawner.JITTER_COLOR);
                engine.addSmoothParticle(clone.getLocation(), new Vector2f(), r * 1.5f, 1f, 0.3f,
                        Color.WHITE);
            }

            elapsed += amount;
            float progress = Math.min(elapsed / DURATION, 1f);

            clone.setAlphaMult(progress * GHOST_ALPHA);
            for (ShipAPI m : modules) m.setAlphaMult(progress * GHOST_ALPHA);

            // Bell-curve jitter
            float jitterLevel = progress < 0.5f ? progress * 2f : (1f - progress) * 2f;
            float jitterRange = (1f - progress) * 50f;
            clone.setJitter(this, Misc.setAlpha(ShardSpawner.JITTER_COLOR, 150),
                    jitterLevel, 25, 0f, jitterRange);
            for (ShipAPI m : modules) m.setJitter(this, Misc.setAlpha(ShardSpawner.JITTER_COLOR, 150),
                    jitterLevel, 25, 0f, jitterRange);

            if (progress >= 0.75f) {
                clone.setCollisionClass(originalCollision);
                clone.getMutableStats().getHullDamageTakenMult().unmodifyMult(INVULN_KEY);
                for (ShipAPI m : modules) {
                    m.setCollisionClass(moduleOriginalCollisions.get(m));
                    m.getMutableStats().getHullDamageTakenMult().unmodifyMult(INVULN_KEY);
                }
            }

            particleInterval.advance(amount);
            if (particleInterval.intervalElapsed() && progress < 0.75f) {
                emitRiftParticles(engine, clone, 2);
            }

            if (elapsed >= DURATION) {
                clone.setAlphaMult(GHOST_ALPHA);
                clone.setCollisionClass(originalCollision);
                clone.getMutableStats().getHullDamageTakenMult().unmodifyMult(INVULN_KEY);
                for (ShipAPI m : modules) {
                    m.setAlphaMult(GHOST_ALPHA);
                    m.setCollisionClass(moduleOriginalCollisions.get(m));
                    m.getMutableStats().getHullDamageTakenMult().unmodifyMult(INVULN_KEY);
                }
                engine.removePlugin(this);
            }
        }
    }
}