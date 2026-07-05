package edn.lakeopossmc.drivebysable.items;

import com.simibubi.create.foundation.item.TooltipHelper;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterItem;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CableTypewriterHubItem extends LinkedTypewriterItem {

    private static final String TOOLTIP_KEY = "block.drivebysable.cable_typewriter_hub.tooltip";

    private static final Style GOLD_DARK = Style.EMPTY.withColor(0xC7954B);
    private static final Style GOLD_LIGHT = Style.EMPTY.withColor(0xEEDA78);

    public CableTypewriterHubItem(final CableTypewriterHubBlock block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {

        final boolean shiftDown = Screen.hasShiftDown();

        final Component shiftKey = Component.translatable("create.tooltip.keyShift")
                .copy().withStyle(shiftDown ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        tooltip.add(Component.translatable("create.tooltip.holdForDescription", shiftKey)
                .withStyle(ChatFormatting.DARK_GRAY));

        if (shiftDown) {
            tooltip.add(Component.empty());

            final MutableComponent summary = Component.translatable(TOOLTIP_KEY + ".summary");
            TooltipHelper.cutTextComponent(summary, GOLD_DARK, GOLD_LIGHT).forEach(tooltip::add);

            tooltip.add(Component.empty());

            for (int i = 1; i <= 4; i++) {
                final String conditionKey = TOOLTIP_KEY + ".condition" + i;
                final String behaviourKey = TOOLTIP_KEY + ".behaviour" + i;

                if (!I18n.exists(conditionKey) || !I18n.exists(behaviourKey)) continue;

                tooltip.add(Component.translatable(conditionKey).withStyle(ChatFormatting.GRAY));

                final MutableComponent behaviour = Component.translatable(behaviourKey);
                TooltipHelper.cutTextComponent(behaviour, GOLD_DARK, GOLD_LIGHT)
                        .forEach(line -> tooltip.add(Component.literal("  ").append(line)));
            }
        }

        if (stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
            final CompoundTag tag = stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
            if (tag.contains("Keys", CompoundTag.TAG_LIST)) {
                final int keyCount = tag.getList("Keys", CompoundTag.TAG_COMPOUND).size();
                tooltip.add(Component.translatable("drivebysable.cable_typewriter_hub.key_count", keyCount)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }
}