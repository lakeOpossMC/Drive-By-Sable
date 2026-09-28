package edn.lakeopossmc.drivebysable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.client.HandheldTypewriterClientHandler;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// --- ANIMATED HANDHELD TYPEWRITER CONTROLLER --- //
public class HandheldTypewriterItemRenderer extends CustomRenderedItemModelRenderer {

    private static final String PREFIX = "item/handheld_typewriter_controller/";
    public static final PartialModel BASE = PartialModel.of(DriveBySableMod.asResource(PREFIX + "base"));
    public static final PartialModel POWERED = PartialModel.of(DriveBySableMod.asResource(PREFIX + "powered"));
    public static final PartialModel KEY = PartialModel.of(DriveBySableMod.asResource(PREFIX + "key"));
    public static final PartialModel SPACE_BAR = PartialModel.of(DriveBySableMod.asResource(PREFIX + "space_bar"));

    private static final float KEY_DEPTH = 0.75F;

    //#region // --- KEY LAYOUT --- //
    private static final float[][] KEY_OFFSETS = {
            {0, 0, 8}, {0, 0, 6}, {0, 0, 4}, {0, 0, 2}, {0, 0, 0},
            {2, -0.5F, 9}, {2, -0.5F, 7}, {2, -0.5F, 5}, {2, -0.5F, 3}, {2, -0.5F, 1}, {2, -0.5F, -1},
            {0, 0, 0}
    };
    public static final int KEY_COUNT = KEY_OFFSETS.length;
    private static final int SPACE_BAR_INDEX = 11;

    // * Rows of a US keyboard, left to right, spread across the model's rows when not listed below
    private static final int[][] BACK_ROWS = {
            {GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5,
                    GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS,
                    GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_BACKSPACE},
            {GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T,
                    GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P,
                    GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_BACKSLASH}
    };
    private static final int[][] FRONT_ROWS = {
            {GLFW.GLFW_KEY_CAPS_LOCK, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G,
                    GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_SEMICOLON,
                    GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_ENTER},
            {GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B,
                    GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH,
                    GLFW.GLFW_KEY_RIGHT_SHIFT}
    };

    // * Which model key a keyboard key presses, -1 for none
    public static int keyToModelKey(final int key) {
        // * Common control keys get their own model key so they never share one
        switch (key) {
            case GLFW.GLFW_KEY_SPACE:
                return SPACE_BAR_INDEX;
            case GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_1:
                return 0;
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_2:
                return 1;
            case GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_3:
                return 2;
            case GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_4:
                return 3;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_5:
                return 4;
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_LEFT_ALT:
                return 5;
            case GLFW.GLFW_KEY_S:
                return 6;
            case GLFW.GLFW_KEY_D:
                return 7;
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_F:
                return 8;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_G:
                return 9;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_RIGHT_CONTROL,
                 GLFW.GLFW_KEY_RIGHT_ALT:
                return 10;
            default:
                break;
        }

        final int back = spread(BACK_ROWS, key, 5);
        if (back >= 0) {
            return back;
        }
        final int front = spread(FRONT_ROWS, key, 6);
        if (front >= 0) {
            return 5 + front;
        }

        // * Anything else still moves a key
        return key < 0 ? -1 : Math.floorMod(key * 31, SPACE_BAR_INDEX);
    }

    private static int spread(final int[][] rows, final int key, final int keysInRow) {
        for (final int[] row : rows) {
            for (int column = 0; column < row.length; column++) {
                if (row[column] == key) {
                    return Math.min(keysInRow - 1, column * keysInRow / row.length);
                }
            }
        }
        return -1;
    }
    //#endregion

    //#region // --- ANIMATION STATE --- //
    private static final LerpedFloat EQUIP_PROGRESS = LerpedFloat.linear().startWithValue(0);
    private static final List<LerpedFloat> KEYS = new ArrayList<>(KEY_COUNT);

    static {
        for (int i = 0; i < KEY_COUNT; i++) {
            KEYS.add(LerpedFloat.linear().startWithValue(0));
        }
    }

    // * Touching the class is enough to register the partial models
    public static void load() {
    }

