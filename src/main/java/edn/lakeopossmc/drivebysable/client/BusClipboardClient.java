package edn.lakeopossmc.drivebysable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.foundation.utility.CreateLang;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.BusClipboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

import java.util.ArrayList;
import java.util.List;

// --- WHAT A HELD CLIPBOARD SHOWS ON A BUS --- //
// * Create draws both of these for the hubs, see ClipboardValueSettingsHandler
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class BusClipboardClient {
    private BusClipboardClient() {
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isSpectator()
                || !(minecraft.hitResult instanceof final BlockHitResult target)
                || target.getType() != HitResult.Type.BLOCK) {
            return;
        }

        final ItemStack held = minecraft.player.getMainHandItem();
        final BlockPos pos = target.getBlockPos();
        if (!AllBlocks.CLIPBOARD.isIn(held) || !BusClipboard.isBus(minecraft.level, pos)) {
            return;
        }

        final boolean canCopy = BusClipboard.write(minecraft.level, pos, new CompoundTag());
        final boolean canPaste = BusClipboard.canPaste(minecraft.level, pos, BusClipboard.copiedValues(held));
        if (!canCopy && !canPaste) {
            return;
        }

        final List<MutableComponent> tip = new ArrayList<>();
        tip.add(CreateLang.translateDirect("clipboard.actions"));
        if (canCopy) {
            tip.add(CreateLang.translateDirect("clipboard.to_copy", Component.keybind("key.use")));
        }
        if (canPaste) {
            tip.add(CreateLang.translateDirect("clipboard.to_paste", Component.keybind("key.attack")));
        }
        CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tip);
    }

    @SubscribeEvent
    public static void drawBlockSelection(final RenderHighlightEvent.Block event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isSpectator()) {
            return;
        }

        final BlockPos pos = event.getTarget().getBlockPos();
        if (!AllBlocks.CLIPBOARD.isIn(minecraft.player.getMainHandItem())
                || !minecraft.level.getWorldBorder().isWithinBounds(pos)
                || !BusClipboard.isBus(minecraft.level, pos)) {
            return;
        }

        final VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
        if (shape.isEmpty()) {
            return;
        }

        final VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        TrackBlockOutline.renderShape(shape, poseStack, buffer, true);
        poseStack.popPose();
        event.setCanceled(true);
    }
}