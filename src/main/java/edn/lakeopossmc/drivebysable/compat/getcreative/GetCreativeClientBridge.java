package edn.lakeopossmc.drivebysable.compat.getcreative;

import amaryllis.get_creative.appliances.linked_controller.AllLinkedDevices;
import amaryllis.get_creative.appliances.linked_controller.LinkedDevicesClient;
import amaryllis.get_creative.appliances.linked_controller.base.LinkedDeviceClientHandler;
import edn.lakeopossmc.drivebysable.mixin.client.MixinLinkedDeviceClientHandlerAccessor;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

// --- EVERY CLIENT SIDE GET CREATIVE TYPE LIVES BEHIND THIS CLASS --- //
// * Nothing here is touched unless get_creative is loaded
public final class GetCreativeClientBridge {
    private GetCreativeClientBridge() {
    }

    // * Each device has its own client handler
    @Nullable
    public static BlockPos getActiveLecternPos() {
        final int deviceCount = AllLinkedDevices.BY_INDEX.size();
        for (int index = 0; index < deviceCount; index++) {
            final LinkedDeviceClientHandler handler = LinkedDevicesClient.getClientHandler(index);
            if (handler == null || !handler.inLectern()) {
                continue;
            }

            final BlockPos lecternPos = ((MixinLinkedDeviceClientHandlerAccessor) handler).drivebysable$getLecternPos();
            if (lecternPos != null) {
                return lecternPos;
            }
        }
        return null;
    }
}
