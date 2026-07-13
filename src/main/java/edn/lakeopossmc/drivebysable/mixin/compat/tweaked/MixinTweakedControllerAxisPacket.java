package edn.lakeopossmc.drivebysable.mixin.compat.tweaked;

import com.getitemfromblock.create_tweaked_controllers.block.TweakedLecternControllerBlockEntity;
import com.getitemfromblock.create_tweaked_controllers.controller.ControllerRedstoneOutput;
import com.getitemfromblock.create_tweaked_controllers.packet.TweakedLinkedControllerAxisPacket;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

// --- FORWARD TWEAKED CONTROLLER AXIS TO CABLES --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(TweakedLinkedControllerAxisPacket.class)
public abstract class MixinTweakedControllerAxisPacket {
    @Shadow private int axis;

    // * Push to lectern hub
    @Inject(method = "handleLectern", at = @At("RETURN"), remap = false)
    private void drivebysable$handleLectern(
        final ServerPlayer player,
        final TweakedLecternControllerBlockEntity lectern,
        final CallbackInfo ci
    ) {
        final List<Byte> axisValues = decodeAxis(axis);
        TweakedControllerCableServerHandler.receiveAxis(player.level(), lectern.getBlockPos(), axisValues);
        if (lectern instanceof final LecternCableHubDuck lecternHub && lecternHub.drivebysable$getHubPos() != null) {
            TweakedControllerCableServerHandler.receiveAxis(player.level(), lecternHub.drivebysable$getHubPos(), axisValues);
        }
    }

    // * Push to hub bound on held item
    @Inject(method = "handleItem", at = @At("RETURN"), remap = false)
    private void drivebysable$handleItem(final ServerPlayer player, final ItemStack heldItem, final CallbackInfo ci) {
        HubItem.ifHubPresent(heldItem, pos -> TweakedControllerCableServerHandler.receiveAxis(player.level(), pos, decodeAxis(axis)));
    }

    // * Unpack packed axis bits into per channel values
    private static List<Byte> decodeAxis(final int axis) {
        final ControllerRedstoneOutput output = new ControllerRedstoneOutput();
        final List<Byte> axisValues = new ArrayList<>(TweakedControllerCableServerHandler.AXIS_TO_CHANNEL.length);
        output.DecodeAxis(axis);

        for (byte index = 0; index < TweakedControllerCableServerHandler.AXIS_TO_CHANNEL.length; index++) {
            byte value = 0;
            if (index < 8) {
                final boolean highBit = (output.axis[index / 2] & 16) != 0;
                if ((index % 2 == 1) == highBit) {
                    value = (byte) (output.axis[index / 2] & 15);
                }
            } else {
                value = output.axis[index - 4];
            }

            axisValues.add(value);
        }

        return axisValues;
    }
}
