import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
        if (status.isPvpMode()) {
            return handlePvpCombat();
        }
        return handleOpportunisticAttack();
    }

    private boolean handlePvpCombat() throws IOException {
        // FIX: Restored the call to isOutOfRangedAmmo() to fix the "unused" warning.
        if (actionHelper.isOutOfRangedAmmo()) {
            System.out.println("No ranged ammo. Disabling dedicated PVP mode.");
            status.setPvpMode(false);
            return false;
        }

        Player target = actionHelper.getNearestPlayer();
        if (target == null) {
            status.setPvpMode(false);
            return false;
        }

        executeBestPvpAction(target);
        return true;
    }

    public boolean handleOpportunisticAttack() throws IOException {
        Inventory inv = actionHelper.getInventory();
        Weapon meleeWeapon = inv.getMelee();
        if (meleeWeapon == null) return false;

        int meleeRange = (meleeWeapon.getRange() != null && meleeWeapon.getRange().length > 0) ? meleeWeapon.getRange()[0] : 1;

        List<Node> potentialTargets = new ArrayList<>();
        potentialTargets.addAll(actionHelper.getAttackablePlayers());
        potentialTargets.addAll(actionHelper.getGameMap().getListEnemies());

        Optional<Node> bestTarget = potentialTargets.stream()
                .filter(t -> actionHelper.distanceTo(t) <= meleeRange)
                .min(Comparator.comparingDouble(actionHelper::distanceTo));

        if (bestTarget.isPresent()) {
            System.out.println("Found opportunistic melee target: " + ((Element) bestTarget.get()).getId());
            actionHelper.attack(bestTarget.get());
            return true;
        }
        return false;
    }

    private void executeBestPvpAction(Player target) throws IOException {

        Inventory inv = actionHelper.getInventory();
        Weapon specialWeapon = inv.getSpecial();

        if (specialWeapon != null && "ROPE".equals(specialWeapon.getId()) && specialWeapon.getUseCount() > 0) {
            if (isWeaponInRange(specialWeapon, target)) {
                System.out.println("Rope Tactic: Using ROPE on player " + target.getId());
                actionHelper.useSpecial(target);
                return;
            }
        }

        List<AttackOption> attackOptions = new ArrayList<>();
        evaluateWeapon(inv.getGun(), target, attackOptions);
        evaluateWeapon(inv.getThrowable(), target, attackOptions);
        evaluateWeapon(inv.getSpecial(), target, attackOptions);

        Optional<AttackOption> bestAttack = attackOptions.stream().max(Comparator.comparingDouble(o -> o.score));

        if (bestAttack.isPresent()) {
            AttackOption best = bestAttack.get();
            System.out.println("Standard PVP: Best attack is with " + best.weapon.getId() + " against " + best.target.getId());
            fireWeapon(best.weapon, best.target);
        } else {
            System.out.println("No valid ranged attack option. Chasing player " + target.getId());
            movementController.moveTo(target, true);
        }
    }

    private void evaluateWeapon(Weapon weapon, Player target, List<AttackOption> attackOptions) {
        if (weapon == null || weapon.getUseCount() <= 0) return;
        if (isWeaponInRange(weapon, target) && actionHelper.hasClearLineOfSight(target)) {
            attackOptions.add(new AttackOption(weapon, target, calculateAttackScore(weapon, target.getHealth())));
        }
    }

    private boolean isWeaponInRange(Weapon weapon, Player target) {
        if (weapon == null) return false;
        int[] rangeArray = weapon.getRange();
        if (rangeArray == null || rangeArray.length == 0) return false;
        int maxRange = rangeArray[rangeArray.length - 1];
        return actionHelper.distanceTo(target) <= maxRange;
    }

    private double calculateAttackScore(Weapon weapon, float targetHp) {
        boolean isLethal = weapon.getDamage() >= targetHp;
        return Configuration.getWeaponScore(weapon.getId()) + (isLethal ? Configuration.LETHAL_ATTACK_SCORE_BONUS : 0);
    }

    private void fireWeapon(Weapon weapon, Node target) throws IOException {
        switch (weapon.getType()) {
            case GUN -> actionHelper.shoot(target);
            case THROWABLE -> actionHelper.throwItem(target);
            case SPECIAL -> actionHelper.useSpecial(target);
            default -> System.out.println("Attempted to fire a non-ranged weapon: " + weapon.getType());
        }
    }
}
