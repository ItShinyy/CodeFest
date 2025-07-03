import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.*;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.*;

/**
 * Lớp trợ giúp, tổng hợp các hành động và phương thức lấy thông tin lặp đi lặp lại.
 * Helper class that centralizes repetitive actions and information-retrieval methods.
 */
public class ActionHelper {
    private final Hero hero;
    private final GameMap gameMap;
    private final HeroStatus status;

    public ActionHelper(Hero hero, GameMap gameMap, HeroStatus status) {
        this.hero = hero;
        this.gameMap = gameMap;
        this.status = status;
    }

    // Lấy thông tin
    public GameMap getGameMap() {return this.gameMap;}
    public Player getPlayer() { return gameMap.getCurrentPlayer(); }
    public HeroStatus getStatus() {return this.status;}
    public Inventory getInventory() { return hero.getInventory(); }
    public List<Player> getAttackablePlayers() { return gameMap.getOtherPlayerInfo(); }
    public Player getNearestPlayer() {
        return getAttackablePlayers().stream()
                .min(Comparator.comparingDouble(this::distanceTo))
                .orElse(null);
    }
    public double distanceTo(Node target) {
        return PathUtils.distance(getPlayer(), target);
    }

    // Các hành động của Hero
    public void move(String path) throws IOException { hero.move(path); }
    public void attack(Obstacle target) throws IOException {
        int dx = target.getX() - getPlayer().getX();
        int dy = target.getY() - getPlayer().getY();
        // Updated to use the new getAttackDirection method
        String direction = getAttackDirection(dx, dy);
        if (direction != null) {
            hero.attack(direction);
        }
    }
    public void pickupItem() throws IOException { hero.pickupItem(); }
    public void revokeItem(String itemType) throws IOException { hero.revokeItem(itemType); }


    /**
     * Kiểm tra xem có hết vũ khí tầm xa không. (Chiến thuật 1)
     */
    public boolean isOutOfRangedAmmo() {
        Inventory inv = getInventory();
        Weapon special = inv.getSpecial();
        Weapon gun = inv.getGun();
        Weapon throwable = inv.getThrowable();
        return (special == null || special.getUseCounts() <= 0) &&
                (gun == null || gun.getUseCounts() <= 0) &&
                (throwable == null || throwable.getUseCounts() <= 0);
    }

    /**
     * Lấy danh sách các node cần tránh khi tìm đường. (Chiến thuật 5)
     */
    public List<Node> getNodesToAvoid() {
        List<Node> nodes = new ArrayList<>();
        // Tránh trap và các vật cản không đi qua được
        for (Obstacle obs : gameMap.getListObstacles()) {
            if (obs.getTag().contains(ObstacleTag.TRAP) || !obs.getTag().contains(ObstacleTag.CAN_GO_THROUGH)) {
                nodes.add(obs);
            }
        }
        // Tránh tất cả kẻ địch và người chơi khác
        nodes.addAll(gameMap.getListEnemies());
        nodes.addAll(getAttackablePlayers());
        return nodes;
    }

    /**
     * Kiểm tra đường bắn có thông thoáng không.
     */
    public boolean hasClearLineOfSight(Player target) {
        List<Node> blockingNodes = getBulletBlockingNodes();

        // The rest of the method's logic remains the same...
        Set<String> blockerCoordinates = new HashSet<>();
        for (Node node : blockingNodes) {
            blockerCoordinates.add(node.getX() + "," + node.getY());
        }

        Player self = getPlayer(); // Get the player from the helper itself
        int x1 = self.getX(), y1 = self.getY();
        int x2 = target.getX(), y2 = target.getY();

        // 2. Kiểm tra đường bắn thẳng đứng
        if (x1 == x2) {
            for (int y = Math.min(y1, y2) + 1; y < Math.max(y1, y2); y++) {
                if (blockerCoordinates.contains(x1 + "," + y)) {
                    return false; // Có vật cản
                }
            }
            return true; // Đường bắn thông thoáng
        }
        // 3. Kiểm tra đường bắn ngang
        else if (y1 == y2) {
            for (int x = Math.min(x1, x2) + 1; x < Math.max(x1, x2); x++) {
                if (blockerCoordinates.contains(x + "," + y1)) {
                    return false; // Có vật cản
                }
            }
            return true; // Đường bắn thông thoáng
        }

        // Phương thức này chỉ xử lý đường bắn thẳng, không xử lý đường chéo
        return false;
    }

