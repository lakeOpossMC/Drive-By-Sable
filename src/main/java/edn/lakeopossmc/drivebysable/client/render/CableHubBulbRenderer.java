package edn.lakeopossmc.drivebysable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import edn.lakeopossmc.drivebysable.blocks.AbstractDirectionalHubBlock;
import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlock;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

// --- BLINKS THE BULB ON THE INTERMEDIATE AND ADVANCED CABLE HUBS --- //
// * The same effect as Create's Display Link and Stock Link (LinkBulbRenderer)
public class CableHubBulbRenderer extends SafeBlockEntityRenderer<CableHubBlockEntity> {

    public CableHubBulbRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            final CableHubBlockEntity be,
            final float partialTicks,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        float glow = be.getGlow(partialTicks);
        if (glow < .125F) {
            return;
        }

        glow = (float) (1 - (2 * Math.pow(glow - .75F, 2)));
        glow = Mth.clamp(glow, -1, 1);
        final int color = (int) (200 * glow);

        final BlockState state = be.getBlockState();
        if (!state.hasProperty(AbstractDirectionalHubBlock.FACE)) {
            return;
        }
        final AttachFace face = state.getValue(AbstractDirectionalHubBlock.FACE);
        final int facingAngle = switch (state.getValue(AbstractDirectionalHubBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };

        final int xRotation;
        final int yRotation;
        switch (face) {
            case WALL -> {
                xRotation = 90;
                yRotation = facingAngle;
            }
            case CEILING -> {
                xRotation = 180;
                yRotation = (facingAngle + 180) % 360;
            }
            default -> {
                xRotation = 0;
                yRotation = facingAngle;
            }
        }

        final boolean advanced = state.getBlock() instanceof AdvancedCableHubBlock;
        final boolean vertical = face == AttachFace.WALL;
        final PartialModel model = advanced
                ? (vertical ? CableHubPartialModels.ADVANCED_GLOW_VERTICAL : CableHubPartialModels.ADVANCED_GLOW)
                : (vertical ? CableHubPartialModels.INTERMEDIATE_GLOW_VERTICAL : CableHubPartialModels.INTERMEDIATE_GLOW);
        final PartialModel bulb = advanced
                ? (vertical ? CableHubPartialModels.ADVANCED_BULB_VERTICAL : CableHubPartialModels.ADVANCED_BULB)
                : (vertical ? CableHubPartialModels.INTERMEDIATE_BULB_VERTICAL : CableHubPartialModels.INTERMEDIATE_BULB);

        ms.pushPose();

        TransformStack.of(ms)
                .center()
                .rotateYDegrees(-yRotation)
                .rotateXDegrees(-xRotation)
                .uncenter();

        CachedBuffers.partial(bulb, state)
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));

        CachedBuffers.partial(model, state)
                .light(LightTexture.FULL_BRIGHT)
                .color(color, color, color, 255)
                .disableDiffuse()
                .renderInto(ms, buffer.getBuffer(RenderTypes.additive()));

        ms.popPose();
    }
}