package edn.lakeopossmc.drivebysable.compat.computercraft;

import com.simibubi.create.compat.Mods;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import javax.annotation.Nullable;

// --- THE ONLY COMPUTER CRAFT ENTRY POINT --- //
public final class ComputerCraftCompat {
    private ComputerCraftCompat() {
    }

    public static boolean isLoaded() {
        return Mods.COMPUTERCRAFT.isLoaded();
    }

    public static void register(final IEventBus modBus) {
        if (!isLoaded()) {
            return;
        }

        modBus.addListener((final RegisterCapabilitiesEvent event) -> ComputerCraftBridge.registerPeripherals(event));
    }

    // * Null when Computer Craft is absent
    @Nullable
    public static Object newComputerHandler() {
        return isLoaded() ? ComputerCraftBridge.newComputerHandler() : null;
    }

    public static void queueTypewriterKey(
            final CableTypewriterHubBlockEntity hub,
            final String eventName,
            final int key,
            final Boolean repeated
    ) {
        // * The handler itself is null checked in the bridge
        if (!isLoaded()) {
            return;
        }

        ComputerCraftBridge.queueTypewriterKey(hub, eventName, key, repeated);
    }

    public static void handleCableHubKeyPress(
            final Level level,
            final BlockPos blockPos,
            final int button,
            final boolean pressed,
            final boolean wasPressed
    ) {
        if (!isLoaded()) {
            return;
        }

        if (!(level.getBlockEntity(blockPos) instanceof final CableHubBlockEntity cableHub)) {
            return;
        }

        if (cableHub.getComputerHandler() == null) {
            return;
        }

        ComputerCraftBridge.queueKeyPress(cableHub, button, pressed, wasPressed);
    }
}