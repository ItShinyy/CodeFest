import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;

public class HeroController {
    private final GameMap gameMap;
    private final HeroStatus status;
    private final MovementController movementController;
    private final CombatController combatController;
    private final HealingController healingController;
    private final ItemController itemController;

    public HeroController(Hero hero) {
        this.gameMap = hero.getGameMap();
        this.status = new HeroStatus();
        ActionHelper actionHelper = new ActionHelper(hero, gameMap, status);
        this.movementController = new MovementController(actionHelper);
        this.combatController = new CombatController(actionHelper, movementController, status);
        this.healingController = new HealingController(actionHelper, movementController);
        this.itemController = new ItemController(actionHelper, movementController, status);
    }

    public void executeTurn(Object... args) throws IOException {
        if (args == null || args.length == 0) return;

        gameMap.updateOnUpdateMap(args[0]);
        Player currentPlayer = gameMap.getCurrentPlayer();
        if (currentPlayer == null || currentPlayer.getHealth() <= 0) {
            System.out.println("Người chơi đã chết hoặc không tồn tại. Bỏ qua lượt.");
            return;
        }
        status.update(currentPlayer);

        // PRIORITY 1: HEALING
        if (healingController.handleHealing(currentPlayer)) {
            return;
        }

        // PRIORITY 2: PROCESS BROKEN CHEST & CHECK FOR PVP TRIGGER
        // The handlePostChestBreak method no longer returns a useful boolean for flow control,
        // so we call it and then proceed.
        itemController.handlePostChestBreak();
        if (status.getChestsBroken() >= 4 && !status.isPvpMode()) {
            System.out.println("Đã phá đủ rương! Bật chế độ PVP!");
            status.setPvpMode(true);
            status.resetChestsBroken(); // Use the reset method
        }

        // PRIORITY 3: ITEM MANAGEMENT (State Machine)
        if (itemController.manageItemActions()) {
            return;
        }

        // PRIORITY 4: COMBAT OR FINAL ACTION
        if (status.isPvpMode()) {
            if (!combatController.handlePvpCombat()) {
                movementController.findAndDestroyPriorityChest();
            }
        } else { // Farming Mode
            if (combatController.handleOpportunisticAttack()) return;
            movementController.findAndDestroyPriorityChest();
        }
    }
}