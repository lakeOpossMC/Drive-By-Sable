package edn.lakeopossmc.drivebysable.cable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// --- WHAT A BACKUP DRIVE CAN AND CANNOT CAPTURE --- //
public final class BackupDriveCapture {

    // * How a source reads in the preview
    public enum Status {
        // * Everything about it would be saved
        VALID,
        // * Some connections would be saved
        PARTIAL,
        // * Nothing would be saved
        INVALID
    }

    private BackupDriveCapture() {
    }

    @Nullable
    public static SubLevel subLevelOf(final Level level, final BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos);
    }

    public static boolean isSameLevel(@Nullable final SubLevel expected, @Nullable final SubLevel actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        return Objects.equals(expected.getUniqueId(), actual.getUniqueId());
    }

    // * Whether ends on another level count, rather than being skipped
    public static boolean crossLevelSavingAllowed() {
        return CableConfig.CONFIG.allowCrossLevelConnectionSaving.get();
    }

    // * Is this position inside the region
    // * Same level is a plain position test
    // * Across levels the position is put into world space first, then into the capturing block space
    public static boolean withinRegion(
            final Level level,
            final AABB bounds,
            @Nullable final SubLevel driveSubLevel,
            final BlockPos pos
    ) {
        final SubLevel posSubLevel = subLevelOf(level, pos);
        if (isSameLevel(driveSubLevel, posSubLevel)) {
            return BackupDriveBounds.contains(bounds, pos);
        }

        if (!crossLevelSavingAllowed()) {
            return false;
        }

        final Vec3 centre = Vec3.atCenterOf(pos);
        final Vec3 world = posSubLevel == null ? centre : posSubLevel.logicalPose().transformPosition(centre);
        final Vec3 driveSpace = driveSubLevel == null
                ? world
                : driveSubLevel.logicalPose().transformPositionInverse(world);

        return bounds.contains(driveSpace);
    }

    // * A source has to be reachable before its connections matter
    public static boolean isSourceCapturable(
            final Level level,
            final AABB bounds,
            @Nullable final SubLevel driveSubLevel,
            final BlockPos source
    ) {
        return withinRegion(level, bounds, driveSubLevel, source);
    }

    // * An output has to land inside the box
    public static boolean isSinkCapturable(
            final Level level,
            final AABB bounds,
            @Nullable final SubLevel driveSubLevel,
            final BlockPos sinkPos
    ) {
        return withinRegion(level, bounds, driveSubLevel, sinkPos);
    }

    // * Green yellow or red for one source
    public static Status classify(
            final Level level,
            final AABB bounds,
            @Nullable final SubLevel driveSubLevel,
            final BlockPos source,
            final Map<String, Set<CableNetworkSink>> perChannel
    ) {
        if (!isSourceCapturable(level, bounds, driveSubLevel, source)) {
            return Status.INVALID;
        }

        int capturable = 0;
        int total = 0;
        for (final Set<CableNetworkSink> sinks : perChannel.values()) {
            for (final CableNetworkSink sink : sinks) {
                total++;
                if (isSinkCapturable(level, bounds, driveSubLevel, sink.blockPos())) {
                    capturable++;
                }
            }
        }

        if (total == 0 || capturable == 0) {
            return Status.INVALID;
        }
        return capturable == total ? Status.VALID : Status.PARTIAL;
    }

    public static Map<String, Map<String, Set<CableNetworkSink>>> groupByModule(
            final Level level,
            final BlockPos source,
            final Map<String, Set<CableNetworkSink>> perChannel
    ) {
        final Map<String, Map<String, Set<CableNetworkSink>>> byModule = new LinkedHashMap<>();
        final boolean hasModules = level.getBlockState(source).getBlock() instanceof SubTargetCableEndpoint;

        for (final Map.Entry<String, Set<CableNetworkSink>> entry : perChannel.entrySet()) {
            final String owner = hasModules
                    ? ((SubTargetCableEndpoint) level.getBlockState(source).getBlock())
                    .cable$subTargetForChannel(level, source, entry.getKey())
                    : null;

            // * An empty key stands for the block itself
            byModule.computeIfAbsent(owner == null ? "" : owner, ignored -> new LinkedHashMap<>())
                    .put(entry.getKey(), entry.getValue());
        }
        return byModule;
    }
}