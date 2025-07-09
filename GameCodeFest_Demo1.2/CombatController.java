import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.base.Node;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Quản lý logic chiến đấu. Chỉ thực hiện tấn công, không tự quyết định khi nào nên chiến đấu.
 */
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

    /**
     * FIX: Logic chiến đấu được tập trung hóa.
     * Tìm mục tiêu tốt nhất và tấn công hoặc truy đuổi.
     * @return true nếu một hành động chiến đấu được thực hiện.
     */
    public boolean handleCombat() throws IOException {
        Optional<AttackOption> bestAttackOption = findBestAttackOption();
        if (bestAttackOption.isPresent()) {
            AttackOption bestAction = bestAttackOption.get();
            System.out.println("Chiến đấu: Tấn công " + bestAction.target().getId() + " bằng " + bestAction.weapon().getId());
            fireWeapon(bestAction.weapon(), bestAction.target());
            return true;
        }

        Player nearestEnemy = findNearestAttackablePlayer();
        if (nearestEnemy != null) {
            if (shouldChase(nearestEnemy)) {
                System.out.println("Chiến đấu: Truy đuổi " + nearestEnemy.getId());
                movementController.moveTo(nearestEnemy, true);
                return true;
            }
        }

        // Không có mục tiêu tấn công hoặc truy đuổi
        return false;
    }

    private Optional<AttackOption> findBestAttackOption() {
        List<AttackOption> allPossibleAttacks = new ArrayList<>();
        List<Player> otherPlayers = actionHelper.getAttackablePlayers();
        if (otherPlayers == null || otherPlayers.isEmpty()) {
            return Optional.empty();
        }

        for (Player p : otherPlayers) {
            if (actionHelper.isInsideSafeZone(p)) {
                evaluateAllWeaponsForTarget(p, allPossibleAttacks);
            }
        }

        return allPossibleAttacks.stream().max(Comparator.comparingDouble(AttackOption::score));
    }

    private void evaluateAllWeaponsForTarget(Player target, List<AttackOption> options) {
        Inventory inv = actionHelper.getInventory();
        evaluateWeapon(inv.getGun(), target, options);
        evaluateWeapon(inv.getThrowable(), target, options);
        evaluateWeapon(inv.getSpecial(), target, options);
        if (actionHelper.distanceTo(target) <= 1.5) {
            evaluateWeapon(inv.getMelee(), target, options);
        }
    }

    private void evaluateWeapon(Weapon weapon, Player target, List<AttackOption> attackOptions) {
        if (weapon == null || weapon.getUseCount() <= 0) return;
        if (isWeaponInRange(weapon, target) && actionHelper.hasClearLineOfSight(target)) {
            attackOptions.add(new AttackOption(weapon, target, calculateThreatScore(weapon, target)));
        }
    }

    private double calculateThreatScore(Weapon weapon, Player target) {
        double baseScore = Configuration.getWeaponScore(weapon.getId());
        double distance = actionHelper.distanceTo(target);

        if (weapon.getType() == ElementType.THROWABLE) {
            baseScore += Configuration.THROWABLE_OPENER_BONUS;
        }

        if ("ROPE".equals(weapon.getId()) && !status.playersPulledByRope.contains(target.getId())) {
            baseScore += 1000;
        }

        boolean isLethal = weapon.getDamage() >= target.getHealth();
        double lethalBonus = isLethal ? Configuration.LETHAL_ATTACK_SCORE_BONUS : 0;
        double distancePenalty = distance * 10;

        return baseScore + lethalBonus - distancePenalty;
    }

    private boolean shouldChase(Player target) {
        if (!actionHelper.isInsideSafeZone(target)) return false;

        if (target.getId().equals(status.lastAttackedPlayerId) && status.turnsSinceLastAttack <= 2) {
            System.out.println("Vừa tấn công " + target.getId() + ", tiếp tục truy đuổi!");
            return true;
        }

        Node lastKnownLocation = status.getOtherPlayerLocation(target.getId());
        if (lastKnownLocation == null || actionHelper.distanceTo(target) <= actionHelper.distanceTo(lastKnownLocation)) {
            Inventory inv = actionHelper.getInventory();
            return Stream.of(inv.getGun(), inv.getThrowable(), inv.getSpecial())
                    .filter(Objects::nonNull)
                    .anyMatch(w -> isWeaponInRange(w, target));
        }

        double currentDistance = actionHelper.distanceTo(target);
        double lastDistance = actionHelper.distanceTo(lastKnownLocation);

        if (currentDistance > lastDistance + Configuration.RETREAT_THRESHOLD) {
            System.out.println("Mục tiêu " + target.getId() + " đang bỏ chạy quá xa. Ngừng truy đuổi.");
            return false;
        }

        Inventory inv = actionHelper.getInventory();
        if (Stream.of(inv.getGun(), inv.getThrowable(), inv.getSpecial())
                .filter(Objects::nonNull)
                .noneMatch(w -> isWeaponInRange(w, target))) {
            System.out.println("Mục tiêu " + target.getId() + " đã ra ngoài tầm bắn của mọi vũ khí tầm xa. Ngừng truy đuổi.");
            return false;
        }

        return true;
    }

    public Player findNearestAttackablePlayer() {
        List<Player> players = actionHelper.getAttackablePlayers();
        if (players == null || players.isEmpty()) return null;
        return players.stream()
                .filter(actionHelper::isInsideSafeZone)
                .min(Comparator.comparingDouble(actionHelper::distanceTo))
                .orElse(null);
    }

    private boolean isWeaponInRange(Weapon weapon, Player target) {
        if (weapon == null) return false;
        int[] rangeArray = weapon.getRange();
        if (rangeArray == null || rangeArray.length == 0) return false;
        int maxRange = Arrays.stream(rangeArray).max().orElse(0);
        return actionHelper.distanceTo(target) <= maxRange;
    }

    private void fireWeapon(Weapon weapon, Player target) throws IOException {
        System.out.println("Sử dụng vũ khí " + weapon.getId() + " (" + weapon.getType() + ") vào mục tiêu (" + target.getX() + "," + target.getY() + ")");

        status.lastAttackedPlayerId = target.getId();
        status.turnsSinceLastAttack = 0;

        if ("ROPE".equals(weapon.getId())) {
            status.playersPulledByRope.add(target.getId());
            System.out.println("Đã dùng ROPE lên " + target.getId() + ". Sẽ không ưu tiên dùng lại.");
        }

        switch (weapon.getType()) {
            case GUN -> actionHelper.shoot(target);
            case THROWABLE -> actionHelper.throwItem(target);
            case SPECIAL -> actionHelper.useSpecial(target);
            case MELEE -> actionHelper.attack(target);
            default -> System.out.println("Loại vũ khí không xác định: " + weapon.getType());
        }
    }
}
