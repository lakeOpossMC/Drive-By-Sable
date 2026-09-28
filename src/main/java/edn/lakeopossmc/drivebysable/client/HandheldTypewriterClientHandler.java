package edn.lakeopossmc.drivebysable.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler;
import dev.simulated_team.simulated.index.SimSoundEvents;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.HandheldTypewriterLecternBlockEntity;
import edn.lakeopossmc.drivebysable.client.render.HandheldTypewriterItemRenderer;
import edn.lakeopossmc.drivebysable.compat.CableTypewriterHubServerHandler;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import edn.lakeopossmc.drivebysable.menu.HandheldTypewriterMenu;
import edn.lakeopossmc.drivebysable.network.HandheldTypewriterBindPacket;
import edn.lakeopossmc.drivebysable.network.HandheldTypewriterInputPacket;
import edn.lakeopossmc.drivebysable.network.HandheldTypewriterStopLecternPacket;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// --- CLIENT SIDE OF THE HANDHELD TYPEWRITER CONTROLLER --- //
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class HandheldTypewriterClientHandler {

    public enum Mode {
        IDLE,
        ACTIVE,
        BIND
    }

    private static final boolean SIMULATED_LOADED = ModList.get().isLoaded("simulated");
    private static final int PACKET_RATE = 5;
    private static final int BIND_OUTLINE_COLOR = 0xB73C2D;

    private static Mode mode = Mode.IDLE;
    private static BlockPos lecternPos;
    private static BlockPos bindPos;
    private static InteractionHand bindHand = InteractionHand.MAIN_HAND;
    private static final Set<Integer> pressed = new HashSet<>();
    private static int packetCooldown;
    private static int bindMessageCooldown;
    private static boolean handheldScreenOpen;
    private static int handheldScreenPending;

    private HandheldTypewriterClientHandler() {
    }

    //#region // --- MODE CHANGES --- //
    public static Mode getMode() {
        return mode;
    }

    // * Active from the hand rather than from a lectern
    public static boolean isActiveInHand() {
        return mode == Mode.ACTIVE && lecternPos == null;
    }

    // * Keyboard keys currently held down on the controller
    public static Set<Integer> getPressedKeys() {
        return Collections.unmodifiableSet(pressed);
    }

    public static void toggle() {
        if (mode == Mode.IDLE) {
            mode = Mode.ACTIVE;
            lecternPos = null;
        } else {
            stop();
        }
    }

    public static void startBind(final BlockPos pos, final InteractionHand hand) {
        if (mode != Mode.IDLE) {
            stop();
        }
        mode = Mode.BIND;
        bindPos = pos.immutable();
        bindHand = hand;
        bindMessageCooldown = 0;
    }

    public static void activateInLectern(final BlockPos pos) {
        if (mode == Mode.IDLE) {
            mode = Mode.ACTIVE;
            lecternPos = pos.immutable();
        }
    }

    public static void deactivateInLectern() {
        if (mode == Mode.ACTIVE && lecternPos != null) {
            stop();
        }
    }

    @SubscribeEvent
    public static void onInteraction(final InputEvent.InteractionKeyMappingTriggered event) {
        if (SIMULATED_LOADED && event.isUseItem()) {
            deactivateInLectern();
        }
    }

    // * Called every client tick by the lectern block entity
    public static void onLecternTick(final BlockPos pos, final UUID user, final UUID prevUser) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (user == null && player.getUUID().equals(prevUser)) {
            deactivateInLectern();
        } else if (prevUser == null && player.getUUID().equals(user)) {
            activateInLectern(pos);
        }
    }

    // * Lets go of everything the server still thinks is held
    private static void stop() {
        if (!pressed.isEmpty()) {
            PacketDistributor.sendToServer(new HandheldTypewriterInputPacket(List.copyOf(pressed), false, Optional.ofNullable(lecternPos)));
        }
        if (lecternPos != null) {
            PacketDistributor.sendToServer(new HandheldTypewriterStopLecternPacket(lecternPos));
        }

        pressed.clear();
        HandheldTypewriterItemRenderer.resetKeys();
        packetCooldown = 0;
        lecternPos = null;
        bindPos = null;
        mode = Mode.IDLE;
    }
    //#endregion

    //#region // --- KEY CAPTURE --- //
    // * Called by MixinKeyboardHandler before Minecraft sees the key
    public static boolean onRawKey(final int key, final int scanCode, final int action) {
        if (!SIMULATED_LOADED || mode == Mode.IDLE) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return false;
        }

        if (mode == Mode.BIND) {
            if (action == GLFW.GLFW_RELEASE) {
                return false;
            }
            if (action == GLFW.GLFW_REPEAT) {
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE && bindPos != null) {
                PacketDistributor.sendToServer(new HandheldTypewriterBindPacket(bindPos, key, bindHand == InteractionHand.OFF_HAND));
                status(Component.translatable("drivebysable.handheld_typewriter.key_bound",
                        InputConstants.getKey(key, scanCode).getDisplayName()));
            } else {
                status(Component.translatable("drivebysable.handheld_typewriter.bind_cancelled"));
            }
            bindPos = null;
            mode = Mode.IDLE;
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }

        if (lecternPos != null && minecraft.options.keyUse.matches(key, scanCode)) {
            return false;
        }

        final ItemStack controller = currentController(minecraft);
        if (controller.isEmpty()) {
            return false;
        }

        if (action == GLFW.GLFW_REPEAT) {
            return true;
        }

        if (action == GLFW.GLFW_PRESS) {
            if (pressed.add(key)) {
                if (isCaptured(minecraft, controller, key)) {
                    PacketDistributor.sendToServer(new HandheldTypewriterInputPacket(List.of(key), true, Optional.ofNullable(lecternPos)));
                    packetCooldown = PACKET_RATE;
                }
                SimSoundEvents.LINKED_TYPEWRITER_TAP.playAt(minecraft.player.level(), minecraft.player.blockPosition(), 1.0F, 1.0F, true);
            }
            return true;
        }

        // * Only swallow releases of keys the controller took, anything held from before lets go normally
        if (!pressed.remove(key)) {
            return false;
        }
        if (isCaptured(minecraft, controller, key)) {
            PacketDistributor.sendToServer(new HandheldTypewriterInputPacket(List.of(key), false, Optional.ofNullable(lecternPos)));
        }
        SimSoundEvents.LINKED_TYPEWRITER_UNTAP.playAt(minecraft.player.level(), minecraft.player.blockPosition(), 1.0F, 1.0F, true);
        return true;
    }

    // * A bound key, or any typewriter channel key when a hub or lectern is listening
    private static boolean isCaptured(final Minecraft minecraft, final ItemStack controller, final int key) {
        if (HandheldTypewriterData.readEntries(controller, minecraft.player.registryAccess()).getEntry(key) != null) {
            return true;
        }
        final boolean channelsListening = lecternPos != null || HubItem.getHubPos(controller).isPresent();
        return channelsListening && CableTypewriterHubServerHandler.KEY_TO_CHANNEL.containsKey(key);
    }

    // * Held keys that actually do something
    private static List<Integer> heldSignalKeys(final Minecraft minecraft) {
        final ItemStack controller = currentController(minecraft);
        if (controller.isEmpty()) {
            return List.of();
        }
        return pressed.stream().filter(key -> isCaptured(minecraft, controller, key)).toList();
    }
    //#endregion

    //#region // --- HANDHELD SCREEN --- //
    public static void onHandheldScreenOpening() {
        handheldScreenPending = 40;
    }

    // * Fallback for a close that skipped the screen's own onClose
    private static void releaseDetachedTypewriter(final Minecraft minecraft) {
        final boolean handheldScreenShowing = minecraft.screen instanceof final MenuAccess<?> access
                && access.getMenu() instanceof HandheldTypewriterMenu;

        // * Only armed once the handheld screen has really been seen
        if (handheldScreenShowing) {
            handheldScreenOpen = true;
            handheldScreenPending = 0;
            return;
        }
        if (handheldScreenPending > 0) {
            if (--handheldScreenPending == 0
                    && LinkedTypewriterInteractionHandler.getMode() == LinkedTypewriterInteractionHandler.Mode.SCREEN_BINDING) {
                LinkedTypewriterInteractionHandler.setMode(LinkedTypewriterInteractionHandler.Mode.IDLE);
            }
            return;
        }
        if (!handheldScreenOpen) {
            return;
        }
        handheldScreenOpen = false;
        LinkedTypewriterInteractionHandler.associateTypewriter(null);
        if (LinkedTypewriterInteractionHandler.getMode() == LinkedTypewriterInteractionHandler.Mode.SCREEN_BINDING) {
            LinkedTypewriterInteractionHandler.setMode(LinkedTypewriterInteractionHandler.Mode.IDLE);
        }
    }
    //#endregion

    //#region // --- TICK --- //
    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        if (!SIMULATED_LOADED) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        HandheldTypewriterItemRenderer.tick();
        releaseDetachedTypewriter(minecraft);
        if (mode == Mode.IDLE) {
            return;
        }

        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) {
            stop();
            return;
        }

        if (mode == Mode.BIND) {
            tickBind(minecraft, player);
            return;
        }

        // * Lost the controller, or walked away from the lectern
        if (lecternPos != null) {
            if (!(minecraft.level.getBlockEntity(lecternPos) instanceof final HandheldTypewriterLecternBlockEntity lectern)
                    || !lectern.isUsedBy(player)) {
                deactivateInLectern();
                return;
            }
        } else if (currentController(minecraft).isEmpty()) {
            stop();
            return;
        }

        // * Any screen, including the pause menu from Escape, puts the controller down
        if (minecraft.screen != null) {
            stop();
            return;
        }

        if (packetCooldown > 0) {
            packetCooldown--;
        }
        if (packetCooldown == 0 && !pressed.isEmpty()) {
            final List<Integer> held = heldSignalKeys(minecraft);
            if (!held.isEmpty()) {
                PacketDistributor.sendToServer(new HandheldTypewriterInputPacket(held, true, Optional.ofNullable(lecternPos)));
            }
            packetCooldown = PACKET_RATE;
        }
    }

    private static void tickBind(final Minecraft minecraft, final LocalPlayer player) {
        if (bindPos == null || !HandheldTypewriterData.isController(player.getItemInHand(bindHand)) || minecraft.screen != null) {
            bindPos = null;
            mode = Mode.IDLE;
            return;
        }

        final VoxelShape shape = minecraft.level.getBlockState(bindPos).getShape(minecraft.level, bindPos);
        if (!shape.isEmpty()) {
            Outliner.getInstance().showAABB("handheld_typewriter_bind", shape.bounds().move(bindPos))
                    .colored(BIND_OUTLINE_COLOR)
                    .lineWidth(1 / 16f);
        }

        if (bindMessageCooldown-- <= 0) {
            status(Component.translatable("drivebysable.handheld_typewriter.bind_mode").withStyle(ChatFormatting.GOLD));
            bindMessageCooldown = 40;
        }
    }
    //#endregion

    private static ItemStack currentController(final Minecraft minecraft) {
        if (lecternPos != null) {
            return minecraft.level != null
                    && minecraft.level.getBlockEntity(lecternPos) instanceof final HandheldTypewriterLecternBlockEntity lectern
                    ? lectern.getController()
                    : ItemStack.EMPTY;
        }

        final LocalPlayer player = minecraft.player;
        if (HandheldTypewriterData.isController(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        if (HandheldTypewriterData.isController(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static void status(final Component message) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }
}