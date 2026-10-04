package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

// --- OUTPUT MODE WIDGET --- //
public class LinkedGearboxModeBehaviour extends ScrollOptionBehaviour<LinkedGearboxOutputMode> {

    public LinkedGearboxModeBehaviour(final SmartBlockEntity blockEntity) {
        super(LinkedGearboxOutputMode.class,
                Component.translatable("drivebysable.linked_gearbox.output_mode"),
                blockEntity,
                new CapSlot());
    }

    public LinkedGearboxOutputMode getMode() {
        return get();
    }

    //#region // --- SLOT --- //
    private static final class CapSlot extends CenteredSideValueBoxTransform {

        private static final float SCALE = 0.5F;

        private static final double DEPTH = 16.6;

        CapSlot() {
            super((state, side) -> state.getBlock() instanceof LinkedGearboxBlock
                    && state.getValue(LinkedGearboxBlock.FACING) == side);
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, DEPTH);
        }

        @Override
        public float getScale() {
            return SCALE;
        }
    }
    //#endregion
}