import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.base.Node;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Quản lý logic vật phẩm.
 */
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

    public void manageItemActions() throws IOException {
        switch (status.getPickupState()) {
            case PENDING_REVOKE -> handlePendingRevoke();
            case PENDING_PICKUP -> handlePendingPickup();
        }
    }

    /**
     * FIX: Handles the complete looting process for a broken chest.
     * It now finds the best available item nearby and initiates an action.
     * It returns true if an action was taken, allowing the HeroController to loop until all items are looted.
     */
    public boolean handlePostChestBreak() throws IOException {
        Node chestPos = status.lastAttackedChestPosition;
        if (chestPos == null) return false;

        Optional<Element> bestNearbyItem = findBestNearbyItem(chestPos);

        if (bestNearbyItem.isPresent()) {
            // If we are already targeting this item, let the state machine handle it.
            if (status.targetedItemId != null && status.targetedItemId.equals(bestNearbyItem.get().getId())) {
                return false;
            }

            Node itemLocation = bestNearbyItem.get();
            System.out.println("Loot từ rương: Ưu tiên nhặt " + bestNearbyItem.get().getId());

            if (actionHelper.distanceTo(itemLocation) == 0) {
                initiatePickupProcess(bestNearbyItem.get());
            } else {
                movementController.moveTo(itemLocation, false);
            }
            return true; // Action was taken
        }

        return false; // No more items to loot
    }

    private Optional<Element> findBestNearbyItem(Node center) {
        return Stream.of(
                        actionHelper.findItemAt(center.getX(), center.getY()),
                        actionHelper.findItemAt(center.getX() + 1, center.getY()),
                        actionHelper.findItemAt(center.getX() - 1, center.getY()),
                        actionHelper.findItemAt(center.getX(), center.getY() + 1),
                        actionHelper.findItemAt(center.getX(), center.getY() - 1)
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isItemAnUpgrade)
                .max(Comparator.comparingDouble(this::calculateItemValue));
    }

    public Optional<PrioritizedTarget> findBestItemOnMap() {
        return actionHelper.getAllItemsOnMap().stream()
                .filter(item -> !status.failedPickupIds.contains(item.getId()))
                .filter(this::isItemAnUpgrade)
                .map(item -> new PrioritizedTarget(item, movementController.getPathLengthTo(item, false)))
                .filter(pt -> pt.pathLength() != Integer.MAX_VALUE)
                .max(Comparator.comparingDouble(pt -> calculateItemValue((Element) pt.target()) / (pt.pathLength() + 1.0)));
    }

    public void initiatePickupProcess(Element targetItem) throws IOException {
        ElementType targetType = targetItem.getType();
        Inventory inv = actionHelper.getInventory();
        status.targetedItemId = targetItem.getId();
        status.setItemTypeBeingProcessed(targetType);

        String idToRevoke = getEquippedItemIdForType(targetType, inv);
        if (idToRevoke != null) {
            System.out.println("Cần bỏ vật phẩm " + idToRevoke + " trước khi nhặt " + targetItem.getId());
            status.setItemToRevokeBeforePickup(idToRevoke);
            actionHelper.revokeItem(idToRevoke);
            status.setPickupState(HeroStatus.PickupState.PENDING_REVOKE);
        } else {
            System.out.println("Không cần bỏ đồ. Nhặt vật phẩm: " + targetItem.getId());
            actionHelper.pickupItem();
            status.setPickupState(HeroStatus.PickupState.PENDING_PICKUP);
        }
    }

    private void handlePendingRevoke() throws IOException {
        ElementType targetType = status.getItemTypeBeingProcessed();
        String equippedItemId = getEquippedItemIdForType(targetType, actionHelper.getInventory());

        if (equippedItemId == null || !equippedItemId.equals(status.itemToRevokeBeforePickup)) {
            System.out.println("Vật phẩm cũ đã được bỏ. Tiến hành nhặt vật phẩm mới: " + status.targetedItemId);
            actionHelper.pickupItem();
            status.setPickupState(HeroStatus.PickupState.PENDING_PICKUP);
        } else {
            System.out.println("Vẫn đang chờ bỏ vật phẩm " + status.itemToRevokeBeforePickup);
        }
    }

    private void handlePendingPickup() throws IOException {
        Inventory inv = actionHelper.getInventory();
        boolean pickupSucceeded = Stream.of(inv.getGun(), inv.getMelee(), inv.getSpecial(), inv.getThrowable(), inv.getArmor(), inv.getHelmet())
                .filter(Objects::nonNull)
                .anyMatch(item -> item.getId().equals(status.targetedItemId));

        if (!pickupSucceeded && inv.getListSupportItem() != null) {
            pickupSucceeded = inv.getListSupportItem().stream().anyMatch(item -> item.getId().equals(status.targetedItemId));
        }

        if (pickupSucceeded) {
            System.out.println("Nhặt thành công: " + status.targetedItemId);
            if ("COMPASS".equals(status.targetedItemId)) {
                actionHelper.useItem("COMPASS");
            }
        } else {
            System.out.println("Nhặt thất bại: " + status.targetedItemId);
            if (status.targetedItemId != null) {
                status.failedPickupIds.add(status.targetedItemId);
            }
        }
        status.resetItemPickupStatus();
    }

    private String getEquippedItemIdForType(ElementType type, Inventory inventory) {
        return switch (type) {
            case GUN -> inventory.getGun() != null ? inventory.getGun().getId() : null;
            case MELEE -> inventory.getMelee() != null && !"HAND".equals(inventory.getMelee().getId()) ? inventory.getMelee().getId() : null;
            case ARMOR -> inventory.getArmor() != null ? inventory.getArmor().getId() : null;
            case HELMET -> inventory.getHelmet() != null ? inventory.getHelmet().getId() : null;
            case SPECIAL -> inventory.getSpecial() != null ? inventory.getSpecial().getId() : null;
            case THROWABLE -> inventory.getThrowable() != null ? inventory.getThrowable().getId() : null;
            case SUPPORT_ITEM -> {
                List<SupportItem> items = inventory.getListSupportItem();
                if (items != null && items.size() >= Configuration.MAX_SUPPORT_ITEMS) {
                    yield items.stream()
                            .min(Comparator.comparingDouble(item -> Configuration.getSupportItemScore(item.getId())))
                            .map(Element::getId).orElse(null);
                }
                yield null;
            }
            default -> null;
        };
    }

    private boolean isItemAnUpgrade(Element newItem) {
        return calculateItemValue(newItem) > 0;
    }

    public double calculateItemValue(Element newItem) {
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
            if (currentWeapon == null || "HAND".equals(currentWeapon.getId())) return newScore;
            double currentScore = Configuration.getWeaponScore(currentWeapon.getId());
            return Math.max(0, newScore - currentScore);
        }
        if (newItem instanceof Armor newArmor) {
            Armor currentArmor = (newArmor.getType() == ElementType.ARMOR) ? inventory.getArmor() : inventory.getHelmet();
            double newScore = Configuration.getArmorScore(newArmor.getId());
            if (currentArmor == null) return newScore;
            double currentScore = Configuration.getArmorScore(currentArmor.getId());
            return Math.max(0, newScore - currentScore);
        }
        if (newItem instanceof SupportItem newSupportItem) {
            List<SupportItem> currentItems = inventory.getListSupportItem();
            double newScore = Configuration.getSupportItemScore(newSupportItem.getId());
            if (currentItems == null || currentItems.size() < Configuration.MAX_SUPPORT_ITEMS) {
                return newScore;
            }
            double worstScore = currentItems.stream()
                    .mapToDouble(item -> Configuration.getSupportItemScore(item.getId()))
                    .min().orElse(0);
            return Math.max(0, newScore - worstScore);
        }
        return 0.0;
    }
}