    // * Called every client tick by the client handler
    public static void tick() {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        final boolean inUse = HandheldTypewriterClientHandler.isActiveInHand()
                || HandheldTypewriterClientHandler.getMode() == HandheldTypewriterClientHandler.Mode.BIND;
        EQUIP_PROGRESS.chase(inUse ? 1 : 0, 0.2F, Chaser.EXP);
        EQUIP_PROGRESS.tickChaser();

        final boolean[] held = new boolean[KEY_COUNT];
        if (HandheldTypewriterClientHandler.getMode() == HandheldTypewriterClientHandler.Mode.ACTIVE) {
            final Set<Integer> pressed = HandheldTypewriterClientHandler.getPressedKeys();
            for (final int key : pressed) {
                final int index = keyToModelKey(key);
                if (index >= 0) {
                    held[index] = true;
                }
            }
        }

        for (int i = 0; i < KEY_COUNT; i++) {
            final LerpedFloat lerped = KEYS.get(i);
            lerped.chase(held[i] ? 1 : 0, 0.4F, Chaser.EXP);
            lerped.tickChaser();
        }
    }

    public static void resetKeys() {
        for (final LerpedFloat key : KEYS) {
            key.startWithValue(0);
        }
    }
    //#endregion

    //#region // --- RENDERING --- //
    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model, final PartialItemModelRenderer renderer,
                          final ItemDisplayContext transformType, final PoseStack ms, final MultiBufferSource buffer,
                          final int light, final int overlay) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        final float pt = AnimationTickHolder.getPartialTicks();

        ms.pushPose();

        boolean active = false;
        int keyLight = light;
        if (player != null) {
            final boolean rightHanded = minecraft.options.mainHand().get() == HumanoidArm.RIGHT;
            final ItemDisplayContext mainHand = rightHanded
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            final ItemDisplayContext offHand = rightHanded
                    ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            final boolean noControllerInMain = !HandheldTypewriterData.isController(player.getMainHandItem());

            // * Lift and tilt the one in use towards the player
            if (transformType == mainHand || (transformType == offHand && noControllerInMain)) {
                final float equip = EQUIP_PROGRESS.getValue(pt);
                final int handModifier = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1 : 1;
                ms.translate(0, equip / 4, equip / 4 * handModifier);
                ms.mulPose(Axis.YP.rotationDegrees(equip * -30 * handModifier));
                ms.mulPose(Axis.ZP.rotationDegrees(equip * -30));
                active = true;
            }

            if (transformType == ItemDisplayContext.GUI) {
                active = stack == player.getMainHandItem() || (stack == player.getOffhandItem() && noControllerInMain);
            }

            active &= HandheldTypewriterClientHandler.isActiveInHand()
                    || HandheldTypewriterClientHandler.getMode() == HandheldTypewriterClientHandler.Mode.BIND;

            if (active && HandheldTypewriterClientHandler.getMode() == HandheldTypewriterClientHandler.Mode.BIND) {
                final int level = (int) Mth.lerp((Mth.sin(AnimationTickHolder.getRenderTime() / 4F) + 1) / 2, 5, 15);
                keyLight = level << 20;
            }
        }

        renderModel(renderer, ms, light, keyLight, active, active);
        ms.popPose();
    }

    public static void renderInLectern(final PartialItemModelRenderer renderer, final PoseStack ms, final int light,
                                       final boolean active, final boolean renderDepression) {
        renderModel(renderer, ms, light, light, active, renderDepression);
    }

    private static void renderModel(final PartialItemModelRenderer renderer, final PoseStack ms, final int light,
                                    final int keyLight, final boolean powered, final boolean depress) {
        final float pt = AnimationTickHolder.getPartialTicks();
        final float s = 1 / 16F;

        renderer.render(powered ? POWERED.get() : BASE.get(), light);

        final BakedModel key = KEY.get();
        for (int i = 0; i < KEY_COUNT; i++) {
            final float[] offset = KEY_OFFSETS[i];
            final float depression = depress ? -KEY_DEPTH * KEYS.get(i).getValue(pt) : 0;

            ms.pushPose();
            ms.translate(offset[0] * s, (offset[1] + depression) * s, offset[2] * s);
            renderer.render(i == SPACE_BAR_INDEX ? SPACE_BAR.get() : key, keyLight);
            ms.popPose();
        }
    }
    //#endregion
}