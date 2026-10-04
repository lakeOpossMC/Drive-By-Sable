package edn.lakeopossmc.drivebysable.items;

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
    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity entity) {
        final ItemStack remaining = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity.isAlive()) {
            entity.hurt(level.damageSources().generic(), Math.min(BITE_DAMAGE, entity.getHealth() * 0.5F));
        }
        return remaining;
    }
}