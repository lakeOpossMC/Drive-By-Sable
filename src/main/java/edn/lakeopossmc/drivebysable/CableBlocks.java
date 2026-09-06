package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlock;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlock;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlock;
import edn.lakeopossmc.drivebysable.blocks.NetworkAnchorBlock;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlock;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;

// --- REGISTERS ALL BLOCKS --- //
public final class CableBlocks {
    // * Namespace the aeronautics toolgun still hardcodes
    private static final String LEGACY_NAMESPACE = LegacyWireCompat.LEGACY_MOD_ID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DriveBySableMod.MOD_ID);

    public static final DeferredBlock<NetworkBackupDriveBlock> BACKUP_DRIVE = BLOCKS.register(
            "backup_drive",
            () -> new NetworkBackupDriveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BLACK)
                    .sound(CableSounds.backupDriveSoundType())
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops())
    );
    // * Creative only
    public static final DeferredBlock<NetworkAnchorBlock> NETWORK_ANCHOR = BLOCKS.register(
            "network_anchor",
            () -> new NetworkAnchorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .strength(-1.0F, Float.MAX_VALUE)
                    .noLootTable())
    );

    public static final DeferredBlock<CableHubBlock> CABLE_HUB = BLOCKS.register(
            "cable_hub",
            () -> new CableHubBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN)
                    .sound(SoundType.METAL)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

    // * Null when tweaked controllers isnt loaded
    @Nullable
    public static final DeferredBlock<AdvancedCableHubBlock> ADVANCED_CABLE_HUB =
            ModList.get().isLoaded("create_tweaked_controllers")
                    ? BLOCKS.register(
                    "advanced_cable_hub",
                    () -> new AdvancedCableHubBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_BLUE)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops()))
                    : null;

    // * Null when simulated isnt loaded
    @Nullable
    public static final DeferredBlock<CableTypewriterHubBlock> CABLE_TYPEWRITER_HUB =
            ModList.get().isLoaded("simulated")
                    ? BLOCKS.register(
                    "cable_typewriter_hub",
                    () -> new CableTypewriterHubBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .sound(SoundType.METAL)
                            .strength(2.5F, 4.0F)
                            .requiresCorrectToolForDrops()))
                    : null;

    private CableBlocks() {
    }

    // * The aeronautics toolgun looks blocks up by hardcoded drivebywire ids
    // * Aliases resolve those to DBS whenever DBW is absent
    private static void addLegacyAliases() {
        BLOCKS.addAlias(
                ResourceLocation.fromNamespaceAndPath(LEGACY_NAMESPACE, LegacyWireCompat.LEGACY_BACKUP_BLOCK),
                ResourceLocation.fromNamespaceAndPath(DriveBySableMod.MOD_ID, "backup_drive")
        );
        BLOCKS.addAlias(
                ResourceLocation.fromNamespaceAndPath(LEGACY_NAMESPACE, LegacyWireCompat.LEGACY_CONTROLLER_HUB),
                ResourceLocation.fromNamespaceAndPath(DriveBySableMod.MOD_ID, "cable_hub")
        );

        // * Only registered when tweaked controllers is loaded
        if (ADVANCED_CABLE_HUB != null) {
            BLOCKS.addAlias(
                    ResourceLocation.fromNamespaceAndPath(LEGACY_NAMESPACE, LegacyWireCompat.LEGACY_TWEAKED_CONTROLLER_HUB),
                    ResourceLocation.fromNamespaceAndPath(DriveBySableMod.MOD_ID, "advanced_cable_hub")
            );
        }
    }

    public static void register(final IEventBus modEventBus) {
        addLegacyAliases();
        BLOCKS.register(modEventBus);
    }
}