import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class ItemController {
    private final ActionHelper actionHelper;
    private final MovementController movementController;
    private final HeroStatus status;

    public ItemController(ActionHelper actionHelper, MovementController movementController, HeroStatus status) {
        this.actionHelper = actionHelper;
        this.movementController = movementController;
        this.status = status;
    }

    /**
     * The main state-driven method for handling all item actions.
     * This will be called on every turn from the HeroController.
     * @return true if the bot is busy with an item action, false otherwise.
     */
    public boolean manageItemActions() throws IOException {
        return switch (status.getPickupState()) {
            case PENDING_PICKUP -> handlePendingPickup();
            default -> findAndInitiatePickup(); // If this returns false, HeroController should break a chest.
        };
    }

    public void handlePostChestBreak() throws IOException {
        if (status.lastAttackedChestPosition == null) return;

        int chestX = status.lastAttackedChestPosition.getX();
        int chestY = status.lastAttackedChestPosition.getY();
        boolean chestIsGone = actionHelper.getGameMap().getListObstacles().stream()
                .noneMatch(obs -> obs.getX() == chestX && obs.getY() == chestY);

        if (chestIsGone) {
            System.out.println("Rương tại (" + chestX + "," + chestY + ") đã bị phá.");
            if (actionHelper.isItemAt(chestX, chestY)) {
                if (actionHelper.getPlayer().getX() != chestX || actionHelper.getPlayer().getY() != chestY) {
                    movementController.moveTo(status.lastAttackedChestPosition, false);
                    return;
                }
            }
            status.incrementChestsBroken();
            status.lastAttackedChestPosition = null;
        }
    }

    /**
     * State 1: Looks for the best item. If found, it initiates the revoke/pickup process.
     */
    private boolean findAndInitiatePickup() throws IOException {
        GameMap gameMap = actionHelper.getGameMap();
        Stream<Element> availableItems = Stream.concat(gameMap.getListWeapons().stream(), gameMap.getListArmors().stream());

        Optional<Element> bestItemTarget = availableItems
                // Ensure we don't try to pick up an item that was just revoked IF it's the SAME item
                .filter(item -> !item.getId().equals(status.recentlyRevokedItemId))
                // Do not re-attempt picking up items that previously failed.
                .filter(item -> !status.failedPickupIds.contains(item.getId()))
                // Also, filter out items that the player already has and are not better
                .filter(this::isItemBetterThanEquipped) // <-- NEW FILTER
                .max(Comparator.comparingDouble(item -> calculateItemScore(item) / (actionHelper.distanceTo(item) + 1.0)));

        if (bestItemTarget.isPresent()) {
            Element target = bestItemTarget.get();
            // We only consider picking up if it's a beneficial item or a neutral (score 0, like full ammo clip if current is empty)
            if (calculateItemScore(target) >= 0) {
                if (actionHelper.distanceTo(target) == 0) {
                    initiatePickupProcess(target);
                } else {
                    movementController.moveTo(target, false);
                }
                return true; // Bot is busy with item action
            }
        }
        return false; // No good item to pick up, HeroController can do something else.
    }

    private void initiatePickupProcess(Element targetItem) throws IOException {
        ElementType targetType = targetItem.getType();
        Inventory inv = actionHelper.getInventory();
        status.targetedItemId = targetItem.getId();

        if (isSlotOccupied(targetType, inv)) {
            String idToRevoke = getEquippedItemIdForType(targetType, inv);
            if (idToRevoke != null) {
                System.out.println("Slot " + targetType.name() + " is full. Attempting REVOKE for item ID: " + idToRevoke + " and then PICKUP.");
                status.recentlyRevokedItemId = idToRevoke; // Remember item we tried to revoke for one turn.
                actionHelper.revokeItem(idToRevoke);
            } else {
                System.out.println("Slot " + targetType.name() + " is full, but no item found to revoke. Initiating PICKUP directly.");
                status.recentlyRevokedItemId = null; // No item to revoke, clear flag.
            }
        } else {
            System.out.println("Slot " + targetType.name() + " is empty. Initiating PICKUP.");
            status.recentlyRevokedItemId = null; // Clear flag if slot is empty.
        }

        status.setPickupState(HeroStatus.PickupState.PENDING_PICKUP); // No type parameter needed now.
        actionHelper.pickupItem(); // Always attempt pickup after initial checks.
    }

    private boolean handlePendingPickup() {
        System.out.println("State: PENDING_PICKUP. Checking inventory...");
        Inventory inv = actionHelper.getInventory();

        boolean pickupSucceeded = Stream.of(inv.getGun(), inv.getMelee(), inv.getSpecial(), inv.getThrowable(), inv.getArmor(), inv.getHelmet())
                .filter(java.util.Objects::nonNull)
                .anyMatch(item -> item.getId().equals(status.targetedItemId));

        if (pickupSucceeded) {
            System.out.println("Pickup successful! Acquired: " + status.targetedItemId);
            status.recentlyRevokedItemId = null; // Crucial: Clear after successful pickup.
            status.targetedItemId = null; // Crucial: Clear after successful pickup.
        } else {
            System.out.println("Pickup FAILED for item ID: " + status.targetedItemId + ". Adding to ignore list.");
            status.failedPickupIds.add(status.targetedItemId);
            status.recentlyRevokedItemId = null; // Crucial: Clear even if pickup failed.
            status.targetedItemId = null; // Crucial: Clear even if pickup failed.
        }

        status.setPickupState(HeroStatus.PickupState.IDLE); // No type parameter needed now.
        return true; // Bot was busy with pickup attempt.
    }

    private String getEquippedItemIdForType(ElementType type, Inventory inventory) {
        return switch (type) {
            case GUN -> inventory.getGun() != null ? inventory.getGun().getId() : null;
            case MELEE -> inventory.getMelee() != null ? inventory.getMelee().getId() : null;
            case ARMOR -> inventory.getArmor() != null ? inventory.getArmor().getId() : null;
            case HELMET -> inventory.getHelmet() != null ? inventory.getHelmet().getId() : null;
            case SPECIAL -> inventory.getSpecial() != null ? inventory.getSpecial().getId() : null;
            case THROWABLE -> inventory.getThrowable() != null ? inventory.getThrowable().getId() : null;
            default -> null;
        };
    }

    private boolean isSlotOccupied(ElementType type, Inventory inventory) {
        return switch (type) {
            case GUN -> inventory.getGun() != null;
            case MELEE -> inventory.getMelee() != null;
            case ARMOR -> inventory.getArmor() != null;
            case HELMET -> inventory.getHelmet() != null;
            case SPECIAL -> inventory.getSpecial() != null;
            case THROWABLE -> inventory.getThrowable() != null;
            default -> false;
        };
    }

    /**
     * Determines if the new item is genuinely better than the currently equipped one.
     * This prevents picking up an identical or worse item, especially after revoking.
     */
    private boolean isItemBetterThanEquipped(Element newItem) {
        Inventory inventory = actionHelper.getInventory();

        if (newItem instanceof Weapon newWeapon) {
            Weapon currentWeapon = switch (newWeapon.getType()) {
                case SPECIAL -> inventory.getSpecial();
                case GUN -> inventory.getGun();
                case THROWABLE -> inventory.getThrowable();
                case MELEE -> inventory.getMelee();
                default -> null;
            };

            // If no current weapon, new one is always better (score > 0 will handle this anyway)
            if (currentWeapon == null) return true;

            // If the item we're considering is the one we currently have, it's not "better" for replacement purposes
            if (newItem.getId().equals(currentWeapon.getId())) return false;

            double newScore = ScoreRegistry.getWeaponScore(newWeapon.getId());
            double currentScore = ScoreRegistry.getWeaponScore(currentWeapon.getId());

            return newScore > currentScore;
        }

        if (newItem instanceof Armor newArmor) {
            Armor currentArmor = (newArmor.getType() == ElementType.ARMOR) ? inventory.getArmor() : inventory.getHelmet();

            if (currentArmor == null) return true;
            if (newItem.getId().equals(currentArmor.getId())) return false;

            double newScore = ScoreRegistry.getArmorScore(newArmor.getId());
            double currentScore = ScoreRegistry.getArmorScore(currentArmor.getId());

            return newScore > currentScore;
        }
        return false; // Not a weapon or armor, or cannot determine if better.
    }


    private double calculateItemScore(Element newItem) {
        Inventory inventory = actionHelper.getInventory();
        if (newItem instanceof Weapon newWeapon) {
            Weapon currentWeapon = switch (newWeapon.getType()) {
                case SPECIAL -> inventory.getSpecial();
                case GUN -> inventory.getGun();
                case THROWABLE -> inventory.getThrowable();
                case MELEE -> inventory.getMelee();
                default -> null;
            };

            double newScore = ScoreRegistry.getWeaponScore(newWeapon.getId());
            if (currentWeapon == null) return newScore;

            double currentScore = ScoreRegistry.getWeaponScore(currentWeapon.getId());
            // Return the difference if new is better, else 0.
            return newScore > currentScore ? newScore - currentScore : 0;
        }
        if (newItem instanceof Armor newArmor) {
            Armor currentArmor = (newArmor.getType() == ElementType.ARMOR) ? inventory.getArmor() : inventory.getHelmet();
            double newScore = ScoreRegistry.getArmorScore(newArmor.getId());
            if (currentArmor == null) return newScore;

            double currentScore = ScoreRegistry.getArmorScore(currentArmor.getId());
            // Return the difference if new is better, else 0.
            return newScore > currentScore ? newScore - currentScore : 0;
        }
        return 0.0;
    }
}