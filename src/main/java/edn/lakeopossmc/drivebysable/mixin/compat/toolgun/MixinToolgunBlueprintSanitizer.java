package edn.lakeopossmc.drivebysable.mixin.compat.toolgun;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// --- MAKES THE TOOLGUN BLUEPRINT SANITIZER ALIAS AWARE --- //
// * Pseudo since the toolgun may not be loaded
@Pseudo
@Mixin(targets = "com.enxv.aeronauticsstructuretool.blueprint.security.MissingRegistryContentSanitizer", remap = false)
public abstract class MixinToolgunBlueprintSanitizer {

    @Redirect(
            method = "isMissingBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/DefaultedRegistry;containsKey(Lnet/minecraft/resources/ResourceLocation;)Z"
            ),
            require = 0,
            expect = 0
    )
    private static boolean drivebysable$resolveBlockAlias(
            final DefaultedRegistry<Block> registry,
            final ResourceLocation id
    ) {
        return registry.getHolder(id).isPresent();
    }

    @Redirect(
            method = "isMissingBlockEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/Registry;containsKey(Lnet/minecraft/resources/ResourceLocation;)Z"
            ),
            require = 0,
            expect = 0
    )
    private static boolean drivebysable$resolveBlockEntityAlias(
            final Registry<BlockEntityType<?>> registry,
            final ResourceLocation id
    ) {
        return registry.getHolder(id).isPresent();
    }
}