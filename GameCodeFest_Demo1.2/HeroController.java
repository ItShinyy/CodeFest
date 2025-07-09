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

        // PRIORITY 1: SURVIVAL & STATE-BASED ACTIONS (Không thay đổi)
        if (status.lastAttackedChestPosition != null) {
            if (itemController.isMidPickupProcess()) {
                itemController.manageItemActions();
                return;
            }
            if (itemController.handlePostChestBreak()) {
                return;
            } else {
                status.lastAttackedChestPosition = null;
                status.incrementChestsBroken();
            }
        }
        if (movementController.handleDragonEgg()) return;
        if (itemController.isMidPickupProcess()) {
            itemController.manageItemActions();
            return;
        }
        if (healingController.handleHealing()) return;

        // PRIORITY 2: STRATEGY DETERMINATION (Logic mới)
        updateStrategy();

        // PRIORITY 3 & 4: ACTION EXECUTION BASED ON STRATEGY
        if (status.getCurrentStrategy() == HeroStatus.Strategy.HUNTING) {
            System.out.println("Chiến lược: SĂN BẮN. Tìm kiếm và tiêu diệt mục tiêu.");
            if (combatController.handleCombat()) {
                return; // Đã thực hiện hành động chiến đấu
            }
            // Nếu không có mục tiêu để săn, chuyển sang farm để không lãng phí lượt
            System.out.println("Không có mục tiêu săn bắn. Tạm thời chuyển sang FARMING.");
        }

        // Logic FARMING (bao gồm cả PvP phản ứng)
        // Sẽ được thực thi nếu đang ở chế độ FARMING, hoặc HUNTING nhưng không tìm thấy mục tiêu
        if (handleReactivePvp()) {
            return; // Đã thực hiện hành động PvP phản ứng
        }

        executeFarmingStrategy();
    }

    /**
     * Cập nhật chiến lược của bot (FARMING hoặc HUNTING) dựa trên số lượng vũ khí.
     */
    private void updateStrategy() {
        long weaponCount = actionHelper.getWeaponCount();
        if (weaponCount >= Configuration.HUNTING_MODE_WEAPON_THRESHOLD) {
            if (status.getCurrentStrategy() != HeroStatus.Strategy.HUNTING) {
                System.out.println("Có " + weaponCount + " vũ khí. Chuyển sang chế độ SĂN BẮN!");
                status.setCurrentStrategy(HeroStatus.Strategy.HUNTING);
            }
        } else if (weaponCount <= Configuration.FARMING_MODE_WEAPON_THRESHOLD) {
            if (status.getCurrentStrategy() != HeroStatus.Strategy.FARMING) {
                System.out.println("Còn " + weaponCount + " vũ khí. Quay lại chế độ FARMING!");
                status.setCurrentStrategy(HeroStatus.Strategy.FARMING);
            }
        }
    }

    /**
     * Xử lý logic PvP phản ứng khi đang ở chế độ FARMING.
     * @return true nếu một hành động PvP được thực hiện.
     */
    private boolean handleReactivePvp() throws IOException {
        Player nearestEnemy = combatController.findNearestAttackablePlayer();
        if (nearestEnemy != null && actionHelper.distanceTo(nearestEnemy) <= Configuration.PVP_ENGAGE_DISTANCE) {
            if (actionHelper.hasStrongWeaponForPvp()) {
                System.out.println("Phát hiện kẻ địch ở gần với vũ khí mạnh. Ưu tiên tấn công!");
                return combatController.handleCombat();
            }
        }
        return false;
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

    private void executeFarmingStrategy() throws IOException {
        Optional<PrioritizedTarget> bestItemTarget = itemController.findBestItemOnMap();

        if (bestItemTarget.isPresent() && bestItemTarget.get().pathLength() == 0) {
            System.out.println("Đã ở vị trí vật phẩm tốt nhất. Bắt đầu nhặt.");
            Element targetElement = (Element) bestItemTarget.get().target();
            itemController.initiatePickupProcess(targetElement);

            if (!itemController.isMidPickupProcess()) {
                status.failedPickupIds.add(targetElement.getId());
                executeFarmingStrategy();
                return;
            }
            return;
        }

        Optional<PrioritizedTarget> bestChestTarget = movementController.findNearestChest();

        if (bestItemTarget.isPresent() && bestChestTarget.isPresent()) {
            double itemValue = itemController.calculateItemValue((Element) bestItemTarget.get().target());
            double itemEfficiency = itemValue / (bestItemTarget.get().pathLength() + 1.0);
            double chestEfficiency = Configuration.CHEST_EFFICIENCY_VALUE / (bestChestTarget.get().pathLength() + 1.0);

            System.out.printf("So sánh hiệu quả: Item (%s, value=%.1f, eff=%.2f) vs Chest (eff=%.2f)%n",
                    ((Element)bestItemTarget.get().target()).getId(), itemValue, itemEfficiency, chestEfficiency);

            if (itemEfficiency > chestEfficiency) {
                movementController.moveTo(bestItemTarget.get().target(), false);
            } else {
                movementController.findAndDestroyNearestChest();
            }
        } else if (bestItemTarget.isPresent()) {
            movementController.moveTo(bestItemTarget.get().target(), false);
        } else if (bestChestTarget.isPresent()) {
            movementController.findAndDestroyNearestChest();
        } else {
            movementController.moveRandomly();
        }
    }
}
