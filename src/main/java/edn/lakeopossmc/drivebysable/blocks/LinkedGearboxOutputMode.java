package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// --- SPEED GEARBOX OUTPUT MODES --- //
public enum LinkedGearboxOutputMode implements INamedIconOptions {
    INPUT_SPEED("input_speed", 1, false),
    DOUBLE_SPEED("double_speed", 2, false),
    INPUT_SPEED_REVERSED("input_speed_reversed", 1, true),
    DOUBLE_SPEED_REVERSED("double_speed_reversed", 2, true);

    private static final String LANG_PREFIX = "drivebysable.linked_gearbox.mode.";

    private final String translationKey;
    private final int multiplier;
    private final boolean reversed;
    private final AllIcons icon;

    LinkedGearboxOutputMode(final String name, final int multiplier, final boolean reversed) {
        this.translationKey = LANG_PREFIX + name;
        this.multiplier = multiplier;
        this.reversed = reversed;
        this.icon = new LinkedGearboxModeIcon(ordinal());
    }

    public static LinkedGearboxOutputMode byIndex(final int index) {
        final LinkedGearboxOutputMode[] modes = values();
        return index >= 0 && index < modes.length ? modes[index] : INPUT_SPEED;
    }

    public int ratio() {
        return multiplier * (reversed ? -1 : 1);
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }

    public MutableComponent displayName() {
        return Component.translatable(translationKey);
    }

    public MutableComponent goggleText() {
        return Component.translatable("drivebysable.linked_gearbox.goggles.speed",
                Component.translatable(multiplier == 2
                        ? "drivebysable.linked_gearbox.goggles.double_speed"
                        : "drivebysable.linked_gearbox.goggles.input_speed"),
                Component.translatable(reversed
                        ? "drivebysable.linked_gearbox.goggles.reversed"
                        : "drivebysable.linked_gearbox.goggles.forwards"));
    }
}