import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element; // FIX: Import Element
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class MovementController {
    private final ActionHelper actionHelper;
    private static final Random random = new Random();

    public MovementController(ActionHelper actionHelper) {
        this.actionHelper = actionHelper;
    }

    public boolean handleDragonEgg() throws IOException {
        Optional<Obstacle> dragonEggOpt = actionHelper.getGameMap().getListObstacles().stream()
                .filter(obs -> obs.getId().equals(ActionHelper.DRAGON_EGG_ID))
                .findFirst();

        if (dragonEggOpt.isPresent()) {
            System.out.println("DRAGON EGG detected! Overriding all other actions.");
            moveToOrAttack(dragonEggOpt.get());
            return true;
        }
        return false;
    }

    public void moveToOrAttack(Node target) throws IOException {
        Weapon meleeWeapon = actionHelper.getInventory().getMelee();
        int meleeRange = 1;
        if (meleeWeapon != null && meleeWeapon.getRange() != null && meleeWeapon.getRange().length > 0) {
            meleeRange = meleeWeapon.getRange()[0];
        }

        if (actionHelper.distanceTo(target) <= meleeRange) {
            // FIX: Cast Node to Element to resolve 'getId' error.
            System.out.println("Attacking target: " + ((Element) target).getId());
            actionHelper.attack(target);
            if (target instanceof Obstacle) {
                actionHelper.getStatus().lastAttackedChestPosition = new Node(target.getX(), target.getY());
            }
        } else {
            System.out.println("Moving towards target: " + ((Element) target).getId());
            moveTo(target, false);
        }
    }

    public Optional<PrioritizedTarget> findBestChest() {
        GameMap gameMap = actionHelper.getGameMap();
        List<Obstacle> chestList = gameMap.getListObstacles().stream()
                .filter(o -> o.getType() == ElementType.CHEST)
                .toList();

        if (chestList.isEmpty()) {
            return Optional.empty();
        }

        return chestList.stream()
                .min(Comparator.comparing((Obstacle c) -> c.getId().equals(ActionHelper.DRAGON_EGG_ID) ? 0 : 1)
                        .thenComparingDouble(actionHelper::distanceTo))
                .map(chest -> {
                    String path = getPathTo(chest, false);
                    int pathLength = (path != null) ? path.length() : Integer.MAX_VALUE;
                    // FIX: Use the new shared PrioritizedTarget record.
                    return new PrioritizedTarget(chest, pathLength);
                });
    }

    public void moveTo(Node target, boolean isPvpPath) throws IOException {
        String path = getPathTo(target, isPvpPath);
        if (path != null && !path.isEmpty()) {
            actionHelper.move(path);
        } else {
            System.out.println("No path found to (" + target.getX() + "," + target.getY() + "). Attempting random move.");
            moveRandomly();
        }
    }

    public void moveRandomly() throws IOException {
        String[] directions = {"u", "d", "l", "r"};
        String randomDirection = directions[random.nextInt(directions.length)];
        actionHelper.move(randomDirection);
        System.out.println("Moving randomly: " + randomDirection);
    }

    public String getPathTo(Node target, boolean isPvpPath) {
        GameMap gameMap = actionHelper.getGameMap();
        Player self = actionHelper.getPlayer();
        List<Node> nodesToAvoid = actionHelper.getNodesToAvoid();
        return PathUtils.getShortestPath(gameMap, nodesToAvoid, self, target, isPvpPath);
    }
}
