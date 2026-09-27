package edn.lakeopossmc.drivebysable.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.simulated_team.simulated.compat.computercraft.AttachedComputerHandler;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.IntermediateCableHubBlockEntity;
import edn.lakeopossmc.drivebysable.compat.GetCreativeCableServerHandler;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// --- COMPUTER CRAFT PERIPHERAL FOR THE INTERMEDIATE HUB --- //
// * Buttons are addressed by channel name, eg "macroUp" or "keypad3"
// * Events: button(channel, repeated) and button_up(channel)
public class IntermediateCableHubPeripheral implements IPeripheral {
    private final CableHubBlockEntity blockEntity;

    public IntermediateCableHubPeripheral(final CableHubBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "intermediate_cable_hub";
    }

    @Override
    public void attach(final IComputerAccess computer) {
        IPeripheral.super.attach(computer);
        if (this.blockEntity.getComputerHandler() instanceof final AttachedComputerHandler handler) {
            handler.attach(computer);
        }
    }

    @Override
    public void detach(final IComputerAccess computer) {
        IPeripheral.super.detach(computer);
        if (this.blockEntity.getComputerHandler() instanceof final AttachedComputerHandler handler) {
            handler.detach(computer);
        }
    }

    @Override
    public boolean equals(@Nullable final IPeripheral other) {
        return other == this;
    }

    private Set<String> pressed() {
        return GetCreativeCableServerHandler.getPressed(this.blockEntity.getLevel(), this.blockEntity.getBlockPos());
    }

    @LuaFunction
    public final boolean getButton(final String channel) {
        return this.pressed().contains(channel);
    }

    @LuaFunction
    public final List<String> getPressedButtons() {
        return new ArrayList<>(this.pressed());
    }

    // * Only the groups opened by binding a device
    @LuaFunction
    public final List<String> getChannels() {
        return this.blockEntity instanceof final IntermediateCableHubBlockEntity hub ? hub.getOpenChannels() : List.of();
    }

    @LuaFunction
    public final String getEventPrefix() {
        return this.blockEntity.getComputerEventPrefix();
    }

    @LuaFunction
    public final void setEventPrefix(final String eventPrefix) {
        this.blockEntity.setComputerEventPrefix(eventPrefix);
    }
}
