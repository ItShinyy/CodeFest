import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.*;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;


import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lớp trợ giúp, tổng hợp các hành động và phương thức lấy thông tin lặp đi lặp lại.
 * Đã loại bỏ caching theo yêu cầu, truy cập dữ liệu trực tiếp từ GameMap.
 */
public class ActionHelper {
    private final Hero hero;
    private final GameMap gameMap;
    private final HeroStatus status;

    public static final String DRAGON_EGG_ID = "DRAGON_EGG";

    public ActionHelper(Hero hero, GameMap gameMap, HeroStatus status) {
        this.hero = hero;
        this.gameMap = gameMap;
        this.status = status;
    }

    // --- Các phương thức truy cập thông tin từ GameMap (không dùng cache) ---
    public GameMap getGameMap() { return this.gameMap; }
    public Player getPlayer() { return gameMap.getCurrentPlayer(); }
    public HeroStatus getStatus() { return this.status; }
    public Inventory getInventory() { return hero.getInventory(); }

    public List<Element> getAllItemsOnMap() {
        return Stream.concat(
                gameMap.getListWeapons().stream(),
                Stream.concat(
                        gameMap.getListArmors().stream(),
                        gameMap.getListSupportItems().stream()
                )
        ).collect(Collectors.toList());
    }

    public List<Obstacle> getAllObstaclesOnMap() {
        return gameMap.getListObstacles();
    }

    public List<Player> getAttackablePlayers() {
        return gameMap.getOtherPlayerInfo();
    }

    public Optional<Ally> findAlly(String allyId) {
        return gameMap.getListAllies().stream()
                .filter(ally -> ally.getId().equals(allyId))
                .findFirst();
    }

    // --- FIX: Removed unused methods getSafeZoneNode() and isSafeZone() ---

    // --- Các phương thức hành động của Hero ---
    public void move(String path) throws IOException { hero.move(path); }
    public void attack(Node target) throws IOException { hero.attack(getDirection(target)); }
    public void shoot(Node target) throws IOException { hero.shoot(getDirection(target)); }
    public void throwItem(Node target) throws IOException { hero.throwItem(getDirection(target)); }
    public void useSpecial(Node target) throws IOException { hero.useSpecial(getDirection(target)); }
    public void pickupItem() throws IOException { hero.pickupItem(); }
    public void revokeItem(String itemType) throws IOException { hero.revokeItem(itemType); }
    public void useItem(String itemId) throws IOException { hero.useItem(itemId); }

    // --- Các phương thức tiện ích ---
    public double distanceTo(Node target) {
        if (target == null) return Double.MAX_VALUE;
        return PathUtils.distance(getPlayer(), target);
    }

    public Optional<Obstacle> findDragonEgg() {
        return getAllObstaclesOnMap().stream()
                .filter(obs -> obs.getId().equals(DRAGON_EGG_ID))
                .findFirst();
    }

    public Optional<Element> findItemAt(int x, int y) {
        return getAllItemsOnMap().stream()
                .filter(item -> item.getX() == x && item.getY() == y)
                .findFirst();
    }

    public List<Node> getValidAdjacentNodes(Node target) {
        List<Node> adjacentNodes = new ArrayList<>();
        int x = target.getX();
        int y = target.getY();
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        Set<String> obstacleCoords = getAllObstaclesOnMap().stream()
                .map(obs -> obs.getX() + "," + obs.getY())
                .collect(Collectors.toSet());

        for (int i = 0; i < 4; i++) {
            int newX = x + dx[i];
            int newY = y + dy[i];
            if (isWalkable(newX, newY, obstacleCoords)) {
                adjacentNodes.add(new Node(newX, newY));
            }
        }
        return adjacentNodes;
    }

    private boolean isWalkable(int x, int y, Set<String> obstacleCoords) {
        if (x < 0 || y < 0 || x >= gameMap.getMapSize() || y >= gameMap.getMapSize()) {
            return false;
        }
        return !obstacleCoords.contains(x + "," + y);
    }

    public List<Node> getNodesToAvoid(boolean isPvpPath) {
        List<Node> nodes = new ArrayList<>();
        getAllObstaclesOnMap().stream()
                .filter(obs -> !obs.getTags().contains(ObstacleTag.CAN_GO_THROUGH))
                .forEach(nodes::add);

        nodes.addAll(getAttackablePlayers());

        if (!isPvpPath) {
            getAllObstaclesOnMap().stream()
                    .filter(obs -> obs.getTags().contains(ObstacleTag.TRAP))
                    .forEach(nodes::add);
        }
        return nodes;
    }

    public boolean hasStrongRangedWeapon() {
        Inventory inv = getInventory();
        return Stream.of(inv.getGun(), inv.getThrowable(), inv.getSpecial())
                .filter(Objects::nonNull)
                .anyMatch(w -> Configuration.getWeaponScore(w.getId()) >= Configuration.STRONG_RANGED_WEAPON_SCORE);
    }

    public long getWeaponCount() {
        Inventory inv = getInventory();
        return Stream.of(inv.getGun(), inv.getThrowable(), inv.getSpecial(), inv.getMelee())
                .filter(w -> w != null && !"HAND".equals(w.getId()))
                .count();
    }

    public boolean hasClearLineOfSight(Node target) {
        List<Node> blockingNodes = getBulletBlockingNodes();
        Set<String> blockerCoordinates = blockingNodes.stream()
                .map(node -> node.getX() + "," + node.getY())
                .collect(Collectors.toSet());

        Player self = getPlayer();
        int x1 = self.getX(), y1 = self.getY();
        int x2 = target.getX(), y2 = target.getY();

        if (x1 == x2) {
            for (int y = Math.min(y1, y2) + 1; y < Math.max(y1, y2); y++) {
                if (blockerCoordinates.contains(x1 + "," + y)) return false;
            }
            return true;
        } else if (y1 == y2) {
            for (int x = Math.min(x1, x2) + 1; x < Math.max(x1, x2); x++) {
                if (blockerCoordinates.contains(x + "," + y1)) return false;
            }
            return true;
        }
        return false;
    }

    private List<Node> getBulletBlockingNodes() {
        List<Node> nodes = new ArrayList<>();
        getAllObstaclesOnMap().stream()
                .filter(obs -> !obs.getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH) && !obs.getId().equals(DRAGON_EGG_ID))
                .forEach(nodes::add);
        nodes.addAll(getAttackablePlayers());
        return nodes;
    }

    private String getDirection(Node target) {
        if (target == null) return null;
        int dx = target.getX() - getPlayer().getX();
        int dy = target.getY() - getPlayer().getY();
        if (dx == 0 && dy == 0) return null;

        return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "r" : "l") : (dy > 0 ? "u" : "d");
    }
}