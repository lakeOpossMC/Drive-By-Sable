package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.NetworkAnchorBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.MultiChannelCableBusBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import edn.lakeopossmc.drivebysable.legacy.LegacyTypewriterCompat;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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

    // * Entity type for the linked-controller hub
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableHubBlockEntity>> CABLE_HUB =
            BLOCK_ENTITY_TYPES.register(
                    "cable_hub",
                    () -> {
                        final List<Block> validBlocks = new ArrayList<>();
                        validBlocks.add(CableBlocks.CABLE_HUB.get());
                        if (CableBlocks.ADVANCED_CABLE_HUB != null) {
                            validBlocks.add(CableBlocks.ADVANCED_CABLE_HUB.get());
                        }
                        return BlockEntityType.Builder.of(
                                CableBlockEntities::createUpgradedCableHub,
                                validBlocks.toArray(new Block[0])
                        ).build(null);
                    });

    // * Null when tweaked controllers isnt loaded
    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedCableHubBlockEntity>> ADVANCED_CABLE_HUB =
            CableBlocks.ADVANCED_CABLE_HUB != null
                    ? BLOCK_ENTITY_TYPES.register(
                    "advanced_cable_hub",
                    () -> BlockEntityType.Builder.of(
                            AdvancedCableHubBlockEntity::new,
                            CableBlocks.ADVANCED_CABLE_HUB.get()).build(null))
                    : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkBackupDriveBlockEntity>> BACKUP_DRIVE = BLOCK_ENTITY_TYPES.register(
            "backup_drive",
            () -> BlockEntityType.Builder.of(NetworkBackupDriveBlockEntity::new, CableBlocks.BACKUP_DRIVE.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IntegratedSensorBusBlockEntity>>
            INTEGRATED_SENSOR_BUS = BLOCK_ENTITY_TYPES.register(
            "integrated_sensor_bus",
            () -> BlockEntityType.Builder.of(
                    IntegratedSensorBusBlockEntity::new,
                    CableBlocks.INTEGRATED_SENSOR_BUS.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiChannelCableBusBlockEntity>>
            MULTI_CHANNEL_CABLE_BUS = BLOCK_ENTITY_TYPES.register(
            "multi_channel_cable_bus",
            () -> BlockEntityType.Builder.of(
                    MultiChannelCableBusBlockEntity::new,
                    CableBlocks.MULTI_CHANNEL_CABLE_BUS.get()).build(null)
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
        if (CABLE_TYPEWRITER_HUB != null) {
            BLOCK_ENTITY_TYPES.addAlias(
                    ResourceLocation.fromNamespaceAndPath(
                            LegacyTypewriterCompat.LEGACY_MOD_ID, LegacyTypewriterCompat.LEGACY_BLOCK),
                    ResourceLocation.fromNamespaceAndPath(DriveBySableMod.MOD_ID, "cable_typewriter_hub")
            );
        }

        BLOCK_ENTITY_TYPES.addAlias(
                ResourceLocation.fromNamespaceAndPath(LegacyWireCompat.LEGACY_MOD_ID, LegacyWireCompat.LEGACY_BACKUP_BLOCK),
                ResourceLocation.fromNamespaceAndPath(DriveBySableMod.MOD_ID, "backup_drive")
        );
    }
    //#endregion

    //#region // --- UPGRADES --- //
    private static CableHubBlockEntity createUpgradedCableHub(BlockPos pos, BlockState state) {
        if (CableBlocks.ADVANCED_CABLE_HUB != null && state.is(CableBlocks.ADVANCED_CABLE_HUB.get())) {
            return new AdvancedCableHubBlockEntity(pos, state);
        }
        return new CableHubBlockEntity(pos, state);
    }
    //#endregion

    public static void register(final IEventBus modEventBus) {
        addLegacyAliases();
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
