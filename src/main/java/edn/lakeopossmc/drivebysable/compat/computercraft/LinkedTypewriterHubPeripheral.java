package edn.lakeopossmc.drivebysable.compat.computercraft;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dev.simulated_team.simulated.compat.computercraft.peripherals.LinkedTypewriterPeripheral;
import edn.lakeopossmc.drivebysable.mixinducks.LinkedTypewriterBlockEntityDuck;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterPromiscuousFilterMode;

public class LinkedTypewriterHubPeripheral extends LinkedTypewriterPeripheral {
    public LinkedTypewriterHubPeripheral(final CableTypewriterHubBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public String getType() {
        return "linked_typewriter_hub";
    }

    @LuaFunction
    public final String getEventPrefix() {
        return ((LinkedTypewriterBlockEntityDuck) this.blockEntity).drivebysable$getComputerEventPrefix();
    }

    @LuaFunction
    public final void setEventPrefix(final String eventPrefix) {
        ((LinkedTypewriterBlockEntityDuck) this.blockEntity).drivebysable$setComputerEventPrefix(eventPrefix);
    }

    @LuaFunction
    public final boolean isInPromiscuousMode() {
        return ((CableTypewriterHubBlockEntity) this.blockEntity).isInPromiscuousMode();
    }

    @LuaFunction
    public final void setPromiscuousMode(final boolean promiscuousMode) {
        ((CableTypewriterHubBlockEntity) this.blockEntity).setPromiscuousMode(promiscuousMode);
    }

    @LuaFunction
    public final String getPromiscuousFilterMode() {
        return ((CableTypewriterHubBlockEntity) this.blockEntity).getPromiscuousFilterMode().getName();
    }

    @LuaFunction
    public final void setPromiscuousFilterMode(final String promiscuousFilterMode) throws LuaException {
        CableTypewriterPromiscuousFilterMode mode = CableTypewriterPromiscuousFilterMode
                .fromName(promiscuousFilterMode);
        if (mode != null) {
            ((CableTypewriterHubBlockEntity) this.blockEntity)
                    .setPromiscuousFilterMode(mode);
        } else {
            throw new LuaException("Invalid promiscuous filter mode: " + promiscuousFilterMode);
        }
    }

    @LuaFunction
    public final Set<Integer> getPromiscuousFilterKeys() {
        return ((CableTypewriterHubBlockEntity) this.blockEntity).getPromiscuousFilterKeys();
    }

    @LuaFunction
    public final void setPromiscuousFilterKeys(final Map<?, ?> promiscuousFilterKeys) {
        Set<Integer> keys = promiscuousFilterKeys.values().stream()
                .filter(key -> key instanceof Double)
                .map(key -> ((Double) key).intValue())
                .collect(Collectors.toSet());
        ((CableTypewriterHubBlockEntity) this.blockEntity).setPromiscuousFilterKeys(keys);
    }
}
