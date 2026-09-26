package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// --- WHERE A LOAD WANTED A BLOCK AND FOUND NOTHING --- //
// * A full block box in the empty space
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class BackupDriveMissingBlockHighlight {

    private static final String SLOT = "drivebysable:missingBlock";

    private static final int BLINK_A = 0xDF2424;
    private static final int BLINK_B = 0xFF4444;
    private static final int BLINK_PERIOD = 16;
    private static final int BLINK_HALF = 8;

    private static final float LINE_WIDTH = 0.0625F;

    private static final int DISPLAY_TICKS = 120;

    private static final AABB UNIT_CUBE = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    private static final Set<BlockPos> missing = new LinkedHashSet<>();

    @Nullable
    private static ResourceKey<Level> dimension;

    private static int ticksRemaining;

    private BackupDriveMissingBlockHighlight() {
    }

    public static void show(final List<BlockPos> positions) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || positions.isEmpty()) {
            return;
        }

        missing.clear();
        for (final BlockPos pos : positions) {
            missing.add(pos.immutable());
        }

        dimension = minecraft.level.dimension();
        ticksRemaining = DISPLAY_TICKS;
    }

    public static void clear() {
        missing.clear();
        dimension = null;
        ticksRemaining = 0;
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        if (ticksRemaining <= 0 || missing.isEmpty()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            clear();
            return;
        }

        ticksRemaining--;

        final int color = blinkColor();

        // * Always the whole block
        for (final BlockPos pos : missing) {
            Outliner.getInstance()
                    .showAABB(SLOT + pos.asLong(), UNIT_CUBE.move(pos))
                    .colored(color)
                    .lineWidth(LINE_WIDTH)
                    .disableLineNormals();
        }

        if (ticksRemaining <= 0) {
            missing.clear();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        clear();
    }

    private static int blinkColor() {
        return AnimationTickHolder.getTicks() % BLINK_PERIOD < BLINK_HALF ? BLINK_A : BLINK_B;
    }
}