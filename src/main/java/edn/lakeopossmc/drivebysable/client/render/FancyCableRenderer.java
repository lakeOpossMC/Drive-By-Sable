package edn.lakeopossmc.drivebysable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.BackupDriveCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.LinkedHashMap;
import java.util.Map;

// --- CONNECTIONS DRAWN AS HANGING CABLES --- //
// * The config option fancyCables swaps the straight connection lines for these
// * No model, each cable is a square tube built on a curve
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class FancyCableRenderer {

    private static final ResourceLocation TEXTURE = DriveBySableMod.asResource("textures/misc/fancy_cable.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

    //#region // --- SHAPE --- //
    private static final double RADIUS = 1.5D / 16.0D;
    private static final double BAND_RADIUS = 0.5D / 16.0D;
    private static final double COLLAR_RADIUS = 2.0D / 16.0D;
    private static final double COLLAR_LENGTH = 3.0D / 16.0D;

    private static final double BAND_LIFT = 0.004D;

    private static final double SAG_PER_BLOCK = 0.08D;
    private static final double MIN_SAG = 0.03D;
    private static final double MAX_SAG = 1.25D;

    private static final double SEGMENTS_PER_BLOCK = 2.5D;
    private static final int MIN_SEGMENTS = 6;
    private static final int MAX_SEGMENTS = 32;
    //#endregion

    //#region // --- TEXTURE LAYOUT (16 x 16, one block of cable is 16 wide) --- //
    private static final float SHEATH_V0 = 0.0F;
    private static final float SHEATH_V1 = 3.0F / 16.0F;
    private static final float SHEATH_ALT_V0 = 3.0F / 16.0F;
    private static final float SHEATH_ALT_V1 = 6.0F / 16.0F;
    private static final float COLLAR_V0 = 6.0F / 16.0F;
    private static final float COLLAR_V1 = 10.0F / 16.0F;
    private static final float BAND_V0 = 10.0F / 16.0F;
    private static final float BAND_V1 = 11.0F / 16.0F;
    private static final float TEXEL = 1.0F / 16.0F;
    //#endregion

    // * A cable stays up this many ticks after it was last asked for
    private static final int LIFETIME = 2;
    private static final int WHITE = 0xFFFFFF;

    private static final Map<Object, Shown> SHOWN = new LinkedHashMap<>();
    private static int ticks;

    private FancyCableRenderer() {
    }

    public static boolean enabled() {
        return CableConfig.CONFIG.fancyCables.get();
    }

    //#region // --- CABLES THAT ARE ASKED FOR EVERY TICK --- //
    // * Each end is given in its own block's space
    public static void show(final Object key, final BlockPos fromBlock, final Vec3 from,
                            final BlockPos toBlock, final Vec3 to, final int color) {
        SHOWN.put(key, new Shown(fromBlock.immutable(), from, toBlock.immutable(), to, color, ticks));
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        ticks++;
        if (SHOWN.isEmpty()) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            SHOWN.clear();
            return;
        }
        SHOWN.values().removeIf(shown -> ticks - shown.lastAsked() > LIFETIME);
    }

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (SHOWN.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        if (level == null) {
            return;
        }

        final Vec3 camera = event.getCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer buffer = buffer(buffers);
        for (final Shown shown : SHOWN.values()) {
            draw(event.getPoseStack(), buffer, camera,
                    inWorld(level, shown.fromBlock(), shown.from()),
                    inWorld(level, shown.toBlock(), shown.to()),
                    shown.color());
        }
        finish(buffers);
    }

    // * Where a point beside this block appears in the world
    private static Vec3 inWorld(final Level level, final BlockPos block, final Vec3 point) {
        final SubLevel subLevel = BackupDriveCapture.subLevelOf(level, block);
        if (subLevel == null) {
            return point;
        }
        return subLevel instanceof final ClientSubLevel client
                ? client.renderPose().transformPosition(point)
                : subLevel.logicalPose().transformPosition(point);
    }
    //#endregion

    //#region // --- DRAWING ONE CABLE --- //
    public static VertexConsumer buffer(final MultiBufferSource buffers) {
        return buffers.getBuffer(RENDER_TYPE);
    }

    public static void finish(final MultiBufferSource.BufferSource buffers) {
        buffers.endBatch(RENDER_TYPE);
    }

    // * Both ends where they appear in the world
    public static void draw(final PoseStack poseStack, final VertexConsumer buffer, final Vec3 camera,
                            final Vec3 from, final Vec3 to, final int color) {
        final Vec3 span = to.subtract(from);
        final double length = span.length();
        if (length < 1.0E-3D) {
            return;
        }

        final int segments = Mth.clamp((int) Math.ceil(length * SEGMENTS_PER_BLOCK), MIN_SEGMENTS, MAX_SEGMENTS);
        final double sag = Mth.clamp(length * SAG_PER_BLOCK, MIN_SAG, MAX_SAG);

        // * Points along the curve, relative to the camera
        final Vec3[] points = new Vec3[segments + 1];
        final Vec3 start = from.subtract(camera);
        for (int i = 0; i <= segments; i++) {
            final double t = i / (double) segments;
            points[i] = start.add(span.scale(t)).add(0.0D, -sag * 4.0D * t * (1.0D - t), 0.0D);
        }

        Vec3 side = span.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();

        final Vec3[] ups = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            final Vec3 along = points[Math.min(i + 1, segments)].subtract(points[Math.max(i - 1, 0)]).normalize();
            ups[i] = side.cross(along).normalize();
        }

        final PoseStack.Pose pose = poseStack.last();
        double travelled = 0.0D;
        for (int i = 0; i < segments; i++) {
            final double step = points[i + 1].subtract(points[i]).length();
            final float u0 = (float) travelled;
            final float u1 = (float) (travelled + step);
            travelled += step;

            tube(pose, buffer, points[i], points[i + 1], side, ups[i], ups[i + 1],
                    RADIUS, RADIUS, 0.0D, u0, u1, SHEATH_V0, SHEATH_V1, SHEATH_ALT_V0, SHEATH_ALT_V1, WHITE);
            tube(pose, buffer, points[i], points[i + 1], side, ups[i], ups[i + 1],
                    BAND_RADIUS, RADIUS, BAND_LIFT, u0, u1, BAND_V0, BAND_V1, BAND_V0, BAND_V1, color);
        }

        collar(pose, buffer, points[0], points[1], side, ups[0], color);
        collar(pose, buffer, points[segments], points[segments - 1], side, ups[segments], color);
    }

    private static void collar(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 end,
                               final Vec3 next, final Vec3 side, final Vec3 up, final int color) {
        final Vec3 inward = next.subtract(end);
        final double reach = Math.min(COLLAR_LENGTH, inward.length());
        if (reach < 1.0E-4D) {
            return;
        }
        final Vec3 along = inward.normalize();
        final Vec3 stop = end.add(along.scale(reach));
        tube(pose, buffer, end, stop, side, up, up, COLLAR_RADIUS, COLLAR_RADIUS, 0.0D,
                0.0F, (float) reach, COLLAR_V0, COLLAR_V1, COLLAR_V0, COLLAR_V1, color);

        cap(pose, buffer, end, side, up, along.scale(-1.0D), color);
        cap(pose, buffer, stop, side, up, along, color);
    }

    private static void cap(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 centre,
                            final Vec3 side, final Vec3 up, final Vec3 facing, final int color) {
        final Vec3 across = side.scale(COLLAR_RADIUS);
        final Vec3 rise = up.scale(COLLAR_RADIUS);
        vertex(pose, buffer, centre.subtract(across).subtract(rise), 0.0F, COLLAR_V0, facing, color);
        vertex(pose, buffer, centre.add(across).subtract(rise), TEXEL, COLLAR_V0, facing, color);
        vertex(pose, buffer, centre.add(across).add(rise), TEXEL, COLLAR_V1, facing, color);
        vertex(pose, buffer, centre.subtract(across).add(rise), 0.0F, COLLAR_V1, facing, color);
    }

    // * Four strips around one stretch of cable
    private static void tube(final PoseStack.Pose pose, final VertexConsumer buffer,
                             final Vec3 a, final Vec3 b, final Vec3 side, final Vec3 upA, final Vec3 upB,
                             final double halfWidth, final double distance, final double lift,
                             final float u0, final float u1,
                             final float v0, final float v1, final float sideV0, final float sideV1,
                             final int color) {
        final double out = distance + lift;
        strip(pose, buffer, a, b, side, side, upA, upB, halfWidth, out, u0, u1, v0, v1, color);
        strip(pose, buffer, a, b, side, side, upA.scale(-1.0D), upB.scale(-1.0D), halfWidth, out, u0, u1, v0, v1, color);
        strip(pose, buffer, a, b, upA, upB, side, side, halfWidth, out, u0, u1, sideV0, sideV1, color);
        strip(pose, buffer, a, b, upA, upB, side.scale(-1.0D), side.scale(-1.0D), halfWidth, out, u0, u1, sideV0, sideV1, color);
    }

    private static void strip(final PoseStack.Pose pose, final VertexConsumer buffer,
                              final Vec3 a, final Vec3 b,
                              final Vec3 acrossA, final Vec3 acrossB, final Vec3 normalA, final Vec3 normalB,
                              final double halfWidth, final double out,
                              final float u0, final float u1, final float v0, final float v1, final int color) {
        final Vec3 centreA = a.add(normalA.scale(out));
        final Vec3 centreB = b.add(normalB.scale(out));
        vertex(pose, buffer, centreA.subtract(acrossA.scale(halfWidth)), u0, v0, normalA, color);
        vertex(pose, buffer, centreA.add(acrossA.scale(halfWidth)), u0, v1, normalA, color);
        vertex(pose, buffer, centreB.add(acrossB.scale(halfWidth)), u1, v1, normalB, color);
        vertex(pose, buffer, centreB.subtract(acrossB.scale(halfWidth)), u1, v0, normalB, color);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 point,
                               final float u, final float v, final Vec3 normal, final int color) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
    //#endregion

    private record Shown(BlockPos fromBlock, Vec3 from, BlockPos toBlock, Vec3 to, int color, int lastAsked) {
    }
}