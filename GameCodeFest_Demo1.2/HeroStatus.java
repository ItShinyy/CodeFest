import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.players.Player;

import java.util.*;

/**
 * Lưu trữ trạng thái và bộ nhớ ngắn hạn của bot qua các lượt.
 */
public class HeroStatus {
    public enum PickupState { IDLE, PENDING_REVOKE, PENDING_PICKUP }
    public enum Strategy { FARMING, HUNTING }

    // FIX: Loại bỏ isPvpMode và các phương thức liên quan vì đã có Strategy enum
    private Strategy currentStrategy = Strategy.FARMING;
    private int chestsBroken = 0;
    private PickupState pickupState = PickupState.IDLE;
    private boolean hasUsedCompass = false;

    public Node lastAttackedChestPosition = null;
    public String targetedItemId = null;
    public final List<String> failedPickupIds = new ArrayList<>();
    private Node lastPosition = null;
    public String itemToRevokeBeforePickup = null;

    public final Set<String> playersPulledByRope = new HashSet<>();
    public int pendingRevokeTurns = 0;

    public String lastAttackedPlayerId = null;
    public int turnsSinceLastAttack = 0;


    private final Map<String, Node> otherPlayerLocations = new HashMap<>();

    // --- Getters and Setters ---
    public Strategy getCurrentStrategy() { return this.currentStrategy; }
    public void setCurrentStrategy(Strategy strategy) { this.currentStrategy = strategy; }
    public PickupState getPickupState() { return this.pickupState; }
    public void setPickupState(PickupState state) { this.pickupState = state; }
    public void setItemToRevokeBeforePickup(String itemId) { this.itemToRevokeBeforePickup = itemId; }

    public boolean hasUsedCompass() {
        return this.hasUsedCompass;
    }
    public void setHasUsedCompass(boolean hasUsedCompass) {
        this.hasUsedCompass = hasUsedCompass;
    }

    public Node getOtherPlayerLocation(String playerId) {
        return otherPlayerLocations.get(playerId);
    }

    public void update(Player currentPlayer, List<Player> otherPlayers) {
        if (lastAttackedPlayerId != null) {
            turnsSinceLastAttack++;
        }

        if (lastPosition == null || currentPlayer.getX() != lastPosition.getX() || currentPlayer.getY() != lastPosition.getY()) {
            this.lastPosition = new Node(currentPlayer.getX(), currentPlayer.getY());
            resetItemPickupStatus();
            this.failedPickupIds.clear();
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
        this.itemToRevokeBeforePickup = null;
        this.pendingRevokeTurns = 0;
    }
}
