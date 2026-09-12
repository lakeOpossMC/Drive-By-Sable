package edn.lakeopossmc.drivebysable.client.screen;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.Label;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.menu.IntegratedSensorBusMenu;
import edn.lakeopossmc.drivebysable.network.SensorBusSettingsPacket;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import javax.annotation.Nullable;
import java.util.List;

// --- SENSOR BUS SETTINGS SCREEN --- //
public class IntegratedSensorBusScreen extends AbstractSimiContainerScreen<IntegratedSensorBusMenu> {

    private static final ResourceLocation SPRITE =
            DriveBySableMod.asResource("textures/gui/integrated_sensor_bus.png");

    private static final int SHEET_SIZE = 256;
    private static final int SPRITE_WIDTH = 142;
    private static final int SPRITE_HEIGHT = 100;

    private static final int TITLE_BAR_WIDTH = 134;

    //#region // --- SPRITE ATLAS --- //
    private static final int BUTTON_U = 0;
    private static final int BUTTON_HOVER_U = 18;
    private static final int BUTTON_PRESS_U = 36;
    private static final int BUTTON_ON_U = 54;
    private static final int BUTTON_LOCKED_U = 72;
    private static final int BUTTON_V = 110;
    private static final int BUTTON_SIZE = 18;

    private static final int ICON_V = 128;
    private static final int ICON_SIZE = 16;
    private static final int ICON_RESET_U = 0;
    private static final int ICON_SAVE_U = 16;
    private static final int ICON_SPEED_U = 32;
    private static final int ICON_ANGLE_U = 48;
    private static final int ICON_ALTITUDE_U = 64;
    private static final int ICON_LOCAL_U = 80;
    private static final int ICON_GLOBAL_U = 96;
    //#endregion

    //#region // --- WIDGET PLACEMENT --- //
    private static final int MAX_SPEED_X = 35;
    private static final int MAX_SPEED_Y = 22;
    private static final int FRAME_X = 58;
    private static final int FRAME_Y = 22;
    private static final int MAX_ANGLE_X = 105;
    private static final int MAX_ANGLE_Y = 22;

    private static final int RANGE_LOWER_X = 35;
    private static final int RANGE_UPPER_X = 80;
    private static final int RANGE_Y = 44;
    private static final int RANGE_WIDTH = 43;

    private static final int WELL_HEIGHT = 18;
    private static final int WELL_SIZE = 18;

    private static final int RESET_X = 8;
    private static final int SPEED_TOGGLE_X = 41;
    private static final int ANGLE_TOGGLE_X = 59;
    private static final int ALTITUDE_TOGGLE_X = 77;
    private static final int SAVE_X = 108;
    private static final int BUTTON_ROW_Y = 76;
    //#endregion

    //#region // --- BLOCK RENDER --- //
    private static final int POINTER_TIP_Y = 85;
    private static final int RENDER_WIDTH = 72;
    private static final int RENDER_HEIGHT = 74;
    private static final int RENDER_OFFSET_X = 4;
    private static final int RENDER_OFFSET_Y = 3;
    private static final int BLOCK_ICON_X = SPRITE_WIDTH + 2;
    private static final int BLOCK_ICON_Y = POINTER_TIP_Y - RENDER_HEIGHT / 2 - RENDER_OFFSET_Y;
    private static final float BLOCK_ICON_Z = -200.0F;
    private static final double BLOCK_ICON_SCALE = 5.0D;
    //#endregion

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int TEXT_INSET_X = 4;
    private static final int TEXT_INSET_Y = (WELL_HEIGHT - 8) / 2;

    private static final int LOCKED_TEXT_COLOR = 0xAAAAAA;

    private static final int TITLE_COLOR = 0x3C3B47;
    private static final int TITLE_Y = 4;

    private static final float CLICK_VOLUME = 0.25F;

    @Nullable
    private String heldButton;

    // * Handed to JEI so it does not draw over the block render
    private List<Rect2i> extraAreas = List.of();

    private boolean resetArmed;

    private final ItemStack sensorIcon = new ItemStack(CableItems.INTEGRATED_SENSOR_BUS.get());

    private ScrollInput maxSpeedInput;
    private ScrollInput maxAngleInput;
    private ScrollInput rangeLowerInput;
    private ScrollInput rangeUpperInput;

    private Label maxSpeedLabel;
    private Label maxAngleLabel;
    private Label rangeLowerLabel;
    private Label rangeUpperLabel;

    public IntegratedSensorBusScreen(
            final IntegratedSensorBusMenu menu,
            final Inventory inventory,
            final Component title
    ) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        setWindowSize(SPRITE_WIDTH, SPRITE_HEIGHT);
        super.init();

        final int x = leftPos;
        final int y = topPos;

