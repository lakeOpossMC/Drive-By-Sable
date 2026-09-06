package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.NetworkAnchorBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// --- REGISTERS ALL BLOCK ENTITY TYPES --- //
public final class CableBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE,
            DriveBySableMod.MOD_ID
    );

    // * One entity type shared by both hub blocks
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkAnchorBlockEntity>> NETWORK_ANCHOR =
            BLOCK_ENTITY_TYPES.register(
                    "network_anchor",
                    () -> BlockEntityType.Builder.of(NetworkAnchorBlockEntity::new, CableBlocks.NETWORK_ANCHOR.get()).build(null)
            );

    // * Null when simulated isnt loaded
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

    //#region // --- LEGACY ALIASES --- //
    private static void addLegacyAliases() {
        BLOCK_ENTITY_TYPES.addAlias(
                ResourceLocation.fromNamespaceAndPath(LegacyWireCompat.LEGACY_MOD_ID, LegacyWireCompat.LEGACY_BACKUP_BLOCK),
                ResourceLocation.fromNamespaceAndPath(DriveBySableMod.MOD_ID, "backup_drive")
        );
    }
    //#endregion

    public static void register(final IEventBus modEventBus) {
        addLegacyAliases();
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}