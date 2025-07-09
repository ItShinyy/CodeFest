import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MovementController {
    private final ActionHelper actionHelper;

    public MovementController(ActionHelper actionHelper) {
        this.actionHelper = actionHelper;
    }

    public Optional<PrioritizedTarget> findNearestChest() {
        return actionHelper.getAllObstaclesOnMap().stream()
                .filter(actionHelper::isInsideSafeZone)
                .filter(obs -> "CHEST".equals(obs.getType().name()) || obs.getId().equals(ActionHelper.DRAGON_EGG_ID))
                .map(chest -> {
                    Optional<Node> bestAdjacent = actionHelper.getValidAdjacentNodes(chest).stream()
                            .min(Comparator.comparingDouble(actionHelper::distanceTo));

                    if (bestAdjacent.isPresent()) {
                        int pathLength = getPathLengthTo(bestAdjacent.get(), false);
                        if (pathLength != Integer.MAX_VALUE) {
                            return new PrioritizedTarget(chest, pathLength);
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(PrioritizedTarget::pathLength)
                        .thenComparing(pt -> ((Obstacle)pt.target()).getId().equals(ActionHelper.DRAGON_EGG_ID) ? 0 : 1));
    }


    public void moveTo(Node target, boolean isPvpPath) throws IOException {
        String path = getPathTo(target, isPvpPath);
        if (path != null && !path.isEmpty()) {
            actionHelper.move(path);
        }
    }

    public boolean moveToAttackPosition(Player target, int range) throws IOException {
        Optional<Node> bestAttackPos = findBestAttackPosition(target, range);
        if (bestAttackPos.isPresent()) {
            moveTo(bestAttackPos.get(), true);
            return true;
        }
        return false;
    }

    private Optional<Node> findBestAttackPosition(Player target, int range) {
        Player self = actionHelper.getPlayer();
        if (target == null || self == null) return Optional.empty();

        int tx = target.getX();
        int ty = target.getY();

        return IntStream.rangeClosed(1, range)
                .boxed()
                .flatMap(r -> Stream.of(
                        new Node(tx + r, ty),
                        new Node(tx - r, ty),
                        new Node(tx, ty + r),
                        new Node(tx, ty - r)
                ))
                .filter(node -> actionHelper.isWalkable(node.getX(), node.getY()))
                .min(Comparator.comparingInt(node -> getPathLengthTo(node, true)));
    }

    public void moveToSafeZoneCenter() throws IOException {
        GameMap map = actionHelper.getGameMap();
        if (map == null) return;
        int mapSize = map.getMapSize();
        Node center = new Node(mapSize / 2, mapSize / 2);

        if (actionHelper.getPlayer().getX() == center.getX() && actionHelper.getPlayer().getY() == center.getY()) {
            return;
        }

        moveTo(center, false);
    }

    public String getPathTo(Node target, boolean isPvpPath) {
        GameMap gameMap = actionHelper.getGameMap();
        Player self = actionHelper.getPlayer();
        if (gameMap == null || self == null || target == null) return "";
        List<Node> nodesToAvoid = actionHelper.getNodesToAvoid(isPvpPath);

        boolean skipDarkArea = true;

        return PathUtils.getShortestPath(gameMap, nodesToAvoid, self, target, skipDarkArea);
    }

    public int getPathLengthTo(Node target, boolean isPvpPath) {
        String path = getPathTo(target, isPvpPath);
        return (path != null) ? path.length() : Integer.MAX_VALUE;
    }
}
