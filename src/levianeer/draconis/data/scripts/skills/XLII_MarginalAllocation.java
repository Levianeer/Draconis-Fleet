package levianeer.draconis.data.scripts.skills;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.ShipSkillEffect;
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
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

public class XLII_MarginalAllocation {

    public static final String SKILL_ID = "XLII_marginal_allocation";

    public static final float AURA_RANGE = 2500f;
    public static final float INTERVAL_MIN = 3f;
    public static final float INTERVAL_MAX = 6f;
    public static final float WEAPON_MALFUNCTION_CHANCE = 0.1f;
    public static final float ENGINE_MALFUNCTION_CHANCE = 0.1f;
    public static final float OVERLOAD_CHANCE = 0.01f;

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
            // Delegate entirely to the manager — it handles deduplication.
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
                    + "all hull and armor are fully restored and the killing blow is negated";
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
                // Aura range ring — drawn every frame as a persistent indicator
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
     * subsides, plus dark rift nebula particles — without fading or removing
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
                "Reality bends to my will.",
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
                "An age ended. I was not in it."
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
}