import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.Element;

import java.io.IOException;
import java.util.Optional;

public class HeroController {
    private final GameMap gameMap;
    private final HeroStatus status;
    private final MovementController movementController;
    private final CombatController combatController;
    private final HealingController healingController;
    private final ItemController itemController;
    private final ActionHelper actionHelper;

    public HeroController(Hero hero) {
        this.gameMap = hero.getGameMap();
        this.status = new HeroStatus();
        this.actionHelper = new ActionHelper(hero, gameMap, status);
        this.movementController = new MovementController(actionHelper);
        this.combatController = new CombatController(actionHelper, movementController, status);
        this.healingController = new HealingController(actionHelper, movementController);
        this.itemController = new ItemController(actionHelper, movementController, status);
    }

    void executeTurn(MapUpdateListener listener, Object... args) throws IOException {
        if (!updateGameState(listener, args)) {
            return;
        }

        // Priority 1: Self-preservation and special item states
        if (movementController.handleDragonEgg()) return;
        if (itemController.isMidPickupProcess()) {
            itemController.manageItemActions();
            return;
        }
        if (healingController.handleHealing()) return;

        // Priority 2: PvP Tactic
        Player nearestPlayer = actionHelper.getNearestPlayer();
        if (actionHelper.hasWeaponWithMinScore(630) && nearestPlayer != null) {
            if (!status.isPvpMode()) {
                System.out.println("High-value weapon and nearby player " + nearestPlayer.getId() + ". Engaging PVP mode!");
                status.setPvpMode(true);
            }
        } else {
            if(status.isPvpMode()){
                System.out.println("No high-value weapon or no players nearby. Disabling PVP mode.");
                status.setPvpMode(false);
            }
        }

        if (status.isPvpMode()) {
            if (combatController.handleCombat()) return;
        }

        // Priority 3: Opportunistic Melee
        if (combatController.handleOpportunisticAttack()) return;

        // Priority 4: Intelligent Farming (Default Action)
        handleFarming();
    }

    private void handleFarming() throws IOException {
        Optional<PrioritizedTarget> itemTarget = itemController.findBestItemOnGround();
        Optional<PrioritizedTarget> chestTarget = movementController.findBestChest();

        if (itemTarget.isPresent() && chestTarget.isPresent()) {
            if (itemTarget.get().pathLength() <= chestTarget.get().pathLength()) {
                System.out.println("Farming decision: Item is closer. Moving to " + ((Element) itemTarget.get().target()).getId());
                movementController.moveTo(itemTarget.get().target(), false);
            } else {
                System.out.println("Farming decision: Chest is closer. Moving to " + ((Element) chestTarget.get().target()).getId());
                movementController.moveToOrAttack(chestTarget.get().target());
            }
        } else if (itemTarget.isPresent()) {
            System.out.println("Farming decision: Only item found. Moving to " + ((Element) itemTarget.get().target()).getId());
            movementController.moveTo(itemTarget.get().target(), false);
        } else if (chestTarget.isPresent()) {
            System.out.println("Farming decision: Only chest found. Moving to " + ((Element) chestTarget.get().target()).getId());
            movementController.moveToOrAttack(chestTarget.get().target());
        } else {
            System.out.println("No items or chests to farm. Moving randomly.");
            movementController.moveRandomly();
        }
    }

    private boolean updateGameState(MapUpdateListener listener, Object... args) {
        if (args == null || args.length == 0) return false;

        try {
            gameMap.updateOnUpdateMap(args[0]);
        } catch (NullPointerException e) {
            if (e.getMessage() != null && e.getMessage().contains("isCooldownActive")) {
                System.err.println("CRITICAL: Caught 'isCooldownActive' SDK error. Re-initializing to unfreeze bot.");
                listener.reinitialize();
            } else {
                System.err.println("ERROR: NullPointerException during map update. Skipping turn. Msg: " + e.getMessage());
            }
            return false;
        }

        Player currentPlayer = gameMap.getCurrentPlayer();
        if (currentPlayer == null || currentPlayer.getHealth() <= 0) {
            System.out.println("Player is dead or invalid. Skipping turn.");
            return false;
        }

        status.update(currentPlayer, gameMap.getOtherPlayerInfo());

        // FIX: Restored the logic to enter PvP mode after breaking enough chests.
        // This fixes the "unused" warnings for getChestsBroken() and resetChestsBroken().
        if (status.getChestsBroken() >= Configuration.CHESTS_BROKEN_BEFORE_PVP && !status.isPvpMode()) {
            System.out.println("Sufficient chests broken. Engaging PVP mode!");
            status.setPvpMode(true);
            status.resetChestsBroken();
        }

        return true;
    }
}
