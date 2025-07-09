import java.util.Map;

public final class Configuration {

    // Health and Healing
    public static final float LOW_HP_THRESHOLD_HEAL = 75.0f;
    public static final float CRITICAL_HP_THRESHOLD = 50.0f;

    // Combat Scoring
    public static final int LETHAL_ATTACK_SCORE_BONUS = 500;
    public static final int LOW_HEALTH_TARGET_BONUS = 250;

    // Combat Behavior
    public static final int PVP_ENGAGE_DISTANCE = 7;
    public static final int REACTIVE_PVP_WEAPON_SCORE = 750;

    // Strategy Toggles
    public static final int HUNTING_MODE_WEAPON_THRESHOLD = 4;
    public static final int FARMING_MODE_WEAPON_THRESHOLD = 2;

    // Item and Inventory
    public static final int MAX_SUPPORT_ITEMS = 4;
    public static final double CHEST_EFFICIENCY_VALUE = 150.0;
    public static final double MIN_CHEST_EFFICIENCY_VALUE = 40.0;

    // Weapon Effect Durations (in turns)
    public static final int COMPASS_STUN_RADIUS = 5;
    public static final int COMPASS_STUN_TURNS = 7;
    public static final int ROPE_STUN_TURNS = 1;
    public static final int SAHUR_BAT_STUN_TURNS = 1;
    public static final int SMOKE_DURATION_TURNS = 2;


    // Item Scoring Maps
    public static final Map<String, Integer> WEAPON_SCORES = Map.ofEntries(
            Map.entry("SAHUR_BAT", 100), // Updated score
            Map.entry("ROPE", 950),
            Map.entry("SHOTGUN", 900),
            Map.entry("SEED", 850),
            Map.entry("MACE", 800),
            Map.entry("CROSSBOW", 750),
            Map.entry("SMOKE", 700),
            Map.entry("CRYSTAL", 650),
            Map.entry("METEORITE_FRAGMENT", 640),
            Map.entry("BANANA", 630),
            Map.entry("AXE", 600),
            Map.entry("BELL", 550),
            Map.entry("RUBBER_GUN", 500),
            Map.entry("SCEPTER", 450),
            Map.entry("BONE", 400),
            Map.entry("KNIFE", 350),
            Map.entry("TREE_BRANCH", 300),
            Map.entry("HAND", 0)
    );

    public static final Map<String, Integer> ARMOR_SCORES = Map.of(
            "MAGIC_ARMOR", 1050, "MAGIC_HELMET", 700, "ARMOR", 700, "WOODEN_HELMET", 250
    );

    public static final Map<String, Integer> SUPPORT_ITEM_SCORES = Map.ofEntries(
            Map.entry("GOD_LEAF", 5),
            Map.entry("SPIRIT_TEAR", 15),
            Map.entry("MERMAID_TAIL", 20),
            Map.entry("PHOENIX_FEATHERS", 25),
            Map.entry("UNICORN_BLOOD", 30),
            Map.entry("ELIXIR_OF_LIFE", 2000),
            Map.entry("ELIXIR", 350),
            Map.entry("MAGIC", 300),
            Map.entry("COMPASS", 1500)
    );

    public static double getWeaponScore(String id) {
        return WEAPON_SCORES.getOrDefault(id, 0);
    }

    public static double getArmorScore(String id) {
        return ARMOR_SCORES.getOrDefault(id, 0);
    }

    public static double getSupportItemScore(String id) {
        return SUPPORT_ITEM_SCORES.getOrDefault(id, 0);
    }
}
