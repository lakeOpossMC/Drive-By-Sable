package edn.lakeopossmc.drivebysable.compat.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlockEntity;

import java.util.List;

import org.jspecify.annotations.Nullable;

// --- COMPUTER CRAFT PERIPHERAL FOR THE ADVANCED CABLE HUB --- //
public final class AdvancedCableHubPeripheral implements IPeripheral {
    private final AdvancedCableHubBlockEntity blockEntity;

    public AdvancedCableHubPeripheral(final AdvancedCableHubBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "advanced_cable_hub";
    }

    @Override
    public boolean equals(@Nullable final IPeripheral other) {
        return other == this;
    }

    @LuaFunction
    public final boolean getButton(final int index) throws LuaException {
        if (index < 1 || index > 15) {
            throw new LuaException("Index out of range: [1,15]");
        }

        return this.blockEntity.getButton(index - 1);
    }

    @LuaFunction
    public final List<Integer> getPressedButtons() {
        return this.blockEntity.getPressedButtons();
    }

    @LuaFunction
    public final float getAxis(final int index) throws LuaException {
        if (index < 1 || index > 6) {
            throw new LuaException("Index out of range: [1,6]");
        }

        return this.blockEntity.getAxis(index - 1);
    }

    @LuaFunction
    public final void setFullPrecision(final boolean precision) {
        this.blockEntity.setFullPrecision(precision);
    }

    @LuaFunction
    public final boolean isFullPrecision() {
        return this.blockEntity.shouldUseFullPrecision();
    }
}
