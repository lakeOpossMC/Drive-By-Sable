package edn.lakeopossmc.drivebysable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.redstone.link.LinkRenderer;
import edn.lakeopossmc.drivebysable.client.LinkedGearboxPowerLinkClient;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlock;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

// --- DRAWS LINKED GEARBOX THINGS --- //
public class LinkedGearboxRenderer extends KineticBlockEntityRenderer<LinkedGearboxBlockEntity> {

    public LinkedGearboxRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(
            final LinkedGearboxBlockEntity be,
            final float partialTicks,
            final PoseStack poseStack,
            final MultiBufferSource buffers,
            final int light,
            final int overlay
    ) {
        LinkRenderer.renderOnBlockEntity(be, partialTicks, poseStack, buffers, light, overlay);
        LinkedGearboxPowerLinkClient.renderItems(be, poseStack, buffers, light, overlay);

        if (VisualizationManager.supportsVisualization(be.getLevel())) return;

        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(LinkedGearboxBlock.FACING);
        final Direction.Axis axis = facing.getAxis();

        final SuperByteBuffer cog = CachedBuffers.partialFacingVertical(LinkedGearboxPartialModels.COG, state, facing);
        kineticRotationTransform(cog, be, axis, getAngleForBe(be, be.getBlockPos(), axis), light)
                .renderInto(poseStack, buffers.getBuffer(RenderType.solid()));
    }
}