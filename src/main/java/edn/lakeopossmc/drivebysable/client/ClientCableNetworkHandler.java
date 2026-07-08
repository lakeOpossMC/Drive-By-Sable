package edn.lakeopossmc.drivebysable.client;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import edn.lakeopossmc.drivebysable.CableBlocks;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.items.CableItem;
import edn.lakeopossmc.drivebysable.items.CableCutterItem;
import edn.lakeopossmc.drivebysable.mixinducks.TweakedControllerDuck;
import edn.lakeopossmc.drivebysable.network.CableAddConnectionPacket;
import edn.lakeopossmc.drivebysable.network.CableNetworkRequestSyncPacket;
import edn.lakeopossmc.drivebysable.network.CableRemoveConnectionPacket;
import edn.lakeopossmc.drivebysable.util.BlockFace;
import edn.lakeopossmc.drivebysable.util.FaceOutlines;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class ClientCableNetworkHandler {
    private static final AABB UNIT_CUBE = AABB.unitCubeFromLowerCorner(Vec3.ZERO);
    private static final Map<Long, Map<String, Set<CableNetworkSink>>> EMPTY_NETWORK = Map.of();

    private static Map<Long, Map<String, Set<CableNetworkSink>>> currentNetwork = EMPTY_NETWORK;
    private static BlockPos selectedSource;
    private static String currentChannel = CableNetworkManager.WORLD_CHANNEL;
    private static int syncCooldown;
    private static String pendingSchematicSyncReason;

    private ClientCableNetworkHandler() {
    }

    @SubscribeEvent
    public static void onWorldUnload(final LevelEvent.Unload event) {
        clearSource();
    }

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final Item eventItem = event.getItemStack().getItem();
        final BlockState hitBlock = event.getLevel().getBlockState(event.getHitVec().getBlockPos());
        final Player eventPlayer = event.getEntity();

        final boolean isCutter = eventItem instanceof CableCutterItem;
        final boolean cutterShiftDown = isCutter && eventPlayer != null && eventPlayer.isShiftKeyDown();

        if (eventItem instanceof CableItem || isCutter) {
            event.setUseBlock(TriState.FALSE);
        }
        if (isCutter && !cutterShiftDown) {
            event.setUseItem(TriState.FALSE);
        }
        if ((eventItem instanceof LinkedControllerItem && hitBlock.is(CableBlocks.CABLE_HUB) || (eventItem instanceof TweakedControllerDuck && hitBlock.is(CableBlocks.ADVANCED_CABLE_HUB)))) {
            event.setUseItem(TriState.FALSE);
        }
        if (event.getSide().isServer()) {
            return;
        }

        final Player player = eventPlayer;
        if (player == null || player.isSpectator()) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        final ItemStack heldItem = event.getItemStack();
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        final Direction face = event.getFace() == null ? Direction.UP : event.getFace();

        if (heldItem.is(CableItems.CABLE.get())) {
            handleCableUse(player, heldItem, level, pos, face);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        if (heldItem.is(CableItems.CABLE_CUTTER.get()) && !cutterShiftDown) {
            handleCutterUse(level, pos, face);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(final InputEvent.MouseScrollingEvent event) {
        final Player player = Minecraft.getInstance().player;
        if (player == null || selectedSource == null) {
            return;
        }

        final ItemStack mainHandItem = player.getMainHandItem();
        if (!mainHandItem.is(CableItems.CABLE.get()) && !mainHandItem.is(CableItems.CABLE_CUTTER.get())) {
            return;
        }

        final double delta = event.getScrollDeltaY();
        if (delta == 0) {
            return;
        }

        changeChannel(player.level(), selectedSource, delta > 0);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        final Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        final ItemStack mainHand = player.getMainHandItem();
        final boolean holdingCableTool = mainHand.is(CableItems.CABLE.get()) || mainHand.is(CableItems.CABLE_CUTTER.get());

        if (holdingCableTool || pendingSchematicSyncReason != null) {
            final Map<Long, Map<String, Set<CableNetworkSink>>> latestNetwork = CableNetworkManager.get(level).getNetwork();
            if (!latestNetwork.equals(currentNetwork)) {
                currentNetwork = latestNetwork;
                if (pendingSchematicSyncReason != null) {
                    DriveBySableMod.LOGGER.info(
                            "[schematic-debug] Client cable mirror refreshed after {}: {} sources / {} connections.",
                            pendingSchematicSyncReason,
                            currentNetwork.size(),
                            countConnections(currentNetwork)
                    );
                    pendingSchematicSyncReason = null;
                }
            }
        }

        if (!holdingCableTool) {
            clearSource();
            return;
        }

        if (--syncCooldown <= 0) {
            syncManager();
        }

        if (selectedSource != null) {
            drawOutline(level, selectedSource, LineColor.SOURCE.SELECTED.getColor());
        }
        drawOutlines(level, selectedSource, currentNetwork, currentChannel);
    }

    public static void clearSource() {
        currentNetwork = EMPTY_NETWORK;
        selectedSource = null;
        currentChannel = CableNetworkManager.WORLD_CHANNEL;
        syncCooldown = 0;
    }

    public static String getCurrentChannel() {
        return currentChannel;
    }

    public static void requestSchematicSync(final String reason) {
        pendingSchematicSyncReason = reason;
        DriveBySableMod.LOGGER.info(
                "[schematic-debug] Requesting cable mirror sync for {}. Current client mirror: {} sources / {} connections.",
                reason,
                currentNetwork.size(),
                countConnections(currentNetwork)
        );
        syncManager();
    }

    private static void handleCableUse(final Player player, final ItemStack heldItem, final Level level, final BlockPos pos, final Direction face) {
        if (selectedSource == null) {
            selectedSource = pos.immutable();
            changeChannel(level, selectedSource, true);
            syncManager();
            return;
        }

        if (selectedSource.equals(pos)) {
            clearSource();
            return;
        }

        final Map<String, Set<CableNetworkSink>> currentSelection = currentNetwork.get(selectedSource.asLong());
        final CableNetworkSink sink = CableNetworkSink.of(pos, face);
        if (currentSelection != null && currentSelection.getOrDefault(currentChannel, Set.of()).contains(sink)) {
            PacketDistributor.sendToServer(new CableRemoveConnectionPacket(selectedSource, pos, face, currentChannel));
            return;
        }

        PacketDistributor.sendToServer(new CableAddConnectionPacket(selectedSource, pos, face, currentChannel));
        if (CableConfig.CONFIG.shouldConsumeCables.get()) heldItem.consume(1, player);
    }

    private static void handleCutterUse(final Level level, final BlockPos pos, final Direction face) {
        if (selectedSource == null) {
            selectedSource = pos.immutable();
            changeChannel(level, selectedSource, true);
            syncManager();
            return;
        }

        if (selectedSource.equals(pos)) {
            clearSource();
            return;
        }

        final Map<String, Set<CableNetworkSink>> currentSelection = currentNetwork.get(selectedSource.asLong());
        final CableNetworkSink sink = CableNetworkSink.of(pos, face);
        if (currentSelection != null && currentSelection.getOrDefault(currentChannel, Set.of()).contains(sink)) {
            PacketDistributor.sendToServer(new CableRemoveConnectionPacket(selectedSource, pos, face, currentChannel));
        }
    }

    private static void syncManager() {
        PacketDistributor.sendToServer(CableNetworkRequestSyncPacket.INSTANCE);
        syncCooldown = 20;
    }

    private static void changeChannel(final Level level, final BlockPos pos, final boolean forward) {
        final Block source = level.getBlockState(pos).getBlock();
        currentChannel = source instanceof MultiChannelCableSource channelSource
                ? channelSource.cable$nextChannel(level, pos, currentChannel, forward)
                : CableNetworkManager.WORLD_CHANNEL;

        if (currentChannel == null) {
            currentChannel = CableNetworkManager.WORLD_CHANNEL;
        }

        final Player player = Minecraft.getInstance().player;
        if (player != null) {
            // 普通通道：强制从 CHANNEL_TO_LANG_KEY 映射表中查找
            // 找不到则兜底显示通道原始名称，避免客户端报错
            String langKey = TweakedControllerCableServerHandler.CHANNEL_TO_LANG_KEY
                    .getOrDefault(currentChannel,currentChannel);
            Component displayName = Component.translatable(langKey);
            player.displayClientMessage(
                    Component.translatable("drivebysable.cable.channel.selected", displayName),
                    true
            );
        }
    }

    private static void drawOutlines(
            final Level level,
            final BlockPos selectedSource,
            final Map<Long, Map<String, Set<CableNetworkSink>>> network,
            final String activeChannel
    ) {
        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> entry : network.entrySet()) {
            final BlockPos source = BlockPos.of(entry.getKey());
            final Map<String, Set<CableNetworkSink>> perChannel = entry.getValue();

            if (selectedSource != null && source.equals(selectedSource)) {
                for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : perChannel.entrySet()) {
                    final boolean active = channelEntry.getKey().equals(activeChannel);
                    for (final CableNetworkSink sink : channelEntry.getValue()) {
                        drawConnection(
                                level,
                                source,
                                BlockPos.of(sink.position()),
                                Direction.from3DDataValue(sink.direction()),
                                active ? LineColor.SINK.SELECTED.getColor() : LineColor.SINK.SAME_SOURCE_DIFFERENT_CHANNEL.getColor(),
                                active ? LineColor.CABLE.SELECTED.getColor() : LineColor.CABLE.SAME_SOURCE_DIFFERENT_CHANNEL.getColor()
                        );
                    }
                }
            } else {
                drawOutline(level, source, LineColor.SOURCE.SAME_NETWORK.getColor());
            }
        }
    }

    private static void drawConnection(
            final Level level,
            final BlockPos start,
            final BlockPos end,
            final Direction direction,
            final int faceColor,
            final int cableColor
    ) {
        drawOutlineFace(end, direction, faceColor);
        Outliner.getInstance()
                .showLine(
                        net.createmod.catnip.data.Pair.of("cableConnection", net.createmod.catnip.data.Pair.of(end, direction)),
                        Vec3.atCenterOf(start),
                        Vec3.atCenterOf(end).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5D))
                )
                .colored(cableColor);
    }

    private static void drawOutlineFace(final BlockPos pos, final Direction direction, final int color) {
        Outliner.getInstance()
                .showAABB(net.createmod.catnip.data.Pair.of("cableFace", BlockFace.of(pos, direction)), FaceOutlines.getOutline(direction).move(pos))
                .colored(color)
                .lineWidth(0.0625F);
    }

    private static void drawOutline(final Level level, final BlockPos pos, final int color) {
        final BlockState state = level.getBlockState(pos);
        final AABB box = state.getShape(level, pos).isEmpty() ? UNIT_CUBE : state.getShape(level, pos).bounds();
        Outliner.getInstance()
                .showAABB(net.createmod.catnip.data.Pair.of("cableBlock", pos), box.move(pos))
                .colored(color)
                .lineWidth(0.0625F);
    }

    private static void notifyPlayer(final Player player, final String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static int countConnections(final Map<Long, Map<String, Set<CableNetworkSink>>> network) {
        int count = 0;
        for (final Map<String, Set<CableNetworkSink>> perChannel : network.values()) {
            for (final Set<CableNetworkSink> sinks : perChannel.values()) {
                count += sinks.size();
            }
        }
        return count;
    }

    private interface LineColor {
        int getColor();

        enum SINK implements LineColor {
            SELECTED(Mode.DEPOSIT.getColor()),
            SAME_SOURCE_DIFFERENT_CHANNEL(ChatFormatting.DARK_GRAY.getColor());

            private final int color;

            SINK(final int color) {
                this.color = color;
            }

            @Override
            public int getColor() {
                return color;
            }
        }

        enum SOURCE implements LineColor {
            SELECTED(Mode.TAKE.getColor()),
            SAME_NETWORK(0x5773d8);

            private final int color;

            SOURCE(final int color) {
                this.color = color;
            }

            @Override
            public int getColor() {
                return color;
            }
        }

        enum CABLE implements LineColor {
            SELECTED(Color.RED.getRGB()),
            SAME_SOURCE_DIFFERENT_CHANNEL(ChatFormatting.DARK_GRAY.getColor());

            private final int color;

            CABLE(final int color) {
                this.color = color;
            }

            @Override
            public int getColor() {
                return color;
            }
        }
    }
}