package edn.lakeopossmc.drivebysable.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// --- PACKED POS + DIR FOR A BLOCK FACE --- //
public record BlockFace(long pos, int dir) {
    public static BlockFace of(final long pos, final int dir) {
        return new BlockFace(pos, dir);
    }

    public static BlockFace of(final BlockPos pos, final Direction direction) {
        return new BlockFace(pos.asLong(), direction.get3DDataValue());
    }
}
