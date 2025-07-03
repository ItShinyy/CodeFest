import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.healing_items.HealingItem;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Xử lý logic tự động hồi máu.
 * Handles automatic healing logic.
 */
public class HealingController {
    private final ActionHelper actionHelper;
    private final MovementController movementController;

    public HealingController(ActionHelper actionHelper, MovementController movementController) {
        this.actionHelper = actionHelper;
        this.movementController = movementController;
    }

    /**
     * Xử lý hồi máu nếu cần. (Chiến thuật 2)
     */
    public boolean handleHealing(Player self) throws IOException {
        // Điều kiện kích hoạt: Máu < 70
        boolean needsHealing = self.getHealth() < 70;

        if (needsHealing) {
            Optional<Node> bestSource = findBestHealingSource();
            if (bestSource.isPresent()) {
                Node target = bestSource.get();
                System.out.println("HP thấp, di chuyển tới nguồn hồi máu hiệu quả nhất: " + ((Element) target).getId());
                if (actionHelper.distanceTo(target) == 0) {
                    actionHelper.pickupItem(); // Giả định SPIRIT cũng được "pickup"
                } else {
                    movementController.moveTo(target, false);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Tìm nguồn hồi máu tốt nhất dựa trên (score / distance).
     */
    private Optional<Node> findBestHealingSource() {
        GameMap gameMap = actionHelper.getGameMap();
        List<Node> healingSources = new ArrayList<>(gameMap.getListHealingItems());
        gameMap.getListAllies().stream()
                .filter(ally -> "SPIRIT".equals(ally.getId()))
                .forEach(healingSources::add);

        if (healingSources.isEmpty()) return Optional.empty();

        return healingSources.stream()
                .max(Comparator.comparingDouble(source ->
                        calculateHealingSourceScore(source) / (actionHelper.distanceTo(source) + 1.0)
                ));
    }

    /**
     * Tính điểm cho một nguồn hồi máu.
     */
    private double calculateHealingSourceScore(Node source) {
        if (source instanceof HealingItem item) {
            return ScoreRegistry.getHealingScore(item.getId());
        }
        if (source instanceof Ally ally && "SPIRIT".equals(ally.getId())) {
            return 50; // Hiệu quả mặc định của SPIRIT
        }
        return 0.0;
    }
}