package edn.lakeopossmc.drivebysable.blocks;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.compat.CableTypewriterHubServerHandler;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CableTypewriterHubBlockEntity extends LinkedTypewriterBlockEntity {

    private static final String SINK_KEY = "Sink";
    private static final String DIRECTION_KEY = "Direction";
    private static final String CHANNEL_KEY = "Channel";

    private final Set<String> connectedChannels = new HashSet<>();

    private static CableTypewriterHubBlockEntity clientInstance;

    public CableTypewriterHubBlockEntity(final BlockPos pos, final BlockState state) {
        super(CableBlockEntities.CABLE_TYPEWRITER_HUB.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level instanceof final ServerLevel level) {
            updateConnectedChannels(level);
        }
    }

    private void updateConnectedChannels(final ServerLevel level) {
        final CableNetworkManager manager = CableNetworkManager.get(level);
        boolean changed = false;

        final Iterator<String> existing = this.connectedChannels.iterator();
        while (existing.hasNext()) {
            if (!manager.hasSinks(this.getBlockPos(), existing.next())) {
                existing.remove();
                changed = true;
            }
        }

        for (final String channel : CableTypewriterHubServerHandler.CHANNELS) {
            if (manager.hasSinks(this.getBlockPos(), channel) && this.connectedChannels.add(channel)) {
                changed = true;
            }
        }

        if (changed) {
            this.sendData();
        }
    }

    public boolean hasConnectionForChannel(final String channel) {
        return this.connectedChannels.contains(channel);
    }

    @Override
    public boolean checkAndStartUsing(final UUID userID) {
        final boolean result = super.checkAndStartUsing(userID);
        if (result && this.level != null && this.level.isClientSide) {
            clientInstance = this;
        }
        return result;
    }

    @Override
    public void pressKey(final int key) {
        if (this.getTypewriterEntries().getEntry(key) != null) {
            super.pressKey(key);
        }
        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.receiveKey(level, this.getBlockPos(), key, true);
        }
    }

    @Override
    public void releaseKey(final int key) {
        if (this.getTypewriterEntries().getEntry(key) != null) {
            super.releaseKey(key);
        }
        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.receiveKey(level, this.getBlockPos(), key, false);
        }
    }

    @Override
    public void disconnectUser() {
        if (this.level != null && this.level.isClientSide) {
            clientInstance = null;
        }
        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.KEY_TO_CHANNEL.values().forEach(channel ->
                    CableNetworkManager.trySetSignalAt(level, this.getBlockPos(), channel, 0));
        }
        super.disconnectUser();
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries,
                         final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            final ListTag list = new ListTag();
            this.connectedChannels.forEach(ch -> list.add(StringTag.valueOf(ch)));
            tag.put("ConnectedChannels", list);
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries,
                        final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket && tag.contains("ConnectedChannels")) {
            this.connectedChannels.clear();
            final ListTag list = tag.getList("ConnectedChannels", 8);
            for (int i = 0; i < list.size(); i++) {
                this.connectedChannels.add(list.getString(i));
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public void sendConnectMessage(final Player player) {
        player.displayClientMessage(
                Lang.builder(DriveBySableMod.MOD_ID).translate("typewriter_hub.start_controlling").component(),
                true
        );
    }

    @Override
    public void sendDisconnectMessage(final Player player) {
        player.displayClientMessage(
                Lang.builder(DriveBySableMod.MOD_ID).translate("typewriter_hub.stop_controlling").component(),
                true
        );
    }

    @Override
    public boolean writeToClipboard(final HolderLookup.Provider registries, final CompoundTag tag, final Direction face) {
        final boolean wroteKeyBindings = super.writeToClipboard(registries, tag, face);

        if (this.level == null) {
            return wroteKeyBindings;
        }

        final Map<String, Set<CableNetworkSink>> perChannel = CableNetworkManager.get(this.level)
                .getNetwork()
                .get(this.getBlockPos().asLong());
        if (perChannel == null || perChannel.isEmpty()) {
            return wroteKeyBindings;
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
            return wroteKeyBindings;
        }

        tag.put(CableHubBlockEntity.CONNECTIONS_KEY, connections);
        return true;
    }

    @Override
    public boolean readFromClipboard(final HolderLookup.Provider registries, final CompoundTag tag, final Player player, final Direction face, final boolean simulate) {
        final boolean readKeyBindings = tag.contains("Keys", Tag.TAG_LIST)
                && super.readFromClipboard(registries, tag, player, face, simulate);

        if (this.level == null || !tag.contains(CableHubBlockEntity.CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return readKeyBindings;
        }

        final ListTag connections = tag.getList(CableHubBlockEntity.CONNECTIONS_KEY, Tag.TAG_COMPOUND);
        if (connections.isEmpty()) {
            return readKeyBindings;
        }

        final List<String> ownChannels = this.getBlockState().getBlock() instanceof final MultiChannelCableSource source
                ? source.cable$getChannels(this.level, this.getBlockPos())
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
            return readKeyBindings || anyChannelMatched;
        }

        if (!anyChannelMatched) {
            player.displayClientMessage(
                    Component.literal("Invalid paste: matching output channels not found").withStyle(ChatFormatting.RED),
                    true
            );
            return readKeyBindings;
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
                    this.getBlockPos(),
                    BlockPos.of(sinkPos),
                    Direction.from3DDataValue(direction),
                    channel
            );
        }

        return true;
    }

    public static CableTypewriterHubBlockEntity getClientInstance() {
        return clientInstance;
    }

    @Override
    public String getClipboardKey() {
        return CableHubBlockEntity.CLIPBOARD_KEY;
    }
}