package edn.lakeopossmc.drivebysable.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

// --- THE REGION A BACKUP DRIVE CAPTURES --- //
public final class BackupDriveBounds {

    private BackupDriveBounds() {
    }

    // * Local box from the drive
    // * The offset rotates with the box
    public static AABB of(final BlockPos drive, final int[] offset, final int[] size, final int rotationSteps) {
        final int[] near = {offset[0], offset[1], offset[2]};
        final int[] far = {offset[0] + size[0], offset[1] + size[1], offset[2] + size[2]};

        rotateHorizontally(near, rotationSteps);
        rotateHorizontally(far, rotationSteps);

        return new AABB(
                drive.getX() + Math.min(near[0], far[0]),
                drive.getY() + Math.min(near[1], far[1]),
                drive.getZ() + Math.min(near[2], far[2]),
                drive.getX() + Math.max(near[0], far[0]),
                drive.getY() + Math.max(near[1], far[1]),
                drive.getZ() + Math.max(near[2], far[2])
        );
    }

    private static void rotateHorizontally(final int[] point, final int steps) {
        for (int step = 0; step < Math.floorMod(steps, 4); step++) {
            final int rotatedX = -point[2];
            final int rotatedZ = point[0];
            point[0] = rotatedX;
            point[2] = rotatedZ;
        }
    }

    // * Mirror first and then rotate
    public static BlockPos transformDelta(final BlockPos delta, final Rotation rotation, final Mirror mirror) {
        final BlockPos mirrored = switch (mirror) {
            case LEFT_RIGHT -> new BlockPos(delta.getX(), delta.getY(), -delta.getZ());
            case FRONT_BACK -> new BlockPos(-delta.getX(), delta.getY(), delta.getZ());
            default -> delta;
        };
        return mirrored.rotate(rotation);
    }

    public static Direction transformDirection(final Direction direction, final Rotation rotation, final Mirror mirror) {
        return direction.getAxis().isVertical() ? direction : rotation.rotate(mirror.mirror(direction));
    }

    public static AABB transformBox(final AABB box, final Rotation rotation, final Mirror mirror) {
        final BlockPos first = transformDelta(
                BlockPos.containing(box.minX, box.minY, box.minZ), rotation, mirror);
        final BlockPos last = transformDelta(
                BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1), rotation, mirror);

        return new AABB(
                Math.min(first.getX(), last.getX()),
                Math.min(first.getY(), last.getY()),
                Math.min(first.getZ(), last.getZ()),
                Math.max(first.getX(), last.getX()) + 1,
                Math.max(first.getY(), last.getY()) + 1,
                Math.max(first.getZ(), last.getZ()) + 1
        );
    }

    public static boolean contains(final AABB box, final BlockPos pos) {
        return pos.getX() >= box.minX && pos.getX() < box.maxX
                && pos.getY() >= box.minY && pos.getY() < box.maxY
                && pos.getZ() >= box.minZ && pos.getZ() < box.maxZ;
    }
}