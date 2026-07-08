package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlock;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlock;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlock;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;

public final class CableBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DriveBySableMod.MOD_ID);

    public static final DeferredBlock<NetworkBackupDriveBlock> BACKUP_DRIVE = BLOCKS.register(
            "backup_drive",
            () -> new NetworkBackupDriveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(CableSounds.backupDriveSoundType())
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredBlock<CableHubBlock> CABLE_HUB = BLOCKS.register(
            "cable_hub",
            () -> new CableHubBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN)
                    .sound(SoundType.METAL)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

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

    @Nullable
    public static final DeferredBlock<CableTypewriterHubBlock> CABLE_TYPEWRITER_HUB =
            ModList.get().isLoaded("simulated")
                    ? BLOCKS.register(
                    "cable_typewriter_hub",
                    () -> new CableTypewriterHubBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .sound(SoundType.METAL)
                            .strength(2.5F, 4.0F)
                            .requiresCorrectToolForDrops()))
                    : null;

    private CableBlocks() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}