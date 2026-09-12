package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- SAVE FROM THE SENSOR BUS SCREEN --- //
public record SensorBusSettingsPacket(
        BlockPos sensorPos,
        int maxSpeed,
        int maxAngle,
        int rangeLower,
        int rangeUpper,
        boolean localFrame,
        boolean speedEnabled,
        boolean angleEnabled,
        boolean altitudeEnabled
) implements CustomPacketPayload {

    public static final Type<SensorBusSettingsPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("sensor_bus_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SensorBusSettingsPacket> STREAM_CODEC =
            StreamCodec.of(SensorBusSettingsPacket::write, SensorBusSettingsPacket::read);

    private static void write(final RegistryFriendlyByteBuf buffer, final SensorBusSettingsPacket packet) {
        buffer.writeBlockPos(packet.sensorPos());
        buffer.writeVarInt(packet.maxSpeed());
        buffer.writeVarInt(packet.maxAngle());
        buffer.writeVarInt(packet.rangeLower());
        buffer.writeVarInt(packet.rangeUpper());
        buffer.writeBoolean(packet.localFrame());
        buffer.writeBoolean(packet.speedEnabled());
        buffer.writeBoolean(packet.angleEnabled());
        buffer.writeBoolean(packet.altitudeEnabled());
    }

    private static SensorBusSettingsPacket read(final RegistryFriendlyByteBuf buffer) {
        return new SensorBusSettingsPacket(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
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

    public static void handle(final SensorBusSettingsPacket packet, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        final Level level = player.level();
        if (!level.isLoaded(packet.sensorPos())) {
            return;
        }

        // * Out of reach means the screen is not really open on this block
        if (player.distanceToSqr(
                packet.sensorPos().getX() + 0.5D,
                packet.sensorPos().getY() + 0.5D,
                packet.sensorPos().getZ() + 0.5D) > 64.0D) {
            return;
        }

        if (!(level.getBlockEntity(packet.sensorPos()) instanceof final IntegratedSensorBusBlockEntity sensor)) {
            return;
        }

        // * Checked again here, not just when opening
        if (sensor.isSettingsLocked()) {
            return;
        }

        sensor.setMaxSpeed(packet.maxSpeed());
        sensor.setMaxAngle(packet.maxAngle());
        sensor.setAltitudeRange(packet.rangeLower(), packet.rangeUpper());
        sensor.setLocalFrame(packet.localFrame());
        sensor.setSpeedEnabled(packet.speedEnabled());
        sensor.setAngleEnabled(packet.angleEnabled());
        sensor.setAltitudeEnabled(packet.altitudeEnabled());
    }
}