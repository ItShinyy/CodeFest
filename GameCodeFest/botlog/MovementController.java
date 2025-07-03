import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Chịu trách nhiệm cho mọi logic di chuyển và tìm đường.
 * Responsible for all movement and pathfinding logic.
 */
public class MovementController {
    private final ActionHelper actionHelper;

    public MovementController(ActionHelper actionHelper) {
        this.actionHelper = actionHelper;
    }

    public void moveTo(Node target, boolean isPvpPath) throws IOException {
        String path = getPathTo(target, isPvpPath);
        if (path != null && !path.isEmpty()) {
            actionHelper.move(path);
        }
    }

    public String getPathTo(Node target, boolean isPvpPath) {
        // FIX: Added missing variable initializations for player and nodes to avoid.
        GameMap gameMap = actionHelper.getGameMap();
        Player self = actionHelper.getPlayer();
        List<Node> nodesToAvoid = actionHelper.getNodesToAvoid();
        return PathUtils.getShortestPath(gameMap, nodesToAvoid, self, target, isPvpPath);
    }

    // FIX: Removed 'throws IOException' as it's handled internally.
    public void findAndDestroyPriorityChest() {
        GameMap gameMap = actionHelper.getGameMap();
        List<Obstacle> chestList = gameMap.getListChests();
        if (chestList.isEmpty()) return;

        Optional<Obstacle> targetChestOptional = chestList.stream()
                .min(Comparator.comparing((Obstacle c) -> "DRAGON_EGG".equals(c.getId()) ? 0 : 1)
                        .thenComparingDouble(actionHelper::distanceTo));

        targetChestOptional.ifPresent(targetChest -> {
            try {
                if (actionHelper.distanceTo(targetChest) == 1) {
                    actionHelper.attack(targetChest);
                    actionHelper.getStatus().lastAttackedChestPosition = new Node(targetChest.getX(), targetChest.getY());
                } else {
                    moveTo(targetChest, false);
                }
            } catch (IOException e) {
                // FIX: Replaced printStackTrace with a more informative error message.
                System.err.println("Error during chest attack/move: " + e.getMessage());
            }
        });
    }
}