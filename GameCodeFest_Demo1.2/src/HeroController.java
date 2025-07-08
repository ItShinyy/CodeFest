import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;
import java.util.Optional;

/**
 * Lớp điều khiển chính, bộ não của bot, hoạt động theo hệ thống ưu tiên 4 cấp.
 */
public class HeroController {
    private final GameMap gameMap;
    private final HeroStatus status;
    private final ActionHelper actionHelper;
    private final MovementController movementController;
    private final CombatController combatController;
    private final HealingController healingController;
    private final ItemController itemController;

    public HeroController(Hero hero) {
        this.gameMap = hero.getGameMap();
        this.status = new HeroStatus();
        this.actionHelper = new ActionHelper(hero, gameMap, status);
        this.movementController = new MovementController(actionHelper);
        this.combatController = new CombatController(actionHelper, movementController, status);
        this.healingController = new HealingController(actionHelper, movementController);
        this.itemController = new ItemController(actionHelper, movementController, status);
    }

    void executeTurn(Object... args) throws IOException {
        if (!updateGameState(args)) {
            System.out.println("Cập nhật trạng thái game thất bại. Bỏ qua lượt.");
            return;
        }

        // PRIORITY 1: LOOTING A RECENTLY BROKEN CHEST
        if (status.lastAttackedChestPosition != null) {
            // If we are already picking up an item, let the state machine finish.
            if (itemController.isMidPickupProcess()) {
                itemController.manageItemActions();
                return;
            }
            // Otherwise, look for the next best item from the chest.
            if (itemController.handlePostChestBreak()) {
                // An action was taken (move or pickup), so end the turn to re-evaluate next turn.
                return;
            } else {
                // No more valuable items were found. Clean up and move on.
                System.out.println("Đã nhặt xong đồ từ rương hoặc không có gì giá trị.");
                status.lastAttackedChestPosition = null;
                status.incrementChestsBroken();
            }
        }

        // PRIORITY 2: SURVIVAL AND OTHER STATE-BASED ACTIONS
        if (movementController.handleDragonEgg()) return;
        if (itemController.isMidPickupProcess()) {
            itemController.manageItemActions();
            return;
        }
        if (healingController.handleHealing()) return;

        // PRIORITY 3: COMBAT LOGIC (PVP)
        if (!status.isPvpMode() && combatController.shouldEnterPvpMode()) {
            status.setPvpMode(true);
            System.out.println("ĐỦ ĐIỀU KIỆN! Kích hoạt chế độ PvP!");
        }
        if (combatController.handleCombat()) return;

        // PRIORITY 4: FARMING LOGIC
        executeFarmingStrategy();
    }

    private boolean updateGameState(Object... args) {
        if (args == null || args.length == 0) {
            System.err.println("ERROR: Không có dữ liệu bản đồ để cập nhật.");
            return false;
        }
        try {
            gameMap.updateOnUpdateMap(args[0]);
        } catch (Exception e) {
            System.err.println("ERROR: Lỗi khi cập nhật bản đồ: " + e.getMessage());
            return false;
        }
        Player currentPlayer = gameMap.getCurrentPlayer();
        if (currentPlayer == null || currentPlayer.getHealth() <= 0) {
            return false;
        }
        status.update(currentPlayer, actionHelper.getAttackablePlayers());
        return true;
    }

    /**
     * IMPROVEMENT: Farming strategy now uses a robust efficiency calculation.
     */
    private void executeFarmingStrategy() throws IOException {
        // Early game: focus on chests
        if (status.getChestsBroken() < Configuration.CHESTS_BROKEN_BEFORE_PVP) {
            System.out.println("Giai đoạn đầu game: Farm rương.");
            movementController.findAndDestroyNearestChest();
            return;
        }

        // Mid/Late game: Find the most efficient action
        Optional<PrioritizedTarget> bestItemTarget = itemController.findBestItemOnMap();
        Optional<PrioritizedTarget> bestChestTarget = movementController.findNearestChest();

        // FIX: If standing on the best item, pick it up immediately.
        if (bestItemTarget.isPresent() && bestItemTarget.get().pathLength() == 0) {
            System.out.println("Đã ở vị trí vật phẩm tốt nhất. Bắt đầu nhặt.");
            itemController.initiatePickupProcess((Element) bestItemTarget.get().target());
            return;
        }

        if (bestItemTarget.isPresent() && bestChestTarget.isPresent()) {
            double itemValue = itemController.calculateItemValue((Element) bestItemTarget.get().target());
            double itemEfficiency = itemValue / (bestItemTarget.get().pathLength() + 1.0);
            double chestEfficiency = Configuration.CHEST_EFFICIENCY_VALUE / (bestChestTarget.get().pathLength() + 1.0);

            System.out.printf("So sánh hiệu quả: Item (%s, value=%.1f, eff=%.2f) vs Chest (eff=%.2f)%n",
                    ((Element)bestItemTarget.get().target()).getId(), itemValue, itemEfficiency, chestEfficiency);

            if (itemEfficiency > chestEfficiency) {
                System.out.println("Mục tiêu hiệu quả hơn: Vật phẩm.");
                movementController.moveTo(bestItemTarget.get().target(), false);
            } else {
                System.out.println("Mục tiêu hiệu quả hơn: Rương.");
                movementController.findAndDestroyNearestChest();
            }
        } else if (bestItemTarget.isPresent()) {
            System.out.println("Chỉ có vật phẩm trên bản đồ. Di chuyển đến vật phẩm tốt nhất.");
            movementController.moveTo(bestItemTarget.get().target(), false);
        } else if (bestChestTarget.isPresent()) {
            System.out.println("Chỉ có rương trên bản đồ. Di chuyển đến rương gần nhất.");
            movementController.findAndDestroyNearestChest();
        } else {
            movementController.moveRandomly();
        }
    }
}
