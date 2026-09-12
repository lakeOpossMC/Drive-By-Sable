package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- TELLS CLIENTS WHICH TELEMETRY GROUPS ARE LIVE --- //
public record SensorBusSettingsSyncPacket(
        BlockPos sensorPos,
        boolean speedEnabled,
        boolean angleEnabled,
        boolean altitudeEnabled,
        boolean settingsLocked,
        boolean localFrame
) implements CustomPacketPayload {

    public static final Type<SensorBusSettingsSyncPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("sensor_bus_settings_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SensorBusSettingsSyncPacket> STREAM_CODEC =
            StreamCodec.of(SensorBusSettingsSyncPacket::write, SensorBusSettingsSyncPacket::read);

    private static void write(final RegistryFriendlyByteBuf buffer, final SensorBusSettingsSyncPacket packet) {
        buffer.writeBlockPos(packet.sensorPos());
        buffer.writeBoolean(packet.speedEnabled());
        buffer.writeBoolean(packet.angleEnabled());
        buffer.writeBoolean(packet.altitudeEnabled());
        buffer.writeBoolean(packet.settingsLocked());
        buffer.writeBoolean(packet.localFrame());
    }

    private static SensorBusSettingsSyncPacket read(final RegistryFriendlyByteBuf buffer) {
        return new SensorBusSettingsSyncPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SensorBusSettingsSyncPacket packet, final IPayloadContext context) {
        if (context.player() == null) {
            return;
        }

        final Level level = context.player().level();
        if (!level.isLoaded(packet.sensorPos())) {
            return;
        }

        if (level.getBlockEntity(packet.sensorPos()) instanceof final IntegratedSensorBusBlockEntity sensor) {
            sensor.applySyncedSettings(
                    packet.speedEnabled(),
                    packet.angleEnabled(),
                    packet.altitudeEnabled(),
                    packet.settingsLocked(),
                    packet.localFrame()
            );
        }
    }
}