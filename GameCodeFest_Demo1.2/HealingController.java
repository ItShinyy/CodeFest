import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class HealingController {
    private final ActionHelper actionHelper;
    private final MovementController movementController;
    private final CombatController combatController;

    public HealingController(ActionHelper actionHelper, MovementController movementController, CombatController combatController) {
        this.actionHelper = actionHelper;
        this.movementController = movementController;
        this.combatController = combatController;
    }

    public boolean handleHealing() throws IOException {
        Player currentPlayer = actionHelper.getPlayer();
        if (currentPlayer == null) return false;

        float currentHp = currentPlayer.getHealth();
        float maxHp = 100.0f;

        if (currentHp < Configuration.LOW_HP_THRESHOLD_HEAL) {
            float healthNeeded = maxHp - currentHp;
            Optional<SupportItem> itemToUse = findMostEfficientHealingItem(healthNeeded);
            if (itemToUse.isPresent()) {
                actionHelper.useItem(itemToUse.get().getId());
                return true;
            }
        }

        if (currentHp < Configuration.CRITICAL_HP_THRESHOLD) {
            Optional<Ally> spiritAlly = actionHelper.findAlly("SPIRIT");
            if (spiritAlly.isPresent() && actionHelper.isInsideSafeZone(spiritAlly.get())) {
                movementController.moveTo(spiritAlly.get(), false);
                return true;
            } else {
                Optional<PrioritizedTarget> nearestChest = movementController.findNearestChest();
                if (nearestChest.isPresent()) {
                    combatController.handleObstacleAttack((Obstacle) nearestChest.get().target());
                    return true;
                }
            }
        }

        return false;
    }

    private Optional<SupportItem> findMostEfficientHealingItem(float healthNeeded) {
        if (actionHelper.getInventory() == null || actionHelper.getInventory().getListSupportItem() == null) {
            return Optional.empty();
        }
        List<SupportItem> healingItems = actionHelper.getInventory().getListSupportItem();

        List<SupportItem> usableItems = healingItems.stream()
                .filter(item -> item.getHealingHP() > 0)
                .sorted(Comparator.comparing(SupportItem::getHealingHP))
                .toList();

        if (usableItems.isEmpty()) {
            return Optional.empty();
        }

        Optional<SupportItem> bestFit = usableItems.stream()
                .filter(item -> item.getHealingHP() >= healthNeeded)
                .findFirst();

        return bestFit.or(() -> usableItems.stream().max(Comparator.comparing(SupportItem::getHealingHP)));
    }
}
