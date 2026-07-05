package edn.lakeopossmc.drivebysable;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CableSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, DriveBySableMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PLUG_IN = SOUND_EVENTS.register(
            "plug_in",
            () -> SoundEvent.createVariableRangeEvent(DriveBySableMod.asResource("plug_in"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUG_OUT = SOUND_EVENTS.register(
            "plug_out",
            () -> SoundEvent.createVariableRangeEvent(DriveBySableMod.asResource("plug_out"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> BACKUP_DRIVE_BREAK = SOUND_EVENTS.register(
            "block.backup_drive.break",
            () -> SoundEvent.createVariableRangeEvent(DriveBySableMod.asResource("block.backup_drive.break"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKUP_DRIVE_PLACE = SOUND_EVENTS.register(
            "block.backup_drive.place",
            () -> SoundEvent.createVariableRangeEvent(DriveBySableMod.asResource("block.backup_drive.place"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKUP_DRIVE_STEP = SOUND_EVENTS.register(
            "block.backup_drive.step",
            () -> SoundEvent.createVariableRangeEvent(DriveBySableMod.asResource("block.backup_drive.step"))
    );

    private CableSounds() {
    }

    public static void register(final IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    public static SoundType backupDriveSoundType() {
        return new SoundType(
                1.0F, 1.0F,
                BACKUP_DRIVE_BREAK.get(),
                BACKUP_DRIVE_STEP.get(),
                BACKUP_DRIVE_PLACE.get(),
                BACKUP_DRIVE_STEP.get(),
                BACKUP_DRIVE_STEP.get()
        );
    }
}