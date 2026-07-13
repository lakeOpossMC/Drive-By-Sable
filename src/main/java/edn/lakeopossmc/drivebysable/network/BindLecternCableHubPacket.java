package edn.lakeopossmc.drivebysable.network;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import edn.lakeopossmc.drivebysable.CableSounds;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// --- LINKS A LECTERN CONTROLLER TO A HUB --- //
public record BindLecternCableHubPacket(BlockPos lecternPos, BlockPos hubPos) implements CustomPacketPayload {
    public static final Type<BindLecternCableHubPacket> TYPE = new Type<>(DriveBySableMod.asResource("bind_lectern_controller_hub"));
    public static final StreamCodec<ByteBuf, BindLecternCableHubPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BindLecternCableHubPacket::lecternPos,
        BlockPos.STREAM_CODEC, BindLecternCableHubPacket::hubPos,
        BindLecternCableHubPacket::new
    );

    @Override
    public Type<BindLecternCableHubPacket> type() {
        return TYPE;
    }

    // * Only bind if player is actually using the lectern
    public static void handle(final BindLecternCableHubPacket payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        final BlockEntity blockEntity = player.level().getBlockEntity(payload.lecternPos());
        if (!(blockEntity instanceof final LecternCableHubDuck lecternHub) || !isUsedByPlayer(blockEntity, player)) {
            return;
        }

        lecternHub.drivebysable$setHubPos(payload.hubPos());
        blockEntity.setChanged();
        if (blockEntity instanceof final SmartBlockEntity smartBlockEntity) {
            smartBlockEntity.sendData();
        }
        player.level().playSound(null, payload.hubPos(), CableSounds.PLUG_IN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.literal("Controller connected!"), true);
    }

    // * Reflection since isUsedBy isnt on a shared type
    private static boolean isUsedByPlayer(final BlockEntity blockEntity, final Player player) {
        try {
            final Method isUsedBy = blockEntity.getClass().getMethod("isUsedBy", Player.class);
            final Object result = isUsedBy.invoke(blockEntity, player);
            return result instanceof final Boolean used && used;
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            DriveBySableMod.LOGGER.debug("Failed to query lectern user state for {}", blockEntity.getType(), exception);
            return false;
        }
    }
}
