package edn.lakeopossmc.drivebysable.client;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterRenderer;
import dev.simulated_team.simulated.index.SimSoundEvents;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.compat.CableTypewriterHubServerHandler;
import edn.lakeopossmc.drivebysable.network.CableTypewriterHubKeyPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientCableEvents {

    private static final boolean SIMULATED_LOADED = ModList.get().isLoaded("simulated");

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        if (!SIMULATED_LOADED) return;

        event.registerBlockEntityRenderer(
                CableBlockEntities.CABLE_TYPEWRITER_HUB.get(),
                LinkedTypewriterRenderer::new);
    }

    @SubscribeEvent
    public static void onKeyInput(final InputEvent.Key event) {
        if (!SIMULATED_LOADED) return;
        if (event.getAction() == GLFW.GLFW_REPEAT) return;

        final CableTypewriterHubBlockEntity be = CableTypewriterHubBlockEntity.getClientInstance();
        if (be == null) return;
        if (LinkedTypewriterInteractionHandler.getMode() != LinkedTypewriterInteractionHandler.Mode.ACTIVE) return;

        final int key = event.getKey();
        final String channel = CableTypewriterHubServerHandler.KEY_TO_CHANNEL.get(key);
        if (channel == null) return;

        if (be.getTypewriterEntries().getEntry(key) != null) return;

        if (!be.hasConnectionForChannel(channel)) return;

        suppressMatchingKeyMappings(Minecraft.getInstance(), key, event.getScanCode());

        final boolean press = event.getAction() == GLFW.GLFW_PRESS;

        PacketDistributor.sendToServer(
                new CableTypewriterHubKeyPacket(be.getBlockPos(), key, press));

        final var player = Minecraft.getInstance().player;
        if (player != null) {
            if (press) {
                SimSoundEvents.LINKED_TYPEWRITER_TAP.playAt(
                        player.level(), player.blockPosition(), 1.0F, 1.0F, true);
                LinkedTypewriterInteractionHandler.getPressedKeys()
                        .add(keyToRenderIndex(key));
            } else {
                SimSoundEvents.LINKED_TYPEWRITER_UNTAP.playAt(
                        player.level(), player.blockPosition(), 1.0F, 1.0F, true);
                LinkedTypewriterInteractionHandler.getPressedKeys()
                        .remove(Integer.valueOf(keyToRenderIndex(key)));
            }
        }
    }

    private static void suppressMatchingKeyMappings(final Minecraft mc, final int key, final int scanCode) {
        for (final KeyMapping mapping : mc.options.keyMappings) {
            if (!mapping.matches(key, scanCode)) continue;
            while (mapping.consumeClick()) {
            }
            mapping.setDown(false);
        }
    }

    private static int keyToRenderIndex(final int keycode) {
        return switch (keycode) {
            case 81, 49, 321  -> 0;   // Q, 1, KP_1
            case 87, 50, 322  -> 1;   // W, 2, KP_2
            case 69, 51, 323  -> 2;   // E, 3, KP_3
            case 52, 324      -> 3;   //    4, KP_4
            case 265, 53, 325 -> 4;   // UP, 5, KP_5
            case 54, 326      -> 5;   //    6, KP_6
            case 65, 55, 327  -> 6;   // A, 7, KP_7
            case 83, 56, 328  -> 7;   // S, 8, KP_8
            case 68, 57, 329  -> 8;   // D, 9, KP_9
            case 263          -> 10;  // LEFT
            case 264          -> 11;  // DOWN
            case 262, 48, 320 -> 12;  // RIGHT, 0, KP_0
            case 32           -> 13;  // SPACE
            default           -> (int)(new java.util.Random(keycode).nextInt(13));
        };
    }

    private ClientCableEvents() {}
}