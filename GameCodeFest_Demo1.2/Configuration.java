import java.util.Map;

/**
 * Tệp cấu hình trung tâm cho các quyết định chiến thuật của bot.
 */
public final class Configuration {

    // --- Ngưỡng chiến đấu ---
    public static final float LOW_HP_THRESHOLD_HEAL = 75.0f;
    public static final int LETHAL_ATTACK_SCORE_BONUS = 500;
    public static final int PVP_ENGAGE_DISTANCE = 7;
    public static final double RETREAT_THRESHOLD = 2.0;
    public static final int THROWABLE_OPENER_BONUS = 150;

    // FIX: Thêm các hằng số cho chế độ HUNTING và PvP phản ứng
    public static final int HUNTING_MODE_WEAPON_THRESHOLD = 4;
    public static final int FARMING_MODE_WEAPON_THRESHOLD = 2;
    public static final int REACTIVE_PVP_WEAPON_SCORE = 750;


    // --- Quản lý vật phẩm & Farm ---
    public static final int MAX_SUPPORT_ITEMS = 4;
    public static final double CHEST_EFFICIENCY_VALUE = 150.0;


    // --- Hệ thống tính điểm vật phẩm ---
    public static final Map<String, Integer> WEAPON_SCORES = Map.ofEntries(
            Map.entry("SAHUR_BAT", 1000), Map.entry("ROPE", 950), Map.entry("SHOTGUN", 900),
            Map.entry("SEED", 850), Map.entry("MACE", 800), Map.entry("CROSSBOW", 750),
            Map.entry("SMOKE", 700), Map.entry("CRYSTAL", 650), Map.entry("METEORITE_FRAGMENT", 640),
            Map.entry("BANANA", 630), Map.entry("AXE", 600), Map.entry("BELL", 550),
            Map.entry("RUBBER_GUN", 500), Map.entry("SCEPTER", 450), Map.entry("BONE", 400),
            Map.entry("KNIFE", 350), Map.entry("TREE_BRANCH", 300),
            Map.entry("HAND", 0)
    );

    public static final Map<String, Integer> ARMOR_SCORES = Map.of(
            "MAGIC_ARMOR", 1050, "MAGIC_HELMET", 700, "ARMOR", 700, "WOODEN_HELMET", 250
    );

    public static final Map<String, Integer> SUPPORT_ITEM_SCORES = Map.of(
            "ELIXIR_OF_LIFE", 2000, "UNICORN_BLOOD", 1100, "PHOENIX_FEATHERS", 850,
            "MERMAID_TAIL", 700, "SPIRIT_TEAR", 550, "GOD_LEAF", 500,
            "COMPASS", 400, "ELIXIR", 350, "MAGIC", 300
    );

    // --- Các phương thức truy xuất điểm ---
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
