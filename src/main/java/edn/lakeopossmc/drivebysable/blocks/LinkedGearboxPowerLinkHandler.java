package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.RaycastHelper;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// --- CLICKING AN ITEM INTO THE POWER FREQUENCY SLOTS --- //
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID)
public final class LinkedGearboxPowerLinkHandler {

    private LinkedGearboxPowerLinkHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final Level level = event.getLevel();
        final Player player = event.getEntity();
        if (player == null || player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }

        final LinkedGearboxPowerLink behaviour = BlockEntityBehaviour.get(level, event.getPos(), LinkedGearboxPowerLink.TYPE);
        if (behaviour == null) {
            return;
        }

        final ItemStack held = player.getItemInHand(event.getHand());
        if (AllItems.LINKED_CONTROLLER.isIn(held) || AllItems.WRENCH.isIn(held)
                || held.is(CableItems.CABLE.get()) || held.is(CableItems.CABLE_CUTTER.get())) {
            return;
        }

        final BlockHitResult ray = RaycastHelper.rayTraceRange(level, player, 10);
        if (ray == null) {
            return;
        }

        for (final boolean first : new boolean[]{false, true}) {
            if (!behaviour.testHit(first, ray.getLocation())) {
                continue;
            }
            if (event.getSide() != LogicalSide.CLIENT) {
                behaviour.setFrequency(first, held);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            level.playSound(null, event.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25F, .1F);
        }
    }
}