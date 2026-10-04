package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.gui.AllIcons;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

// --- ICON DRAWN FROM TEXTURE --- //
public class LinkedGearboxModeIcon extends AllIcons {

    private static final ResourceLocation ATLAS = DriveBySableMod.asResource("textures/gui/linked_gearbox_modes.png");
    private static final int ATLAS_WIDTH = 64;
    private static final int ATLAS_HEIGHT = 16;
    private static final int SIZE = 16;

    private final int u;

    public LinkedGearboxModeIcon(final int index) {
        super(0, 0);
        this.u = index * SIZE;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void bind() {
        RenderSystem.setShaderTexture(0, ATLAS);
    }

    // * Selection board cursor
    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(final GuiGraphics graphics, final int x, final int y) {
        graphics.blit(ATLAS, x, y, 0, u, 0, SIZE, SIZE, ATLAS_WIDTH, ATLAS_HEIGHT);
    }

    // * In-world value box
    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(final PoseStack ms, final MultiBufferSource buffer, final int color) {
        final VertexConsumer builder = buffer.getBuffer(RenderType.text(ATLAS));
        final Matrix4f matrix = ms.last().pose();
        final Color rgb = new Color(color);
        final int light = LightTexture.FULL_BRIGHT;

        final float u1 = (float) u / ATLAS_WIDTH;
        final float u2 = (float) (u + SIZE) / ATLAS_WIDTH;

        vertex(builder, matrix, 0, 0, rgb, u1, 0, light);
        vertex(builder, matrix, 0, 1, rgb, u1, 1, light);
        vertex(builder, matrix, 1, 1, rgb, u2, 1, light);
        vertex(builder, matrix, 1, 0, rgb, u2, 0, light);
    }

    @OnlyIn(Dist.CLIENT)
    private static void vertex(final VertexConsumer builder, final Matrix4f matrix, final float x, final float y,
                               final Color rgb, final float u, final float v, final int light) {
        builder.addVertex(matrix, x, y, 0)
                .setColor(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255)
                .setUv(u, v)
                .setLight(light);
    }
}