    /**
     * Lấy danh sách các node chặn đường đạn.
     * Gets a list of nodes that block a bullet's path.
     */
    public List<Node> getBulletBlockingNodes() {
        List<Node> nodes = new ArrayList<>();
        GameMap gameMap = getGameMap();

        // Thêm các vật cản không thể bắn xuyên qua
        for (Obstacle obs : gameMap.getListObstacles()) {
            if (!obs.getTag().contains(ObstacleTag.CAN_SHOOT_THROUGH)) {
                nodes.add(obs);
            }
        }

        // Người chơi khác cũng chặn đạn
        nodes.addAll(getAttackablePlayers());

        return nodes;
    }

    /**
     * Kiểm tra xem có bất kỳ vũ khí hoặc giáp nào tại một tọa độ cụ thể không.
     * Checks if any weapon or armor exists at a specific coordinate.
     */
    public boolean isItemAt(int x, int y) {
        GameMap gameMap = getGameMap();
        boolean weaponFound = gameMap.getListWeapons().stream()
                .anyMatch(item -> item.getX() == x && item.getY() == y);
        if (weaponFound) return true;

        return gameMap.getListArmors().stream()
                .anyMatch(item -> item.getX() == x && item.getY() == y);
    }

    public void attack(Node target) throws IOException {
        if (target == null) return;

        int dx = target.getX() - getPlayer().getX();
        int dy = target.getY() - getPlayer().getY();
        String direction = getAttackDirection(dx, dy);

        if (direction != null) {
            hero.attack(direction);
        }
    }

    /**
     * Bắn súng vào một mục tiêu.
     * Shoots a gun at a target.
     */
    public void shoot(Node target) throws IOException {
        String direction = getAttackDirection(target.getX() - getPlayer().getX(), target.getY() - getPlayer().getY());
        if (direction != null) {
            hero.shoot(direction);
        }
    }

    /**
     * Ném vũ khí vào một mục tiêu.
     * Throws a weapon at a target.
     */
    public void throwItem(Node target) throws IOException {
        int dx = target.getX() - getPlayer().getX();
        int dy = target.getY() - getPlayer().getY();
        String direction = getAttackDirection(dx, dy);
        if (direction != null) {
            // The SDK's throwItem requires a distance parameter. We use Manhattan distance.
            int distance = Math.abs(dx) + Math.abs(dy);
            hero.throwItem(direction, distance);
        }
    }

    /**
     * Sử dụng vũ khí đặc biệt vào một mục tiêu.
     * Uses a special weapon on a target.
     */
    public void useSpecial(Node target) throws IOException {
        String direction = getAttackDirection(target.getX() - getPlayer().getX(), target.getY() - getPlayer().getY());
        if (direction != null) {
            hero.useSpecial(direction);
        }
    }

    /**
     * Helper để lấy hướng tấn công từ chênh lệch tọa độ.
     * Prioritizes the direction with the largest absolute difference.
     *
     * @param dx The difference in x-coordinates (target.x - player.x).
     * @param dy The difference in y-coordinates (target.y - player.y).
     * @return The attack direction ("u", "d", "l", "r") or null if no movement.
     */
    private String getAttackDirection(int dx, int dy) {
        if (Math.abs(dx) > Math.abs(dy)) { // Horizontal difference is greater
            if (dx > 0) {
                return "r"; // Right
            } else if (dx < 0) {
                return "l"; // Left
            }
        } else if (Math.abs(dy) > Math.abs(dx)) { // Vertical difference is greater
            if (dy > 0) {
                return "u"; // Down
            } else if (dy < 0) {
                return "d"; // Up
            }
        } else {
            if (dx > 0) return "r";
            if (dx < 0) return "l";
            if (dy > 0) return "u";
            if (dy < 0) return "d";
        }
        return null;
    }
}