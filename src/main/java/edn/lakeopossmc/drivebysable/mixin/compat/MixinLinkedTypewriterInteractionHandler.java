package edn.lakeopossmc.drivebysable.mixin.compat;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler;
import dev.simulated_team.simulated.data.SimLang;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.ref.WeakReference;

@Mixin(LinkedTypewriterInteractionHandler.class)
public abstract class MixinLinkedTypewriterInteractionHandler {
    private static LinkedTypewriterBlockEntity drivebysable$previousTypewriter;

    // --- FIX "STOPPED CONTROLLING" MESSAGE --- //
    @Redirect(
            method = "associateTypewriter",
            at = @At(value = "INVOKE", target = "Ljava/lang/ref/WeakReference;get()Ljava/lang/Object;")
    )
    private static Object drivebysable$captureOldTypewriter(final WeakReference<?> ref) {
        final Object value = ref.get();
        drivebysable$previousTypewriter = value instanceof LinkedTypewriterBlockEntity typewriter ? typewriter : null;
        return value;
    }
    @Redirect(
            method = "associateTypewriter",
            at = @At(value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/data/SimLang;translate(Ljava/lang/String;[Ljava/lang/Object;)Lnet/createmod/catnip/lang/LangBuilder;",
                    ordinal = 0)
    )
    private static LangBuilder drivebysable$stopControllingMessage(final String key, final Object[] args) {
        if (drivebysable$previousTypewriter instanceof CableTypewriterHubBlockEntity) {
            return Lang.builder("drivebysable").translate("typewriter_hub.stop_controlling", args);
        }
        return SimLang.translate(key, args);
    }

    // --- FIX "NOW CONTROLLING" MESSAGE --- //
    @Redirect(
            method = "associateTypewriter",
            at = @At(value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/data/SimLang;translate(Ljava/lang/String;[Ljava/lang/Object;)Lnet/createmod/catnip/lang/LangBuilder;",
                    ordinal = 1)
    )
    private static LangBuilder drivebysable$startControllingMessage(final String key, final Object[] args,
                                                                    final LinkedTypewriterBlockEntity typewriter) {
        if (typewriter instanceof CableTypewriterHubBlockEntity) {
            return Lang.builder("drivebysable").translate("typewriter_hub.start_controlling", args);
        }
        return SimLang.translate(key, args);
    }
}