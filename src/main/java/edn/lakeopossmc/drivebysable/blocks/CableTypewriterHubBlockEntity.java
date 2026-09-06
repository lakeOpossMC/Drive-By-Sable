package edn.lakeopossmc.drivebysable.blocks;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableServerFeedback;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.compat.CableTypewriterHubServerHandler;
import edn.lakeopossmc.drivebysable.compat.keytranslator.ControllerChannelTranslator;
import edn.lakeopossmc.drivebysable.compat.keytranslator.ControllerChannelTranslator.Vocabulary;
import edn.lakeopossmc.drivebysable.mixinducks.LinkedTypewriterBlockEntityDuck;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// --- BLOCK ENTITY FOR TYPEWRITER HUB --- //
// * Wraps the typewriter to expose per key channels
public class CableTypewriterHubBlockEntity extends LinkedTypewriterBlockEntity {
    private static final String SINK_KEY = "Sink";
    private static final String DIRECTION_KEY = "Direction";
    private static final String CHANNEL_KEY = "Channel";
    private static CableTypewriterHubBlockEntity clientInstance;

    private final Set<String> connectedChannels = new HashSet<>();
    private boolean promiscuousMode = false;

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

    //#region // --- TRACK WHICH CHANNELS HAVE LINKS --- //
    // * Only resyncs to client when the set actually changed
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
    //#endregion

    // * Remember the client side block entity for key forwarding
    @Override
    public boolean checkAndStartUsing(final UUID userID) {
        final boolean result = super.checkAndStartUsing(userID);
        if (result && this.level != null && this.level.isClientSide) {
            clientInstance = this;
        }
        return result;
    }