        final int speedCentreX = x + MAX_SPEED_X + WELL_SIZE / 2;
        maxSpeedLabel = new Label(speedCentreX, y + MAX_SPEED_Y + TEXT_INSET_Y, Component.empty()).withShadow();
        maxSpeedInput = new ScrollInput(x + MAX_SPEED_X, y + MAX_SPEED_Y, WELL_SIZE, WELL_HEIGHT)
                .withRange(IntegratedSensorBusBlockEntity.MAX_SPEED_MIN,
                        IntegratedSensorBusBlockEntity.MAX_SPEED_MAX + 1)
                .writingTo(maxSpeedLabel)
                .titled(Component.translatable("drivebysable.sensor_bus.max_speed"))
                .calling(state -> {
                    menu.setMaxSpeed(state);
                    maxSpeedLabel.setX(speedCentreX - font.width(maxSpeedLabel.text) / 2);
                });
        maxSpeedInput.setState(menu.getMaxSpeed());
        maxSpeedInput.onChanged();

        final int angleCentreX = x + MAX_ANGLE_X + WELL_SIZE / 2;
        maxAngleLabel = new Label(angleCentreX, y + MAX_ANGLE_Y + TEXT_INSET_Y, Component.empty()).withShadow();
        maxAngleInput = new ScrollInput(x + MAX_ANGLE_X, y + MAX_ANGLE_Y, WELL_SIZE, WELL_HEIGHT)
                .withRange(IntegratedSensorBusBlockEntity.MAX_ANGLE_MIN,
                        IntegratedSensorBusBlockEntity.MAX_ANGLE_MAX + 1)
                .writingTo(maxAngleLabel)
                .titled(Component.translatable("drivebysable.sensor_bus.max_angle"))
                .calling(state -> {
                    menu.setMaxAngle(state);
                    maxAngleLabel.setX(angleCentreX - font.width(maxAngleLabel.text) / 2);
                });
        maxAngleInput.setState(menu.getMaxAngle());
        maxAngleInput.onChanged();

        rangeLowerLabel = wellLabel(x + RANGE_LOWER_X, y + RANGE_Y);
        rangeLowerInput = new ScrollInput(x + RANGE_LOWER_X, y + RANGE_Y, RANGE_WIDTH, WELL_HEIGHT)
                .withRange(IntegratedSensorBusBlockEntity.RANGE_MIN,
                        IntegratedSensorBusBlockEntity.RANGE_MAX + 1)
                .writingTo(rangeLowerLabel)
                .titled(Component.translatable("drivebysable.sensor_bus.range_lower"))
                .calling(menu::setRangeLower);
        rangeLowerInput.setState(menu.getRangeLower());

        rangeUpperLabel = wellLabel(x + RANGE_UPPER_X, y + RANGE_Y);
        rangeUpperInput = new ScrollInput(x + RANGE_UPPER_X, y + RANGE_Y, RANGE_WIDTH, WELL_HEIGHT)
                .withRange(IntegratedSensorBusBlockEntity.RANGE_MIN,
                        IntegratedSensorBusBlockEntity.RANGE_MAX + 1)
                .writingTo(rangeUpperLabel)
                .titled(Component.translatable("drivebysable.sensor_bus.range_upper"))
                .calling(menu::setRangeUpper);
        rangeUpperInput.setState(menu.getRangeUpper());

        addRenderableWidgets(maxSpeedInput, maxAngleInput, rangeLowerInput, rangeUpperInput);
        addRenderableOnly(maxSpeedLabel);
        addRenderableOnly(maxAngleLabel);
        addRenderableOnly(rangeLowerLabel);
        addRenderableOnly(rangeUpperLabel);

        refreshInputs();

