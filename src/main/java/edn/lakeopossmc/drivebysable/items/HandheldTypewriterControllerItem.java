package edn.lakeopossmc.drivebysable.items;

import com.simibubi.create.foundation.item.TooltipHelper;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler;
import edn.lakeopossmc.drivebysable.CableBlocks;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableSounds;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlock;
import edn.lakeopossmc.drivebysable.blocks.HandheldTypewriterLecternBlock;
import edn.lakeopossmc.drivebysable.client.HandheldTypewriterClientHandler;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import edn.lakeopossmc.drivebysable.menu.HandheldTypewriterMenu;
import edn.lakeopossmc.drivebysable.network.HandheldTypewriterBindPacket;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

// --- HANDHELD TYPEWRITER CONTROLLER --- //
// * A Linked Typewriter you can carry
public class HandheldTypewriterControllerItem extends Item {

    private static final String TOOLTIP_KEY = "item.drivebysable.handheld_typewriter_controller.tooltip";
    private static final Style GOLD_DARK = Style.EMPTY.withColor(0xC7954B);
    private static final Style GOLD_LIGHT = Style.EMPTY.withColor(0xEEDA78);

    public HandheldTypewriterControllerItem(final Properties properties) {
        super(properties);
    }

    //#region // --- USE ON BLOCKS --- //
    @Override
    public InteractionResult onItemUseFirst(final ItemStack stack, final UseOnContext context) {
        final Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final BlockState state = level.getBlockState(pos);

        if (!player.mayBuild()) {
            return this.use(level, player, context.getHand()).getResult();
        }

        // * Sneaking swaps with a lectern already holding one, otherwise opens the screen
        if (player.isShiftKeyDown()) {
            if (state.getBlock() instanceof HandheldTypewriterLecternBlock lecternBlock) {
                if (!level.isClientSide) {
                    lecternBlock.withBlockEntityDo(level, pos, be -> be.swapControllers(stack, player, context.getHand(), state));
                }
                return InteractionResult.SUCCESS;
            }
            return this.use(level, player, context.getHand()).getResult();
        }

        // * Link binding, the key comes next on the client
        if (HandheldTypewriterBindPacket.frequencyAt(level, pos) != null) {
            if (level.isClientSide && FMLEnvironment.dist.isClient()) {
                HandheldTypewriterClientHandler.startBind(pos, context.getHand());
            }
            player.getCooldowns().addCooldown(this, 2);
            return InteractionResult.SUCCESS;
        }

        // * Hub binding, same as a controller on a cable hub
        if (state.getBlock() instanceof CableTypewriterHubBlock) {
            if (!level.isClientSide) {
                HubItem.putHub(stack, pos);
                level.playSound(null, pos, CableSounds.PLUG_IN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("drivebysable.handheld_typewriter.hub_connected"), true);
            }
            return InteractionResult.SUCCESS;
        }

        // * Onto an empty lectern
        if (state.is(Blocks.LECTERN) && !state.getValue(LecternBlock.HAS_BOOK)
                && CableBlocks.HANDHELD_TYPEWRITER_LECTERN != null) {
            if (!level.isClientSide) {
                final ItemStack lecternStack = player.isCreative() ? stack.copy() : stack.split(1);
                CableBlocks.HANDHELD_TYPEWRITER_LECTERN.get().replaceLectern(state, level, pos, lecternStack);
            }
            return InteractionResult.SUCCESS;
        }

        // * Let the lectern start using it
        if (state.getBlock() instanceof HandheldTypewriterLecternBlock) {
            return InteractionResult.PASS;
        }

        return this.use(level, player, context.getHand()).getResult();
    }
    //#endregion

    //#region // --- USE IN AIR --- //
    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            if (!level.isClientSide && player instanceof final ServerPlayer serverPlayer && player.mayBuild()) {
                serverPlayer.openMenu(
                        new SimpleMenuProvider((id, inv, p) -> HandheldTypewriterMenu.create(id, inv, stack), stack.getHoverName()),
                        buf -> HandheldTypewriterMenu.writeExtraData(buf, stack, level.registryAccess()));
            } else if (level.isClientSide && FMLEnvironment.dist.isClient()) {
                // * Simulated's key editor only listens while its handler is in screen binding mode
                LinkedTypewriterInteractionHandler.setMode(LinkedTypewriterInteractionHandler.Mode.SCREEN_BINDING);
                HandheldTypewriterClientHandler.onHandheldScreenOpening();
            }
            return InteractionResultHolder.success(stack);
        }

        if (!player.isShiftKeyDown()) {
            if (level.isClientSide && FMLEnvironment.dist.isClient()) {
                HandheldTypewriterClientHandler.toggle();
            }
            player.getCooldowns().addCooldown(this, 2);
        }

        return InteractionResultHolder.pass(stack);
    }
    //#endregion

    //#region // --- EXTENSION TOGGLE --- //
    @Override
    public boolean isEnabled(final FeatureFlagSet enabledFeatures) {
        return super.isEnabled(enabledFeatures) && isExtensionEnabled();
    }

    public static boolean isExtensionEnabled() {
        try {
            return CableConfig.CONFIG.handheldTypewriterController.get();
        } catch (final Exception e) {
            return true;
        }
    }
    //#endregion

    //#region // --- TOOLTIP --- //
    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

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

            // * Only pairs that actually have translations
            for (int i = 1; i <= 5; i++) {
                final String conditionKey = TOOLTIP_KEY + ".condition" + i;
                final String behaviourKey = TOOLTIP_KEY + ".behaviour" + i;
                if (!I18n.exists(conditionKey) || !I18n.exists(behaviourKey)) {
                    continue;
                }
                tooltip.add(Component.translatable(conditionKey).withStyle(ChatFormatting.GRAY));
                TooltipHelper.cutTextComponent(Component.translatable(behaviourKey), GOLD_DARK, GOLD_LIGHT)
                        .forEach(line -> tooltip.add(Component.literal("  ").append(line)));
            }
        }

        final int keyCount = HandheldTypewriterData.keyCount(stack);
        if (keyCount > 0) {
            tooltip.add(Component.translatable("drivebysable.handheld_typewriter.key_count", keyCount)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (HubItem.getHubPos(stack).isPresent()) {
            tooltip.add(Component.translatable("drivebysable.handheld_typewriter.hub_bound")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
    //#endregion
}