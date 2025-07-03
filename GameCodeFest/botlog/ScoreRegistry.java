import java.util.Map;

/**
 * Nơi lưu trữ và truy xuất điểm của tất cả các vật phẩm dựa trên chiến thuật.
 * Central registry for storing and retrieving item scores based on tactics.
 */
public class ScoreRegistry {
    private static final Map<String, Integer> WEAPON_SCORES = Map.ofEntries(
            Map.entry("SAHUR_BAT", 1000), Map.entry("ROPE", 950), Map.entry("SHOTGUN", 900),
            Map.entry("SEED", 850), Map.entry("MACE", 800), Map.entry("CROSSBOW", 750),
            Map.entry("SMOKE", 700), Map.entry("CRYSTAL", 650), Map.entry("METEORITE_FRAGMENT", 640),
            Map.entry("BANANA", 630), Map.entry("AXE", 600), Map.entry("BELL", 550),
            Map.entry("RUBBER_GUN", 500), Map.entry("SCEPTER", 450), Map.entry("BONE", 400),
            Map.entry("KNIFE", 350), Map.entry("TREE_BRANCH", 300)
    );

    private static final Map<String, Integer> ARMOR_SCORES = Map.of(
            "MAGIC_ARMOR", 1050, "MAGIC_HELMET", 700, "ARMOR", 700, "WOODEN_HELMET", 250
    );

    private static final Map<String, Integer> HEALING_SCORES = Map.of(
            "ELIXIR_OF_LIFE", 2000, "UNICORN_BLOOD", 1100, "PHOENIX_FEATHERS", 850,
            "MERMAID_TAIL", 700, "SPIRIT_TEAR", 550, "GOD_LEAF", 500,
            "COMPASS", 400, "ELIXIR", 350, "MAGIC", 300
    );

    public static double getWeaponScore(String id) { return WEAPON_SCORES.getOrDefault(id, 0); }
    public static double getArmorScore(String id) { return ARMOR_SCORES.getOrDefault(id, 0); }
    public static double getHealingScore(String id) { return HEALING_SCORES.getOrDefault(id, 0); }
}