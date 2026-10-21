package levianeer.draconis.data.scripts.combat.carrierdoctrine;

/**
 * What {@link CarrierDoctrinePlugin} drives, so the legacy {@link CarrierDoctrineAI} and the
 * {@link TaskForceAI} rewrite can be swapped by settings toggle without the plugin knowing
 * which one it holds. Exists only while both implementations are live.
 */
public interface DoctrineController {
    void advance(float amount);
}
