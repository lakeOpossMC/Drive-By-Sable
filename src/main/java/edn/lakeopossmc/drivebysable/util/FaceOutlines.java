package edn.lakeopossmc.drivebysable.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// --- FLAT BOX FOR EACH BLOCK FACE --- //
// * Used for outline rendering
public enum FaceOutlines {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST);

    private final AABB outline;

    // * Squash a unit box down to the given face
    FaceOutlines(final Direction direction) {
        final Vec3 shrink = new Vec3(direction.step()).scale(0.5D);
        outline = new AABB(BlockPos.ZERO)
            .inflate(-Math.abs(shrink.x), -Math.abs(shrink.y), -Math.abs(shrink.z))
            .move(new Vec3(direction.step()).scale(0.5D));
    }

    public static AABB getOutline(final Direction direction) {
        return switch (direction) {
            case DOWN -> DOWN.outline;
            case NORTH -> NORTH.outline;
            case SOUTH -> SOUTH.outline;
            case WEST -> WEST.outline;
            case EAST -> EAST.outline;
            case UP -> UP.outline;
        };
    }
}
