package edn.lakeopossmc.drivebysable.cable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.foundation.utility.CreateLang;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlock;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.MultiChannelCableBusBlock;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.network.CableNetworkFullSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// --- CLIPBOARD COPY PASTE FOR THE BUS BLOCKS --- //
// * Neither bus is a Smart Block Entity, this adds that behavior
// * Works the way CableHubBlockEntity does, for channels 1 to 100 and the Sensor Bus channels
// * Sensor channels only paste onto a Sensor Bus that has them turned on
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID)
public final class BusClipboard {
    public static final String CLIPBOARD_KEY = "drivebysable_bus_connections";

    private static final String CONNECTIONS_KEY = "Connections";
    private static final String SINK_KEY = "Sink";
    private static final String DIRECTION_KEY = "Direction";
    private static final String CHANNEL_KEY = "Channel";
    private static final String SINK_CHANNEL_KEY = "SinkChannel";

    private static final Set<String> COPIED_CHANNELS = Stream.concat(
            IntStream.rangeClosed(1, MultiChannelCableBusBlock.CHANNEL_COUNT).mapToObj(Integer::toString),
            IntegratedSensorBusBlockEntity.TELEMETRY_CHANNELS.stream()
    ).collect(Collectors.toUnmodifiableSet());

    private BusClipboard() {
    }

    public static boolean isBus(final Level level, final BlockPos pos) {
        final Block block = level.getBlockState(pos).getBlock();
        return block instanceof MultiChannelCableBusBlock || block instanceof IntegratedSensorBusBlock;
    }

    //#region // --- COPY --- //
    // * False when the bus has nothing connected
    public static boolean write(final Level level, final BlockPos pos, final CompoundTag tag) {
        final CableNetworkManager manager = CableNetworkManager.get(level);
        if (manager == null) {
            return false;
        }

        final ListTag connections = new ListTag();
        for (final Map.Entry<String, List<CableNetworkSink>> channelEntry : manager.getConnections(pos).entrySet()) {
            if (!COPIED_CHANNELS.contains(channelEntry.getKey())) {
                continue;
            }
            for (final CableNetworkSink sink : channelEntry.getValue()) {
                final CompoundTag connection = new CompoundTag();
                connection.putLong(SINK_KEY, sink.position());
                connection.putByte(DIRECTION_KEY, (byte) sink.direction());
                connection.putString(CHANNEL_KEY, channelEntry.getKey());
                if (!sink.sinkChannel().isEmpty()) {
                    connection.putString(SINK_CHANNEL_KEY, sink.sinkChannel());
                }
                connections.add(connection);
            }
        }

        if (connections.isEmpty()) {
            return false;
        }

        tag.put(CONNECTIONS_KEY, connections);
        return true;
    }
    //#endregion

    //#region // --- PASTE --- //
    @Nullable
    public static CompoundTag copiedValues(final ItemStack clipboard) {
        return clipboard.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY)
                .copiedValues()
                .orElse(null);
    }

    // * What this bus offers right now, so a Sensor Bus only takes the sensor channels it has on
    private static Set<String> ownChannels(final Level level, final BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof final MultiChannelCableSource source
                ? Set.copyOf(source.cable$getChannels(level, pos))
                : Set.of();
    }

    // * Whether anything on the clipboard lands on a channel of this bus
    public static boolean canPaste(final Level level, final BlockPos pos, @Nullable final CompoundTag copiedValues) {
        if (copiedValues == null) {
            return false;
        }

        final Set<String> ownChannels = ownChannels(level, pos);
        for (final Tag entry : connections(copiedValues.getCompound(CLIPBOARD_KEY))) {
            if (entry instanceof final CompoundTag connection
                    && ownChannels.contains(connection.getString(CHANNEL_KEY))) {
                return true;
            }
        }
        return false;
    }

    private static ListTag connections(final CompoundTag tag) {
        return tag.contains(CONNECTIONS_KEY, Tag.TAG_LIST)
                ? tag.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)
                : new ListTag();
    }

    // * Channels switched off on this bus are left out, canPaste has already refused if that was all of them
    private static void paste(final Level level, final BlockPos pos, final CompoundTag tag) {
        final Set<String> ownChannels = ownChannels(level, pos);
        for (final Tag entry : connections(tag)) {
            if (!(entry instanceof final CompoundTag connection)
                    || !connection.contains(SINK_KEY, Tag.TAG_LONG)
                    || !connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                    || !ownChannels.contains(connection.getString(CHANNEL_KEY))) {
                continue;
            }

            CableNetworkManager.createConnection(
                    level,
                    pos,
                    BlockPos.of(connection.getLong(SINK_KEY)),
                    Direction.from3DDataValue(connection.getByte(DIRECTION_KEY)),
                    connection.getString(CHANNEL_KEY),
                    connection.getString(SINK_CHANNEL_KEY)
            );
        }
    }
    //#endregion

    //#region // --- CLICKS --- //
    private static boolean applies(final PlayerInteractEvent event) {
        final Player player = event.getEntity();
        return event.getHand() == InteractionHand.MAIN_HAND
                && AllBlocks.CLIPBOARD.isIn(event.getItemStack())
                && !player.isSpectator()
                && !player.isShiftKeyDown()
                && isBus(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void rightClickToCopy(final PlayerInteractEvent.RightClickBlock event) {
        if (!applies(event)) {
            return;
        }

        // * Also keeps the Clipboard and the Sensor Bus screens from opening
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(event.getEntity() instanceof final ServerPlayer player)) {
            return;
        }

        final CompoundTag connections = new CompoundTag();
        if (!write(event.getLevel(), event.getPos(), connections)) {
            CableServerFeedback.showInvalidOperationMessage(player, "drivebysable.invalid_op.no_connections");
            return;
        }

        final CompoundTag copiedValues = new CompoundTag();
        copiedValues.put(CLIPBOARD_KEY, connections);

        final ItemStack clipboard = event.getItemStack();
        clipboard.set(AllDataComponents.CLIPBOARD_CONTENT,
                clipboard.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY)
                        .setType(ClipboardType.WRITTEN)
                        .setCopiedValues(copiedValues));

        showResult(player, event.getLevel(), event.getPos(), "clipboard.copied_from");
    }

    @SubscribeEvent
    public static void leftClickToPaste(final PlayerInteractEvent.LeftClickBlock event) {
        if (!applies(event)) {
            return;
        }

        // * A Clipboard nothing was copied to hits the block as usual
        final CompoundTag copiedValues = copiedValues(event.getItemStack());
        if (copiedValues == null) {
            return;
        }

        event.setCanceled(true);

        // * The server hears about the release as well, only the press pastes
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof final ServerPlayer player)) {
            return;
        }

        if (!canPaste(event.getLevel(), event.getPos(), copiedValues)) {
            CableServerFeedback.showInvalidOperationMessage(player, "drivebysable.invalid_op.invalid_paste");
            return;
        }

        paste(event.getLevel(), event.getPos(), copiedValues.getCompound(CLIPBOARD_KEY));
        CableNetworkFullSyncPacket.sendTo(player);
        showResult(player, event.getLevel(), event.getPos(), "clipboard.pasted_to");
    }

    private static void showResult(final ServerPlayer player, final Level level, final BlockPos pos, final String key) {
        player.displayClientMessage(
                CreateLang.translate(key, level.getBlockState(pos).getBlock().getName().withStyle(ChatFormatting.WHITE))
                        .style(ChatFormatting.GREEN)
                        .component(),
                true
        );
    }
    //#endregion
}