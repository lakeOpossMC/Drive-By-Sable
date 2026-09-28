package edn.lakeopossmc.drivebysable.mixin.client;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen;
import dev.simulated_team.simulated.index.SimGUITextures;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import edn.lakeopossmc.drivebysable.menu.HandheldTypewriterMenu;
import edn.lakeopossmc.drivebysable.network.HandheldTypewriterSavePacket;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;

// --- POINT THE TYPEWRITER SCREEN AT THE HANDHELD CONTROLLER --- //
// * Pseudo since mod may not be loaded
// * Only acts when the open menu is the handheld one
@Pseudo
@Mixin(LinkedTypewriterScreen.class)
public abstract class MixinLinkedTypewriterScreen {

    @Unique private static final int DRIVEBYSABLE$PREVIEW_X = -1;
    @Unique private static final int DRIVEBYSABLE$PREVIEW_Y = -44;
    @Unique private static final double DRIVEBYSABLE$PREVIEW_SCALE = 3.0;

    @Shadow(remap = false) @Final private LinkedTypewriterEntries newEntries;
    @Shadow(remap = false) protected SimGUITextures backgroundMain;

    @Unique
    private boolean drivebysable$isHandheld() {
        return ((MenuAccess<?>) this).getMenu() instanceof HandheldTypewriterMenu;
    }

    @Inject(method = "sendNewKeys", at = @At("HEAD"), cancellable = true, remap = false)
    private void drivebysable$saveToController(final boolean clearAll, final CallbackInfo ci) {
        if (!this.drivebysable$isHandheld()) {
            return;
        }

        if (clearAll) {
            this.newEntries.clearAll();
        }
        PacketDistributor.sendToServer(new HandheldTypewriterSavePacket(new HashMap<>(this.newEntries.getKeyMap()), clearAll));
        ci.cancel();
    }

    // * The screen normally shows the Linked Typewriter block, show the controller item instead
    @Inject(method = "renderTypeWriter", at = @At("HEAD"), cancellable = true, remap = false)
    private void drivebysable$renderController(final GuiGraphics graphics, final int x, final int y, final CallbackInfo ci) {
        if (!this.drivebysable$isHandheld()) {
            return;
        }
        ci.cancel();

        final ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getMainHandItem();
        final ItemStack controller = HandheldTypewriterData.isController(held) ? held
                : CableItems.HANDHELD_TYPEWRITER_CONTROLLER != null
                ? new ItemStack(CableItems.HANDHELD_TYPEWRITER_CONTROLLER.get()) : ItemStack.EMPTY;
        if (controller.isEmpty()) {
            return;
        }

        GuiGameElement.of(controller)
                .<GuiGameElement.GuiRenderBuilder>at(
                        x + this.backgroundMain.width + DRIVEBYSABLE$PREVIEW_X,
                        y + this.backgroundMain.height + DRIVEBYSABLE$PREVIEW_Y,
                        100)
                .scale(DRIVEBYSABLE$PREVIEW_SCALE)
                .render(graphics);
    }

    @Inject(method = "getTypewriterBlockRect", at = @At("HEAD"), cancellable = true, remap = false)
    private void drivebysable$controllerRect(final CallbackInfoReturnable<Rect2i> cir) {
        if (!this.drivebysable$isHandheld()) {
            return;
        }

        final AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        final int size = (int) Math.ceil(16 * DRIVEBYSABLE$PREVIEW_SCALE);
        cir.setReturnValue(new Rect2i(
                screen.getGuiLeft() + this.backgroundMain.width + DRIVEBYSABLE$PREVIEW_X,
                screen.getGuiTop() + this.backgroundMain.height + DRIVEBYSABLE$PREVIEW_Y,
                size, size));
    }

    @Inject(method = "onClose", at = @At("TAIL"), remap = false)
    private void drivebysable$releaseDetachedTypewriter(final CallbackInfo ci) {
        if (!this.drivebysable$isHandheld()) {
            return;
        }

        LinkedTypewriterInteractionHandler.associateTypewriter(null);
        LinkedTypewriterInteractionHandler.setMode(LinkedTypewriterInteractionHandler.Mode.IDLE);
    }
}