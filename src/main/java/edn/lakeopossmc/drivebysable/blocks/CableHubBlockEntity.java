package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CableHubBlockEntity extends SmartBlockEntity implements ClipboardCloneable {
    public static final String CLIPBOARD_KEY = "drivebysable_hub_connections";
    public static final String CONNECTIONS_KEY = "Connections";

    private static final String SINK_KEY = "Sink";
    private static final String DIRECTION_KEY = "Direction";
    private static final String CHANNEL_KEY = "Channel";

    public CableHubBlockEntity(final BlockPos pos, final BlockState state) {
        super(CableBlockEntities.CABLE_HUB.get(), pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public String getClipboardKey() {
        return CLIPBOARD_KEY;
    }

    @Override
    public boolean writeToClipboard(final HolderLookup.Provider registries, final CompoundTag tag, final Direction face) {
        if (this.level == null) {
            return false;
        }

        final Map<String, Set<CableNetworkSink>> perChannel = CableNetworkManager.get(this.level)
                .getNetwork()
                .get(this.worldPosition.asLong());
        if (perChannel == null || perChannel.isEmpty()) {
            return false;
        }

        final ListTag connections = new ListTag();
        for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : perChannel.entrySet()) {
            for (final CableNetworkSink sink : channelEntry.getValue()) {
                final CompoundTag connection = new CompoundTag();
                connection.putLong(SINK_KEY, sink.position());
                connection.putByte(DIRECTION_KEY, (byte) sink.direction());
                connection.putString(CHANNEL_KEY, channelEntry.getKey());
                connections.add(connection);
            }
        }

        if (connections.isEmpty()) {
            return false;
        }

        tag.put(CONNECTIONS_KEY, connections);
        return true;
    }

    @Override
    public boolean readFromClipboard(final HolderLookup.Provider registries, final CompoundTag tag, final Player player, final Direction face, final boolean simulate) {
        if (this.level == null || !tag.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return false;
        }

        final ListTag connections = tag.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
        if (connections.isEmpty()) {
            return false;
        }

        final List<String> ownChannels = this.getBlockState().getBlock() instanceof final MultiChannelCableSource source
                ? source.cable$getChannels(this.level, this.worldPosition)
                : List.of();

        boolean anyChannelMatched = false;
        for (final Tag entry : connections) {
            if (entry instanceof final CompoundTag connection
                    && connection.contains(CHANNEL_KEY, Tag.TAG_STRING)
                    && ownChannels.contains(connection.getString(CHANNEL_KEY))) {
                anyChannelMatched = true;
                break;
            }
        }

        if (simulate) {
            return anyChannelMatched;
        }

        if (!anyChannelMatched) {
            player.displayClientMessage(
                    Component.literal("Invalid paste: matching output channels not found").withStyle(ChatFormatting.RED),
                    true
            );
            return false;
        }

        for (final Tag entry : connections) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }
            if (!connection.contains(SINK_KEY, Tag.TAG_LONG)
                    || !connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                    || !connection.contains(CHANNEL_KEY, Tag.TAG_STRING)) {
                continue;
            }

            final long sinkPos = connection.getLong(SINK_KEY);
            final int direction = connection.getByte(DIRECTION_KEY);
            final String channel = connection.getString(CHANNEL_KEY);
            CableNetworkManager.createConnection(
                    this.level,
                    this.worldPosition,
                    BlockPos.of(sinkPos),
                    Direction.from3DDataValue(direction),
                    channel
            );
        }

        return true;
    }
}