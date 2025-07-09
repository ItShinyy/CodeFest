import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.players.Player;

import java.util.*;

public class HeroStatus {
    public enum PickupState { IDLE, PENDING_REVOKE, PENDING_PICKUP }
    public enum Strategy { FARMING, HUNTING }

    private Strategy currentStrategy = Strategy.FARMING;
    private PickupState pickupState = PickupState.IDLE;
    private boolean hasUsedCompass = false;
    private boolean isEngaging = false;

    public Node lastAttackedChestPosition = null;
    public String targetedItemId = null;
    public final List<String> failedPickupIds = new ArrayList<>();
    private Node lastPosition = null;
    public String itemToRevokeBeforePickup = null;

    public final Set<String> playersPulledByRope = new HashSet<>();
    public int pendingRevokeTurns = 0;

    public String lastAttackedPlayerId = null;
    public int turnsSinceLastAttack = 0;

    public final Map<String, Integer> stunnedTargetTurns = new HashMap<>();
    public final Map<String, Integer> smokedTargetTurns = new HashMap<>();

    private final Map<String, Node> otherPlayerLocations = new HashMap<>();

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

    public boolean isEngaging() {
        return this.isEngaging;
    }
    public void setEngaging(boolean engaging) {
        this.isEngaging = engaging;
    }

    public Node getOtherPlayerLocation(String playerId) {
        return otherPlayerLocations.get(playerId);
    }

    public void update(Player currentPlayer, List<Player> otherPlayers) {
        stunnedTargetTurns.replaceAll((_, turns) -> turns - 1);
        smokedTargetTurns.replaceAll((_, turns) -> turns - 1);
        stunnedTargetTurns.entrySet().removeIf(entry -> entry.getValue() <= 0);
        smokedTargetTurns.entrySet().removeIf(entry -> entry.getValue() <= 0);

        if (lastAttackedPlayerId != null) {
            turnsSinceLastAttack++;
        }

        if (otherPlayers == null || otherPlayers.isEmpty()) {
            isEngaging = false;
        }

        if (lastPosition == null || currentPlayer.getX() != lastPosition.getX() || currentPlayer.getY() != lastPosition.getY()) {
            this.lastPosition = new Node(currentPlayer.getX(), currentPlayer.getY());
            resetItemPickupStatus();
            this.failedPickupIds.clear();
        }

        otherPlayerLocations.clear();
        if (otherPlayers != null) {
            for (Player p : otherPlayers) {
                otherPlayerLocations.put(p.getID(), new Node(p.getX(), p.getY()));
            }
        }
    }

    public void resetItemPickupStatus() {
        this.pickupState = PickupState.IDLE;
        this.targetedItemId = null;
        this.itemToRevokeBeforePickup = null;
        this.pendingRevokeTurns = 0;
    }
}
