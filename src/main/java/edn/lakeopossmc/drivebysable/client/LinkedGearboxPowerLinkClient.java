package edn.lakeopossmc.drivebysable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxPowerLink;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

// --- HOVER BOXES AND ITEMS FOR THE POWER FREQUENCY SLOTS --- //
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class LinkedGearboxPowerLinkClient {

    private static final String OUTLINE_KEY = "drivebysable:gearboxPowerFrequency";

    private LinkedGearboxPowerLinkClient() {
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof final BlockHitResult result)
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        final BlockPos pos = result.getBlockPos();
        final LinkedGearboxPowerLink behaviour = BlockEntityBehaviour.get(minecraft.level, pos, LinkedGearboxPowerLink.TYPE);
        if (behaviour == null) {
            return;
        }

        for (final boolean first : new boolean[]{true, false}) {
            final Component label = Component.translatable(first
                    ? "drivebysable.linked_gearbox.power_frequency.first"
                    : "drivebysable.linked_gearbox.power_frequency.second");
            final boolean hit = behaviour.testHit(first, result.getLocation());
            final boolean empty = behaviour.stack(first).isEmpty();

            final ValueBox box = new ValueBox(label, new AABB(Vec3.ZERO, Vec3.ZERO).inflate(.25F), pos).passive(!hit);
            if (!empty) {
                box.wideOutline();
            }
            Outliner.getInstance()
                    .showOutline(Pair.of(OUTLINE_KEY + first, pos), box.transform(behaviour.slot(first)))
                    .highlightFace(result.getDirection());

            if (!hit) {
                continue;
            }
            final List<MutableComponent> tip = new ArrayList<>();
            tip.add(label.copy());
            tip.add(CreateLang.translateDirect(empty
                    ? "logistics.filter.click_to_set"
                    : "logistics.filter.click_to_replace"));
            CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tip);
        }
    }

    // * Items in the slots
    public static void renderItems(final SmartBlockEntity be, final PoseStack ms, final MultiBufferSource buffer,
                                   final int light, final int overlay) {
        if (be == null || be.isRemoved()) {
            return;
        }
        final Entity camera = Minecraft.getInstance().cameraEntity;
        final float max = AllConfigs.client().filterItemRenderDistance.getF();
        if (!be.isVirtual() && camera != null
                && camera.position().distanceToSqr(VecHelper.getCenterOf(be.getBlockPos())) > max * max) {
            return;
        }

        final LinkedGearboxPowerLink behaviour = be.getBehaviour(LinkedGearboxPowerLink.TYPE);
        if (behaviour == null) {
            return;
        }
        for (final boolean first : new boolean[]{true, false}) {
            final ValueBoxTransform transform = behaviour.slot(first);
            final ItemStack stack = behaviour.stack(first);
            ms.pushPose();
            transform.transform(be.getLevel(), be.getBlockPos(), be.getBlockState(), ms);
            ValueBoxRenderer.renderItemIntoValueBox(stack, ms, buffer, light, overlay);
            ms.popPose();
        }
    }
}