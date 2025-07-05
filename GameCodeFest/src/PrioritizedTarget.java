import jsclub.codefest.sdk.base.Node;

/**
 * A simple data class to hold a potential target and its calculated path length.
 * This allows the HeroController to easily compare different types of objectives
 * (e.g., chests vs. items) and choose the most efficient one.
 */
public record PrioritizedTarget(Node target, int pathLength) {
    // This record is intentionally simple and contains no additional logic.
}
