package edn.lakeopossmc.drivebysable.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// --- RECIPES THAT ONLY EXISTS WHEN A CONFIG OPTION SAYS SO --- //
// * Lets both versions of a recipe ship at once
public record CableConfigCondition(Option option, boolean expected) implements ICondition {

    public enum Option implements StringRepresentable {
        CHEAPER_BACKUP_DRIVE("expensive_drive"),
        CHEAPER_HUBS("andesite_hub"),
        MULTI_CHANNEL_CABLE_BUS("multi_channel_cable_bus"),
        INTEGRATED_SENSOR_BUS("integrated_sensor_bus");

        private final String name;

        Option(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public boolean get() {
            return switch (this) {
                case CHEAPER_BACKUP_DRIVE -> CableConfig.CONFIG.expensiveBackupDrive.get();
                case CHEAPER_HUBS -> CableConfig.CONFIG.andesiteHub.get();
                case MULTI_CHANNEL_CABLE_BUS -> CableConfig.CONFIG.multiChannelCableBus.get();
                case INTEGRATED_SENSOR_BUS -> CableConfig.CONFIG.integratedSensorBus.get();
            };
        }
    }

    public static final MapCodec<CableConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    StringRepresentable.fromEnum(Option::values)
                            .fieldOf("option")
                            .forGetter(CableConfigCondition::option),
                    com.mojang.serialization.Codec.BOOL
                            .optionalFieldOf("expected", true)
                            .forGetter(CableConfigCondition::expected)
            )
            .apply(instance, CableConfigCondition::new));

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, DriveBySableMod.MOD_ID);

    static {
        CONDITIONS.register("config", () -> CODEC);
    }

    public static void register(final IEventBus modEventBus) {
        CONDITIONS.register(modEventBus);
    }

    @Override
    public boolean test(final IContext context) {
        return option.get() == expected;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}