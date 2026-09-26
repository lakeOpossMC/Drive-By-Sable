package edn.lakeopossmc.drivebysable.compat.toolgun;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

// --- A PORTABLE STRUCTURE CONTAINER PICKING A STRUCTURE UP --- //
// * A container moves a structure rather than copying it
// * Connections must travel with the structure
public final class ToolgunStructureMove {

    // * Server thread only, and only for as long as one pickup
    private static int armedTick = Integer.MIN_VALUE;

    private ToolgunStructureMove() {
    }

    public static void begin(final Level level) {
        armedTick = tickOf(level);
    }

    public static void end() {
        armedTick = Integer.MIN_VALUE;
    }

    // * Held for the one tick the pickup runs on
    public static boolean inProgress(final Level level) {
        final int tick = tickOf(level);

        return tick != Integer.MIN_VALUE && tick == armedTick;
    }

    private static int tickOf(final Level level) {
        final MinecraftServer server = level instanceof final ServerLevel serverLevel
                ? serverLevel.getServer()
                : null;

        return server == null ? Integer.MIN_VALUE : server.getTickCount();
    }
}