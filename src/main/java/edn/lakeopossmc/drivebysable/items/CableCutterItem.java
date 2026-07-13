package edn.lakeopossmc.drivebysable.items;

import com.simibubi.create.foundation.item.TooltipHelper;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableServerFeedback;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
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
import java.util.Map;
import java.util.Set;

// --- ITEM CLASS FOR CABLE CUTTER --- //
// * Checks specifically if the item is used with sneak-click
// * Builds full tooltip
public class CableCutterItem extends Item {
    //#region // --- ITEM PROPERTIES SETUP --- //
    public CableCutterItem(final Properties properties) {
        super(properties);
    }
    //#endregion

    //#region // --- ITEM INTERACTION --- //
    @Override
    public InteractionResult useOn(final UseOnContext context) {
        // * Get current world/dim
        final Level level = context.getLevel();
        // * Get where click happened
        final BlockPos pos = context.getClickedPos();

        // * Check if on client side first
        if (level.isClientSide()) {
            final Map<String, Set<CableNetworkSink>> perChannel = CableNetworkManager.get(level).getNetwork().get(pos.asLong());
            final boolean hasConnections = perChannel != null && perChannel.values().stream().anyMatch(sinks -> !sinks.isEmpty());
            return hasConnections ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }

        // * Check whether connections were removed or not
        final boolean removed = CableNetworkManager.removeAllFromSource((ServerPlayer) context.getPlayer(), level, pos);
        if (removed) {
            // * Play shear use sound if success
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            // * Flash the error and play the deny sound
            CableServerFeedback.showInvalidOperationMessage((ServerPlayer) context.getPlayer(), "drivebysable.invalid_op.no_connections");
        }

        // * Return correct trigger for item use anim
        return removed ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
    //#endregion

    //#region // --- HOLD [SHIFT] TOOLTIP INFO && FEATURE --- //
    // * This is the main key
    private static final String TOOLTIP_KEY = "item.drivebysable.cable_cutter.tooltip";
    // * This is for fancy text color
    private static final Style GOLD_DARK = Style.EMPTY.withColor(0xC7954B);
    private static final Style GOLD_LIGHT = Style.EMPTY.withColor(0xEEDA78);
    // * Actually constructs the tooltip
    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        // * Append the text
        super.appendHoverText(stack, context, tooltip, flag);
        // * Check if shift held
        final boolean shiftDown = Screen.hasShiftDown();
        // * Find translation key for tip Hold [Shift] hint and apply color
        final Component shiftKey = Component.translatable("create.tooltip.keyShift")
                .copy().withStyle(shiftDown ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        tooltip.add(Component.translatable("create.tooltip.holdForDescription", shiftKey)
                .withStyle(ChatFormatting.DARK_GRAY));
        // * If shift not held, get outta here
        if (!shiftDown) return;

        // * Add spacing
        tooltip.add(Component.empty());
        // * Find translation key for item summary
        final MutableComponent summary = Component.translatable(TOOLTIP_KEY + ".summary");
        TooltipHelper.cutTextComponent(summary, GOLD_DARK, GOLD_LIGHT).forEach(tooltip::add);
        // * Add spacing
        tooltip.add(Component.empty());

        // * Loop for all matching conditions and behaviours
        for (int i = 1; i <= 4; i++) {
            // * Find keys
            final String conditionKey = TOOLTIP_KEY + ".condition" + i;
            final String behaviourKey = TOOLTIP_KEY + ".behaviour" + i;
            // * If they exist, continue
            if (!I18n.exists(conditionKey) || !I18n.exists(behaviourKey)) continue;
            // * Add text for condition and apply color
            tooltip.add(Component.translatable(conditionKey).withStyle(ChatFormatting.GRAY));
            // * Add text for behaviour and apply color
            final MutableComponent behaviour = Component.translatable(behaviourKey);
            TooltipHelper.cutTextComponent(behaviour, GOLD_DARK, GOLD_LIGHT)
                    .forEach(line -> tooltip.add(Component.literal("  ").append(line)));
        }
    }
    //#endregion
}