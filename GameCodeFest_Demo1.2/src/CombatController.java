import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.base.Node;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Quản lý logic chiến đấu với các điều kiện vào/ra PvP chặt chẽ hơn.
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

    public boolean shouldEnterPvpMode() {
        Player nearestEnemy = findNearestAttackablePlayer();
        if (nearestEnemy == null) return false;

        boolean inEngageDistance = actionHelper.distanceTo(nearestEnemy) <= Configuration.PVP_ENGAGE_DISTANCE;
        boolean hasSufficientHp = actionHelper.getPlayer().getHealth() >= 60;
        boolean hasStrongWeapon = actionHelper.hasStrongRangedWeapon();
        boolean hasEnoughWeapons = actionHelper.getWeaponCount() >= Configuration.MIN_WEAPONS_FOR_PVP;

        return inEngageDistance && hasSufficientHp && hasStrongWeapon && hasEnoughWeapons;
    }

    public boolean handleCombat() throws IOException {
        if (status.isPvpMode()) {
            return handlePvpCombat();
        }
        return handleOpportunisticAttack();
    }

    private boolean handlePvpCombat() throws IOException {
        if (actionHelper.getInventory().getGun() == null && actionHelper.getInventory().getThrowable() == null && actionHelper.getInventory().getSpecial() == null) {
            System.out.println("Hết đạn tầm xa. Tắt chế độ PvP.");
            status.setPvpMode(false);
            return false;
        }

        Optional<AttackOption> bestAttackOption = findBestAttackOption();
        if (bestAttackOption.isPresent()) {
            AttackOption bestAction = bestAttackOption.get();
            System.out.println("Trong PvP: Tấn công " + bestAction.target().getId() + " bằng " + bestAction.weapon().getId());
            fireWeapon(bestAction.weapon(), bestAction.target());
            return true;
        }

        Player nearestEnemy = findNearestAttackablePlayer();
        if (nearestEnemy != null) {
            if (shouldChase(nearestEnemy)) {
                System.out.println("Trong PvP: Truy đuổi " + nearestEnemy.getId());
                movementController.moveTo(nearestEnemy, true);
            } else {
                System.out.println("Mục tiêu " + nearestEnemy.getId() + " đang bỏ chạy hoặc ngoài tầm bắn. Tắt chế độ PvP.");
                status.setPvpMode(false);
            }
            return true;
        }

        System.out.println("Không tìm thấy kẻ địch trong PvP mode. Tắt chế độ PvP.");
        status.setPvpMode(false);
        return false;
    }

    private boolean handleOpportunisticAttack() throws IOException {
        Player nearestEnemy = findNearestAttackablePlayer();
        if (nearestEnemy == null || actionHelper.distanceTo(nearestEnemy) > Configuration.PVP_ENGAGE_DISTANCE) {
            return false;
        }

        Optional<AttackOption> bestAttack = findBestAttackOption();
        if (bestAttack.isPresent()) {
            System.out.println("Tấn công cơ hội vào " + bestAttack.get().target().getId());
            fireWeapon(bestAttack.get().weapon(), bestAttack.get().target());
            return true;
        }
        return false;
    }

    private Optional<AttackOption> findBestAttackOption() {
        List<AttackOption> allPossibleAttacks = new ArrayList<>();
        List<Player> otherPlayers = actionHelper.getAttackablePlayers();
        if (otherPlayers == null || otherPlayers.isEmpty()) {
            return Optional.empty();
        }

        for (Player p : otherPlayers) {
            evaluateAllWeaponsForTarget(p, allPossibleAttacks);
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
        boolean isLethal = weapon.getDamage() >= target.getHealth();
        double distance = actionHelper.distanceTo(target);
        double lethalBonus = isLethal ? Configuration.LETHAL_ATTACK_SCORE_BONUS : 0;
        double distancePenalty = distance * 10;
        return baseScore + lethalBonus - distancePenalty;
    }

    private boolean shouldChase(Player target) {
        // FIX: This call is now valid because getOtherPlayerLocation exists in HeroStatus
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

    private Player findNearestAttackablePlayer() {
        List<Player> players = actionHelper.getAttackablePlayers();
        if (players == null || players.isEmpty()) return null;
        return players.stream()
                .min(Comparator.comparingDouble(actionHelper::distanceTo))
                .orElse(null);
    }

    private boolean isWeaponInRange(Weapon weapon, Player target) {
        if (weapon == null) return false;
        int[] rangeArray = weapon.getRange();
        if (rangeArray == null || rangeArray.length == 0) return false;

        int maxRange = 0;
        for(int r : rangeArray) {
            if (r > maxRange) {
                maxRange = r;
            }
        }
        return actionHelper.distanceTo(target) <= maxRange;
    }

    private void fireWeapon(Weapon weapon, Node target) throws IOException {
        System.out.println("Sử dụng vũ khí " + weapon.getId() + " (" + weapon.getType() + ") vào mục tiêu (" + target.getX() + "," + target.getY() + ")");
        switch (weapon.getType()) {
            case GUN -> actionHelper.shoot(target);
            case THROWABLE -> actionHelper.throwItem(target);
            case SPECIAL -> actionHelper.useSpecial(target);
            case MELEE -> actionHelper.attack(target);
            default -> System.out.println("Loại vũ khí không xác định: " + weapon.getType());
        }
    }
}
