package edn.lakeopossmc.drivebysable.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.simulated_team.simulated.compat.computercraft.AttachedComputerHandler;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import edn.lakeopossmc.drivebysable.mixinducks.LinkedTypewriterBlockEntityDuck;

@Pseudo
@Mixin(LinkedTypewriterBlockEntity.class)
public class MixinLinkedTypewriterBlockEntity implements LinkedTypewriterBlockEntityDuck {
    @Unique
    private String drivebysable$computerEventPrefix = "";

    // * Prefixing Computer Craft events helps the computer to distinguish
    // between the internal keyboard and external linked typewriter key presses
    @Unique
    public String drivebysable$getComputerEventPrefix() {
        return drivebysable$computerEventPrefix;
    }

    @Unique
    public void drivebysable$setComputerEventPrefix(final String computerEventPrefix) {
        this.drivebysable$computerEventPrefix = computerEventPrefix;
    }

    @Unique
    public String drivebysable$getComputerEventName(final String eventName) {
        return (this.drivebysable$computerEventPrefix != null && !this.drivebysable$computerEventPrefix.isEmpty())
                ? String.format("%s_%s", this.drivebysable$computerEventPrefix, eventName)
                : eventName;
    }

    @WrapOperation(method = "pressKey", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/compat/computercraft/AttachedComputerHandler;queueEvent(Ljava/lang/String;[Ljava/lang/Object;)V"), remap = false, require = 0)
    private void queueEventPress(AttachedComputerHandler instance, String event, Object[] arguments,
            Operation<Void> operation) {
        operation.call(instance, this.drivebysable$getComputerEventName(event), arguments);
    }

    @WrapOperation(method = "releaseKey", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/compat/computercraft/AttachedComputerHandler;queueEvent(Ljava/lang/String;[Ljava/lang/Object;)V"), remap = false, require = 0)
    private void queueEventRelease(AttachedComputerHandler instance, String event, Object[] arguments,
            Operation<Void> operation) {
        operation.call(instance, this.drivebysable$getComputerEventName(event), arguments);
    }
}
