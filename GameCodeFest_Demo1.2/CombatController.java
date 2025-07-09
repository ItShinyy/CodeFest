import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.base.Node;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;


public class CombatController {
    private record AttackOption(Weapon weapon, Player target, double score) {}

    private final ActionHelper actionHelper;
    private final MovementController movementController;
    private final HeroStatus status;

    public CombatController(ActionHelper actionHelper, MovementController movementController, HeroStatus status) {
        this.actionHelper = actionHelper;
        this.movementController = movementController;
        this.status = status;
    }

    public boolean handleCombat() throws IOException {
        Inventory inv = actionHelper.getInventory();
        if (inv == null) {
            status.setEngaging(false);
            return false;
        }

        if (tryUseCompass(inv)) {
            return true;
        }

        Optional<AttackOption> bestAttackOption = findBestOverallAttack();

        if (bestAttackOption.isPresent()) {
            status.setEngaging(true);
            Weapon weapon = bestAttackOption.get().weapon();
            Player target = bestAttackOption.get().target();
            int maxRange = Arrays.stream(weapon.getRange()).max().orElse(0);

            if (isAlignedAndInRange(target, maxRange) && actionHelper.hasClearLineOfSight(target)) {
                fireWeapon(weapon, target);
            } else {
                movementController.moveToAttackPosition(target, maxRange);
            }
            return true;
        }

        status.setEngaging(false);
        Player nearestEnemy = findNearestAttackablePlayer();
        if (nearestEnemy != null && shouldChase(nearestEnemy)) {
            movementController.moveTo(nearestEnemy, true);
            return true;
        }

        return false;
    }

