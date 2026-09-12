package edn.lakeopossmc.drivebysable.menu;

import edn.lakeopossmc.drivebysable.CableMenus;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

// --- SENSOR BUS SETTINGS MENU --- //
public class IntegratedSensorBusMenu extends AbstractContainerMenu {

    private final BlockPos sensorPos;

    @Nullable
    private final IntegratedSensorBusBlockEntity sensor;

    private int maxSpeed = IntegratedSensorBusBlockEntity.MAX_SPEED_DEFAULT;
    private int maxAngle = IntegratedSensorBusBlockEntity.MAX_ANGLE_DEFAULT;
    private int rangeLower = IntegratedSensorBusBlockEntity.RANGE_MIN;
    private int rangeUpper = IntegratedSensorBusBlockEntity.RANGE_MAX;
    private boolean localFrame = true;
    private boolean speedEnabled = true;
    private boolean angleEnabled = true;
    private boolean altitudeEnabled = true;

    // * Client side constructor, reading what the server sent
    public IntegratedSensorBusMenu(final int containerId, final Inventory inventory, final RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
        readSettings(buffer);
    }

    public IntegratedSensorBusMenu(final int containerId, final Inventory inventory, final BlockPos sensorPos) {
        super(CableMenus.INTEGRATED_SENSOR_BUS.get(), containerId);
        this.sensorPos = sensorPos;

        final Level level = inventory.player.level();
        this.sensor = level.getBlockEntity(sensorPos) instanceof final IntegratedSensorBusBlockEntity found
                ? found
                : null;

        if (this.sensor != null) {
            copyFrom(this.sensor);
        }
    }

    public static void writeSettings(final RegistryFriendlyByteBuf buffer, final IntegratedSensorBusBlockEntity sensor) {
        buffer.writeVarInt(sensor.getMaxSpeed());
        buffer.writeVarInt(sensor.getMaxAngle());
        buffer.writeVarInt(sensor.getRangeLower());
        buffer.writeVarInt(sensor.getRangeUpper());
        buffer.writeBoolean(sensor.isLocalFrame());
        buffer.writeBoolean(sensor.isSpeedEnabled());
        buffer.writeBoolean(sensor.isAngleEnabled());
        buffer.writeBoolean(sensor.isAltitudeEnabled());
    }

    private void readSettings(final RegistryFriendlyByteBuf buffer) {
        this.maxSpeed = buffer.readVarInt();
        this.maxAngle = buffer.readVarInt();
        this.rangeLower = buffer.readVarInt();
        this.rangeUpper = buffer.readVarInt();
        this.localFrame = buffer.readBoolean();
        this.speedEnabled = buffer.readBoolean();
        this.angleEnabled = buffer.readBoolean();
        this.altitudeEnabled = buffer.readBoolean();
    }

    private void copyFrom(final IntegratedSensorBusBlockEntity source) {
        this.maxSpeed = source.getMaxSpeed();
        this.maxAngle = source.getMaxAngle();
        this.rangeLower = source.getRangeLower();
        this.rangeUpper = source.getRangeUpper();
        this.localFrame = source.isLocalFrame();
        this.speedEnabled = source.isSpeedEnabled();
        this.angleEnabled = source.isAngleEnabled();
        this.altitudeEnabled = source.isAltitudeEnabled();
    }

    //#region // --- WHAT THE SCREEN EDITS --- //
    public BlockPos getSensorPos() {
        return this.sensorPos;
    }

    public int getMaxSpeed() {
        return this.maxSpeed;
    }

    public void setMaxSpeed(final int value) {
        this.maxSpeed = Math.max(IntegratedSensorBusBlockEntity.MAX_SPEED_MIN,
                Math.min(IntegratedSensorBusBlockEntity.MAX_SPEED_MAX, value));
    }

    public int getMaxAngle() {
        return this.maxAngle;
    }

    public void setMaxAngle(final int value) {
        this.maxAngle = Math.max(IntegratedSensorBusBlockEntity.MAX_ANGLE_MIN,
                Math.min(IntegratedSensorBusBlockEntity.MAX_ANGLE_MAX, value));
    }

    public int getRangeLower() {
        return this.rangeLower;
    }

    public void setRangeLower(final int value) {
        this.rangeLower = clampRange(value);
    }

    public int getRangeUpper() {
        return this.rangeUpper;
    }

    public void setRangeUpper(final int value) {
        this.rangeUpper = clampRange(value);
    }

    private static int clampRange(final int value) {
        return Math.max(IntegratedSensorBusBlockEntity.RANGE_MIN,
                Math.min(IntegratedSensorBusBlockEntity.RANGE_MAX, value));
    }

    public boolean isLocalFrame() {
        return this.localFrame;
    }

    public void setLocalFrame(final boolean local) {
        this.localFrame = local;
    }

    public boolean isSpeedEnabled() {
        return this.speedEnabled;
    }

    public void setSpeedEnabled(final boolean enabled) {
        this.speedEnabled = enabled;
    }

    public boolean isAngleEnabled() {
        return this.angleEnabled;
    }

    public void setAngleEnabled(final boolean enabled) {
        this.angleEnabled = enabled;
    }

    public boolean isAltitudeEnabled() {
        return this.altitudeEnabled;
    }

    public void setAltitudeEnabled(final boolean enabled) {
        this.altitudeEnabled = enabled;
    }

    public void resetToDefaults() {
        this.maxSpeed = IntegratedSensorBusBlockEntity.MAX_SPEED_DEFAULT;
        this.maxAngle = IntegratedSensorBusBlockEntity.MAX_ANGLE_DEFAULT;
        this.rangeLower = IntegratedSensorBusBlockEntity.RANGE_MIN;
        this.rangeUpper = IntegratedSensorBusBlockEntity.RANGE_MAX;
        this.localFrame = true;
        this.speedEnabled = true;
        this.angleEnabled = true;
        this.altitudeEnabled = true;
    }
    //#endregion

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final Player player) {
        if (this.sensor == null || this.sensor.isRemoved()) {
            return false;
        }

        // * A computer locking the block closes any screen already open on it
        if (this.sensor.isSettingsLocked()) {
            return false;
        }

        return player.distanceToSqr(
                this.sensorPos.getX() + 0.5D,
                this.sensorPos.getY() + 0.5D,
                this.sensorPos.getZ() + 0.5D) <= 64.0D;
    }
}