import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.players.Player;

import java.util.ArrayList;
import java.util.List;

public class HeroStatus {
    public enum PickupState { IDLE, PENDING_PICKUP }

    private boolean isPvpMode = false;
    private int chestsBroken = 0;
    private PickupState pickupState = PickupState.IDLE;
    // Removed: private ElementType itemTypeToProcess = null; // This field is no longer needed

    public Node lastAttackedChestPosition = null;
    public String targetedItemId = null;
    public final List<String> failedPickupIds = new ArrayList<>();
    private Node lastPosition = null;
    public String recentlyRevokedItemId = null;

    // Getters and Setters
    public boolean isPvpMode() { return this.isPvpMode; }
    public void setPvpMode(boolean mode) { this.isPvpMode = mode; }
    public int getChestsBroken() { return this.chestsBroken; }
    public PickupState getPickupState() { return this.pickupState; }

    // setPickupState method updated to remove 'targetType' parameter as it's no longer stored or used
    public void setPickupState(PickupState pickupState) { // Removed ElementType targetType parameter
        this.pickupState = pickupState;
        // The check for IDLE and setting itemTypeToProcess to null is also no longer needed.
    }

    public void update(Player currentPlayer) {
        if (lastPosition == null || currentPlayer.getX() != lastPosition.getX() || currentPlayer.getY() != lastPosition.getY()) {
            this.lastPosition = new Node(currentPlayer.getX(), currentPlayer.getY());
            this.failedPickupIds.clear();
            this.targetedItemId = null;
            this.recentlyRevokedItemId = null;
            System.out.println("Vị trí thay đổi, reset failedPickupIds, targetedItemId, and recentlyRevokedItemId.");
        }
    }

    public void incrementChestsBroken() {
        this.chestsBroken++;
        System.out.println("Số rương đã phá: " + this.chestsBroken);
    }

    public void resetChestsBroken() {
        this.chestsBroken = 0;
        System.out.println("Đã reset bộ đếm rương.");
    }
}