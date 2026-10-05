package edn.lakeopossmc.drivebysable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.BackupDriveCapture;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxLinks;
import edn.lakeopossmc.drivebysable.client.render.FancyCableRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Set;

// --- NETWORK OF THE SELECTED LINKED GEARBOX --- //
// * Every gearbox in the network (cable or frequency) blinks
// * Refreshed every tick while a gearbox is selected
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class LinkedGearboxGroupHighlight {

    private static final String SLOT = "drivebysable:gearboxGroup";

    private static final int BLINK_A = 0x708DAD;
    private static final int BLINK_B = 0x90ADCD;

    private static final int FANCY_BLINK_A = 0x395CA1;
    private static final int FANCY_BLINK_B = 0x5371C6;
    private static final int BLINK_PERIOD = 16;
    private static final int BLINK_HALF = 8;
    private static final float LINE_WIDTH = 1 / 32.0F;
    private static final double LINK_THICKNESS = LINE_WIDTH;

    private static final AABB UNIT_CUBE = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    private static Set<BlockPos> members = Set.of();
    private static List<BlockPos[]> links = List.of();

    private LinkedGearboxGroupHighlight() {
    }

    public static void show(final Level level, final LinkedGearboxLinks.LinkGroup group) {
        members = group.members();
        links = group.links();

        final int color = blinkColor();
        for (final BlockPos pos : members) {
            Outliner.getInstance()
                    .showAABB(SLOT + pos.asLong(), blockBounds(level, pos))
                    .colored(color)
                    .lineWidth(LINE_WIDTH)
                    .disableLineNormals();
        }
    }

    public static void hide() {
        members = Set.of();
        links = List.of();
    }

    //#region // --- LINES BETWEEN LINKED GEARBOXES --- //
    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (links.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        // * The fancyCables option hangs a cable along each link instead
        if (FancyCableRenderer.enabled()) {
            final int color = blink(FANCY_BLINK_A, FANCY_BLINK_B);
            final VertexConsumer cables = FancyCableRenderer.buffer(buffers);
            for (final BlockPos[] link : links) {
                FancyCableRenderer.draw(poseStack, cables, camera,
                        worldCentreOf(minecraft.level, link[0]),
                        worldCentreOf(minecraft.level, link[1]),
                        color);
            }
            FancyCableRenderer.finish(buffers);
            return;
        }

        final int color = blinkColor();
        final VertexConsumer buffer = buffers.getBuffer(RenderType.debugQuads());

        for (final BlockPos[] link : links) {
            drawLink(poseStack, buffer, camera,
                    worldCentreOf(minecraft.level, link[0]),
                    worldCentreOf(minecraft.level, link[1]),
                    color);
        }

        buffers.endBatch(RenderType.debugQuads());
    }

    private static void drawLink(final PoseStack poseStack, final VertexConsumer buffer, final Vec3 camera,
                                 final Vec3 from, final Vec3 to, final int color) {
        final Vec3 delta = to.subtract(from);
        final double length = delta.length();
        if (length < 1.0E-4D) {
            return;
        }

        final double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        final float yaw = (float) Mth.atan2(delta.x, delta.z);
        final float pitch = (float) -Mth.atan2(delta.y, horizontal);

        poseStack.pushPose();
        poseStack.translate(from.x - camera.x, from.y - camera.y, from.z - camera.z);
        poseStack.mulPose(Axis.YP.rotation(yaw));
        poseStack.mulPose(Axis.XP.rotation(pitch));
        ClientCableNetworkHandler.renderLocalBox(
                new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, length), poseStack, buffer, color, LINK_THICKNESS);
        poseStack.popPose();
    }

    private static Vec3 worldCentreOf(final Level level, final BlockPos pos) {
        final Vec3 centre = blockBounds(level, pos).getCenter();
        final SubLevel subLevel = BackupDriveCapture.subLevelOf(level, pos);
        return subLevel == null ? centre : subLevel.logicalPose().transformPosition(centre);
    }
    //#endregion

    private static int blinkColor() {
        return blink(BLINK_A, BLINK_B);
    }

    private static int blink(final int first, final int second) {
        return AnimationTickHolder.getTicks() % BLINK_PERIOD < BLINK_HALF ? first : second;
    }

    private static AABB blockBounds(final Level level, final BlockPos pos) {
        final VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        return (shape.isEmpty() ? UNIT_CUBE : shape.bounds()).move(pos);
    }
}