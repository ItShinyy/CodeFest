import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;

/**
 * Listens for game updates and triggers the hero's controller class.
 * Includes a recovery mechanism to handle critical SDK errors.
 */
class MapUpdateListener implements Emitter.Listener {
    private HeroController heroController;
    private final Hero hero;

    public MapUpdateListener(Hero hero) {
        this.hero = hero;
        this.heroController = new HeroController(hero); // Initial setup
    }

    /**
     * **RECOVERY METHOD**
     * Re-initializes all controller classes to recover from a frozen state
     * caused by an unrecoverable SDK error.
     */
    public void reinitialize() {
        System.out.println("====== RE-INITIALIZING CONTROLLERS TO RECOVER FROM STATE CORRUPTION ======");
        this.heroController = new HeroController(hero);
    }

    /**
     * Called on every game update from the server.
     */
    @Override
    public void call(Object... args) {
        try {
            // Pass a reference to this listener so the controller can call reinitialize()
            heroController.executeTurn(this, args);
        } catch (Exception e) {
            // Catch any unexpected errors during execution.
            System.err.println("FATAL_ERROR in turn execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
}