import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Manages the hero's self-preservation logic. If health is low,
 * it will attempt to move to a safe location before using a healing item.
 */
public class HealingController {
    private final ActionHelper actionHelper;
    private final MovementController movementController;
    private final List<Node> safeZones;

    public HealingController(ActionHelper actionHelper, MovementController movementController) {
        this.actionHelper = actionHelper;
        this.movementController = movementController;
        this.safeZones = List.of(new Node(1, 1), new Node(28, 28), new Node(1, 28), new Node(28, 1));
    }

    private boolean isNodeInSafeZone(Node node) {
        return safeZones.stream().anyMatch(safeNode ->
                Math.abs(node.getX() - safeNode.getX()) <= Configuration.HEALING_SAFE_ZONE_RADIUS &&
                        Math.abs(node.getY() - safeNode.getY()) <= Configuration.HEALING_SAFE_ZONE_RADIUS
        );
    }

    /**
     * Main entry point for healing logic.
     * @return true if a healing-related action was taken.
     */
    public boolean handleHealing() throws IOException {
        Player currentPlayer = actionHelper.getPlayer();
        if (currentPlayer.getHealth() >= Configuration.LOW_HP_THRESHOLD) {
            return false; // Only heal if health is low.
        }

        List<SupportItem> supportItems = actionHelper.getInventory().getListSupportItem();
        if (supportItems == null || supportItems.isEmpty()) {
            return false; // No healing items to use.
        }

        Optional<SupportItem> bestHealingItemOptional = supportItems.stream()
                .max(Comparator.comparingDouble(item -> Configuration.getSupportItemScore(item.getId())));

        if (bestHealingItemOptional.isEmpty()) {
            return false; // Should not happen if list is not empty, but good practice.
        }

        SupportItem bestHealingItem = bestHealingItemOptional.get();

        if (!isNodeInSafeZone(currentPlayer)) {
            Optional<Node> nearestSafeSpot = safeZones.stream()
                    .min(Comparator.comparingDouble(actionHelper::distanceTo));

            if (nearestSafeSpot.isPresent()) {
                System.out.println("Low HP. Moving to nearest safe zone at (" + nearestSafeSpot.get().getX() + "," + nearestSafeSpot.get().getY() + ") to heal.");
                movementController.moveTo(nearestSafeSpot.get(), false);
                return true;
            }
        }

        System.out.println("In safe zone. Using best healing item: " + bestHealingItem.getId());
        actionHelper.useItem(bestHealingItem.getId());
        return true;
    }
}
