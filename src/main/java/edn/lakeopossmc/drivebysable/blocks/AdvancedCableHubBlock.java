package edn.lakeopossmc.drivebysable.blocks;

import edn.lakeopossmc.drivebysable.CableSounds;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.mixinducks.TweakedControllerDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// --- DIRECTIONAL ADVANCED CONTROLLER HUB --- //
// * Define channel list based on tweaked controller
// * Link to tweaked controller on use
public class AdvancedCableHubBlock extends AbstractDirectionalHubBlock {
    //#region // --- DEF CHANNELS AND APPEND TO LIST --- //
    private static final List<String> CHANNELS = Stream.concat(
            Arrays.stream(TweakedControllerCableServerHandler.AXIS_TO_CHANNEL),
            Arrays.stream(TweakedControllerCableServerHandler.BUTTON_TO_CHANNEL)
    ).toList();
    //#endregion

    // --- GET PROPS FROM MAIN --- //
    public AdvancedCableHubBlock(final Properties properties) {
        super(properties);
    }

    // --- TELL MAIN THE CHANNELS TO LIST ON USE --- //
    @Override
    protected List<String> channels() {
        return CHANNELS;
    }

    //#region // --- CHECK FOR TWEAKED CONTROLLER USE --- //
    @Override
    protected ItemInteractionResult useItemOn(
            final ItemStack itemStack,
            final BlockState state,
            final Level level,
            final BlockPos blockPos,
            final Player player,
            final InteractionHand interactionHand,
            final BlockHitResult hitResult
    ) {
        // * Make sure we're looking for the correct item for linking
        if (!(itemStack.getItem() instanceof TweakedControllerDuck)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // * When successful, show message and play sound
        if (!level.isClientSide()) {
            HubItem.putHub(itemStack, blockPos);
            level.playSound(null, blockPos, CableSounds.PLUG_IN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.literal("Controller connected!"), true);
        }
        // * Return a success trigger
        return ItemInteractionResult.SUCCESS;
    }
    //#endregion
}