package edn.lakeopossmc.drivebysable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterRenderer;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

// --- THE TYPEWRITER HUB PLUS ITS BULB --- //
public class CableTypewriterHubRenderer extends LinkedTypewriterRenderer {

    public CableTypewriterHubRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(
            final LinkedTypewriterBlockEntity be,
            final float partialTicks,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        // * The typewriter's renderer leaves its own moves on the stack
        ms.pushPose();
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        ms.popPose();

        if (!(be instanceof final CableTypewriterHubBlockEntity hub)) {
            return;
        }

        float glow = hub.getGlow(partialTicks);
        if (glow < .125F) {
            return;
        }

        glow = (float) (1 - (2 * Math.pow(glow - .75F, 2)));
        glow = Mth.clamp(glow, -1, 1);
        final int color = (int) (200 * glow);

        final BlockState state = be.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        final int yRotation = switch (state.getValue(HorizontalDirectionalBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };

        ms.pushPose();

        TransformStack.of(ms)
                .center()
                .rotateYDegrees(-yRotation)
                .uncenter();

        CachedBuffers.partial(CableHubPartialModels.TYPEWRITER_BULB, state)
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));

        CachedBuffers.partial(CableHubPartialModels.TYPEWRITER_GLOW, state)
                .light(LightTexture.FULL_BRIGHT)
                .color(color, color, color, 255)
                .disableDiffuse()
                .renderInto(ms, buffer.getBuffer(RenderTypes.additive()));

        ms.popPose();
    }
}