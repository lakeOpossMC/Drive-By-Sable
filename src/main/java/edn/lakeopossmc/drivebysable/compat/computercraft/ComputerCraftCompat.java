package edn.lakeopossmc.drivebysable.compat.computercraft;

import com.simibubi.create.compat.Mods;
import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.MultiChannelCableBusBlockEntity;
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

    public static void queueSensorBusInput(
            final IntegratedSensorBusBlockEntity sensor,
            final int channel,
            final int signal,
            final boolean intercepted
    ) {
        if (!isLoaded()) {
            return;
        }

        ComputerCraftBridge.queueSensorBusInput(sensor, channel, signal, intercepted);
    }

    // * Tells any attached computer that a signal arrived on a sink channel
    public static void queueCableBusInput(
            final MultiChannelCableBusBlockEntity bus,
            final int channel,
            final int signal,
            final boolean intercepted
    ) {
        if (!isLoaded()) {
            return;
        }

        ComputerCraftBridge.queueCableBusInput(bus, channel, signal, intercepted);
    }

    //#region // --- LINKED GEARBOX ("TRANSCEIVER") --- //
    // * Events a transceiver raises on attached computers, see LinkedGearboxPeripheral
    public static final String TRANSCEIVER_ROLE_EVENT = "transceiver_role";
    public static final String TRANSCEIVER_NETWORK_EVENT = "transceiver_network";
    public static final String TRANSCEIVER_OVERSTRESSED_EVENT = "transceiver_overstressed";

    // * Null when Computer Craft is absent
    @Nullable
    public static Object newTransceiverPeripheral(final LinkedGearboxBlockEntity owner) {
        return isLoaded() ? ComputerCraftBridge.newTransceiverPeripheral(owner) : null;
    }

    public static boolean hasTransceiverComputers(final LinkedGearboxBlockEntity transceiver) {
        return isLoaded() && ComputerCraftBridge.hasTransceiverComputers(transceiver);
    }

    public static void queueTransceiverEvent(
            final LinkedGearboxBlockEntity transceiver,
            final String event,
            final Object... arguments
    ) {
        if (!isLoaded()) {
            return;
        }

        ComputerCraftBridge.queueTransceiverEvent(transceiver, event, arguments);
    }
    //#endregion

    // * Null when Computer Craft is absent, so the Cable Bus simply has no peripheral
    @Nullable
    public static Object newCableBusPeripheral(final MultiChannelCableBusBlockEntity owner) {
        return isLoaded() ? ComputerCraftBridge.newCableBusPeripheral(owner) : null;
    }

    @Nullable
    public static Object newSensorBusPeripheral(final IntegratedSensorBusBlockEntity owner) {
        return isLoaded() ? ComputerCraftBridge.newSensorBusPeripheral(owner) : null;
    }

    // * Null when Computer Craft is absent
    @Nullable
    public static Object newComputerHandler() {
        return isLoaded() ? ComputerCraftBridge.newComputerHandler() : null;
    }

    // * Null when Computer Craft is absent
    @Nullable
    public static Object newAdvancedCableHubPeripheral(final AdvancedCableHubBlockEntity owner) {
        return isLoaded() ? ComputerCraftBridge.newAdvancedCableHubPeripheral(owner) : null;
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

    // * Same as above but keyed by channel name, used by hubs whose button indices mean different things per device
    public static void handleChannelHubPress(
            final Level level,
            final BlockPos blockPos,
            final String channel,
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

        ComputerCraftBridge.queueChannelPress(cableHub, channel, pressed, wasPressed);
    }
}