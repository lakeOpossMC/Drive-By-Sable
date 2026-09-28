package edn.lakeopossmc.drivebysable.mixin.client;

import edn.lakeopossmc.drivebysable.client.HandheldTypewriterClientHandler;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- HANDHELD TYPEWRITER TAKES THE WHOLE KEYBOARD --- //
// * Runs before Minecraft handles the key
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void drivebysable$handheldTypewriterKeys(final long windowPointer, final int key, final int scanCode,
                                                     final int action, final int modifiers, final CallbackInfo ci) {
        if (windowPointer != this.minecraft.getWindow().getWindow()) {
            return;
        }
        if (HandheldTypewriterClientHandler.onRawKey(key, scanCode, action)) {
            ci.cancel();
        }
    }
}