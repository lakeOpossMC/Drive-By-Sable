package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.CableBlocks;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableServerFeedback;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import edn.lakeopossmc.drivebysable.compat.keytranslator.ControllerChannelTranslator;
import edn.lakeopossmc.drivebysable.compat.keytranslator.ControllerChannelTranslator.Vocabulary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;

// --- SHARED BE FOR CABLE HUB AND ADVANCED HUB --- //
// * Implements clipboard copy paste for connections
public class CableHubBlockEntity extends SmartBlockEntity implements ClipboardCloneable {
    public static final String CLIPBOARD_KEY = "drivebysable_hub_connections";
    public static final String CONNECTIONS_KEY = "Connections";
    public static final String VOCABULARY_KEY = "ChannelVocabulary";

    private static final String SINK_KEY = "Sink";
    private static final String DIRECTION_KEY = "Direction";
    private static final String CHANNEL_KEY = "Channel";

    // * Typed as Object on purpose, the real type drags in Computer Craft
    private final Object computerHandler;
    public String computerEventPrefix = "";

    public CableHubBlockEntity(final BlockPos pos, final BlockState state) {
        super(CableBlockEntities.CABLE_HUB.get(), pos, state);

        this.computerHandler = ComputerCraftCompat.newComputerHandler();
    }

    // * Cable hub speaks linked controller, advanced hub speaks tweaked controller
    private Vocabulary getVocabulary() {
        return CableBlocks.ADVANCED_CABLE_HUB != null && this.getBlockState().is(CableBlocks.ADVANCED_CABLE_HUB.get())
                ? Vocabulary.TWEAKED_CONTROLLER
                : Vocabulary.LINKED_CONTROLLER;
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public String getClipboardKey() {
        return CLIPBOARD_KEY;
    }

    // * Dump every channel and sink into the tag
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
        tag.putString(VOCABULARY_KEY, this.getVocabulary().name());
        return true;
    }

    //#region // --- PASTE CONNECTIONS BACK --- //
    // * Simulate only checks if any channel would match
    // * Real paste shows error if nothing matched
    @Override
    public boolean readFromClipboard(final HolderLookup.Provider registries, final CompoundTag tag, final Player player, final Direction face, final boolean simulate) {
        if (this.level == null) {
            return false;
        }

        // * No connections key at all means source had nothing to copy, still counts as invalid
        final ListTag connections = tag.contains(CONNECTIONS_KEY, Tag.TAG_LIST)
                ? tag.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)
                : new ListTag();

        // * Missing tag means old data or same vocabulary
        final Vocabulary myVocabulary = this.getVocabulary();
        final Vocabulary sourceVocabulary = tag.contains(VOCABULARY_KEY, Tag.TAG_STRING)
                ? Vocabulary.valueOf(tag.getString(VOCABULARY_KEY))
                : myVocabulary;

        final List<String> ownChannels = this.getBlockState().getBlock() instanceof final MultiChannelCableSource source
                ? source.cable$getChannels(this.level, this.worldPosition)
                : List.of();

        boolean anyChannelMatched = false;
        for (final Tag entry : connections) {
            if (entry instanceof final CompoundTag connection
                    && connection.contains(CHANNEL_KEY, Tag.TAG_STRING)
                    && ownChannels.contains(ControllerChannelTranslator.translate(
                    connection.getString(CHANNEL_KEY), sourceVocabulary, myVocabulary, player.getUUID()))) {
                anyChannelMatched = true;
                break;
            }
        }

        if (simulate) {
            return anyChannelMatched;
        }

        if (!anyChannelMatched) {
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
                    connection.getString(CHANNEL_KEY), sourceVocabulary, myVocabulary, player.getUUID());
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
    //#endregion

    //#region // --- COMPUTER CRAFT COMPAT --- //
    // * Opaque to everything but the bridge
    public Object getComputerHandler() {
        return this.computerHandler;
    }

    public String getComputerEventPrefix() {
        return computerEventPrefix;
    }

    public void setComputerEventPrefix(final String computerEventPrefix) {
        this.computerEventPrefix = computerEventPrefix;
    }

    public String getComputerEventName(final String eventName) {
        return (this.computerEventPrefix != null && !this.computerEventPrefix.isEmpty())
                ? String.format("%s_%s", this.computerEventPrefix, eventName)
                : eventName;
    }
    //#endregion
}