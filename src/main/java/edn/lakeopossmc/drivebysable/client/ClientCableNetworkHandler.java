package edn.lakeopossmc.drivebysable.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import edn.lakeopossmc.drivebysable.CableBlocks;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.client.screen.ChannelQuickSelectScreen;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.ChannelGroupedCableSource;
import edn.lakeopossmc.drivebysable.mixin.client.MixinGuiOverlayMessageAccessor;
import edn.lakeopossmc.drivebysable.cable.ModuleSinkTarget;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.cable.SubTargetCableEndpoint;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.compat.keytranslator.TweakedKeybindResolver;
import edn.lakeopossmc.drivebysable.items.CableItem;
import edn.lakeopossmc.drivebysable.items.CableCutterItem;
import edn.lakeopossmc.drivebysable.mixinducks.TweakedControllerDuck;
import edn.lakeopossmc.drivebysable.network.CableAddConnectionPacket;
import edn.lakeopossmc.drivebysable.network.CableNetworkRequestSyncPacket;
import edn.lakeopossmc.drivebysable.network.CableSelectionStatePacket;
import edn.lakeopossmc.drivebysable.network.MovementKeybindsPacket;
import edn.lakeopossmc.drivebysable.network.TweakedKeybindsPacket;
import edn.lakeopossmc.drivebysable.network.CableRemoveConnectionPacket;
import edn.lakeopossmc.drivebysable.util.BlockFace;
import edn.lakeopossmc.drivebysable.util.CableOutlineBox;
import edn.lakeopossmc.drivebysable.util.FaceOutlines;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// --- CLIENT SIDE CABLE TOOL LOGIC --- //
// * Handles selection, connections, outlines, and hover tips
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class ClientCableNetworkHandler {
    private static final AABB UNIT_CUBE = AABB.unitCubeFromLowerCorner(Vec3.ZERO);
    private static final Map<Long, Map<String, Set<CableNetworkSink>>> EMPTY_NETWORK = Map.of();
    private static final float OUTLINE_LINE_WIDTH = 0.0625F;
    // * Hover outline colour when the thing under the crosshair is further from the
    // * selected source than the configured range allows
    private static final int OUT_OF_RANGE_COLOR = 0xFF4444;

    private static final String SOURCE_CHANNEL_MESSAGE = "drivebysable.cable.channel.selected";
    private static final String OUTPUT_CHANNEL_MESSAGE = "drivebysable.cable.output_channel.selected";

    // * Keeps the persistent channel readout off the action bar for a moment so errors
    // * and toggles are not overwritten by the next tick's redraw
    private static final int MESSAGE_HOLD_TICKS = 60;

    // * Matches CableHoverTip.FADE_GRACE_TICKS. Gui fades over its last twenty ticks, so
    // * this both starts the fade and sets its length
    private static final int READOUT_FADE_TICKS = 15;
    private static int messageHoldTicks;

    // * Whether the action bar is currently ours, so it is only cleared once on exit
    private static boolean showingChannelReadout;
    // * Module boxes take their thickness from RenderType.lines

    private static Map<Long, Map<String, Set<CableNetworkSink>>> currentNetwork = EMPTY_NETWORK;
    private static BlockPos selectedSource;
    // * Null when the whole block is the source
    private static String selectedSourceModule;

    // * Which slice of a grouped source the cable is scrolling through
    private static String currentChannelGroup;

    private static long lastCancelTick = Long.MIN_VALUE;

    // * Last value sent to the server, so the packet only goes on a change
    private static boolean selectionReported;
    private static String currentChannel = CableNetworkManager.WORLD_CHANNEL;

    // * An output module with more than one channel waits here
    private static BlockPos armedSinkPos;
    private static String armedSinkModule;
    private static String armedSinkChannel;

    // * Module boxes are not drawn from here
    private static final Map<BlockPos, Map<String, Integer>> moduleOutlines = new LinkedHashMap<>();

    // * Toggled by an optional keybind
    private static boolean hideInactiveChannels;

    private static int syncCooldown;
    private static String pendingSchematicSyncReason;
    private static final List<ScheduledFlash> scheduledFlashes = new ArrayList<>();

    private ClientCableNetworkHandler() {
    }

    @SubscribeEvent
    public static void onWorldUnload(final LevelEvent.Unload event) {
        clearSource();
    }

    //#region // --- MAIN CLICK HANDLING --- //
    // * Cable and non shift cutter fully take over the click
    // * Linked controller items are blocked from opening hub menus
    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final Item eventItem = event.getItemStack().getItem();
        final BlockState hitBlock = event.getLevel().getBlockState(event.getHitVec().getBlockPos());
        final Player eventPlayer = event.getEntity();

        final boolean isCutter = eventItem instanceof CableCutterItem;
        final boolean cutterShiftDown = isCutter && eventPlayer != null && eventPlayer.isShiftKeyDown();

        // * A sneak click mid-selection backs out instead of disconnecting
        final boolean cutterCancelling = isCutter && canCancelChannelSelect(eventPlayer);

        if (eventItem instanceof CableItem || isCutter) {
            event.setUseBlock(TriState.FALSE);
        }
        if (isCutter && (!cutterShiftDown || cutterCancelling)) {
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
            if (cancelChannelSelect(player)) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }

            final boolean acted = handleCableUse(player, heldItem, level, pos, face);
            event.setCancellationResult(acted ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // * Checked before the shift branch below
        if (heldItem.is(CableItems.CABLE_CUTTER.get()) && cancelChannelSelect(player)) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (heldItem.is(CableItems.CABLE_CUTTER.get()) && !cutterShiftDown) {
            final boolean acted = handleCutterUse(player, level, pos, face);
            event.setCancellationResult(acted ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (!event.getSide().isClient()) {
            return;
        }

        final Player player = event.getEntity();
        if (player == null || player.isSpectator() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!event.getItemStack().is(CableItems.CABLE.get())
                && !event.getItemStack().is(CableItems.CABLE_CUTTER.get())) {
            return;
        }

        if (cancelChannelSelect(player)) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    // * Drops the source as well as any armed output
    public static boolean canCancelChannelSelect(final Player player) {
        if (player == null || !player.isShiftKeyDown()) {
            return false;
        }

        return selectedSource != null || justCancelled(player);
    }

    private static boolean justCancelled(final Player player) {
        return player.level().getGameTime() == lastCancelTick;
    }

    private static boolean cancelChannelSelect(final Player player) {
        // * Only the pass that still sees a selection does the work
        if (!player.isShiftKeyDown() || selectedSource == null) {
            return false;
        }

        lastCancelTick = player.level().getGameTime();

        clearSource();
        CableHoverTip.clear();
        clearChannelReadout();

        messageHoldTicks = MESSAGE_HOLD_TICKS;
        player.displayClientMessage(
                Component.translatable("drivebysable.cable.selection_cancelled")
                        .withStyle(ChatFormatting.GRAY), true);

        player.level().playSound(
                player,
                player.blockPosition(),
                SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.BLOCKS,
                0.75F,
                1.0F
        );
        return true;
    }
    //#endregion

    //#region // --- MODAL SCROLL --- //
    // * Exactly one channel list is live at a time
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

        final Level level = player.level();

        // * Ctrl moves between groups, plain scroll moves within one
        if (armedSinkPos == null && net.minecraft.client.gui.screens.Screen.hasControlDown()
                && cycleChannelGroup(level, player, delta > 0)) {
            event.setCanceled(true);
            return;
        }

        if (armedSinkPos != null) {
            changeArmedSinkChannel(level, player, delta > 0);
        } else {
            changeChannel(level.getBlockState(selectedSource).getBlock(), delta > 0);
        }
        event.setCanceled(true);
    }
    //#endregion

    //#region // --- MAIN CLIENT TICK --- //
    // * Keeps mirror synced, draws outlines, shows tip
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        final Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        // * Runs regardless of held item so a flash finishes even after switching tools
        if (!scheduledFlashes.isEmpty()) {
            final Iterator<ScheduledFlash> flashIterator = scheduledFlashes.iterator();
            while (flashIterator.hasNext()) {
                final ScheduledFlash flash = flashIterator.next();
                if (--flash.ticksRemaining <= 0) {
                    flash.action.run();
                    flashIterator.remove();
                }
            }
        }

        final ItemStack mainHand = player.getMainHandItem();
        final boolean holdingCableTool = mainHand.is(CableItems.CABLE.get()) || mainHand.is(CableItems.CABLE_CUTTER.get());
        final boolean holdingClipboard = AllBlocks.CLIPBOARD.isIn(mainHand);

        // * Only meaningful while a source is selected
        while (CableKeyMappings.HIDE_INACTIVE_CHANNELS.consumeClick()) {
            if (!holdingCableTool || selectedSource == null) {
                continue;
            }

            hideInactiveChannels = !hideInactiveChannels;
            messageHoldTicks = MESSAGE_HOLD_TICKS;
            player.displayClientMessage(
                    Component.translatable(
                            "drivebysable.cable_actions.inactive_channels",
                            Component.translatable(hideInactiveChannels
                                            ? "drivebysable.cable_actions.hidden"
                                            : "drivebysable.cable_actions.shown")
                                    .withStyle(hideInactiveChannels ? ChatFormatting.RED : ChatFormatting.GREEN)
                    ).withStyle(ChatFormatting.GRAY),
                    true
            );

            // * Local only, nobody else needs to hear a view filter
            final BlockPos soundPos = player.blockPosition();
            player.level().playLocalSound(
                    soundPos.getX() + 0.5,
                    soundPos.getY() + 0.5,
                    soundPos.getZ() + 0.5,
                    hideInactiveChannels
                            ? SoundEvents.COPPER_BULB_TURN_OFF
                            : SoundEvents.COPPER_BULB_TURN_ON,
                    SoundSource.PLAYERS,
                    0.7F,
                    1.0F,
                    false
            );
        }

        // * Setup mode only: without a selected source there is no channel list to offer
        while (CableKeyMappings.CHANNEL_QUICK_SELECT.consumeClick()) {
            if (!holdingCableTool || selectedSource == null) {
                continue;
            }
            openChannelQuickSelect(player);
        }

        // * Clipboard needed for the empty source copy check
        if (holdingCableTool || holdingClipboard || pendingSchematicSyncReason != null) {
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
        }

        // * Clipboard needs sync request
        if ((holdingCableTool || holdingClipboard) && --syncCooldown <= 0) {
            syncManager();
            if (holdingClipboard) {
                sendMovementKeybinds();
                if (ModList.get().isLoaded("create_tweaked_controllers")) {
                    sendTweakedKeybinds();
                }
            }
        }

        if (!holdingCableTool) {
            moduleOutlines.clear();
            CableHoverTip.clear();
            clearChannelReadout();
            return;
        }

        // * Rebuilt every tick
        CableHoverTip.clear();
        if (mainHand.is(CableItems.CABLE.get())) {
            showCableHoverTip(minecraft, level, player);
        } else if (mainHand.is(CableItems.CABLE_CUTTER.get())) {
            showCableCutterHoverTip(minecraft, level, player);
        }

        if (messageHoldTicks > 0) {
            messageHoldTicks--;
        } else if (selectedSource != null) {
            player.displayClientMessage(armedSinkPos != null
                    ? channelMessage(OUTPUT_CHANNEL_MESSAGE, armedSinkChannel)
                    : channelMessage(SOURCE_CHANNEL_MESSAGE, currentChannel), true);
            showingChannelReadout = true;
        } else {
            clearChannelReadout();
        }

        // * Rebuilt from scratch each tick
        moduleOutlines.clear();
        sinkOutlined.clear();

        if (selectedSource != null) {
            if (!claimedByDrive(selectedSource, selectedSourceModule)) {
                drawSourceOutline(level, selectedSource, selectedSourceModule, LineColor.SOURCE.SELECTED.getColor());
            }
        }

        drawOutlines(level, selectedSource, currentNetwork, currentChannel);

        // * Same treatment for a module under the crosshair
        if (minecraft.hitResult instanceof final BlockHitResult hover
                && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            final BlockPos hoverPos = hover.getBlockPos();
            final String hoverModule = pickSubTarget(level, hoverPos, player);
            final boolean cutterInHand = mainHand.is(CableItems.CABLE_CUTTER.get());
            if (hoverModule != null
                    && drivebysable$hoverInvalid(level, hoverPos, hover.getDirection(), player, cutterInHand)) {
                drawModuleOutline(level, hoverPos, hoverModule, "cableOutOfRange", OUT_OF_RANGE_COLOR);
            }
        }

        // * Last, so it wins draw order
        if (armedSinkPos != null) {
            if (armedSinkModule == null) {
                drawOutline(level, armedSinkPos, LineColor.SINK.SELECTED.getColor(), true);
            } else {
                drawModuleOutline(level, armedSinkPos, armedSinkModule, "cableArmedSink", LineColor.SINK.SELECTED.getColor(), true);
            }
        }
    }
    //#endregion

    //#region // --- WHITE HITBOX WHILE HOLDING CABLE --- //
    @SubscribeEvent
    public static void onRenderBlockHighlight(final RenderHighlightEvent.Block event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || player.isSpectator()) {
            return;
        }

        final boolean holdingCutter = player.getMainHandItem().is(CableItems.CABLE_CUTTER.get());
        if (!player.getMainHandItem().is(CableItems.CABLE.get()) && !holdingCutter) {
            return;
        }

        final Level level = minecraft.level;
        final BlockPos pos = event.getTarget().getBlockPos();
        if (level == null || !level.getWorldBorder().isWithinBounds(pos)) {
            return;
        }

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();

        // * Modules are outlined by the panel renderer
        if (pickSubTarget(level, pos, player) != null) {
            event.setCanceled(true);
            return;
        }

        final VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

        if (drivebysable$hoverInvalid(level, pos, event.getTarget().getDirection(), player, holdingCutter)) {
            renderShapeEdges(shape, poseStack, event.getMultiBufferSource().getBuffer(RenderType.lines()), OUT_OF_RANGE_COLOR, 0.6F);
        } else {
            TrackBlockOutline.renderShape(shape, poseStack, event.getMultiBufferSource().getBuffer(RenderType.lines()), true);
        }

        event.setCanceled(true);
        poseStack.popPose();
    }

    // * Only meaningful once a source is picked
    private static boolean isHoverBlocked(final Level level, final BlockPos pos, final Player player) {
        // * Bare surface of a module bearing block is never a valid target
        if (missingRequiredSubTarget(level, pos, player)) {
            return true;
        }

        if (selectedSource == null) {
            // * A module that cannot act as a Source is refused
            final String subTarget = pickSubTarget(level, pos, player);
            if (subTarget != null && !isSourceSubTarget(level, pos, subTarget)) {
                return true;
            }

            return CableNetworkManager.wouldExceedSourceLimit(level, pos);
        }

        if (CableNetworkManager.checkRange(level, selectedSource, pos).blocked()) {
            return true;
        }

        // * A full channel refuses every new output
        return CableNetworkManager.wouldExceedSinkLimit(level, selectedSource, currentChannel)
                && !activeChannelHasSinkAt(pos);
    }

    // * Which rule applies depends on the tool
    private static boolean drivebysable$hoverInvalid(
            final Level level,
            final BlockPos pos,
            final Direction face,
            final Player player,
            final boolean cutter
    ) {
        return cutter
                ? drivebysable$cutterHoverInvalid(level, pos, face, player)
                : isHoverBlocked(level, pos, player);
    }

    // * Mirrors the cutter tip exactly
    private static boolean drivebysable$cutterHoverInvalid(
            final Level level,
            final BlockPos pos,
            final Direction face,
            final Player player
    ) {
        if (missingRequiredSubTarget(level, pos, player)) {
            return true;
        }

        // * Sneak clears the whole target
        if (player.isShiftKeyDown() || selectedSource == null) {
            return !hasConnections(pos);
        }

        // * The source itself stays valid: it is how you leave select mode
        final String subTarget = pickSubTarget(level, pos, player);
        if (selectedSource.equals(pos) && Objects.equals(selectedSourceModule, subTarget)) {
            return false;
        }

        return !drivebysable$isDisconnectable(level, pos, face, subTarget);
    }

    // * Does the active channel already drive something at this position?
    private static boolean activeChannelHasSinkAt(final BlockPos pos) {
        final long posKey = pos.asLong();
        return currentNetwork
                .getOrDefault(selectedSource.asLong(), Map.of())
                .getOrDefault(currentChannel, Set.of())
                .stream()
                .anyMatch(sink -> sink.position() == posKey);
    }

    // * Edges of a voxel shape as coloured lines
    private static void renderShapeEdges(
            final VoxelShape shape,
            final PoseStack poseStack,
            final VertexConsumer buffer,
            final int color,
            final float alpha
    ) {
        final float red = ((color >> 16) & 0xFF) / 255.0F;
        final float green = ((color >> 8) & 0xFF) / 255.0F;
        final float blue = (color & 0xFF) / 255.0F;
        final PoseStack.Pose pose = poseStack.last();

        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            final float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-5F) {
                return;
            }

            dx /= length;
            dy /= length;
            dz /= length;
            buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                    .setColor(red, green, blue, alpha)
                    .setNormal(pose, dx, dy, dz);
            buffer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                    .setColor(red, green, blue, alpha)
                    .setNormal(pose, dx, dy, dz);
        });
    }

    //#endregion

    public static void clearSource() {
        reportSelection(false);
        moduleOutlines.clear();
        currentNetwork = EMPTY_NETWORK;
        selectedSource = null;
        selectedSourceModule = null;
        currentChannelGroup = null;
        currentChannel = CableNetworkManager.WORLD_CHANNEL;
        syncCooldown = 0;
        clearArmedSink();
    }

    private static void clearArmedSink() {
        armedSinkPos = null;
        armedSinkModule = null;
        armedSinkChannel = null;
    }

    // * Used by CableItem for the enchant glint while a source is selected
    public static boolean isInSetupMode() {
        return selectedSource != null;
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

    //#region // --- SUB TARGET LOOKUPS --- //
    // * Blocks made of separately targetable parts accept connections on those parts only
    private static boolean requiresSubTarget(final Level level, final BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SubTargetCableEndpoint;
    }

    // * True when the crosshair is on bare surface of such a block
    private static boolean missingRequiredSubTarget(final Level level, final BlockPos pos, final Player player) {
        return requiresSubTarget(level, pos) && pickSubTarget(level, pos, player) == null;
    }

    // * Which module the crosshair is on, null on bare surface
    @Nullable
    private static String pickSubTarget(final Level level, final BlockPos pos, final Player player) {
        return level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint
                ? endpoint.cable$pickSubTarget(level, pos, player)
                : null;
    }

    private static List<CableOutlineBox> subTargetOutline(final Level level, final BlockPos pos, @Nullable final String subTarget) {
        if (subTarget == null || !(level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint)) {
            return List.of();
        }
        return endpoint.cable$getSubTargetOutline(level, pos, subTarget);
    }

    @Nullable
    private static String subTargetForChannel(final Level level, final BlockPos pos, final String channel) {
        return level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint
                ? endpoint.cable$subTargetForChannel(level, pos, channel)
                : null;
    }

    private static boolean isSourceSubTarget(final Level level, final BlockPos pos, final String subTarget) {
        return level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint
                && endpoint.cable$isSourceSubTarget(level, pos, subTarget);
    }

    private static boolean isSinkSubTarget(final Level level, final BlockPos pos, final String subTarget) {
        return level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint
                && endpoint.cable$isSinkSubTarget(level, pos, subTarget);
    }

    // * A block that names output channels without being made of parts
    private static boolean isMultiChannelSink(final Level level, final BlockPos pos) {
        return !(level.getBlockState(pos).getBlock() instanceof SubTargetCableEndpoint)
                && !sinkChannelsFor(level, pos, null).isEmpty();
    }

    private static List<String> sinkChannelsFor(final Level level, final BlockPos pos, final String subTarget) {
        return level.getBlockState(pos).getBlock() instanceof final ModuleSinkTarget target
                ? target.cable$getSinkChannels(level, pos, subTarget)
                : List.of();
    }

    // * Where a cable line should start or end
    private static Vec3 anchorOf(final Level level, final BlockPos pos, @Nullable final String subTarget) {
        final List<CableOutlineBox> boxes = subTargetOutline(level, pos, subTarget);
        return boxes.isEmpty() ? Vec3.atCenterOf(pos) : boxes.getFirst().center();
    }
    //#endregion

    //#region // --- CABLE CLICK FLOW --- //
    // * First click picks a source, a later click on the same endpoint deselects
    // * Otherwise the click either toggles a connection outright, or arms a multi channel output
    private static boolean handleCableUse(final Player player, final ItemStack heldItem, final Level level, final BlockPos pos, final Direction face) {
        final String subTarget = pickSubTarget(level, pos, player);

        // * Refuse the bare surface outright
        if (subTarget == null && requiresSubTarget(level, pos)) {
            showInvalidOperationMessage(player, "drivebysable.invalid_op.module_required");
            return false;
        }

        // * Nothing selected yet
        if (selectedSource == null) {
            if (subTarget != null && !isSourceSubTarget(level, pos, subTarget)) {
                showInvalidOperationMessage(player, "drivebysable.invalid_op.not_a_source");
                return false;
            }

            // * Refuse setup mode outright
            if (CableNetworkManager.wouldExceedSourceLimit(level, pos)) {
                showInvalidOperationMessage(player, CableNetworkManager.sourceLimitLangKey(level, pos));
                return false;
            }

            selectedSource = pos.immutable();
            reportSelection(true);
            selectedSourceModule = subTarget;
            clearArmedSink();
            changeChannel(level.getBlockState(pos).getBlock(), true);
            syncManager();
            return true;
        }

        // * Clicking endpoint started from leaves setup mode
        if (selectedSource.equals(pos) && Objects.equals(selectedSourceModule, subTarget)) {
            clearSource();
            return true;
        }

        // * Second click on an armed output confirms whatever channel is showing
        if (armedSinkPos != null && armedSinkPos.equals(pos) && Objects.equals(armedSinkModule, subTarget)) {
            final String confirmed = armedSinkChannel;
            clearArmedSink();
            return toggleConnection(player, heldItem, level, pos, face, confirmed, true);
        }

        // * Either a module on a panel, or a plain block that names its own output channels
        // * The Multi-Channel Cable Bus is the second kind
        if (subTarget != null || isMultiChannelSink(level, pos)) {
            if (subTarget != null && !isSinkSubTarget(level, pos, subTarget)) {
                showInvalidOperationMessage(player, "drivebysable.invalid_op.not_an_output");
                return false;
            }

            final List<String> channels = sinkChannelsFor(level, pos, subTarget);
            if (channels.isEmpty()) {
                showInvalidOperationMessage(player, "drivebysable.invalid_op.no_output_channels");
                return false;
            }

            // * Only one channel to choose from, so there is nothing to arm
            if (channels.size() == 1) {
                clearArmedSink();
                return toggleConnection(player, heldItem, level, pos, face, channels.getFirst(), true);
            }

            armSink(level, player, pos, subTarget, channels);
            return true;
        }

        // * Plain block face
        clearArmedSink();
        return toggleConnection(player, heldItem, level, pos, face, CableNetworkSink.BLOCK_FACE, true);
    }

    // * Same as cable but blocks selecting a source with no connections
    private static boolean handleCutterUse(final Player player, final Level level, final BlockPos pos, final Direction face) {
        final String subTarget = pickSubTarget(level, pos, player);

        // * Refuse the bare surface outright
        if (subTarget == null && requiresSubTarget(level, pos)) {
            showInvalidOperationMessage(player, "drivebysable.invalid_op.module_required");
            return false;
        }

        if (selectedSource == null) {
            if (!hasConnections(pos)) {
                showInvalidOperationMessage(player, "drivebysable.invalid_op.no_connections");
                return false;
            }

            selectedSource = pos.immutable();
            reportSelection(true);
            selectedSourceModule = subTarget;
            clearArmedSink();
            changeChannel(level.getBlockState(pos).getBlock(), true);
            syncManager();
            return true;
        }

        if (selectedSource.equals(pos) && Objects.equals(selectedSourceModule, subTarget)) {
            clearSource();
            return true;
        }

        if (armedSinkPos != null && armedSinkPos.equals(pos) && Objects.equals(armedSinkModule, subTarget)) {
            final String confirmed = armedSinkChannel;
            clearArmedSink();
            return toggleConnection(player, ItemStack.EMPTY, level, pos, face, confirmed, false);
        }

        if (subTarget != null || isMultiChannelSink(level, pos)) {
            // * Cutter only offers the connected channels
            final List<String> channels = connectedSinkChannels(level, pos, subTarget);
            if (channels.isEmpty()) {
                showInvalidOperationMessage(player, "drivebysable.invalid_op.no_output_connections");
                return false;
            }

            if (channels.size() == 1) {
                clearArmedSink();
                return toggleConnection(player, ItemStack.EMPTY, level, pos, face, channels.getFirst(), false);
            }

            armSink(level, player, pos, subTarget, channels);
            return true;
        }

        clearArmedSink();
        return toggleConnection(player, ItemStack.EMPTY, level, pos, face, CableNetworkSink.BLOCK_FACE, false);
    }

    // * Hold an output while the player scrolls its channels
    private static void armSink(
            final Level level,
            final Player player,
            final BlockPos pos,
            final String subTarget,
            final List<String> channels
    ) {
        armedSinkPos = pos.immutable();
        armedSinkModule = subTarget;

        final List<String> connected = connectedSinkChannels(level, pos, subTarget);
        armedSinkChannel = connected.isEmpty() ? channels.getFirst() : connected.getFirst();

        announceChannel(player, OUTPUT_CHANNEL_MESSAGE, armedSinkChannel);
    }

    private static void changeArmedSinkChannel(final Level level, final Player player, final boolean forward) {
        if (!(level.getBlockState(armedSinkPos).getBlock() instanceof final ModuleSinkTarget target)) {
            return;
        }

        final String next = target.cable$nextSinkChannel(level, armedSinkPos, armedSinkModule, armedSinkChannel, forward);
        if (next == null) {
            return;
        }

        armedSinkChannel = next;
        announceChannel(player, OUTPUT_CHANNEL_MESSAGE, armedSinkChannel);
    }

    // * Channels on this module
    private static List<String> connectedSinkChannels(final Level level, final BlockPos pos, final String subTarget) {
        if (selectedSource == null) {
            return List.of();
        }

        final Set<CableNetworkSink> onChannel = currentNetwork
                .getOrDefault(selectedSource.asLong(), Map.of())
                .getOrDefault(currentChannel, Set.of());

        final List<String> connected = new ArrayList<>();
        for (final String channel : sinkChannelsFor(level, pos, subTarget)) {
            if (onChannel.contains(CableNetworkSink.ofModule(pos, channel))) {
                connected.add(channel);
            }
        }
        return connected;
    }

    // * Add when absent, remove when present
    private static boolean toggleConnection(
            final Player player,
            final ItemStack heldItem,
            final Level level,
            final BlockPos pos,
            final Direction face,
            final String sinkChannel,
            final boolean allowAdd
    ) {
        final Map<String, Set<CableNetworkSink>> currentSelection = currentNetwork.get(selectedSource.asLong());
        final CableNetworkSink sink = CableNetworkSink.of(pos, face, sinkChannel);

        if (currentSelection != null && currentSelection.getOrDefault(currentChannel, Set.of()).contains(sink)) {
            if (allowAdd && !CableConfig.CONFIG.allowCableDisconnect.get()) {
                showInvalidOperationMessage(player, "drivebysable.invalid_op.cable_removal_disabled");
                return false;
            }

            PacketDistributor.sendToServer(
                    new CableRemoveConnectionPacket(selectedSource, pos, sink.facing(), currentChannel, sink.sinkChannel())
            );
            return true;
        }

        // * Cutter reached this point without finding a connection to remove
        if (!allowAdd) {
            showInvalidOperationMessage(player, "drivebysable.invalid_op.no_output_connections");
            return false;
        }

        // * Checked here so the player gets the flash and deny sound on the click
        if (selectedSource.equals(pos) && (sinkChannel.isEmpty() || currentChannel.equals(sinkChannel))) {
            showInvalidOperationMessage(player, "drivebysable.invalid_op.same_block");
            return false;
        }

        if (CableNetworkManager.wouldExceedSourceLimit(level, selectedSource)) {
            showInvalidOperationMessage(player, CableNetworkManager.sourceLimitLangKey(level, selectedSource));
            return false;
        }

        if (CableNetworkManager.wouldExceedSinkLimit(level, selectedSource, currentChannel)) {
            showInvalidOperationMessage(player, "drivebysable.invalid_op.too_many_sinks");
            return false;
        }

        final CableNetworkManager.RangeResult range = CableNetworkManager.checkRange(level, selectedSource, pos);
        if (range.blocked()) {
            showInvalidOperationMessage(player, range == CableNetworkManager.RangeResult.CROSS_LEVEL
                    ? "drivebysable.invalid_op.cross_level"
                    : "drivebysable.invalid_op.out_of_range");
            return false;
        }

        PacketDistributor.sendToServer(
                new CableAddConnectionPacket(selectedSource, pos, sink.facing(), currentChannel, sink.sinkChannel())
        );
        if (CableConfig.CONFIG.shouldConsumeCables.get()) {
            heldItem.consume(1, player);
        }
        return true;
    }

    public static boolean hasConnections(final BlockPos pos) {
        final Map<String, Set<CableNetworkSink>> perChannel = currentNetwork.get(pos.asLong());
        return perChannel != null && perChannel.values().stream().anyMatch(sinks -> !sinks.isEmpty());
    }
    //#endregion

    //#region // --- INVALID OP FLASH MESSAGE --- //
    // * Display message in red, then flash white
    public static void showInvalidOperationMessage(final Player player, final String langKey) {
        messageHoldTicks = MESSAGE_HOLD_TICKS;
        player.displayClientMessage(Component.translatable(langKey).withStyle(ChatFormatting.RED), true);
        scheduledFlashes.add(new ScheduledFlash(2, () -> {
            player.displayClientMessage(Component.translatable(langKey).withStyle(ChatFormatting.WHITE), true);
            final BlockPos pos = player.blockPosition();
            player.level().playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    AllSoundEvents.DENY.getMainEvent(), SoundSource.PLAYERS, 1.0F, 0.5F, false);
        }));
        scheduledFlashes.add(new ScheduledFlash(4, () ->
                player.displayClientMessage(Component.translatable(langKey).withStyle(ChatFormatting.RED), true)));
    }

    // * Tiny delayed task, ticked down in onClientTick
    private static final class ScheduledFlash {
        int ticksRemaining;
        final Runnable action;

        ScheduledFlash(final int ticksRemaining, final Runnable action) {
            this.ticksRemaining = ticksRemaining;
            this.action = action;
        }
    }
    //#endregion

    //#region // --- CABLE ACTIONS HOVER TIP --- //
    // * Text tracks the state machine
    private static void showCableHoverTip(final Minecraft minecraft, final Level level, final Player player) {
        final List<MutableComponent> tip = new ArrayList<>();
        tip.add(Component.translatable("drivebysable.cable_actions.header"));

        final HitResult hitResult = minecraft.hitResult;
        final boolean hitBlock = hitResult instanceof BlockHitResult && hitResult.getType() == HitResult.Type.BLOCK;

        // * Says why the bare panel is refused, before the click rather than after.
        // * Checked ahead of both branches since it applies with or without a source
        if (hitBlock && missingRequiredSubTarget(level, ((BlockHitResult) hitResult).getBlockPos(), player)) {
            tip.add(Component.translatable("drivebysable.cable_actions.module_required")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            CableHoverTip.show(tip);
            return;
        }

        if (selectedSource == null) {
            if (!hitBlock) {
                return;
            }

            final BlockPos entryPos = ((BlockHitResult) hitResult).getBlockPos();
            final String entryModule = pickSubTarget(level, entryPos, player);

            // * Matches the not_a_source refusal, shown before the click rather than after
            if (entryModule != null && !isSourceSubTarget(level, entryPos, entryModule)) {
                tip.add(Component.translatable("drivebysable.cable_actions.invalid_module")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            } else if (CableNetworkManager.wouldExceedSourceLimit(level, entryPos)) {
                // * Say the cap is reached
                tip.add(Component.translatable("drivebysable.cable_actions.source_limit_reached")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            } else {
                tip.add(Component.translatable("drivebysable.cable_actions.enter_setup", Component.keybind("key.use")));
            }

            CableHoverTip.show(tip);
            return;
        }

        // * An armed output owns scroll until it is confirmed
        if (armedSinkPos != null) {
            tip.add(Component.translatable("drivebysable.cable_actions.select_output_channel"));
            tip.add(Component.translatable("drivebysable.cable_actions.confirm_output", Component.keybind("key.use")));
            CableHoverTip.show(tip);
            return;
        }

        final BlockPos hitPos = hitBlock ? ((BlockHitResult) hitResult).getBlockPos() : null;
        final String subTarget = hitPos == null ? null : pickSubTarget(level, hitPos, player);

        if (hitPos != null && selectedSource.equals(hitPos) && Objects.equals(selectedSourceModule, subTarget)) {
            tip.add(Component.translatable("drivebysable.cable_actions.exit_setup", Component.keybind("key.use")));
            CableHoverTip.show(tip);
            return;
        }

        tip.add(Component.translatable("drivebysable.cable_actions.select_channel"));

        // * Only worth mentioning when the block actually has groups to move between
        if (hasMultipleChannelGroups(level)) {
            tip.add(Component.translatable(
                    "drivebysable.cable_actions.select_channel_group",
                    drivebysable$ctrlScroll(net.minecraft.client.gui.screens.Screen.hasControlDown())));
        }

        // * Say so before the click
        if (CableNetworkManager.wouldExceedSinkLimit(level, selectedSource, currentChannel)
                && (hitPos == null || !activeChannelHasSinkAt(hitPos))) {
            tip.add(Component.translatable("drivebysable.cable_actions.output_limit_reached")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            CableHoverTip.show(tip);
            return;
        }

        final CableNetworkManager.RangeResult hoverRange = hitPos == null
                ? CableNetworkManager.RangeResult.OK
                : CableNetworkManager.checkRange(level, selectedSource, hitPos);
        if (hoverRange.blocked()) {
            tip.add(Component.translatable(hoverRange == CableNetworkManager.RangeResult.CROSS_LEVEL
                            ? "drivebysable.cable_actions.cross_level"
                            : "drivebysable.cable_actions.out_of_range")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            CableHoverTip.show(tip);
            return;
        }

        // * Flag that this output needs a channel picked
        if (hitPos != null && subTarget != null
                && isSinkSubTarget(level, hitPos, subTarget)
                && sinkChannelsFor(level, hitPos, subTarget).size() > 1) {
            tip.add(Component.translatable("drivebysable.cable_actions.choose_output_channel", Component.keybind("key.use")));
        } else {
            tip.add(Component.translatable(
                    CableConfig.CONFIG.allowCableDisconnect.get()
                            ? "drivebysable.cable_actions.toggle_output"
                            : "drivebysable.cable_actions.add_output",
                    Component.keybind("key.use")
            ));
        }

        // * Last of everything, so it sits directly on top of the channel readout
        addChannelGroupLine(tip, level);
        CableHoverTip.show(tip);
    }
    //#endregion

    // * Cutter equivalent of the cable tip
    private static void showCableCutterHoverTip(final Minecraft minecraft, final Level level, final Player player) {
        final List<MutableComponent> tip = new ArrayList<>();
        tip.add(Component.translatable("drivebysable.cable_cutter_actions.header"));

        final HitResult hitResult = minecraft.hitResult;
        final boolean hitBlock = hitResult instanceof BlockHitResult && hitResult.getType() == HitResult.Type.BLOCK;
        final BlockPos hitPos = hitBlock ? ((BlockHitResult) hitResult).getBlockPos() : null;
        final Direction hitFace = hitBlock ? ((BlockHitResult) hitResult).getDirection() : Direction.UP;
        // * Aiming at nothing says nothing
        if (hitPos == null) {
            return;
        }

        final String subTarget = pickSubTarget(level, hitPos, player);

        //#region // --- SNEAKING --- //
        // * Sneak plus use clears everything on the target
        if (player.isShiftKeyDown()) {
            if (missingRequiredSubTarget(level, hitPos, player)) {
                drivebysable$refuse(tip, "drivebysable.cable_actions.module_required");
                return;
            }

            if (!hasConnections(hitPos)) {
                drivebysable$refuse(tip, "drivebysable.cable_cutter_actions.invalid_source");
                return;
            }

            // * Sneak is already held, so it is shown as satisfied
            tip.add(Component.translatable("drivebysable.cable_cutter_actions.disconnect_all", drivebysable$sneakUse(true)));
            CableHoverTip.show(tip);
            return;
        }
        //#endregion

        //#region // --- BEFORE A SOURCE IS PICKED --- //
        if (selectedSource == null) {
            // * A panel is only addressable by module, so say that
            if (hitPos != null && missingRequiredSubTarget(level, hitPos, player)) {
                drivebysable$refuse(tip, "drivebysable.cable_actions.module_required");
                return;
            }

            if (!hasConnections(hitPos)) {
                drivebysable$refuse(tip, "drivebysable.cable_cutter_actions.invalid_source");
                return;
            }

            tip.add(Component.translatable("drivebysable.cable_cutter_actions.enter_select", Component.keybind("key.use")));
            tip.add(Component.translatable("drivebysable.cable_cutter_actions.disconnect_all", drivebysable$sneakUse(false)));
            CableHoverTip.show(tip);
            return;
        }
        //#endregion

        // * An armed output owns scroll until a second click disconnects it
        if (armedSinkPos != null) {
            tip.add(Component.translatable("drivebysable.cable_actions.select_output_channel"));
            tip.add(Component.translatable("drivebysable.cable_cutter_actions.disconnect_output", Component.keybind("key.use")));
            CableHoverTip.show(tip);
            return;
        }

        // * Back on the source itself
        if (hitPos != null && selectedSource.equals(hitPos) && Objects.equals(selectedSourceModule, subTarget)) {
            tip.add(Component.translatable("drivebysable.cable_cutter_actions.exit_select", Component.keybind("key.use")));
            tip.add(Component.translatable("drivebysable.cable_cutter_actions.disconnect_all", drivebysable$sneakUse(false)));
            CableHoverTip.show(tip);
            return;
        }

        //#region // --- CHOOSING AN OUTPUT TO CUT --- //
        if (hitPos != null && missingRequiredSubTarget(level, hitPos, player)) {
            drivebysable$refuse(tip, "drivebysable.cable_actions.module_required");
            return;
        }

        // * Scrolling stays useful either way
        tip.add(Component.translatable("drivebysable.cable_actions.select_channel"));

        if (!drivebysable$isDisconnectable(level, hitPos, hitFace, subTarget)) {
            drivebysable$refuse(tip, "drivebysable.cable_cutter_actions.invalid_output");
            return;
        }

        tip.add(Component.translatable("drivebysable.cable_cutter_actions.disconnect_output", Component.keybind("key.use")));
        CableHoverTip.show(tip);
        //#endregion
    }

    // * Is there something here the cutter could actually remove right now?
    private static boolean drivebysable$isDisconnectable(
            final Level level,
            final BlockPos pos,
            final Direction face,
            @Nullable final String subTarget
    ) {
        if (subTarget != null) {
            return !connectedSinkChannels(level, pos, subTarget).isEmpty();
        }

        return currentNetwork
                .getOrDefault(selectedSource.asLong(), Map.of())
                .getOrDefault(currentChannel, Set.of())
                .contains(CableNetworkSink.of(pos, face, CableNetworkSink.BLOCK_FACE));
    }

    // * Closes the tip on a red reason line
    private static void drivebysable$refuse(final List<MutableComponent> tip, final String langKey) {
        tip.add(Component.translatable(langKey).withStyle(ChatFormatting.RED));
        CableHoverTip.show(tip);
    }

    // * Sneak plus use, built from the real keybinds
    private static MutableComponent drivebysable$ctrlScroll(final boolean controlHeld) {
        final MutableComponent modifier = Component.literal("Ctrl + Scroll");
        if (controlHeld) {
            modifier.withStyle(ChatFormatting.GREEN);
        }

        return modifier;
    }

    private static MutableComponent drivebysable$sneakUse(final boolean sneakHeld) {
        final MutableComponent sneak = Component.keybind("key.sneak");
        if (sneakHeld) {
            sneak.withStyle(ChatFormatting.GREEN);
        }

        return sneak
                .append(Component.literal(" + ").withStyle(ChatFormatting.RESET))
                .append(Component.keybind("key.use"));
    }

    private static void reportSelection(final boolean selecting) {
        if (selectionReported == selecting) {
            return;
        }

        selectionReported = selecting;
        PacketDistributor.sendToServer(new CableSelectionStatePacket(selecting));
    }

    private static void syncManager() {
        PacketDistributor.sendToServer(CableNetworkRequestSyncPacket.INSTANCE);
        syncCooldown = 20;
    }

    // * Same key mapping fields ControlsUtil reads for the linked controller
    private static void sendMovementKeybinds() {
        final Options options = Minecraft.getInstance().options;
        PacketDistributor.sendToServer(new MovementKeybindsPacket(
                resolveKeycode(options.keyUp),
                resolveKeycode(options.keyDown),
                resolveKeycode(options.keyLeft),
                resolveKeycode(options.keyRight),
                resolveKeycode(options.keyJump),
                resolveKeycode(options.keyShift)
        ));
    }

    // * Only called once the mod is confirmed loaded
    private static void sendTweakedKeybinds() {
        PacketDistributor.sendToServer(new TweakedKeybindsPacket(TweakedKeybindResolver.resolveAll()));
    }

    private static int resolveKeycode(final KeyMapping mapping) {
        final InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM ? key.getValue() : -1;
    }

    // * Scoped to the selected module when the source block has sub targets
    // * Null unless the selected source hands out groups
    private static ChannelGroupedCableSource groupedSource(final Level level) {
        if (level == null || selectedSource == null) {
            return null;
        }

        return level.getBlockState(selectedSource).getBlock() instanceof final ChannelGroupedCableSource grouped
                ? grouped
                : null;
    }

    // * Falls back to the first group
    private static String activeGroup(final Level level) {
        final ChannelGroupedCableSource grouped = groupedSource(level);
        if (grouped == null) {
            return null;
        }

        final List<String> groups = grouped.cable$getChannelGroups(level, selectedSource);
        if (groups.isEmpty()) {
            return null;
        }

        if (currentChannelGroup == null || !groups.contains(currentChannelGroup)) {
            currentChannelGroup = groups.getFirst();
        }

        return currentChannelGroup;
    }

    private static boolean cycleChannelGroup(final Level level, final Player player, final boolean forward) {
        final ChannelGroupedCableSource grouped = groupedSource(level);
        if (grouped == null) {
            return false;
        }

        final List<String> groups = grouped.cable$getChannelGroups(level, selectedSource);
        if (groups.size() <= 1) {
            return false;
        }

        final String current = activeGroup(level);
        final int index = groups.indexOf(current);
        currentChannelGroup = groups.get(Math.floorMod(index + (forward ? 1 : -1), groups.size()));

        // * Land on the first channel of the group the player just moved to
        final List<String> channels = grouped.cable$getGroupChannels(level, selectedSource, currentChannelGroup);
        if (!channels.isEmpty()) {
            currentChannel = channels.getFirst();
        }

        announceChannel(player, SOURCE_CHANNEL_MESSAGE, currentChannel);
        return true;
    }

    // * The channels the cable may reach right now
    private static List<String> selectableChannels(final Level level) {
        final ChannelGroupedCableSource grouped = groupedSource(level);
        if (grouped != null) {
            final String group = activeGroup(level);
            if (group != null) {
                return grouped.cable$getGroupChannels(level, selectedSource, group);
            }
        }

        return level.getBlockState(selectedSource).getBlock() instanceof final MultiChannelCableSource channelSource
                ? channelSource.cable$getChannels(level, selectedSource, selectedSourceModule)
                : List.of();
    }

    private static boolean hasMultipleChannelGroups(final Level level) {
        final ChannelGroupedCableSource grouped = groupedSource(level);
        return grouped != null && grouped.cable$getChannelGroups(level, selectedSource).size() > 1;
    }

    private static void addChannelGroupLine(final List<MutableComponent> tip, final Level level) {
        final ChannelGroupedCableSource grouped = groupedSource(level);
        if (grouped == null) {
            return;
        }

        final String group = activeGroup(level);
        if (group == null) {
            return;
        }

        tip.add(Component.translatable(
                        "drivebysable.cable.channel_group.selected",
                        Component.translatable(grouped.cable$getChannelGroupLangKey(group))
                                .withStyle(ChatFormatting.GREEN))
                .withStyle(ChatFormatting.GRAY));
    }

    private static void changeChannel(final Block source, final boolean forward) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || selectedSource == null) {
            return;
        }

        final List<String> group = groupedSource(level) != null ? selectableChannels(level) : List.of();
        if (!group.isEmpty()) {
            // * Wraps inside the group rather than running into the next one
            final int index = group.indexOf(currentChannel);
            currentChannel = index < 0
                    ? group.getFirst()
                    : group.get(Math.floorMod(index + (forward ? 1 : -1), group.size()));
        } else {
            currentChannel = source instanceof final MultiChannelCableSource channelSource
                    ? channelSource.cable$nextChannel(level, selectedSource, selectedSourceModule, currentChannel, forward)
                    : CableNetworkManager.WORLD_CHANNEL;
        }

        if (currentChannel == null) {
            currentChannel = CableNetworkManager.WORLD_CHANNEL;
        }

        final Player player = Minecraft.getInstance().player;
        if (player != null) {
            announceChannel(player, SOURCE_CHANNEL_MESSAGE, currentChannel);
        }
    }

    private static void clearChannelReadout() {
        if (!showingChannelReadout) {
            return;
        }

        showingChannelReadout = false;

        final Gui gui = Minecraft.getInstance().gui;
        if (gui instanceof final MixinGuiOverlayMessageAccessor accessor) {
            accessor.drivebysable$setOverlayMessageTime(READOUT_FADE_TICKS);
        }
    }

    // * Look up display name, fall back to raw channel id
    private static void announceChannel(final Player player, final String messageKey, final String channel) {
        player.displayClientMessage(channelMessage(messageKey, channel), true);
    }

    private static Component channelMessage(final String messageKey, final String channel) {
        final String langKey = channelLangKey(channel);
        final int channelColor = messageKey.equals(OUTPUT_CHANNEL_MESSAGE)
                ? LineColor.SINK.SELECTED.getColor()
                : LineColor.SOURCE.SELECTED.getColor();

        return Component.translatable(
                        messageKey,
                        Component.translatable(langKey).withStyle(style -> style.withColor(channelColor)))
                .withStyle(ChatFormatting.GRAY);
    }

    //#region // --- CHANNEL QUICK SELECT --- //
    private static void openChannelQuickSelect(final Player player) {
        final Level level = player.level();
        final boolean forOutput = armedSinkPos != null;

        final List<String> ids = forOutput
                ? sinkChannelsFor(level, armedSinkPos, armedSinkModule)
                : selectableChannels(level);

        final ChannelGroupedCableSource grouped = groupedSource(level);
        if (!forOutput && grouped != null) {
            final String group = activeGroup(level);
            if (group != null && !grouped.cable$groupAllowsQuickSelect(level, selectedSource, group)) {
                return;
            }
        }

        // * Silently does nothing when there is nothing
        if (ids.size() <= 1) {
            return;
        }

        final List<ChannelQuickSelectScreen.Channel> entries = new ArrayList<>(ids.size());
        for (final String id : ids) {
            entries.add(new ChannelQuickSelectScreen.Channel(id, displayNameOf(id)));
        }

        final String current = forOutput ? armedSinkChannel : currentChannel;

        Minecraft.getInstance().setScreen(new ChannelQuickSelectScreen(entries, current, chosen -> {
            if (forOutput) {
                armedSinkChannel = chosen;
                announceChannel(player, OUTPUT_CHANNEL_MESSAGE, chosen);
            } else {
                currentChannel = chosen;
                announceChannel(player, SOURCE_CHANNEL_MESSAGE, chosen);
            }
        }));
    }

    // * Sensor Bus channels carry ids rather than names, so they translate too
    private static String channelLangKey(final String channel) {
        final String sensorKey = IntegratedSensorBusBlockEntity.CHANNEL_TO_LANG_KEY.get(channel);
        if (sensorKey != null) {
            return sensorKey;
        }

        return TweakedControllerCableServerHandler.CHANNEL_TO_LANG_KEY.getOrDefault(channel, channel);
    }

    private static String displayNameOf(final String channel) {
        final String langKey = channelLangKey(channel);
        return Component.translatable(langKey).getString();
    }
    //#endregion

    //#region // --- DRAW ALL NETWORK OUTLINES --- //
    // * Selected source gets full connection detail
    // * Everything else just gets a plain box
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
                // * A panel hosts one source per module
                final Map<String, Set<CableNetworkSink>> ownChannels = new LinkedHashMap<>();
                final Set<String> siblingModules = new HashSet<>();

                perChannel.forEach((channel, sinksOnChannel) -> {
                    if (selectedSourceModule == null) {
                        ownChannels.put(channel, sinksOnChannel);
                        return;
                    }

                    final String owner = subTargetForChannel(level, source, channel);
                    if (selectedSourceModule.equals(owner)) {
                        ownChannels.put(channel, sinksOnChannel);
                    } else if (owner != null) {
                        siblingModules.add(owner);
                    }
                });

                // * Track endpoints already used by the active channel
                final Set<CableNetworkSink> activeSinks = new HashSet<>(ownChannels.getOrDefault(activeChannel, Set.of()));

                for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : ownChannels.entrySet()) {
                    final boolean active = channelEntry.getKey().equals(activeChannel);

                    // * The greyed out channels are the ones this hides
                    if (!active && hideInactiveChannels) {
                        continue;
                    }

                    for (final CableNetworkSink sink : channelEntry.getValue()) {
                        if (!active && activeSinks.contains(sink)) {
                            continue;
                        }
                        drawConnection(
                                level,
                                source,
                                sink,
                                channelEntry.getKey(),
                                active ? LineColor.SINK.SELECTED.getColor() : LineColor.SINK.SAME_SOURCE_DIFFERENT_CHANNEL.getColor(),
                                active ? LineColor.CABLE.SELECTED.getColor() : LineColor.CABLE.SAME_SOURCE_DIFFERENT_CHANNEL.getColor()
                        );
                    }
                }

                // * Wired modules elsewhere on this panel are just other sources
                siblingModules.stream()
                        .filter(module -> !claimedByDrive(source, module))
                        .forEach(module -> drawModuleOutline(
                                level,
                                source,
                                module,
                                "cableNetworkSource",
                                LineColor.SOURCE.SAME_NETWORK.getColor()
                        ));
            } else {
                drawUnselectedSource(level, source, perChannel);
            }
        }
    }

    // * Other sources in the network
    // * Whether a drive is already saying something about this source
    private static boolean claimedByDrive(final BlockPos source) {
        return BackupDrivePreview.isOutlining(source) || BackupDriveLoadHighlight.isOutlining(source);
    }

    private static boolean claimedByDrive(final BlockPos source, final String module) {
        if (module == null || module.isEmpty()) {
            return claimedByDrive(source);
        }

        return BackupDrivePreview.isOutliningModule(source, module)
                || BackupDriveLoadHighlight.isOutliningModule(source, module);
    }

    private static void drawUnselectedSource(
            final Level level,
            final BlockPos source,
            final Map<String, Set<CableNetworkSink>> perChannel
    ) {
        final int color = LineColor.SOURCE.SAME_NETWORK.getColor();
        if (!(level.getBlockState(source).getBlock() instanceof SubTargetCableEndpoint)) {
            if (!claimedByDrive(source)) {
                drawOutline(level, source, color);
            }
            return;
        }

        final Set<String> drawn = new HashSet<>();
        for (final String channel : perChannel.keySet()) {
            final String module = subTargetForChannel(level, source, channel);
            if (module != null && drawn.add(module) && !claimedByDrive(source, module)) {
                drawModuleOutline(level, source, module, "cableNetworkSource", color);
            }
        }

        if (drawn.isEmpty()) {
            drawOutline(level, source, color);
        }
    }
    //#endregion

    // * Highlight whichever endpoint the source actually is
    private static void drawSourceOutline(
            final Level level,
            final BlockPos pos,
            @Nullable final String subTarget,
            final int color
    ) {
        if (subTarget == null) {
            drawOutline(level, pos, color);
            return;
        }
        drawModuleOutline(level, pos, subTarget, "cableSourceModule", color);
    }

    private static void drawConnection(
            final Level level,
            final BlockPos start,
            final CableNetworkSink sink,
            final String channel,
            final int faceColor,
            final int cableColor
    ) {
        final BlockPos end = sink.blockPos();
        final Vec3 lineStart = anchorOf(level, start, selectedSourceModule);
        final Vec3 lineEnd;

        if (sink.isModule()) {
            final String module = subTargetForChannel(level, end, sink.sinkChannel());
            final String outlineTarget = module == null ? sink.sinkChannel() : module;
            drawModuleOutline(level, end, outlineTarget, "cableSinkModule:" + channel, faceColor, true);
            lineEnd = anchorOf(level, end, module);
        } else {
            drawOutlineFace(end, sink.facing(), channel, faceColor);
            lineEnd = Vec3.atCenterOf(end).add(Vec3.atLowerCornerOf(sink.facing().getNormal()).scale(0.5D));
        }

        Outliner.getInstance()
                .showLine(
                        net.createmod.catnip.data.Pair.of(
                                "cableConnection",
                                net.createmod.catnip.data.Pair.of(
                                        net.createmod.catnip.data.Pair.of(start, end),
                                        net.createmod.catnip.data.Pair.of(sink.sinkChannel() + "|" + sink.direction(), channel)
                                )
                        ),
                        lineStart,
                        lineEnd
                )
                .colored(cableColor);
    }

    private static void drawOutlineFace(final BlockPos pos, final Direction direction, final String channel, final int color) {
        Outliner.getInstance()
                .showAABB(net.createmod.catnip.data.Pair.of("cableFace", net.createmod.catnip.data.Pair.of(BlockFace.of(pos, direction), channel)), FaceOutlines.getOutline(direction).move(pos))
                .colored(color)
                .lineWidth(OUTLINE_LINE_WIDTH);
    }

    // * Catnip only knows how to draw axis aligned boxes
    private static void drawModuleOutline(
            final Level level,
            final BlockPos pos,
            final String subTarget,
            final String slotTag,
            final int color
    ) {
        drawModuleOutline(level, pos, subTarget, slotTag, color, false);
    }

    private static void drawModuleOutline(
            final Level level,
            final BlockPos pos,
            final String subTarget,
            final String slotTag,
            final int color,
            final boolean asSink
    ) {
        final List<CableOutlineBox> boxes = subTargetOutline(level, pos, subTarget);
        if (boxes.isEmpty()) {
            drawOutline(level, pos, color, asSink);
            return;
        }

        moduleOutlines.computeIfAbsent(pos.immutable(), ignored -> new LinkedHashMap<>()).put(subTarget, color);
    }

    // * The panel renderer asks here for every module box
    public static Map<String, Integer> moduleOutlinesFor(final BlockPos pos) {
        final Map<String, Integer> fromTools = moduleOutlines.getOrDefault(pos, Map.of());
        final Map<String, Integer> fromPreview = BackupDrivePreview.moduleOutlinesFor(pos);
        final Map<String, Integer> fromLoad = BackupDriveLoadHighlight.moduleOutlinesFor(pos);

        if (!fromLoad.isEmpty()) {
            final Map<String, Integer> merged = new LinkedHashMap<>(fromTools);
            merged.putAll(fromPreview);
            merged.putAll(fromLoad);
            return merged;
        }

        if (fromPreview.isEmpty()) {
            return fromTools;
        }
        if (fromTools.isEmpty()) {
            return fromPreview;
        }

        final Map<String, Integer> merged = new LinkedHashMap<>(fromTools);
        merged.putAll(fromPreview);
        return merged;
    }

    public static void renderLocalBox(
            final AABB box,
            final PoseStack poseStack,
            final VertexConsumer buffer,
            final int color,
            final double thickness
    ) {
        final float red = ((color >> 16) & 0xFF) / 255.0F;
        final float green = ((color >> 8) & 0xFF) / 255.0F;
        final float blue = (color & 0xFF) / 255.0F;
        final double half = thickness / 2.0D;
        final PoseStack.Pose pose = poseStack.last();

        final double[] xs = {box.minX, box.maxX};
        final double[] ys = {box.minY, box.maxY};
        final double[] zs = {box.minZ, box.maxZ};

        // * Four bars along each axis, twelve edges in total
        for (final double y : ys) {
            for (final double z : zs) {
                bar(pose, buffer, new AABB(box.minX - half, y - half, z - half, box.maxX + half, y + half, z + half), red, green, blue);
            }
        }
        for (final double x : xs) {
            for (final double z : zs) {
                bar(pose, buffer, new AABB(x - half, box.minY - half, z - half, x + half, box.maxY + half, z + half), red, green, blue);
            }
        }
        for (final double x : xs) {
            for (final double y : ys) {
                bar(pose, buffer, new AABB(x - half, y - half, box.minZ - half, x + half, y + half, box.maxZ + half), red, green, blue);
            }
        }
    }

    // * Six faces of one bar
    private static void bar(
            final PoseStack.Pose pose,
            final VertexConsumer buffer,
            final AABB b,
            final float red,
            final float green,
            final float blue
    ) {
        quad(pose, buffer, red, green, blue,
                b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ);
        quad(pose, buffer, red, green, blue,
                b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.minY, b.maxZ);
        quad(pose, buffer, red, green, blue,
                b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, b.minX, b.minY, b.minZ);
        quad(pose, buffer, red, green, blue,
                b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ);
        quad(pose, buffer, red, green, blue,
                b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ);
        quad(pose, buffer, red, green, blue,
                b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ);
    }

    private static void quad(
            final PoseStack.Pose pose,
            final VertexConsumer buffer,
            final float red,
            final float green,
            final float blue,
            final double x1, final double y1, final double z1,
            final double x2, final double y2, final double z2,
            final double x3, final double y3, final double z3,
            final double x4, final double y4, final double z4
    ) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(red, green, blue, 1.0F);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(red, green, blue, 1.0F);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(red, green, blue, 1.0F);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(red, green, blue, 1.0F);
    }


    // * Uses approximate bounding box
    private static final Set<BlockPos> sinkOutlined = new HashSet<>();

    private static void drawOutline(final Level level, final BlockPos pos, final int color) {
        drawOutline(level, pos, color, false);
    }

    private static void drawOutline(final Level level, final BlockPos pos, final int color, final boolean asSink) {
        if (asSink) {
            sinkOutlined.add(pos.immutable());
        } else if (sinkOutlined.contains(pos)) {
            return;
        }

        final BlockState state = level.getBlockState(pos);
        final AABB box = state.getShape(level, pos).isEmpty() ? UNIT_CUBE : state.getShape(level, pos).bounds();
        Outliner.getInstance()
                .showAABB(net.createmod.catnip.data.Pair.of("cableBlock", pos), box.move(pos))
                .colored(color)
                .lineWidth(OUTLINE_LINE_WIDTH);
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

    // --- COLORS FOR EACH OUTLINE STATE --- //
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