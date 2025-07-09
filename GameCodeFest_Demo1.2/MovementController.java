import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Chịu trách nhiệm cho mọi logic di chuyển và tìm đường.
 */
public class MovementController {
    private final ActionHelper actionHelper;
    private static final Random random = new Random();

    public MovementController(ActionHelper actionHelper) {
        this.actionHelper = actionHelper;
    }

    public boolean handleDragonEgg() throws IOException {
        Optional<Obstacle> dragonEggOpt = actionHelper.findDragonEgg();
        if (dragonEggOpt.isPresent()) {
            System.out.println("Phát hiện TRỨNG RỒNG! Ưu tiên tấn công.");
            attackObstacle(dragonEggOpt.get());
            return true;
        }
        return false;
    }

    public void findAndDestroyNearestChest() throws IOException {
        Optional<PrioritizedTarget> targetChestOpt = findNearestChest();

        if (targetChestOpt.isPresent()) {
            System.out.println("Tìm thấy rương gần nhất. Đang di chuyển để phá.");
            attackObstacle((Obstacle) targetChestOpt.get().target());
        } else {
            System.out.println("Không tìm thấy rương nào trên bản đồ.");
            moveRandomly();
        }
    }

    private void attackObstacle(Obstacle obstacle) throws IOException {
        if (actionHelper.distanceTo(obstacle) == 1) {
            System.out.println("Đã ở cạnh " + obstacle.getId() + ". Tấn công.");
            actionHelper.attack(obstacle);
            actionHelper.getStatus().lastAttackedChestPosition = new Node(obstacle.getX(), obstacle.getY());
            return;
        }

        Optional<Node> bestAdjacentNode = actionHelper.getValidAdjacentNodes(obstacle).stream()
                .min(Comparator.comparingDouble(actionHelper::distanceTo));

        if (bestAdjacentNode.isPresent()) {
            System.out.println("Di chuyển đến ô cạnh " + obstacle.getId() + " để tấn công.");
            moveTo(bestAdjacentNode.get(), false);
        } else {
            System.out.println("Không tìm thấy đường đi hợp lệ đến bên cạnh " + obstacle.getId() + ". Di chuyển ngẫu nhiên.");
            moveRandomly();
        }
    }

    public Optional<PrioritizedTarget> findNearestChest() {
        return actionHelper.getAllObstaclesOnMap().stream()
                .filter(obs -> "CHEST".equals(obs.getType().name()) || obs.getId().equals(ActionHelper.DRAGON_EGG_ID))
                .min(Comparator.comparing((Obstacle c) -> c.getId().equals(ActionHelper.DRAGON_EGG_ID) ? 0 : 1)
                        .thenComparingDouble(actionHelper::distanceTo))
                .map(chest -> {
                    int pathLength = getPathLengthTo(chest, false);
                    return new PrioritizedTarget(chest, pathLength);
                });
    }

    public void moveTo(Node target, boolean isPvpPath) throws IOException {
        String path = getPathTo(target, isPvpPath);
        if (path != null && !path.isEmpty()) {
            actionHelper.move(path);
        } else {
            System.out.println("Không tìm thấy đường đi đến (" + target.getX() + "," + target.getY() + ").");
        }
    }

    public void moveRandomly() throws IOException {
        String[] directions = {"u", "d", "l", "r"};
        String randomDirection = directions[random.nextInt(directions.length)];
        System.out.println("Di chuyển ngẫu nhiên: " + randomDirection);
        actionHelper.move(randomDirection);
    }

    public String getPathTo(Node target, boolean isPvpPath) {
        GameMap gameMap = actionHelper.getGameMap();
        Player self = actionHelper.getPlayer();
        List<Node> nodesToAvoid = actionHelper.getNodesToAvoid(isPvpPath);
        return PathUtils.getShortestPath(gameMap, nodesToAvoid, self, target, isPvpPath);
    }

    /**
     * FIX: Added the missing method to resolve errors in ItemController.
     * This calculates the length of the path to a target.
     * @param target The destination node.
     * @param isPvpPath True if pathing in PvP mode.
     * @return The length of the path, or Integer.MAX_VALUE if unreachable.
     */
    public int getPathLengthTo(Node target, boolean isPvpPath) {
        String path = getPathTo(target, isPvpPath);
        return (path != null) ? path.length() : Integer.MAX_VALUE;
    }
}
