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

    public boolean handlePostChestBreak() throws IOException {
        Node chestPos = status.lastAttackedChestPosition;
        if (chestPos == null) return false;

        Optional<Element> bestNearbyItem = findBestNearbyItem(chestPos);

        if (bestNearbyItem.isPresent()) {
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
            return true;
        }
        return false;
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
                .filter(actionHelper::isInsideSafeZone)
                .filter(item -> !status.failedPickupIds.contains(item.getId()))
                .filter(this::isItemAnUpgrade)
                .max(Comparator.comparingDouble(this::calculateItemValue));
    }

    public Optional<PrioritizedTarget> findBestItemOnMap() {
        return actionHelper.getAllItemsOnMap().stream()
                .filter(actionHelper::isInsideSafeZone)
                .filter(item -> !status.failedPickupIds.contains(item.getId()))
                .filter(this::isItemAnUpgrade)
                .map(item -> new PrioritizedTarget(item, movementController.getPathLengthTo(item, false)))
                .filter(pt -> pt.pathLength() != Integer.MAX_VALUE)
                .max(Comparator.comparingDouble(pt -> calculateItemValue((Element) pt.target()) / (pt.pathLength() + 1.0)));
    }

    // FIX: Loại bỏ phương thức không được sử dụng
    // public boolean isAnyWeaponUpgradeAvailable() { ... }

    public void initiatePickupProcess(Element targetItem) throws IOException {
        Inventory inv = actionHelper.getInventory();
        String idToRevoke = getIdOfItemToRevoke(targetItem, inv);

        if ("NO_UPGRADE".equals(idToRevoke)) {
            System.out.println("Mục tiêu " + targetItem.getId() + " không phải là một nâng cấp đủ tốt. Bỏ qua.");
            return;
        }

        status.targetedItemId = targetItem.getId();

        if (idToRevoke != null) {
            System.out.println("Cần vứt vật phẩm '" + idToRevoke + "' để nhặt " + targetItem.getId());
            status.setItemToRevokeBeforePickup(idToRevoke);
            status.setPickupState(HeroStatus.PickupState.PENDING_REVOKE);
            actionHelper.revokeItem(idToRevoke);
        } else {
            System.out.println("Không cần bỏ đồ. Nhặt vật phẩm: " + targetItem.getId());
            status.setPickupState(HeroStatus.PickupState.PENDING_PICKUP);
            actionHelper.pickupItem();
        }
    }

    private void handlePendingRevoke() throws IOException {
        status.pendingRevokeTurns++;
        String idToRevoke = status.itemToRevokeBeforePickup;

        if (status.pendingRevokeTurns > 2) {
            System.out.println("Không thể vứt vật phẩm '" + idToRevoke + "' sau 2 lượt. Tạm thời bỏ qua vật phẩm " + status.targetedItemId);
            status.failedPickupIds.add(status.targetedItemId);
            status.resetItemPickupStatus();
            return;
        }

        boolean isRevoked = isItemRevoked(idToRevoke, actionHelper.getInventory());

        if (isRevoked) {
            System.out.println("Vật phẩm '" + idToRevoke + "' đã được giải phóng. Tiến hành nhặt vật phẩm mới.");
            status.setPickupState(HeroStatus.PickupState.PENDING_PICKUP);
            actionHelper.pickupItem();
        } else {
            System.out.println("Vẫn đang chờ vứt vật phẩm '" + idToRevoke + "'...");
        }
    }

    private void handlePendingPickup() throws IOException {
        Inventory inv = actionHelper.getInventory();
        boolean pickupSucceeded = Stream.of(inv.getGun(), inv.getMelee(), inv.getSpecial(), inv.getThrowable(), inv.getArmor(), inv.getHelmet())
                .filter(Objects::nonNull)
                .anyMatch(item -> status.targetedItemId.equals(item.getId()));

        if (!pickupSucceeded && inv.getListSupportItem() != null) {
            pickupSucceeded = inv.getListSupportItem().stream().anyMatch(item -> status.targetedItemId.equals(item.getId()));
        }

        if (pickupSucceeded) {
            System.out.println("Nhặt thành công: " + status.targetedItemId);
            if ("COMPASS".equals(status.targetedItemId)) {
                actionHelper.useItem("COMPASS");
                status.setHasUsedCompass(true);
            }
        } else {
            System.out.println("Nhặt thất bại: " + status.targetedItemId);
            if (status.targetedItemId != null) {
                status.failedPickupIds.add(status.targetedItemId);
            }
        }
        status.resetItemPickupStatus();
    }

    private String getIdOfItemToRevoke(Element newItem, Inventory inventory) {
        switch (newItem) {
            case null -> {
                return null;
            }
            case Armor newArmor -> {
                Armor currentArmor = (newArmor.getType() == ElementType.ARMOR) ? inventory.getArmor() : inventory.getHelmet();
                if (currentArmor == null) return null;
                return isItemAnUpgrade(newItem) ? currentArmor.getId() : "NO_UPGRADE";
            }
            case SupportItem newSupportItem -> {
                List<SupportItem> items = inventory.getListSupportItem();
                if (items == null || items.size() < Configuration.MAX_SUPPORT_ITEMS) return null;

                if ("COMPASS".equals(newSupportItem.getId()) && !status.hasUsedCompass()) {
                    System.out.println("Ưu tiên nhặt COMPASS. Tìm vật phẩm hỗ trợ yếu nhất để vứt.");
                    return items.stream()
                            .min(Comparator.comparingDouble(item -> Configuration.getSupportItemScore(item.getId())))
                            .map(Element::getId)
                            .orElse("NO_UPGRADE");
                }

                Optional<SupportItem> worstItem = items.stream()
                        .min(Comparator.comparingDouble(item -> Configuration.getSupportItemScore(item.getId())));

                if (worstItem.isPresent() && isItemAnUpgrade(newItem)) {
                    return worstItem.get().getId();
                }
                return "NO_UPGRADE";
            }
            case Weapon newWeapon -> {
                Weapon currentWeaponInSlot = getWeaponFromInventoryByType(inventory, newWeapon.getType());
                if (currentWeaponInSlot == null || "HAND".equals(currentWeaponInSlot.getId())) {
                    return null;
                }
                if (Configuration.getWeaponScore(newWeapon.getId()) > Configuration.getWeaponScore(currentWeaponInSlot.getId())) {
                    return currentWeaponInSlot.getId();
                } else {
                    return "NO_UPGRADE";
                }
            }
            default -> {
            }
        }

        return null;
    }

    private boolean isItemRevoked(String idToRevoke, Inventory inventory) {
        boolean itemStillExists = Stream.of(
                        inventory.getGun(), inventory.getMelee(), inventory.getSpecial(), inventory.getThrowable(),
                        inventory.getArmor(), inventory.getHelmet()
                )
                .filter(Objects::nonNull)
                .anyMatch(item -> idToRevoke.equals(item.getId()));

        if (itemStillExists) return false;

        if (inventory.getListSupportItem() != null) {
            return inventory.getListSupportItem().stream().noneMatch(item -> idToRevoke.equals(item.getId()));
        }

        return true;
    }

    private Weapon getWeaponFromInventoryByType(Inventory inventory, ElementType type) {
        return switch (type) {
            case GUN -> inventory.getGun();
            case MELEE -> inventory.getMelee();
            case SPECIAL -> inventory.getSpecial();
            case THROWABLE -> inventory.getThrowable();
            default -> null;
        };
    }

    private boolean isItemAnUpgrade(Element newItem) {
        return calculateItemValue(newItem) > 0;
    }

    public double calculateItemValue(Element newItem) {
        Inventory inventory = actionHelper.getInventory();
        if (newItem instanceof Weapon newWeapon) {
            Weapon currentWeaponInSlot = getWeaponFromInventoryByType(inventory, newWeapon.getType());
            double newScore = Configuration.getWeaponScore(newWeapon.getId());
            if (currentWeaponInSlot == null || "HAND".equals(currentWeaponInSlot.getId())) return newScore;
            double currentScore = Configuration.getWeaponScore(currentWeaponInSlot.getId());
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
            if ("COMPASS".equals(newSupportItem.getId())) {
                return status.hasUsedCompass() ? 0 : Configuration.getSupportItemScore("COMPASS") * 2;
            }

            List<SupportItem> currentItems = inventory.getListSupportItem();
            double newScore = Configuration.getSupportItemScore(newSupportItem.getId());
            if (currentItems == null || currentItems.size() < Configuration.MAX_SUPPORT_ITEMS) return newScore;
            double worstScore = currentItems.stream()
                    .mapToDouble(item -> Configuration.getSupportItemScore(item.getId()))
                    .min().orElse(0);
            return Math.max(0, newScore - worstScore);
        }
        return 0.0;
    }
}
