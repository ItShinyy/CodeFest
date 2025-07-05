import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.players.Player;

import java.util.ArrayList;
import java.util.HashMap; // FIX: Import HashMap
import java.util.List;
import java.util.Map; // FIX: Import Map

public class HeroStatus {
    public enum PickupState { IDLE, PENDING_REVOKE, PENDING_PICKUP }

    private boolean isPvpMode = false;
    private int chestsBroken = 0;
    private PickupState pickupState = PickupState.IDLE;
    private ElementType itemTypeBeingProcessed = null;

    public Node lastAttackedChestPosition = null;
    public String targetedItemId = null;
    public final List<String> failedPickupIds = new ArrayList<>();
    private Node lastPosition = null;
    public String recentlyRevokedItemId = null;
    public String itemToRevokeBeforePickup = null;

    // FIX: New map to track other players' locations for the "get closer" tactic.
    private final Map<String, Node> otherPlayerLocations = new HashMap<>();

    public boolean isPvpMode() { return this.isPvpMode; }
    public void setPvpMode(boolean mode) { this.isPvpMode = mode; }
    public int getChestsBroken() { return this.chestsBroken; }
    public PickupState getPickupState() { return this.pickupState; }

    public ElementType getItemTypeBeingProcessed() { return this.itemTypeBeingProcessed; }

    public void setPickupState(PickupState pickupState, ElementType type) {
        this.pickupState = pickupState;
        this.itemTypeBeingProcessed = type;
        if (pickupState == PickupState.IDLE) {
            this.itemTypeBeingProcessed = null;
        }
    }

    public void setItemToRevokeBeforePickup(String itemId) { this.itemToRevokeBeforePickup = itemId; }
    public String getItemToRevokeBeforePickup() { return this.itemToRevokeBeforePickup; }

    public void update(Player currentPlayer, List<Player> otherPlayers) {
        if (lastPosition == null || currentPlayer.getX() != lastPosition.getX() || currentPlayer.getY() != lastPosition.getY()) {
            this.lastPosition = new Node(currentPlayer.getX(), currentPlayer.getY());
            this.failedPickupIds.clear();
            this.targetedItemId = null;
            this.recentlyRevokedItemId = null;
            this.itemToRevokeBeforePickup = null;
            System.out.println("Player position changed, resetting item-related status flags.");
        }

        // FIX: Update the locations of all visible players.
        otherPlayerLocations.clear();
        if (otherPlayers != null) {
            for (Player p : otherPlayers) {
                otherPlayerLocations.put(p.getId(), new Node(p.getX(), p.getY()));
            }
        }
    }

    public Node getOtherPlayerLocation(String playerId) {
        return otherPlayerLocations.get(playerId);
    }

    public void incrementChestsBroken() {
        this.chestsBroken++;
        System.out.println("Chests broken count: " + this.chestsBroken);
    }

    public void resetChestsBroken() {
        this.chestsBroken = 0;
        System.out.println("Chests broken count reset.");
    }
}
