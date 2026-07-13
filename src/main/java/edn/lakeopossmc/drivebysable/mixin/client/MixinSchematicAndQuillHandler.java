package edn.lakeopossmc.drivebysable.mixin.client;

import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.client.ClientCableNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// --- RESYNC CABLE MIRROR AROUND SCHEMATIC ACTIONS --- //
@Mixin(SchematicAndQuillHandler.class)
public abstract class MixinSchematicAndQuillHandler {
    @Shadow
    public BlockPos firstPos;

    @Shadow
    public BlockPos secondPos;

    // * Request sync after picking either corner
    @Inject(
        method = "onMouseInput",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/lang/LangBuilder;sendStatus(Lnet/minecraft/world/entity/player/Player;)V",
            ordinal = 2,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void drivebysable$requestSyncAfterFirstSelection(
        final int button,
        final boolean pressed,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        ClientCableNetworkHandler.requestSchematicSync("schematic_first_corner");
    }

    @Inject(
        method = "onMouseInput",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/lang/LangBuilder;sendStatus(Lnet/minecraft/world/entity/player/Player;)V",
            ordinal = 1,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void drivebysable$requestSyncAfterSecondSelection(
        final int button,
        final boolean pressed,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        ClientCableNetworkHandler.requestSchematicSync("schematic_second_corner");
    }

    @Inject(method = "saveSchematic", at = @At("HEAD"))
    private void drivebysable$logBeforeSavingSchematic(
        final String name,
        final boolean convertImmediately,
        final CallbackInfo ci
    ) {
        ClientCableNetworkHandler.requestSchematicSync("schematic_save:" + name);
        logSelectionState(name);
    }

    // * Debug log for selection and sublevel state on save
    private void logSelectionState(final String name) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || this.firstPos == null || this.secondPos == null) {
            DriveBySableMod.LOGGER.info(
                "[schematic-debug] save:{} skipped selection log because level/positions were not ready. firstPos={}, secondPos={}.",
                name,
                this.firstPos,
                this.secondPos
            );
            return;
        }

        final BoundingBox3d schematicBounds = new BoundingBox3d(
            this.firstPos.getX(),
            this.firstPos.getY(),
            this.firstPos.getZ(),
            this.secondPos.getX() + 1,
            this.secondPos.getY() + 1,
            this.secondPos.getZ() + 1
        );
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(level, schematicBounds.center(new Vector3d()));
        if (containingSubLevel != null) {
            final Pose3d containingPose = containingSubLevel.logicalPose();
            schematicBounds.transform(containingPose, schematicBounds);
        }

        final List<String> intersectingIds = new ArrayList<>();
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, schematicBounds)) {
            intersectingIds.add(subLevel.getUniqueId().toString());
        }

        DriveBySableMod.LOGGER.info(
            "[schematic-debug] save:{} selection firstPos={} secondPos={} containingSubLevel={} intersectingSubLevels={} ids={}.",
            name,
            this.firstPos,
            this.secondPos,
            containingSubLevel == null ? "none" : containingSubLevel.getUniqueId(),
            intersectingIds.size(),
            intersectingIds
        );
    }
}
