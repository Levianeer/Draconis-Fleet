package levianeer.draconis.data.scripts.combat.carrierdoctrine;

/**
 * What {@link CarrierDoctrinePlugin} drives, so {@link DraconisAdmiralAI} and the legacy
 * {@link CarrierDoctrineAI} can be swapped by settings toggle without the plugin knowing which one
 * it holds. Exists only while both implementations are live.
 */
public interface DoctrineController {
    void advance(float amount);
}
