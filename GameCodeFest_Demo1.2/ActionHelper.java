import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.*;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActionHelper {
    private final Hero hero;
    private final GameMap gameMap;

    public static final String DRAGON_EGG_ID = "DRAGON_EGG";

    public ActionHelper(Hero hero, GameMap gameMap) {
        this.hero = hero;
        this.gameMap = gameMap;
    }

    public GameMap getGameMap() { return this.gameMap; }
    public Player getPlayer() { return gameMap.getCurrentPlayer(); }
    public Inventory getInventory() { return hero.getInventory(); }

    public List<Element> getAllItemsOnMap() {
        if (gameMap == null) return new ArrayList<>();
        return Stream.concat(
                Optional.ofNullable(gameMap.getListWeapons()).orElse(new ArrayList<>()).stream(),
                Stream.concat(
                        Optional.ofNullable(gameMap.getListArmors()).orElse(new ArrayList<>()).stream(),
                        Optional.ofNullable(gameMap.getListSupportItems()).orElse(new ArrayList<>()).stream()
                )
        ).collect(Collectors.toList());
    }

    public List<Obstacle> getAllObstaclesOnMap() {
        return Optional.ofNullable(gameMap.getListObstacles()).orElse(new ArrayList<>());
    }

    public List<Enemy> getAllEnemiesOnMap() {
        return Optional.ofNullable(gameMap.getListEnemies()).orElse(new ArrayList<>());
    }

    public List<Player> getAttackablePlayers() {
        return Optional.ofNullable(gameMap.getOtherPlayerInfo()).orElse(new ArrayList<>());
    }

    public Optional<Ally> findAlly(String allyId) {
        if (gameMap.getListAllies() == null) return Optional.empty();
        return gameMap.getListAllies().stream()
                .filter(ally -> ally.getId().equals(allyId))
                .findFirst();
    }

    public void move(String path) throws IOException { hero.move(path); }
    public void attack(Node target) throws IOException { hero.attack(getDirection(target)); }
    public void shoot(Node target) throws IOException { hero.shoot(getDirection(target)); }
    public void throwItem(Node target) throws IOException { hero.throwItem(getDirection(target)); }
    public void useSpecial(Node target) throws IOException { hero.useSpecial(getDirection(target)); }
    public void pickupItem() throws IOException { hero.pickupItem(); }
    public void revokeItem(String itemType) throws IOException { hero.revokeItem(itemType); }
    public void useItem(String itemId) throws IOException { hero.useItem(itemId); }

    public double distanceTo(Node target) {
        if (target == null) return Double.MAX_VALUE;
        return PathUtils.distance(getPlayer(), target);
    }

    public boolean isInsideSafeZone(Node target) {
        if (target == null || gameMap == null) {
            return false;
        }
        return PathUtils.checkInsideSafeArea(target, gameMap.getMapSize(), gameMap.getSafeZone());
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

    public boolean isWalkable(int x, int y) {
        Set<String> obstacleCoords = getAllObstaclesOnMap().stream()
                .map(obs -> obs.getX() + "," + obs.getY())
                .collect(Collectors.toSet());
        return isWalkable(x, y, obstacleCoords);
    }

    private boolean isWalkable(int x, int y, Set<String> obstacleCoords) {
        if (x < 0 || y < 0 || x >= gameMap.getMapSize() || y >= gameMap.getMapSize()) {
            return false;
        }
        if (!isInsideSafeZone(new Node(x, y))) {
            return false;
        }
        return !obstacleCoords.contains(x + "," + y);
    }

    public List<Node> getNodesToAvoid(boolean isPvpPath) {
        List<Node> nodes = new ArrayList<>();

        getAllObstaclesOnMap().stream()
                .filter(obs -> !obs.getTags().contains(ObstacleTag.CAN_GO_THROUGH))
                .forEach(nodes::add);

        getAllObstaclesOnMap().stream()
                .filter(obs -> obs.getTags().contains(ObstacleTag.TRAP))
                .forEach(nodes::add);

        nodes.addAll(getAllEnemiesOnMap());

        if (!isPvpPath && getAttackablePlayers() != null) {
            nodes.addAll(getAttackablePlayers());
        }

        return nodes;
    }

    public boolean hasStrongWeaponForPvp() {
        Inventory inv = getInventory();
        if (inv == null) return false;
        return Stream.of(inv.getGun(), inv.getThrowable(), inv.getSpecial(), inv.getMelee())
                .filter(Objects::nonNull)
                .anyMatch(w -> Configuration.getWeaponScore(w.getId()) >= Configuration.REACTIVE_PVP_WEAPON_SCORE);
    }

    public long getWeaponCount() {
        Inventory inv = getInventory();
        if (inv == null) return 0;
        return Stream.of(inv.getGun(), inv.getThrowable(), inv.getSpecial(), inv.getMelee())
                .filter(w -> w != null && !"HAND".equals(w.getId()))
                .count();
    }

    public double calculateTotalEquipmentScore() {
        Inventory inv = getInventory();
        if (inv == null) return 0;
        double totalScore = 0;
        if (inv.getGun() != null) totalScore += Configuration.getWeaponScore(inv.getGun().getId());
        if (inv.getMelee() != null) totalScore += Configuration.getWeaponScore(inv.getMelee().getId());
        if (inv.getThrowable() != null) totalScore += Configuration.getWeaponScore(inv.getThrowable().getId());
        if (inv.getSpecial() != null) totalScore += Configuration.getWeaponScore(inv.getSpecial().getId());
        if (inv.getArmor() != null) totalScore += Configuration.getArmorScore(inv.getArmor().getId());
        if (inv.getHelmet() != null) totalScore += Configuration.getArmorScore(inv.getHelmet().getId());
        return totalScore;
    }

    public boolean hasClearLineOfSight(Node target) {
        Set<String> blockerCoordinates = getBulletBlockingNodes(target).stream()
                .map(node -> node.getX() + "," + node.getY())
                .collect(Collectors.toSet());

        Player self = getPlayer();
        int x1 = self.getX();
        int y1 = self.getY();
        int x2 = target.getX();
        int y2 = target.getY();

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

    private List<Node> getBulletBlockingNodes(Node target) {
        List<Node> nodes = new ArrayList<>();
        getAllObstaclesOnMap().stream()
                .filter(obs -> !obs.getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH) && !obs.getId().equals(DRAGON_EGG_ID))
                .forEach(nodes::add);
        if (getAttackablePlayers() != null && target instanceof Player) {
            getAttackablePlayers().stream()
                    .filter(p -> !p.getID().equals(((Player)target).getID()))
                    .forEach(nodes::add);
        }
        return nodes;
    }

    public String getDirection(Node target) {
        if (target == null) return null;
        Player self = getPlayer();
        if(self == null) return null;

        int dx = target.getX() - self.getX();
        int dy = target.getY() - self.getY();
        if (dx == 0 && dy == 0) return null;

        return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "r" : "l") : (dy > 0 ? "u" : "d");
    }
}
