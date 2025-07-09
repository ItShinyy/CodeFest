import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.algorithm.PathUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Quản lý logic hồi máu với 2 ngưỡng HP và khả năng tìm đến SPIRIT.
 */
public class HealingController {
    private final ActionHelper actionHelper;
    private final MovementController movementController;

    public HealingController(ActionHelper actionHelper, MovementController movementController) {
        this.actionHelper = actionHelper;
        this.movementController = movementController;
    }

    public boolean handleHealing() throws IOException {
        Player currentPlayer = actionHelper.getPlayer();
        float currentHp = currentPlayer.getHealth();

        if (currentHp < Configuration.LOW_HP_THRESHOLD_HEAL) {
            Optional<SupportItem> bestHealingItem = findBestHealingItemInInventory();
            if (bestHealingItem.isPresent()) {
                System.out.println("HP (" + currentHp + ") < " + Configuration.LOW_HP_THRESHOLD_HEAL + ". Sử dụng vật phẩm hồi máu: " + bestHealingItem.get().getId());
                actionHelper.useItem(bestHealingItem.get().getId());
                return true;
            }
        }

        if (currentHp < 50 && findBestHealingItemInInventory().isEmpty()) {
            // --- FIX: Use ActionHelper to find the ally for better code consistency.
            Optional<Ally> spiritAlly = actionHelper.findAlly("SPIRIT");
            if (spiritAlly.isPresent() && isAllyInSafeZone(spiritAlly.get())) {
                System.out.println("HP (" + currentHp + ") nguy kịch và không có đồ hồi máu. Tìm đến SPIRIT an toàn tại (" + spiritAlly.get().getX() + "," + spiritAlly.get().getY() + ").");
                movementController.moveTo(spiritAlly.get(), false);
            } else {
                System.out.println("HP (" + currentHp + ") nguy kịch, không có SPIRIT an toàn hoặc đồ hồi máu. Ưu tiên phá rương để tìm đồ hồi máu.");
                movementController.findAndDestroyNearestChest();
            }
            return true;
        }

        return false;
    }

    private Optional<SupportItem> findBestHealingItemInInventory() {
        List<SupportItem> supportItems = actionHelper.getInventory().getListSupportItem();
        if (supportItems == null || supportItems.isEmpty()) {
            return Optional.empty();
        }
        return supportItems.stream()
                .filter(item -> Configuration.getSupportItemScore(item.getId()) > 0)
                .max(Comparator.comparingDouble(item -> Configuration.getSupportItemScore(item.getId())));
    }

    private boolean isAllyInSafeZone(Ally ally) {
        GameMap map = actionHelper.getGameMap();
        return PathUtils.checkInsideSafeArea(ally, map.getMapSize(), map.getSafeZone());
    }
}