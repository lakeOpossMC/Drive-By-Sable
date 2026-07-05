package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CableBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
        Registries.BLOCK_ENTITY_TYPE,
        DriveBySableMod.MOD_ID
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkBackupDriveBlockEntity>> BACKUP_DRIVE = BLOCK_ENTITY_TYPES.register(
        "backup_drive",
        () -> BlockEntityType.Builder.of(NetworkBackupDriveBlockEntity::new, CableBlocks.BACKUP_DRIVE.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableTypewriterHubBlockEntity>>
            CABLE_TYPEWRITER_HUB = BLOCK_ENTITY_TYPES.register(
            "cable_typewriter_hub",
            () -> BlockEntityType.Builder.of(
                    CableTypewriterHubBlockEntity::new,
                    CableBlocks.CABLE_TYPEWRITER_HUB.get()
            ).build(null));

    private CableBlockEntities() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
