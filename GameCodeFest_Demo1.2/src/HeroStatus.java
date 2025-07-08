import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.players.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lưu trữ trạng thái và bộ nhớ ngắn hạn của bot qua các lượt.
 */
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
    public String itemToRevokeBeforePickup = null;
    public int postChestBreakCheckTurns = 0;

    private final Map<String, Node> otherPlayerLocations = new HashMap<>();

    // --- Getters and Setters ---
    public boolean isPvpMode() { return this.isPvpMode; }
    public void setPvpMode(boolean mode) { this.isPvpMode = mode; }
    public int getChestsBroken() { return this.chestsBroken; }
    public PickupState getPickupState() { return this.pickupState; }
    public void setPickupState(PickupState state) { this.pickupState = state; }
    public ElementType getItemTypeBeingProcessed() { return itemTypeBeingProcessed; }
    public void setItemTypeBeingProcessed(ElementType type) { this.itemTypeBeingProcessed = type; }
    public void setItemToRevokeBeforePickup(String itemId) { this.itemToRevokeBeforePickup = itemId; }

    /**
     * FIX: Added the missing getter to resolve the error in CombatController.
     * This also fixes the "never queried" warning for the otherPlayerLocations map.
     * @param playerId The ID of the player to look up.
     * @return The last known Node (location) of the player.
     */
    public Node getOtherPlayerLocation(String playerId) {
        return otherPlayerLocations.get(playerId);
    }

    public void update(Player currentPlayer, List<Player> otherPlayers) {
        if (lastPosition == null || currentPlayer.getX() != lastPosition.getX() || currentPlayer.getY() != lastPosition.getY()) {
            this.lastPosition = new Node(currentPlayer.getX(), currentPlayer.getY());
            resetItemPickupStatus();
            this.failedPickupIds.clear();
            System.out.println("Vị trí người chơi thay đổi, reset các cờ trạng thái vật phẩm.");
        }

        otherPlayerLocations.clear();
        if (otherPlayers != null) {
            for (Player p : otherPlayers) {
                otherPlayerLocations.put(p.getId(), new Node(p.getX(), p.getY()));
            }
        }
    }

    public void incrementChestsBroken() {
        this.chestsBroken++;
        System.out.println("Số rương đã phá: " + this.chestsBroken);
    }

    public void resetItemPickupStatus() {
        this.pickupState = PickupState.IDLE;
        this.targetedItemId = null;
        this.itemTypeBeingProcessed = null;
        this.itemToRevokeBeforePickup = null;
    }
}