    // * Only run vanilla key logic if it has a saved entry, always push to cable
    @Override
    public void pressKey(final int key) {
        boolean isEventHandledBySuper = false;
        if (this.getTypewriterEntries().getEntry(key) != null) {
            super.pressKey(key);
            isEventHandledBySuper = true;
        }

        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.receiveKey(level, this.getBlockPos(), key, true);

            if (!isEventHandledBySuper) {
                this.getPressedKeys().add(key);
                if (this.computerHandler != null) {
                    // * This event is only sent to the computer once per key press, so we always
                    // pass false for the repeated parameter, see https://tweaked.cc/event/key.html
                    LinkedTypewriterBlockEntityDuck linkedTypewriter = (LinkedTypewriterBlockEntityDuck) this;
                    this.computerHandler.queueEvent(linkedTypewriter.drivebysable$getComputerEventName("key"), key, false);
                }
            }
        }
    }

    @Override
    public void releaseKey(final int key) {
        boolean isEventHandledBySuper = false;
        if (this.getTypewriterEntries().getEntry(key) != null) {
            super.releaseKey(key);
            isEventHandledBySuper = true;
        }

        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.receiveKey(level, this.getBlockPos(), key, false);

            if (!isEventHandledBySuper) {
                this.getPressedKeys().removeIf(x -> x == key);
                if (this.computerHandler != null) {
                    LinkedTypewriterBlockEntityDuck linkedTypewriter = (LinkedTypewriterBlockEntityDuck) this;
                    this.computerHandler.queueEvent(linkedTypewriter.drivebysable$getComputerEventName("key_up"), key);
                }
            }
        }
    }

    // * Clear all channel signals on disconnect
    @Override
    public void disconnectUser() {
        if (this.level != null && this.level.isClientSide) {
            clientInstance = null;
        }
        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.KEY_TO_CHANNEL.values()
                    .forEach(channel -> CableNetworkManager.trySetSignalAt(level, this.getBlockPos(), channel, 0));

        }
        try {
            super.disconnectUser();
        } catch (final NullPointerException ignored) {
            // * No user was connected — nothing to disconnect
        }
    }

    // * Sync state to client only, not saved to disk
    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries,
            final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            final ListTag list = new ListTag();
            this.connectedChannels.forEach(ch -> list.add(StringTag.valueOf(ch)));
            tag.put("ConnectedChannels", list);

            tag.put("PromiscuousMode", this.isInPromiscuousMode() ? ByteTag.ONE : ByteTag.ZERO);
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries,
                        final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) {
            return;
        }

        if (tag.contains("ConnectedChannels")) {
            this.connectedChannels.clear();
            final ListTag list = tag.getList("ConnectedChannels", 8);
            for (int i = 0; i < list.size(); i++) {
                this.connectedChannels.add(list.getString(i));
            }
        }

        if (tag.contains("PromiscuousMode")) {
            this.setPromiscuousMode(tag.getByte("PromiscuousMode") != 0);
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

    // * Adds connections on top of the vanilla key binding clipboard data
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
        tag.putString(CableHubBlockEntity.VOCABULARY_KEY, Vocabulary.TYPEWRITER.name());
        return true;
    }

    //#region // --- PASTE CONNECTIONS BACK --- //
    // * Simulate only checks if any channel would match
    // * Real paste shows error if nothing matched
    @Override
    public boolean readFromClipboard(final HolderLookup.Provider registries, final CompoundTag tag, final Player player, final Direction face, final boolean simulate) {
        // * Presence of Keys means source was a typewriter hub
        final boolean sourceIsTypewriter = tag.contains("Keys", Tag.TAG_LIST);
        final boolean readKeyBindings = sourceIsTypewriter && super.readFromClipboard(registries, tag, player, face, simulate);

        if (this.level == null) {
            return readKeyBindings;
        }

        // * No connections key at all means source had nothing to copy, still counts as invalid
        final ListTag connections = tag.contains(CableHubBlockEntity.CONNECTIONS_KEY, Tag.TAG_LIST)
                ? tag.getList(CableHubBlockEntity.CONNECTIONS_KEY, Tag.TAG_COMPOUND)
                : new ListTag();

        // * Missing tag means old data or same vocabulary
        final Vocabulary sourceVocabulary = tag.contains(CableHubBlockEntity.VOCABULARY_KEY, Tag.TAG_STRING)
                ? Vocabulary.valueOf(tag.getString(CableHubBlockEntity.VOCABULARY_KEY))
                : Vocabulary.TYPEWRITER;

        final List<String> ownChannels = this.getBlockState().getBlock() instanceof final MultiChannelCableSource source
                ? source.cable$getChannels(this.level, this.getBlockPos())
                : List.of();

        boolean anyChannelMatched = false;
        for (final Tag entry : connections) {
            if (entry instanceof final CompoundTag connection
                    && connection.contains(CHANNEL_KEY, Tag.TAG_STRING)
                    && ownChannels.contains(ControllerChannelTranslator.translate(
                    connection.getString(CHANNEL_KEY), sourceVocabulary, Vocabulary.TYPEWRITER, player.getUUID()))) {
                anyChannelMatched = true;
                break;
            }
        }

        if (simulate) {
            return readKeyBindings || anyChannelMatched;
        }

        // * Typewriter to typewriter never errors
        if (!anyChannelMatched && !sourceIsTypewriter) {
            // * Flash the error and play the deny sound
            CableServerFeedback.showInvalidOperationMessage((ServerPlayer) player, "drivebysable.invalid_op.invalid_paste");
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
            final String channel = ControllerChannelTranslator.translate(
                    connection.getString(CHANNEL_KEY), sourceVocabulary, Vocabulary.TYPEWRITER, player.getUUID());
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
    // #endregion

    //#region // --- COMPUTER CRAFT COMPAT --- //
    // * Promiscuous mode allows the typewriter hub to relay all key events to
    // connected computers, regardless of channel matching.
    public boolean isInPromiscuousMode() {
        return promiscuousMode;
    }

    public void setPromiscuousMode(final boolean promiscuousMode) {
        this.promiscuousMode = promiscuousMode;
        this.sendData();
    }
    //#endregion

    public static CableTypewriterHubBlockEntity getClientInstance() {
        return clientInstance;
    }

    @Override
    public String getClipboardKey() {
        return CableHubBlockEntity.CLIPBOARD_KEY;
    }
}