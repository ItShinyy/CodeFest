import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;
import java.util.Optional;

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
        this.actionHelper = new ActionHelper(hero, gameMap);
        this.movementController = new MovementController(actionHelper);
        this.combatController = new CombatController(actionHelper, movementController, status);
        this.healingController = new HealingController(actionHelper, movementController, combatController);
        this.itemController = new ItemController(actionHelper, movementController, status);
    }

    void executeTurn(Object... args) throws IOException {
        if (!updateGameState(args)) {
            return;
        }

        if (status.isEngaging()) {
            if (combatController.handleCombat()) {
                return;
            }
        }

        if (itemController.isMidPickupProcess()) {
            itemController.manageItemActions();
            return;
        }

        if (status.lastAttackedChestPosition != null) {
            if (itemController.handlePostChestBreak()) {
                return;
            }
            if (actionHelper.distanceTo(status.lastAttackedChestPosition) > 2.0) {
                status.lastAttackedChestPosition = null;
            }
        }

        Optional<Obstacle> dragonEggOpt = actionHelper.findDragonEgg();
        if (dragonEggOpt.isPresent() && actionHelper.isInsideSafeZone(dragonEggOpt.get())) {
            if (combatController.handleObstacleAttack(dragonEggOpt.get())) {
                return;
            }
        }

        if (healingController.handleHealing()) {
            return;
        }

        updateStrategy();

        if (status.getCurrentStrategy() == HeroStatus.Strategy.HUNTING) {
            if (combatController.handleCombat()) {
                return;
            }
        }

        if (handleReactivePvp()) {
            return;
        }

        executeFarmingStrategy();
    }

    private void updateStrategy() {
        long weaponCount = actionHelper.getWeaponCount();
        if (weaponCount >= Configuration.HUNTING_MODE_WEAPON_THRESHOLD) {
            if (status.getCurrentStrategy() != HeroStatus.Strategy.HUNTING) {
                status.setCurrentStrategy(HeroStatus.Strategy.HUNTING);
            }
        } else if (weaponCount <= Configuration.FARMING_MODE_WEAPON_THRESHOLD) {
            if (status.getCurrentStrategy() != HeroStatus.Strategy.FARMING) {
                status.setCurrentStrategy(HeroStatus.Strategy.FARMING);
            }
        }
    }

    private boolean handleReactivePvp() throws IOException {
        Player nearestEnemy = combatController.findNearestAttackablePlayer();
        if (nearestEnemy != null && actionHelper.distanceTo(nearestEnemy) <= Configuration.PVP_ENGAGE_DISTANCE) {
            if (actionHelper.hasStrongWeaponForPvp()) {
                return combatController.handleCombat();
            }
        }
        return false;
    }

    private boolean updateGameState(Object... args) {
        if (args == null || args.length == 0) {
            return false;
        }
        try {
            gameMap.updateOnUpdateMap(args[0]);
        } catch (Exception e) {
            System.err.println("Error updating game map: " + e.getMessage());
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
        for (int i = 0; i < 2; i++) {
            Optional<PrioritizedTarget> bestItemTarget = itemController.findBestItemOnMap();

            if (bestItemTarget.isPresent() && bestItemTarget.get().pathLength() == 0) {
                Element targetElement = (Element) bestItemTarget.get().target();
                itemController.initiatePickupProcess(targetElement);
                if (itemController.isMidPickupProcess()) {
                    return;
                }
                status.failedPickupIds.add(targetElement.getId());
                continue;
            }

            Optional<PrioritizedTarget> bestChestTarget = movementController.findNearestChest();

            boolean hasItemTarget = bestItemTarget.isPresent();
            boolean hasChestTarget = bestChestTarget.isPresent();

            if (hasItemTarget && hasChestTarget) {
                double itemValue = itemController.calculateItemValue((Element) bestItemTarget.get().target());
                double itemEfficiency = itemValue / (bestItemTarget.get().pathLength() + 1.0);
                double dynamicChestValue = calculateDynamicChestValue();
                double chestEfficiency = dynamicChestValue / (bestChestTarget.get().pathLength() + 1.0);

                if (itemEfficiency > chestEfficiency) {
                    movementController.moveTo(bestItemTarget.get().target(), false);
                } else {
                    combatController.handleObstacleAttack((Obstacle) bestChestTarget.get().target());
                }
            } else if (hasItemTarget) {
                movementController.moveTo(bestItemTarget.get().target(), false);
            } else if (hasChestTarget) {
                combatController.handleObstacleAttack((Obstacle) bestChestTarget.get().target());
            } else {
                movementController.moveToSafeZoneCenter();
            }
            break;
        }
    }

    private double calculateDynamicChestValue() {
        double equipmentScore = actionHelper.calculateTotalEquipmentScore();
        double dynamicValue = Configuration.CHEST_EFFICIENCY_VALUE - (equipmentScore * 0.05);
        return Math.max(Configuration.MIN_CHEST_EFFICIENCY_VALUE, dynamicValue);
    }
}