        // * Tells JEI the block render is part of the screen, so it wraps around it
        extraAreas = List.of(new Rect2i(
                x + BLOCK_ICON_X + RENDER_OFFSET_X,
                y + BLOCK_ICON_Y + RENDER_OFFSET_Y,
                RENDER_WIDTH,
                RENDER_HEIGHT
        ));
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return extraAreas;
    }

    private Label wellLabel(final int wellX, final int wellY) {
        return new Label(wellX + TEXT_INSET_X, wellY + TEXT_INSET_Y, Component.empty()).withShadow();
    }

    // * A disabled group's value cannot be edited
    private void refreshInputs() {
        maxSpeedInput.active = menu.isSpeedEnabled();
        maxAngleInput.active = menu.isAngleEnabled();
        rangeLowerInput.active = menu.isAltitudeEnabled();
        rangeUpperInput.active = menu.isAltitudeEnabled();

        maxSpeedLabel.colored(wellColor(menu.isSpeedEnabled()));
        maxAngleLabel.colored(wellColor(menu.isAngleEnabled()));
        rangeLowerLabel.colored(wellColor(menu.isAltitudeEnabled()));
        rangeUpperLabel.colored(wellColor(menu.isAltitudeEnabled()));
    }

    private void syncInputs() {
        maxSpeedInput.setState(menu.getMaxSpeed());
        maxAngleInput.setState(menu.getMaxAngle());
        rangeLowerInput.setState(menu.getRangeLower());
        rangeUpperInput.setState(menu.getRangeUpper());

        maxSpeedInput.onChanged();
        maxAngleInput.onChanged();
    }

    //#region // --- INPUT --- //
    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!over(mouseX, mouseY, RESET_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            resetArmed = false;
        }

        if (over(mouseX, mouseY, FRAME_X, FRAME_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            // * The frame only decides which axes the speed channels read
            if (!menu.isSpeedEnabled()) {
                return true;
            }

            heldButton = "frame";
            menu.setLocalFrame(!menu.isLocalFrame());
            playToggleSound(!menu.isLocalFrame());
            return true;
        }

        if (over(mouseX, mouseY, SPEED_TOGGLE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            heldButton = "speed";
            menu.setSpeedEnabled(!menu.isSpeedEnabled());
            playToggleSound(menu.isSpeedEnabled());
            refreshInputs();
            return true;
        }

        if (over(mouseX, mouseY, ANGLE_TOGGLE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            heldButton = "angle";
            menu.setAngleEnabled(!menu.isAngleEnabled());
            playToggleSound(menu.isAngleEnabled());
            refreshInputs();
            return true;
        }

        if (over(mouseX, mouseY, ALTITUDE_TOGGLE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            heldButton = "altitude";
            menu.setAltitudeEnabled(!menu.isAltitudeEnabled());
            playToggleSound(menu.isAltitudeEnabled());
            refreshInputs();
            return true;
        }

        if (over(mouseX, mouseY, RESET_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            heldButton = "reset";
            click(1.0F);

            if (!resetArmed) {
                resetArmed = true;
                return true;
            }

            resetArmed = false;
            menu.resetToDefaults();
            syncInputs();
            refreshInputs();
            return true;
        }

        if (over(mouseX, mouseY, SAVE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            heldButton = "save";
            click(1.0F);
            save();
            onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        heldButton = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void playToggleSound(final boolean turningOn) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        final BlockPos soundPos = minecraft.player.blockPosition();
        minecraft.player.level().playLocalSound(
                soundPos.getX() + 0.5,
                soundPos.getY() + 0.5,
                soundPos.getZ() + 0.5,
                turningOn ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
                SoundSource.PLAYERS,
                0.7F,
                1.0F,
                false
        );
    }

    private void click(final float pitch) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        final BlockPos soundPos = minecraft.player.blockPosition();
        minecraft.player.level().playLocalSound(
                soundPos.getX() + 0.5,
                soundPos.getY() + 0.5,
                soundPos.getZ() + 0.5,
                SoundEvents.UI_BUTTON_CLICK.value(),
                SoundSource.MASTER,
                CLICK_VOLUME,
                pitch,
                false
        );
    }

    private void save() {
        PacketDistributor.sendToServer(new SensorBusSettingsPacket(
                menu.getSensorPos(),
                menu.getMaxSpeed(),
                menu.getMaxAngle(),
                menu.getRangeLower(),
                menu.getRangeUpper(),
                menu.isLocalFrame(),
                menu.isSpeedEnabled(),
                menu.isAngleEnabled(),
                menu.isAltitudeEnabled()
        ));
    }

    private boolean over(final double mouseX, final double mouseY, final int x, final int y, final int width, final int height) {
        final int left = leftPos + x;
        final int top = topPos + y;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }
    //#endregion

    //#region // --- RENDER --- //
    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTicks, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;

        graphics.blit(SPRITE, x, y, 0, 0, SPRITE_WIDTH, SPRITE_HEIGHT, SHEET_SIZE, SHEET_SIZE);

        if (menu.isSpeedEnabled()) {
            button(graphics, x + FRAME_X, y + FRAME_Y, mouseX, mouseY, !menu.isLocalFrame(), "frame");
        } else {
            graphics.blit(SPRITE, x + FRAME_X, y + FRAME_Y, BUTTON_LOCKED_U, BUTTON_V,
                    BUTTON_SIZE, BUTTON_SIZE, SHEET_SIZE, SHEET_SIZE);
        }
        icon(graphics, x + FRAME_X, y + FRAME_Y, menu.isLocalFrame() ? ICON_LOCAL_U : ICON_GLOBAL_U);

        toggle(graphics, x + SPEED_TOGGLE_X, y + BUTTON_ROW_Y, mouseX, mouseY, menu.isSpeedEnabled(), ICON_SPEED_U, "speed");
        toggle(graphics, x + ANGLE_TOGGLE_X, y + BUTTON_ROW_Y, mouseX, mouseY, menu.isAngleEnabled(), ICON_ANGLE_U, "angle");
        toggle(graphics, x + ALTITUDE_TOGGLE_X, y + BUTTON_ROW_Y, mouseX, mouseY, menu.isAltitudeEnabled(), ICON_ALTITUDE_U, "altitude");

        button(graphics, x + RESET_X, y + BUTTON_ROW_Y, mouseX, mouseY, false, "reset");
        icon(graphics, x + RESET_X, y + BUTTON_ROW_Y, ICON_RESET_U);

        button(graphics, x + SAVE_X, y + BUTTON_ROW_Y, mouseX, mouseY, false, "save");
        icon(graphics, x + SAVE_X, y + BUTTON_ROW_Y, ICON_SAVE_U);

        GuiGameElement.of(sensorIcon)
                .<GuiGameElement.GuiRenderBuilder>at(x + BLOCK_ICON_X, y + BLOCK_ICON_Y, BLOCK_ICON_Z)
                .scale(BLOCK_ICON_SCALE)
                .render(graphics);
    }

    private void toggle(
            final GuiGraphics graphics,
            final int x,
            final int y,
            final int mouseX,
            final int mouseY,
            final boolean enabled,
            final int iconU,
            final String id
    ) {
        button(graphics, x, y, mouseX, mouseY, enabled, id);
        icon(graphics, x, y, iconU);
    }

    private void button(
            final GuiGraphics graphics,
            final int x,
            final int y,
            final int mouseX,
            final int mouseY,
            final boolean on,
            final String id
    ) {
        final boolean hovered = mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;

        final int u = id.equals(heldButton)
                ? BUTTON_PRESS_U
                : hovered ? BUTTON_HOVER_U : on ? BUTTON_ON_U : BUTTON_U;

        graphics.blit(SPRITE, x, y, u, BUTTON_V, BUTTON_SIZE, BUTTON_SIZE, SHEET_SIZE, SHEET_SIZE);
    }

    private void icon(final GuiGraphics graphics, final int x, final int y, final int iconU) {
        graphics.blit(SPRITE, x + 1, y + 1, iconU, ICON_V, ICON_SIZE, ICON_SIZE, SHEET_SIZE, SHEET_SIZE);
    }

    @Override
    protected void renderTooltip(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (over(mouseX, mouseY, RESET_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            // * Only speaks up once armed
            if (resetArmed) {
                tip(graphics, mouseX, mouseY, "drivebysable.sensor_bus.reset_confirm", ChatFormatting.RED);
            }
            return;
        }

        if (over(mouseX, mouseY, FRAME_X, FRAME_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            tip(graphics, mouseX, mouseY, menu.isLocalFrame()
                    ? "drivebysable.sensor_bus.frame_local"
                    : "drivebysable.sensor_bus.frame_global", ChatFormatting.GRAY);
            return;
        }

        if (over(mouseX, mouseY, SPEED_TOGGLE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            tip(graphics, mouseX, mouseY, menu.isSpeedEnabled()
                    ? "drivebysable.sensor_bus.speed_lock"
                    : "drivebysable.sensor_bus.speed_unlock", ChatFormatting.GRAY);
            return;
        }

        if (over(mouseX, mouseY, ANGLE_TOGGLE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            tip(graphics, mouseX, mouseY, menu.isAngleEnabled()
                    ? "drivebysable.sensor_bus.angle_lock"
                    : "drivebysable.sensor_bus.angle_unlock", ChatFormatting.GRAY);
            return;
        }

        if (over(mouseX, mouseY, ALTITUDE_TOGGLE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            tip(graphics, mouseX, mouseY, menu.isAltitudeEnabled()
                    ? "drivebysable.sensor_bus.altitude_lock"
                    : "drivebysable.sensor_bus.altitude_unlock", ChatFormatting.GRAY);
            return;
        }

        if (over(mouseX, mouseY, SAVE_X, BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            tip(graphics, mouseX, mouseY, "drivebysable.sensor_bus.save", ChatFormatting.GRAY);
            return;
        }

        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private void tip(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final String key,
            final ChatFormatting style
    ) {
        graphics.renderTooltip(font, Component.translatable(key).withStyle(style), mouseX, mouseY);
    }

    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        graphics.drawString(font, title, (TITLE_BAR_WIDTH - font.width(title)) / 2, TITLE_Y, TITLE_COLOR, false);
    }

    private int wellColor(final boolean editable) {
        return editable ? TEXT_COLOR : LOCKED_TEXT_COLOR;
    }
    //#endregion

}