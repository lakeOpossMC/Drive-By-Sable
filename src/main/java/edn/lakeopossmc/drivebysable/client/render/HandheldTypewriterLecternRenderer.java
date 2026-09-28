package edn.lakeopossmc.drivebysable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import edn.lakeopossmc.drivebysable.blocks.HandheldTypewriterLecternBlock;
import edn.lakeopossmc.drivebysable.blocks.HandheldTypewriterLecternBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// --- DRAWS THE CONTROLLER ON TOP OF THE LECTERN --- //
public class HandheldTypewriterLecternRenderer implements BlockEntityRenderer<HandheldTypewriterLecternBlockEntity> {

    public HandheldTypewriterLecternRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final HandheldTypewriterLecternBlockEntity lectern, final float partialTicks, final PoseStack ms,
                       final MultiBufferSource buffer, final int light, final int overlay) {
        final ItemStack controller = lectern.getController();
        if (controller.isEmpty()) {
            return;
        }

        final PartialItemModelRenderer renderer =
                PartialItemModelRenderer.of(controller, ItemDisplayContext.NONE, ms, buffer, overlay);
        final boolean active = lectern.hasUser();
        final boolean renderDepression = lectern.isUsedBy(Minecraft.getInstance().player);
        final Direction facing = lectern.getBlockState().getValue(HandheldTypewriterLecternBlock.FACING);

        ms.pushPose();
        ms.translate(0.5, 1.45, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(horizontalAngle(facing) - 90));
        ms.translate(0.28, 0, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(-22.0F));
        HandheldTypewriterItemRenderer.renderInLectern(renderer, ms, light, active, renderDepression);
        ms.popPose();
    }

    // * Create's AngleHelper.horizontalAngle
    private static float horizontalAngle(final Direction facing) {
        final float angle = facing.toYRot();
        return facing.getAxis() == Direction.Axis.X ? -angle : angle;
    }
}