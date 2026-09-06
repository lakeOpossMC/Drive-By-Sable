package edn.lakeopossmc.drivebysable.mixinducks;

// --- MARKER FOR LINKED TYPEWRITER HUB --- //
// * Stores the prefix for Computer Craft events
public interface LinkedTypewriterBlockEntityDuck {
    String drivebysable$getComputerEventPrefix();

    void drivebysable$setComputerEventPrefix(final String computerEventPrefix);

    String drivebysable$getComputerEventName(final String eventName);
}