    private boolean tryUseCompass(Inventory inv) throws IOException {
        Optional<SupportItem> compass = inv.getListSupportItem() != null ? inv.getListSupportItem().stream().filter(i -> "COMPASS".equals(i.getId())).findFirst() : Optional.empty();
        if (compass.isPresent() && !status.hasUsedCompass()) {
            List<Player> attackablePlayers = actionHelper.getAttackablePlayers();
            if (attackablePlayers != null) {
                long enemiesInRadius = attackablePlayers.stream()
                        .filter(p -> actionHelper.distanceTo(p) <= Configuration.COMPASS_STUN_RADIUS)
                        .count();

                if (enemiesInRadius >= 1) {
                    actionHelper.useItem("COMPASS");
                    status.setHasUsedCompass(true);
                    attackablePlayers.stream()
                            .filter(p -> actionHelper.distanceTo(p) <= Configuration.COMPASS_STUN_RADIUS)
                            .forEach(p -> status.stunnedTargetTurns.put(p.getID(), Configuration.COMPASS_STUN_TURNS));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAlignedAndInRange(Player target, int range) {
        Player self = actionHelper.getPlayer();
        if (self == null || target == null) return false;

        int dx = Math.abs(self.getX() - target.getX());
        int dy = Math.abs(self.getY() - target.getY());

        return (dx == 0 && dy <= range) || (dy == 0 && dx <= range);
    }

    private Optional<AttackOption> findBestOverallAttack() {
        Inventory inv = actionHelper.getInventory();
        if (inv == null) return Optional.empty();

        return actionHelper.getAttackablePlayers().stream()
                .filter(actionHelper::isInsideSafeZone)
                .flatMap(player ->
                        Stream.of(inv.getThrowable(), inv.getSpecial(), inv.getGun(), inv.getMelee())
                                .filter(Objects::nonNull)
                                .filter(w -> w.getUseCount() > 0)
                                .map(weapon -> new AttackOption(weapon, player, calculateThreatScore(weapon, player)))
                )
                .max(Comparator.comparingDouble(AttackOption::score));
    }

    public boolean handleObstacleAttack(Obstacle obstacle) throws IOException {
        if (!actionHelper.isInsideSafeZone(obstacle)) {
            return false;
        }

        if (actionHelper.distanceTo(obstacle) <= 1) {
            actionHelper.attack(obstacle);
            if ("CHEST".equals(obstacle.getType().name())) {
                status.lastAttackedChestPosition = new Node(obstacle.getX(), obstacle.getY());
            }
            return true;
        }

        Optional<Node> bestAdjacentNode = actionHelper.getValidAdjacentNodes(obstacle).stream()
                .min(Comparator.comparingDouble(actionHelper::distanceTo));

        if (bestAdjacentNode.isPresent()) {
            movementController.moveTo(bestAdjacentNode.get(), false);
            return true;
        }
        return false;
    }

    private double calculateThreatScore(Weapon weapon, Player target) {
        double baseScore = Configuration.getWeaponScore(weapon.getId());
        double distance = actionHelper.distanceTo(target);

        if (weapon.getType() == ElementType.THROWABLE) {
            baseScore += 2000;
        }

        if (status.stunnedTargetTurns.getOrDefault(target.getID(), 0) > 0) {
            baseScore += 400;
        }
        if (status.smokedTargetTurns.getOrDefault(target.getID(), 0) > 0 && (weapon.getType() == ElementType.MELEE || "SHOTGUN".equals(weapon.getId()))) {
            baseScore += 350;
        }
        if (status.playersPulledByRope.contains(target.getID()) && (weapon.getType() == ElementType.MELEE || "SHOTGUN".equals(weapon.getId()))) {
            baseScore += 500;
        }
        if ("ROPE".equals(weapon.getId()) && !status.playersPulledByRope.contains(target.getID())) {
            baseScore += 1000;
        }
        if ("SEED".equals(weapon.getId()) || "BELL".equals(weapon.getId())) {
            baseScore += 200;
        }
        if ("SMOKE".equals(weapon.getId())) {
            baseScore += 300;
        }

        boolean isLethal = weapon.getDamage() >= target.getHealth();
        double lethalBonus = isLethal ? Configuration.LETHAL_ATTACK_SCORE_BONUS : 0;

        float maxHp = 100.0f;
        float currentHp = target.getHealth();
        double healthBonus = (1.0 - (currentHp / maxHp)) * Configuration.LOW_HEALTH_TARGET_BONUS;

        double distancePenalty = distance * 10;

        return baseScore + lethalBonus + healthBonus - distancePenalty;
    }

    private void fireWeapon(Weapon weapon, Player target) throws IOException {
        if (actionHelper.getDirection(target) == null) {
            return;
        }

        status.lastAttackedPlayerId = target.getID();
        status.turnsSinceLastAttack = 0;

        switch (weapon.getId()) {
            case "ROPE" -> {
                status.playersPulledByRope.add(target.getID());
                status.stunnedTargetTurns.put(target.getID(), Configuration.ROPE_STUN_TURNS);
            }
            case "SAHUR_BAT", "SEED" -> status.stunnedTargetTurns.put(target.getID(), Configuration.SAHUR_BAT_STUN_TURNS);
            case "SMOKE" -> status.smokedTargetTurns.put(target.getID(), Configuration.SMOKE_DURATION_TURNS);
        }

        switch (weapon.getType()) {
            case GUN -> actionHelper.shoot(target);
            case THROWABLE -> actionHelper.throwItem(target);
            case SPECIAL -> actionHelper.useSpecial(target);
            case MELEE -> actionHelper.attack(target);
        }
    }

    private boolean shouldChase(Player target) {
        if (!actionHelper.isInsideSafeZone(target)) {
            return false;
        }

        if (target.getID().equals(status.lastAttackedPlayerId) && status.turnsSinceLastAttack <= 2) {
            return true;
        }

        Node lastKnownLocation = status.getOtherPlayerLocation(target.getID());
        if (lastKnownLocation != null && actionHelper.distanceTo(target) > actionHelper.distanceTo(lastKnownLocation)) {
            return false;
        }

        return actionHelper.hasStrongWeaponForPvp();
    }

    public Player findNearestAttackablePlayer() {
        List<Player> players = actionHelper.getAttackablePlayers();
        if (players == null || players.isEmpty()) return null;
        return players.stream()
                .filter(actionHelper::isInsideSafeZone)
                .min(Comparator.comparingDouble(actionHelper::distanceTo))
                .orElse(null);
    }
}
