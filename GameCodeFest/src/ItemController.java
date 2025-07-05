import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.base.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

    public boolean isMidPickupProcess() {
        return status.getPickupState() != HeroStatus.PickupState.IDLE;
    }

    public boolean manageItemActions() throws IOException {
        if (handlePostChestBreak()) return true;

        return switch (status.getPickupState()) {
            case PENDING_REVOKE -> handlePendingRevoke();
            case PENDING_PICKUP -> handlePendingPickup();
            default -> false;
        };
    }

    public Optional<PrioritizedTarget> findBestItemOnGround() {
        GameMap gameMap = actionHelper.getGameMap();
        List<Element> allItemsOnMap = new ArrayList<>();
        if (gameMap.getListWeapons() != null) allItemsOnMap.addAll(gameMap.getListWeapons());
        if (gameMap.getListArmors() != null) allItemsOnMap.addAll(gameMap.getListArmors());
        if (gameMap.getListSupportItems() != null) allItemsOnMap.addAll(gameMap.getListSupportItems());

        return allItemsOnMap.stream()
                .filter(Objects::nonNull)
                .filter(item -> !isLocationObstructed(item))
                .filter(item -> !item.getId().equals(status.recentlyRevokedItemId))
                .filter(item -> !status.failedPickupIds.contains(item.getId()))
                .filter(this::isItemBetterThanEquipped)
                .max(Comparator.comparingDouble(item -> calculateItemScore(item) / (actionHelper.distanceTo(item) + 1.0)))
                .map(item -> {
                    if (actionHelper.distanceTo(item) == 0) {
                        try {
                            initiatePickupProcess(item);
                        } catch (IOException e) {
                            System.err.println("Error initiating pickup: " + e.getMessage());
                        }
                        return new PrioritizedTarget(item, 0);
                    }
                    String path = movementController.getPathTo(item, false);
                    int pathLength = (path != null) ? path.length() : Integer.MAX_VALUE;
                    // FIX: Use the new shared PrioritizedTarget record.
                    return new PrioritizedTarget(item, pathLength);
                });
    }

    // The rest of the methods are unchanged and correct.
    private boolean handlePostChestBreak() throws IOException {
        if (status.lastAttackedChestPosition == null) return false;

        int chestX = status.lastAttackedChestPosition.getX();
        int chestY = status.lastAttackedChestPosition.getY();

        boolean chestIsGone = actionHelper.getGameMap().getListObstacles().stream()
                .noneMatch(obs -> obs.getX() == chestX && obs.getY() == chestY);

        if (chestIsGone) {
            System.out.println("Chest at (" + chestX + "," + chestY + ") is confirmed broken.");
            if (actionHelper.getPlayer().getX() != chestX || actionHelper.getPlayer().getY() != chestY) {
                System.out.println("Moving to broken chest location.");
                movementController.moveTo(status.lastAttackedChestPosition, false);
                return true;
            } else {
                System.out.println("Player is at broken chest location. Resetting and searching for items.");
                status.incrementChestsBroken();
                status.lastAttackedChestPosition = null;
                return false;
            }
        }
        return false;
    }

    private void initiatePickupProcess(Element targetItem) throws IOException {
        status.targetedItemId = targetItem.getId();
        String idToRevoke = findItemIdToRevoke(targetItem);
        status.setItemToRevokeBeforePickup(idToRevoke);

        if (idToRevoke != null) {
            System.out.println("Slot for " + targetItem.getType().name() + " is full. Revoking item ID: " + idToRevoke);
            status.recentlyRevokedItemId = idToRevoke;
            status.setPickupState(HeroStatus.PickupState.PENDING_REVOKE, targetItem.getType());
            actionHelper.revokeItem(idToRevoke);
        } else {
            System.out.println("Slot for " + targetItem.getType().name() + " is available. Picking up item.");
            status.setPickupState(HeroStatus.PickupState.PENDING_PICKUP, targetItem.getType());
            actionHelper.pickupItem();
        }
    }

    private String findItemIdToRevoke(Element targetItem) {
        ElementType targetType = targetItem.getType();
        Inventory inv = actionHelper.getInventory();
        if (!isSlotOccupied(targetType, inv)) {
            return null;
        }

        if (targetType == ElementType.SUPPORT_ITEM) {
            return inv.getListSupportItem().stream()
                    .min(Comparator.comparingDouble(item -> Configuration.getSupportItemScore(item.getId())))
                    .map(SupportItem::getId)
                    .orElse(null);
        }
        return getEquippedItemIdForType(targetType, inv);
    }

    private boolean handlePendingRevoke() throws IOException {
        System.out.println("State: PENDING_REVOKE. Verifying if revoke succeeded...");
        ElementType typeToProcess = status.getItemTypeBeingProcessed();
        Inventory inv = actionHelper.getInventory();

        String revokedItemId = status.getItemToRevokeBeforePickup();
        boolean slotFreed = !isItemInInventory(revokedItemId, inv);

        if (slotFreed) {
            System.out.println("Revoke successful for item ID: " + revokedItemId + ". Proceeding to PICKUP.");
            status.setPickupState(HeroStatus.PickupState.PENDING_PICKUP, typeToProcess);
            actionHelper.pickupItem();
            return true;
        } else {
            System.out.println("Revoke FAILED for item ID: " + revokedItemId + ". Slot still occupied. Blacklisting target item and resetting state.");
            status.failedPickupIds.add(status.targetedItemId);
            status.setPickupState(HeroStatus.PickupState.IDLE, null);
            return false;
        }
    }

    private boolean handlePendingPickup() {
        System.out.println("State: PENDING_PICKUP. Verifying inventory for new item...");
        boolean pickupSucceeded = isItemInInventory(status.targetedItemId, actionHelper.getInventory());

        if (pickupSucceeded) {
            System.out.println("Pickup successful! Acquired: " + status.targetedItemId);
        } else {
            System.out.println("Pickup FAILED for item ID: " + status.targetedItemId + ". Blacklisting and resetting state.");
            status.failedPickupIds.add(status.targetedItemId);
        }
        status.setPickupState(HeroStatus.PickupState.IDLE, null);
        return false;
    }

    private boolean isLocationObstructed(Node location) {
        boolean isObstructed = actionHelper.getGameMap().getListIndestructibles().stream()
                .anyMatch(obstacle -> obstacle.getX() == location.getX() && obstacle.getY() == location.getY());
        if (isObstructed) {
            System.out.println("Ignoring item at obstructed location (" + location.getX() + "," + location.getY() + ")");
        }
        return isObstructed;
    }

    private boolean isItemInInventory(String itemId, Inventory inv) {
        if (itemId == null || inv == null) return false;
        return Stream.of(inv.getGun(), inv.getMelee(), inv.getSpecial(), inv.getThrowable(), inv.getArmor(), inv.getHelmet())
                .filter(Objects::nonNull)
                .anyMatch(item -> item.getId().equals(itemId))
                ||
                (inv.getListSupportItem() != null && inv.getListSupportItem().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(item -> item.getId().equals(itemId)));
    }

    private String getEquippedItemIdForType(ElementType type, Inventory inventory) {
        return switch (type) {
            case GUN -> Optional.ofNullable(inventory.getGun()).map(Weapon::getId).orElse(null);
            case MELEE -> Optional.ofNullable(inventory.getMelee()).map(Weapon::getId).orElse(null);
            case ARMOR -> Optional.ofNullable(inventory.getArmor()).map(Armor::getId).orElse(null);
            case HELMET -> Optional.ofNullable(inventory.getHelmet()).map(Armor::getId).orElse(null);
            case SPECIAL -> Optional.ofNullable(inventory.getSpecial()).map(Weapon::getId).orElse(null);
            case THROWABLE -> Optional.ofNullable(inventory.getThrowable()).map(Weapon::getId).orElse(null);
            case SUPPORT_ITEM -> Optional.ofNullable(inventory.getListSupportItem())
                    .orElse(List.of())
                    .stream()
                    .map(SupportItem::getId)
                    .findFirst()
                    .orElse(null);
            default -> null;
        };
    }

    private boolean isSlotOccupied(ElementType type, Inventory inventory) {
        return switch (type) {
            case GUN -> inventory.getGun() != null;
            case MELEE -> inventory.getMelee() != null && !inventory.getMelee().getId().equals("HAND");
            case ARMOR -> inventory.getArmor() != null;
            case HELMET -> inventory.getHelmet() != null;
            case SPECIAL -> inventory.getSpecial() != null;
            case THROWABLE -> inventory.getThrowable() != null;
            case SUPPORT_ITEM ->
                    inventory.getListSupportItem() != null && inventory.getListSupportItem().size() >= Configuration.MAX_SUPPORT_ITEMS;
            default -> false;
        };
    }

    private boolean isItemBetterThanEquipped(Element newItem) {
        return calculateItemScore(newItem) > 0;
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
            double newScore = Configuration.getWeaponScore(newWeapon.getId());
            if (currentWeapon == null) return newScore;
            if (newWeapon.getId().equals(currentWeapon.getId())) return 0;
            double currentScore = Configuration.getWeaponScore(currentWeapon.getId());
            return newScore - currentScore;
        }
        if (newItem instanceof Armor newArmor) {
            Armor currentArmor = (newArmor.getType() == ElementType.ARMOR) ? inventory.getArmor() : inventory.getHelmet();
            double newScore = Configuration.getArmorScore(newArmor.getId());
            if (currentArmor == null) return newScore;
            if (newArmor.getId().equals(currentArmor.getId())) return 0;
            double currentScore = Configuration.getArmorScore(currentArmor.getId());
            return newScore - currentScore;
        }
        if (newItem instanceof SupportItem newSupportItem) {
            List<SupportItem> currentItems = inventory.getListSupportItem();
            double newScore = Configuration.getSupportItemScore(newSupportItem.getId());
            if (currentItems == null || currentItems.isEmpty()) return newScore;
            if (currentItems.stream().anyMatch(item -> item.getId().equals(newSupportItem.getId()))) return 0;

            if (currentItems.size() < Configuration.MAX_SUPPORT_ITEMS) {
                return newScore;
            }
            SupportItem worstCurrentItem = currentItems.stream()
                    .min(Comparator.comparingDouble(item -> Configuration.getSupportItemScore(item.getId())))
                    .orElse(null);

            if (worstCurrentItem == null) return newScore;

            double worstScore = Configuration.getSupportItemScore(worstCurrentItem.getId());
            return newScore - worstScore;
        }
        return 0.0;
    }
}
