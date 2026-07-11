package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class CableBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE,
            DriveBySableMod.MOD_ID
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableHubBlockEntity>> CABLE_HUB =
            BLOCK_ENTITY_TYPES.register(
                    "cable_hub",
                    () -> {
                        final List<Block> validBlocks = new ArrayList<>();
                        validBlocks.add(CableBlocks.CABLE_HUB.get());
                        if (CableBlocks.ADVANCED_CABLE_HUB != null) {
                            validBlocks.add(CableBlocks.ADVANCED_CABLE_HUB.get());
                        }
                        return BlockEntityType.Builder.of(CableHubBlockEntity::new, validBlocks.toArray(new Block[0])).build(null);
                    });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkBackupDriveBlockEntity>> BACKUP_DRIVE = BLOCK_ENTITY_TYPES.register(
            "backup_drive",
            () -> BlockEntityType.Builder.of(NetworkBackupDriveBlockEntity::new, CableBlocks.BACKUP_DRIVE.get()).build(null)
    );

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableTypewriterHubBlockEntity>>
            CABLE_TYPEWRITER_HUB = ModList.get().isLoaded("simulated")
            ? BLOCK_ENTITY_TYPES.register(
            "cable_typewriter_hub",
            () -> BlockEntityType.Builder.of(
                    CableTypewriterHubBlockEntity::new,
                    CableBlocks.CABLE_TYPEWRITER_HUB.get()
            ).build(null))
            : null;

    private CableBlockEntities() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}