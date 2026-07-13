package edn.lakeopossmc.drivebysable.cable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// --- INVALID OP FLASH FOR SERVER TRIGGERED MESSAGES --- //
// * Red/white error message flash for server side
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID)
public final class CableServerFeedback {
    private CableServerFeedback() {
    }

    private static final List<ScheduledAction> scheduledActions = new ArrayList<>();

    // * Uses the deny sound, matches how create's track item flags bad placement
    public static void showInvalidOperationMessage(final ServerPlayer player, final String langKey) {
        player.displayClientMessage(Component.translatable(langKey).withStyle(ChatFormatting.RED), true);
        scheduledActions.add(new ScheduledAction(2, () -> {
            player.displayClientMessage(Component.translatable(langKey).withStyle(ChatFormatting.WHITE), true);
            player.level().playSound(null, player.blockPosition(), AllSoundEvents.DENY.getMainEvent(), SoundSource.PLAYERS, 1.0F, 0.5F);
        }));
        scheduledActions.add(new ScheduledAction(4, () ->
                player.displayClientMessage(Component.translatable(langKey).withStyle(ChatFormatting.RED), true)));
    }

    // * Checked for clipboard copy/paste attempt
    @SubscribeEvent(receiveCanceled = true)
    public static void onClipboardCopyAttempt(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) {
            return;
        }

        final Player player = event.getEntity();
        if (!(player instanceof final ServerPlayer serverPlayer) || player.isSpectator()
                || player.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        final ItemStack heldItem = event.getItemStack();
        final BlockPos pos = event.getPos();
        final Level level = event.getLevel();

        // * Warn when source is empty
        if (AllBlocks.CLIPBOARD.isIn(heldItem)
                && level.getBlockEntity(pos) instanceof CableHubBlockEntity
                && !hasConnections(level, pos)) {
            showInvalidOperationMessage(serverPlayer, "drivebysable.invalid_op.no_connections");
        }
    }

    private static boolean hasConnections(final Level level, final BlockPos pos) {
        final var perChannel = CableNetworkManager.get(level).getNetwork().get(pos.asLong());
        return perChannel != null && perChannel.values().stream().anyMatch(sinks -> !sinks.isEmpty());
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event) {
        if (scheduledActions.isEmpty()) {
            return;
        }

        final Iterator<ScheduledAction> iterator = scheduledActions.iterator();
        while (iterator.hasNext()) {
            final ScheduledAction scheduled = iterator.next();
            if (--scheduled.ticksRemaining <= 0) {
                scheduled.action.run();
                iterator.remove();
            }
        }
    }

    private static final class ScheduledAction {
        int ticksRemaining;
        final Runnable action;

        ScheduledAction(final int ticksRemaining, final Runnable action) {
            this.ticksRemaining = ticksRemaining;
            this.action = action;
        }
    }
}