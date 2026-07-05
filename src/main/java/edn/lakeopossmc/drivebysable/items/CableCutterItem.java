package edn.lakeopossmc.drivebysable.items;

import com.simibubi.create.foundation.item.TooltipHelper;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class CableCutterItem extends Item {
    public CableCutterItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        final BlockPos pos = context.getClickedPos();
        if (CableNetworkManager.removeAllFromSource((ServerPlayer) context.getPlayer(), level, pos)) {
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        return InteractionResult.SUCCESS;
    }

    private static final String TOOLTIP_KEY = "item.drivebysable.cable_cutter.tooltip";

    private static final Style GOLD_DARK = Style.EMPTY.withColor(0xC7954B);
    private static final Style GOLD_LIGHT = Style.EMPTY.withColor(0xEEDA78);

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
}
