import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.*;

import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;


import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public GameMap getGameMap() { return this.gameMap; }
    public Player getPlayer() { return gameMap.getCurrentPlayer(); }
    public HeroStatus getStatus() { return this.status; }
    public Inventory getInventory() { return hero.getInventory(); }

    /**
     * FIX: Now includes all enemies and all obstacles tagged as TRAP in the list of nodes to avoid.
     */
    public List<Node> getNodesToAvoid() {
        List<Node> nodes = new ArrayList<>();
        gameMap.getListObstacles().forEach(obs -> {
            boolean isTrap = obs.getTags().contains(ObstacleTag.TRAP);
            boolean isSolid = !obs.getTags().contains(ObstacleTag.CAN_GO_THROUGH);
            if ((isTrap || isSolid) && !obs.getId().equals(DRAGON_EGG_ID)) {
                nodes.add(obs);
            }
        });
        nodes.addAll(gameMap.getListEnemies());
        nodes.addAll(getAttackablePlayers());
        return nodes;
    }

    /**
     * FIX: New method to check if the bot has a weapon meeting a minimum score threshold.
     */
    public boolean hasWeaponWithMinScore(double minScore) {
        Inventory inv = getInventory();
        return Stream.of(inv.getGun(), inv.getMelee(), inv.getThrowable(), inv.getSpecial())
                .filter(Objects::nonNull)
                .anyMatch(weapon -> Configuration.getWeaponScore(weapon.getId()) >= minScore);
    }

    public List<Player> getAttackablePlayers() {
        return Optional.ofNullable(gameMap.getOtherPlayerInfo()).orElse(Collections.emptyList());
    }

    public Player getNearestPlayer() {
        return getAttackablePlayers().stream()
                .min(Comparator.comparingDouble(this::distanceTo))
                .orElse(null);
    }

    public double distanceTo(Node target) {
        if (target == null) return Double.MAX_VALUE;
        return PathUtils.distance(getPlayer(), target);
    }

    public void move(String path) throws IOException { hero.move(path); }
    public void pickupItem() throws IOException { hero.pickupItem(); }
    public void revokeItem(String itemType) throws IOException { hero.revokeItem(itemType); }
    public void useItem(String itemId) throws IOException { hero.useItem(itemId); }

    public void attack(Node target) throws IOException {
        if (target == null) return;
        String direction = getAttackDirection(target.getX() - getPlayer().getX(), target.getY() - getPlayer().getY());
        if (direction != null) hero.attack(direction);
    }

    public void shoot(Node target) throws IOException {
        if (target == null) return;
        String direction = getAttackDirection(target.getX() - getPlayer().getX(), target.getY() - getPlayer().getY());
        if (direction != null) hero.shoot(direction);
    }

    public void throwItem(Node target) throws IOException {
        if (target == null) return;
        String direction = getAttackDirection(target.getX() - getPlayer().getX(), target.getY() - getPlayer().getY());
        if (direction != null) hero.throwItem(direction);
    }

    public void useSpecial(Node target) throws IOException {
        if (target == null) return;
        String direction = getAttackDirection(target.getX() - getPlayer().getX(), target.getY() - getPlayer().getY());
        if (direction != null) hero.useSpecial(direction);
    }

    public boolean isOutOfRangedAmmo() {
        Inventory inv = getInventory();
        return (inv.getSpecial() == null || inv.getSpecial().getUseCount() <= 0) &&
                (inv.getGun() == null || inv.getGun().getUseCount() <= 0) &&
                (inv.getThrowable() == null || inv.getThrowable().getUseCount() <= 0);
    }

    public List<Node> getBulletBlockingNodes() {
        List<Node> nodes = new ArrayList<>();
        gameMap.getListObstacles().stream()
                .filter(obs -> !obs.getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH) && !obs.getId().equals(DRAGON_EGG_ID))
                .forEach(nodes::add);
        nodes.addAll(getAttackablePlayers());
        return nodes;
    }

    public boolean hasClearLineOfSight(Player target) {
        Set<String> blockerCoordinates = getBulletBlockingNodes().stream()
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

    private String getAttackDirection(int dx, int dy) {
        if (dx == 0 && dy == 0) return null;
        return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "r" : "l") : (dy > 0 ? "u" : "d");
    }
}
