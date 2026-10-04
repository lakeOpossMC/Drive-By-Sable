package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// --- FREQUENCY SLOTS --- //
public class LinkedGearboxFrequencySlot extends ValueBoxTransform.Dual {

    private static final double DEPTH = 14 - 0.5 + 0.01;
    private static final double BACK_DEPTH = 2 + 0.5 - 0.01;
    private static final double FIRST_X = 5.5;
    private static final double SECOND_X = 10.5;
    private static final double CENTRE_Y = 13;

    private static final Vec3 NORMAL = new Vec3(0, 0, 1);
    private static final Vec3 BACK_NORMAL = new Vec3(0, 0, -1);

    private final boolean back;

    public LinkedGearboxFrequencySlot(final boolean first) {
        this(first, false);
    }

    public LinkedGearboxFrequencySlot(final boolean first, final boolean back) {
        super(first);
        this.back = back;
    }

    public static LinkedGearboxFrequencySlot back(final boolean first) {
        return new LinkedGearboxFrequencySlot(first, true);
    }

    @Override
    public Vec3 getLocalOffset(final LevelAccessor level, final BlockPos pos, final BlockState state) {
        if (!(state.getBlock() instanceof LinkedGearboxBlock)) {
            return null;
        }
        final boolean lowX = back != isFirst();
        final Vec3 model = new Vec3((lowX ? FIRST_X : SECOND_X) / 16.0, CENTRE_Y / 16.0,
                (back ? BACK_DEPTH : DEPTH) / 16.0);
        return LinkedGearboxBlock.rotateModelPoint(state, model);
    }

    @Override
    public void rotate(final LevelAccessor level, final BlockPos pos, final BlockState state, final PoseStack ms) {
        if (!(state.getBlock() instanceof LinkedGearboxBlock)) {
            return;
        }
        final Vec3 normal = LinkedGearboxBlock.rotateModelVector(state, back ? BACK_NORMAL : NORMAL);
        final Direction side = Direction.getNearest(normal.x, normal.y, normal.z);
        final float yRot = side.getAxis().isVertical() ? 0 : AngleHelper.horizontalAngle(side) + 180;
        final float xRot = side == Direction.UP ? 90 : side == Direction.DOWN ? 270 : 0;
        TransformStack.of(ms)
                .rotateYDegrees(yRot)
                .rotateXDegrees(xRot);
    }

    @Override
    public float getScale() {
        return .4975F;
    }
}