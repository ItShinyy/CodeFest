import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;

// // Listens for game updates and triggers the hero's utility class.
class MapUpdateListener implements Emitter.Listener {
    // // Reference to the hero's brain.
    private final HeroController heroUtils;

    public MapUpdateListener(Hero hero) {
        this.heroUtils = new HeroController(hero);
    }

    // // Called on every game update from the server.
    @Override
    public void call(Object... args) {
        try {
            // // Delegate all logic to the utility class.
            heroUtils.executeTurn(args);
        } catch (Exception e) {
            // // Catch any errors during execution.
            System.err.println("Error during turn execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
}