package edn.lakeopossmc.drivebysable.mixin;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// --- REANCHOR DRIVE BY WIRE PAYLOADS AS TEMPLATES ARE READ --- //
@Mixin(StructureTemplate.class)
public abstract class MixinStructureTemplate {
    @Shadow
    @Final
    private List<StructureTemplate.Palette> palettes;

    @Inject(method = "load", at = @At("TAIL"))
    private void drivebysable$rebaseLegacyPayloads(
            final HolderGetter<Block> blockGetter,
            final CompoundTag tag,
            final CallbackInfo ci
    ) {
        int rebased = 0;

        for (final StructureTemplate.Palette palette : this.palettes) {
            for (final StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                // * Records are immutable but the tag behind them is not
                final CompoundTag payload = LegacyWireCompat.payloadIn(info.nbt());
                if (payload == null) {
                    continue;
                }

                if (LegacyWireCompat.rebaseTemplatePayload(payload, info.pos())) {
                    rebased++;
                }
            }
        }

        if (rebased > 0) {
            DriveBySableMod.LOGGER.info(
                    "[drivebywire-migration] Reanchored {} Drive-By-Wire payload(s) while reading a structure "
                            + "template, converting them to the placement independent form.",
                    rebased
            );
        }
    }
}