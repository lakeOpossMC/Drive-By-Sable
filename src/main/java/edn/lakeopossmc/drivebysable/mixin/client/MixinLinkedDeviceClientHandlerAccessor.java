package edn.lakeopossmc.drivebysable.mixin.client;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- EXPOSE GET CREATIVE LECTERN POS FIELD --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(targets = "amaryllis.get_creative.appliances.linked_controller.base.LinkedDeviceClientHandler", remap = false)
public interface MixinLinkedDeviceClientHandlerAccessor {
    @Accessor(value = "lecternPos", remap = false)
    BlockPos drivebysable$getLecternPos();
}
