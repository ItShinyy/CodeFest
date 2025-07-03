import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.model.effects.Effect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Xử lý toàn bộ logic chiến đấu, từ tấn công cơ hội đến PVP.
 * Handles all combat logic, from opportunistic attacks to PVP.
 */
public class CombatController {
    private record AttackOption(Weapon weapon, double score) {}

    private final ActionHelper actionHelper;
    private final MovementController movementController;
    private final HeroStatus status;

    public CombatController(ActionHelper actionHelper, MovementController movementController, HeroStatus status) {
        this.actionHelper = actionHelper;
        this.movementController = movementController;
        this.status = status;
    }

    public boolean handlePvpCombat() throws IOException {
        if (actionHelper.isOutOfRangedAmmo()) {
            System.out.println("Hết vũ khí tầm xa, tắt chế độ PVP.");
            status.setPvpMode(false);
            return false;
        }

        Player target = actionHelper.getNearestPlayer();
        if (target == null) {
            status.setPvpMode(false);
            return false;
        }

        return findAndExecuteBestAction(target);
    }

    public boolean handleOpportunisticAttack() throws IOException {
        Inventory inv = actionHelper.getInventory();

        // The bot is not considered "ready" if it has no ranged weapons.
        if (inv.getGun() == null && inv.getThrowable() == null && inv.getSpecial() == null) {
            return false;
        }

        Player nearestTarget = actionHelper.getNearestPlayer();

        // Check if an enemy is nearby
        if (nearestTarget != null && actionHelper.distanceTo(nearestTarget) <= 7) {

            // --- NEW LOGIC ---
            // Before engaging, check if we have a weapon that is good enough.
            final double MINIMUM_SCORE_TO_ENGAGE = 600; // Threshold: Only fight if we have a weapon better than an AXE.

            double bestHeldWeaponScore = 0;
            if (inv.getGun() != null)
                bestHeldWeaponScore = Math.max(bestHeldWeaponScore, ScoreRegistry.getWeaponScore(inv.getGun().getId()));
            if (inv.getThrowable() != null)
                bestHeldWeaponScore = Math.max(bestHeldWeaponScore, ScoreRegistry.getWeaponScore(inv.getThrowable().getId()));
            if (inv.getSpecial() != null)
                bestHeldWeaponScore = Math.max(bestHeldWeaponScore, ScoreRegistry.getWeaponScore(inv.getSpecial().getId()));

            // Only engage if our best weapon meets the minimum score requirement.
            if (bestHeldWeaponScore >= MINIMUM_SCORE_TO_ENGAGE) {
                System.out.println("Phát hiện người chơi ở gần VÀ có vũ khí tốt! Chuyển sang chế độ PVP.");
                status.setPvpMode(true);
                return findAndExecuteBestAction(nearestTarget);
            } else {
                System.out.println("Phát hiện người chơi ở gần nhưng vũ khí quá yếu. Tiếp tục farm.");
            }
        }

        return false;
    }

    private boolean findAndExecuteBestAction(Player target) throws IOException {
        Inventory inv = actionHelper.getInventory();
        List<AttackOption> possibleAttacks = new ArrayList<>();
        double distance = actionHelper.distanceTo(target);

        // Weapon evaluation logic remains the same
        evaluateWeapon(inv.getSpecial(), distance, true, possibleAttacks, target.getHealth());
        evaluateWeapon(inv.getGun(), distance, actionHelper.hasClearLineOfSight(target), possibleAttacks, target.getHealth());
        evaluateWeapon(inv.getThrowable(), distance, true, possibleAttacks, target.getHealth());
        evaluateWeapon(inv.getMelee(), distance, distance == 1, possibleAttacks, target.getHealth());

        Optional<AttackOption> bestAttack = possibleAttacks.stream().max(Comparator.comparingDouble(AttackOption::score));

        if (bestAttack.isPresent()) {
            Weapon weaponToUse = bestAttack.get().weapon();
            System.out.println("Executing best attack with " + weaponToUse.getType() + ": " + weaponToUse.getId());

            // --- NEW LOGIC ---
            // Use a switch to call the correct action based on the weapon's type.
            switch (weaponToUse.getType()) {
                case GUN:
                    actionHelper.shoot(target);
                    break;
                case THROWABLE:
                    actionHelper.throwItem(target);
                    break;
                case SPECIAL:
                    actionHelper.useSpecial(target);
                    break;
                case MELEE:
                    actionHelper.attack(target);
                    break;
            }
            return true;
        }

        // Move closer if no attack is possible
        System.out.println("Không có lựa chọn tấn công hợp lệ. Di chuyển lại gần mục tiêu.");
        String path = movementController.getPathTo(target, true);
        if (path != null && !path.isEmpty()) {
            actionHelper.move(path);
            return true;
        }

        return false;
    }

    private void evaluateWeapon(Weapon weapon, double distance, boolean isConditionMet, List<AttackOption> options, float targetHp) {
        if (weapon != null && weapon.getUseCounts() > 0 && distance <= weapon.getRange() && isConditionMet) {
            double score = calculateAttackScore(weapon, targetHp);
            options.add(new AttackOption(weapon, score));
        }
    }

    private double calculateAttackScore(Weapon weapon, float targetHp) {
        double score = ScoreRegistry.getWeaponScore(weapon.getId());

        if (weapon.getDamage() >= targetHp) {
            score += 500;
        }

        if (weapon.getEffects() != null) {
            for (Effect effect : weapon.getEffects()) {
                if (List.of("STUN", "PULL", "REVERSE", "KNOCKBACK").contains(effect.id)) {
                    score += 200;
                }
            }
        }
        return score;
    }
}