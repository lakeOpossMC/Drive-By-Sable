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
import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import edn.lakeopossmc.drivebysable.mixinducks.LinkedTypewriterBlockEntityDuck;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.nbt.NBTHelper;
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

import java.util.Collection;
import java.util.HashMap;
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

    // * What the Linked Typewriter files its bindings under
    private static final String KEYS_KEY = "Keys";

    // * Whoever is at the keyboard right now
    private static final String CURRENT_USER_KEY = "CurrentUser";

    private static final String PULSE_KEY = "Pulse";

    // * A handheld counts as switched on for this long after it was last heard from
    private static final int HANDHELD_TIMEOUT = 30;

    private static CableTypewriterHubBlockEntity clientInstance;

    private final Set<String> connectedChannels = new HashSet<>();
    private boolean promiscuousMode = false;

    private final LerpedFloat glow = LerpedFloat.linear().startWithValue(0);
    private boolean sendPulse;
    private boolean pulseWaiting;

    // * Players with a switched on Handheld Typewriter bound here, and the keys each is holding
    private final Map<UUID, HandheldUser> handheldUsers = new HashMap<>();

    private static final class HandheldUser {
        private final Set<Integer> held = new HashSet<>();
        private int timeout = HANDHELD_TIMEOUT;
    }

    // * Carries the connection half of the clipboard under the mod's own key
    // * See CableTypewriterHubConnectionClipboard for why it is not on this class
    private CableTypewriterHubConnectionClipboard connectionClipboard;

    public CableTypewriterHubBlockEntity(final BlockPos pos, final BlockState state) {
        super(CableBlockEntities.CABLE_TYPEWRITER_HUB.get(), pos, state);
        this.glow.chase(0, 0.5F, Chaser.EXP);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.connectionClipboard = new CableTypewriterHubConnectionClipboard(this);
        behaviours.add(this.connectionClipboard);
    }

    @Override
    public void tick() {
        // * Before the typewriter's own tick
        if (this.level instanceof ServerLevel) {
            tickHandhelds();
        }

        super.tick();

        if (this.level != null && this.level.isClientSide) {
            this.glow.tickChaser();
        }
        if (this.level instanceof final ServerLevel level) {
            // * One blink per tick, however many keys went down in it
            if (this.pulseWaiting) {
                this.pulseWaiting = false;
                this.sendPulse = true;
                this.sendData();
            }
            updateConnectedChannels(level);
        }
    }

    //#region // --- HANDHELD TYPEWRITERS BOUND TO THIS HUB --- //
    public void receiveHandheldInput(final UUID player, final Collection<Integer> keys, final boolean pressed) {
        if (pressed) {
            final HandheldUser user = this.handheldUsers.computeIfAbsent(player, id -> new HandheldUser());
            user.timeout = HANDHELD_TIMEOUT;
            for (final Integer key : keys) {
                if (key != null && user.held.add(key)) {
                    this.pulseWaiting = true;
                }
            }
        } else if (keys.isEmpty()) {
            // * Switched off
            this.handheldUsers.remove(player);
        } else {
            final HandheldUser user = this.handheldUsers.get(player);
            if (user != null) {
                user.held.removeAll(keys);
            }
        }
        updatePowered();
    }

    // * A handheld that went quiet (dropped, logged out, out of reach)
    private void tickHandhelds() {
        if (this.handheldUsers.isEmpty()) {
            return;
        }
        this.handheldUsers.values().removeIf(user -> --user.timeout <= 0);
        updatePowered();
    }

    // * Lit while someone is at the keyboard or has a handheld switched on
    private void updatePowered() {
        this.powered = !this.handheldUsers.isEmpty() || this.isInUse();
    }

    public float getGlow(final float partialTicks) {
        return this.glow.getValue(partialTicks);
    }
    //#endregion

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
            this.pulseWaiting = true;

            if (!isEventHandledBySuper) {
                this.getPressedKeys().add(key);
                // * This event is only sent to the computer once per key press, so we
                // always pass false for repeated, see https://tweaked.cc/event/key.html
                ComputerCraftCompat.queueTypewriterKey(this, "key", key, false);
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
                ComputerCraftCompat.queueTypewriterKey(this, "key_up", key, null);
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

            if (this.sendPulse) {
                this.sendPulse = false;
                NBTHelper.putMarker(tag, PULSE_KEY);
            }
        }
    }

    @Override
    public void writeSafe(final CompoundTag tag, final HolderLookup.Provider registries) {
        this.write(tag, registries, false);

        // * A freshly printed hub should not believe someone is already typing on it
        tag.remove(CURRENT_USER_KEY);
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

        if (tag.contains(PULSE_KEY)) {
            this.glow.setValue(2);
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

    //#region // --- PASTE KEY BINDINGS BACK --- //
    @Override
    public boolean readFromClipboard(final HolderLookup.Provider registries, final CompoundTag tag,
                                     final Player player, final Direction face, final boolean simulate) {
        final boolean sourceIsTypewriter = tag.contains(KEYS_KEY, Tag.TAG_LIST);
        final boolean readKeyBindings = sourceIsTypewriter
                && super.readFromClipboard(registries, tag, player, face, simulate);

        if (simulate) {
            return readKeyBindings;
        }

        final boolean pastedConnections = this.connectionClipboard != null
                && this.connectionClipboard.consumePastedConnections();

        if (!readKeyBindings && !pastedConnections && player instanceof final ServerPlayer serverPlayer) {
            CableServerFeedback.showInvalidOperationMessage(serverPlayer, "drivebysable.invalid_op.invalid_paste");
        }

        return readKeyBindings;
    }
    //#endregion

    //#region // --- THE CONNECTION HALF, DRIVEN BY THE BEHAVIOUR --- //
    // * Dump every channel and sink into the tag
    boolean writeConnectionsToClipboard(final CompoundTag tag) {
        if (this.level == null) {
            return false;
        }

        final Map<String, Set<CableNetworkSink>> perChannel = CableNetworkManager.get(this.level)
                .getNetwork()
                .get(this.getBlockPos().asLong());
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

        tag.put(CableHubBlockEntity.CONNECTIONS_KEY, connections);
        tag.putString(CableHubBlockEntity.VOCABULARY_KEY, Vocabulary.TYPEWRITER.name());
        return true;
    }

    boolean applyConnectionsFromClipboard(final CompoundTag tag, final Player player, final boolean simulate) {
        if (this.level == null) {
            return false;
        }

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

        if (simulate || !anyChannelMatched) {
            return anyChannelMatched;
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
    //#endregion

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

}