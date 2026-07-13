package edn.lakeopossmc.drivebysable.items;

import com.simibubi.create.foundation.item.TooltipHelper;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

// --- ITEM CLASS FOR BACKUP DRIVE --- //
// * Builds tooltip only, block does the work
public class NetworkBackupDriveItem extends BlockItem {

    private static final String TOOLTIP_KEY = "block.drivebysable.backup_drive.tooltip";

    private static final Style GOLD_DARK = Style.EMPTY.withColor(0xC7954B);
    private static final Style GOLD_LIGHT = Style.EMPTY.withColor(0xEEDA78);

    public NetworkBackupDriveItem(final NetworkBackupDriveBlock block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        final boolean shiftDown = Screen.hasShiftDown();

        final Component shiftKey = Component.translatable("create.tooltip.keyShift")
                .copy().withStyle(shiftDown ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        tooltip.add(Component.translatable("create.tooltip.holdForDescription", shiftKey)
                .withStyle(ChatFormatting.DARK_GRAY));

        if (!shiftDown) return;

        tooltip.add(Component.empty());

        final MutableComponent summary = Component.translatable(TOOLTIP_KEY + ".summary");
        TooltipHelper.cutTextComponent(summary, GOLD_DARK, GOLD_LIGHT).forEach(tooltip::add);
    }
}