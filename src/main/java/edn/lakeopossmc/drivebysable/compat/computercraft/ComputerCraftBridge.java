package edn.lakeopossmc.drivebysable.compat.computercraft;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.simulated_team.simulated.compat.computercraft.AttachedComputerHandler;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.mixinducks.LinkedTypewriterBlockEntityDuck;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

// --- EVERY COMPUTER CRAFT TYPE LIVES BEHIND THIS CLASS --- //
// * Nothing here is touched unless ComputerCraftCompat.isLoaded() said yes
final class ComputerCraftBridge {
    private ComputerCraftBridge() {
    }

    static void registerPeripherals(final RegisterCapabilitiesEvent event) {
        final var peripheralCapability = PeripheralCapability.get();
        if (peripheralCapability == null) {
            return;
        }

        final var cableTypewriterHubHolder = CableBlockEntities.CABLE_TYPEWRITER_HUB;
        if (cableTypewriterHubHolder != null) {
            final var cableTypewriterHub = cableTypewriterHubHolder.get();
            if (cableTypewriterHub != null) {
                event.registerBlockEntity(peripheralCapability, cableTypewriterHub,
                        (b, d) -> new LinkedTypewriterHubPeripheral(b));
            }
        }

        final var cableHubHolder = CableBlockEntities.CABLE_HUB;
        if (cableHubHolder != null) {
            final var cableHub = cableHubHolder.get();
            if (cableHub != null) {
                event.registerBlockEntity(peripheralCapability, cableHub,
                        (b, d) -> new CableHubPeripheral(b));
            }
        }
    }

    // * Built here so the block entity never has to name the type
    static Object newComputerHandler() {
        return new AttachedComputerHandler();
    }

    static void queueTypewriterKey(
            final CableTypewriterHubBlockEntity hub,
            final String eventName,
            final int key,
            final Boolean repeated
    ) {
        // * Inherited from simulated's LinkedTypewriterBlockEntity
        final AttachedComputerHandler handler = hub.computerHandler;
        if (handler == null) {
            return;
        }

        final LinkedTypewriterBlockEntityDuck typewriter = (LinkedTypewriterBlockEntityDuck) hub;
        final String event = typewriter.drivebysable$getComputerEventName(eventName);

        if (repeated == null) {
            handler.queueEvent(event, key);
        } else {
            handler.queueEvent(event, key, repeated);
        }
    }

    static void queueKeyPress(
            final CableHubBlockEntity cableHub,
            final int button,
            final boolean pressed,
            final boolean wasPressed
    ) {
        if (!(cableHub.getComputerHandler() instanceof final AttachedComputerHandler handler)) {
            return;
        }

        if (pressed) {
            handler.queueEvent(cableHub.getComputerEventName("button"), button, wasPressed);
        } else {
            handler.queueEvent(cableHub.getComputerEventName("button_up"), button);
        }
    }
}