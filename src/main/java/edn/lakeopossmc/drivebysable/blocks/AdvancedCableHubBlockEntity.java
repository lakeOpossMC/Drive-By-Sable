package edn.lakeopossmc.drivebysable.blocks;

import com.getitemfromblock.create_tweaked_controllers.controller.ControllerRedstoneOutput;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import edn.lakeopossmc.drivebysable.compat.keytranslator.ControllerChannelTranslator.Vocabulary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// --- SPECIFIC FOR ADVANCED CABLE HUB --- //
// * Stores controller input for the ComputerCraft peripheral and syncs precision with clients
public class AdvancedCableHubBlockEntity extends CableHubBlockEntity {
    private static final String USE_FULL_PRECISION_KEY = "UseFullPrecision";

    private boolean useFullPrecision = false;
    private final ControllerRedstoneOutput output = new ControllerRedstoneOutput();
    private final Object peripheral;

    public AdvancedCableHubBlockEntity(final BlockPos pos, final BlockState state) {
        this(CableBlockEntities.ADVANCED_CABLE_HUB.get(), pos, state);
    }

    public AdvancedCableHubBlockEntity(
            final BlockEntityType<?> type,
            final BlockPos pos,
            final BlockState state
    ) {
        super(type, pos, state);

        this.output.Clear();
        this.peripheral = ComputerCraftCompat.newAdvancedCableHubPeripheral(this);
    }

    @Override
    protected Vocabulary getVocabulary() {
        return Vocabulary.TWEAKED_CONTROLLER;
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries,
            final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putBoolean(USE_FULL_PRECISION_KEY, this.useFullPrecision);
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries,
            final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            this.useFullPrecision = tag.getBoolean(USE_FULL_PRECISION_KEY);
        }
    }

    public Object getPeripheral() {
        return this.peripheral;
    }

    public void receiveButtons(final List<Boolean> buttonStates) {
        for (int index = 0; index < this.output.buttons.length && index < buttonStates.size(); index++) {
            this.output.buttons[index] = Boolean.TRUE.equals(buttonStates.get(index));
        }
    }

    public boolean getButton(final int index) {
        return this.output.buttons[index];
    }

    public List<Integer> getPressedButtons() {
        final List<Integer> pressedButtons = new ArrayList<>();
        for (int index = 0; index < this.output.buttons.length; index++) {
            if (this.output.buttons[index]) {
                pressedButtons.add(index + 1);
            }
        }
        return pressedButtons;
    }

    public void receiveAxisStates(final int axisStates) {
        this.output.DecodeAxis(axisStates);
    }

    public void receiveFullAxisStates(final float[] fullAxis) {
        for (int index = 0; index < this.output.fullAxis.length && index < fullAxis.length; index++) {
            this.output.fullAxis[index] = fullAxis[index];
        }
    }

    public float getAxis(final int index) {
        if (this.useFullPrecision) {
            return this.output.fullAxis[index];
        }

        final byte value = this.output.axis[index];
        // Value is a 5-bit signed integer, so convert to integer and then normalize to the range [-1, 1]
        return (value & 0x10) != 0 ? -(value & 0x0f) / 15.0F : value / 15.0F;
    }

    public void setFullPrecision(final boolean precision) {
        if (this.useFullPrecision == precision) {
            return;
        }

        this.useFullPrecision = precision;
        this.setChanged();
        this.sendData();
    }

    public boolean shouldUseFullPrecision() {
        return this.useFullPrecision;
    }

    public void clearButton(final int index) {
        if (index >= 0 && index < this.output.buttons.length) {
            this.output.buttons[index] = false;
        }
    }

    public void clearAxis(final int index) {
        final int physicalAxis = index < 8 ? index / 2 : index - 4;
        if (physicalAxis >= 0 && physicalAxis < this.output.axis.length) {
            this.output.axis[physicalAxis] = 0;
            this.output.fullAxis[physicalAxis] = 0.0F;
        }
    }

    public void resetInputState() {
        this.output.DecodeButtons((short) 0);
        this.output.DecodeAxis(0);
        Arrays.fill(this.output.fullAxis, 0.0F);
    }
}
