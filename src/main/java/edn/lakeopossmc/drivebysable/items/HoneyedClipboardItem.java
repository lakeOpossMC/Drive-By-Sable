package edn.lakeopossmc.drivebysable.items;

import edn.lakeopossmc.drivebysable.advancement.CableAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

// --- THE HONEYED CLIPBOARD IN THE HAND --- //
public class HoneyedClipboardItem extends BlockItem {

    private static final float BITE_DAMAGE = 0.01F;

    public HoneyedClipboardItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return super.useOn(context);
        }
        return InteractionResult.PASS;
    }

    // * Eating a clipboard hurts a little!
    // * The health is put straight back
    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity entity) {
        final ItemStack remaining = super.finishUsingItem(stack, level, entity);
        if (entity instanceof final ServerPlayer player) {
            CableAdvancements.onClipboardEaten(player);
        }
        if (!level.isClientSide && entity.isAlive()) {
            final float before = entity.getHealth();
            entity.hurt(level.damageSources().generic(), Math.min(BITE_DAMAGE, before * 0.5F));
            if (entity.isAlive() && entity.getHealth() < before) {
                entity.setHealth(before);
            }
        }
        return remaining;
    }
}