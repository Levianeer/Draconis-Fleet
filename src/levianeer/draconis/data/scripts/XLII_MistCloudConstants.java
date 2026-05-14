package levianeer.draconis.data.scripts;

public final class XLII_MistCloudConstants {

    public static final float CLOUD_RADIUS = 750f;
    public static final float CLOUD_RADIUS_SQ = CLOUD_RADIUS * CLOUD_RADIUS;

    // Cloud lifetime
    public static final float CLOUD_MIN_LIFETIME = 30f;
    public static final float CLOUD_MAX_LIFETIME = 60f;

    // Per-second heal/damage rates (as fraction of max hull)   // 1.0f = 100%
    public static final float HEAL_PERCENT_PER_SEC = 0.02f;     // 2% /s
    public static final float DOT_PERCENT_PER_SEC  = 0.01f;     // 1% /s

    // HP pool per cloud - both healing and damage drain this pool; exhausted clouds despawn
    public static final float CLOUD_POOL_HP = 3500f;

    // Activation thresholds
    public static final float MIN_XLII_PERCENTAGE  = 0.25f;
    public static final int   MIN_TOTAL_SUPPLY_COST = 75;

    private XLII_MistCloudConstants() {}
}