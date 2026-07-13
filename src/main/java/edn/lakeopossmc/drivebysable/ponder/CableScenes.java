package edn.lakeopossmc.drivebysable.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.instruction.ChaseAABBInstruction;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// --- ALL PONDER SCENES FOR THE MOD --- //
// * Story boards for hub, lectern, and typewriter intros
public class CableScenes {
    // --- SINGLE FACE BOX HELPER --- //
    private static AABB faceBox(final BlockPos pos, final Direction face) {
        final double margin = 0.05;
        final double outset = 0.002;
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return switch (face) {
            case NORTH -> new AABB(x + margin, y + margin, z - outset,     x + 1 - margin, y + 1 - margin, z + outset);
            case SOUTH -> new AABB(x + margin, y + margin, z + 1 - outset, x + 1 - margin, y + 1 - margin, z + 1 + outset);
            case WEST  -> new AABB(x - outset,     y + margin, z + margin, x + outset,     y + 1 - margin, z + 1 - margin);
            case EAST  -> new AABB(x + 1 - outset, y + margin, z + margin, x + 1 + outset, y + 1 - margin, z + 1 - margin);
            case UP    -> new AABB(x + margin, y + 1 - outset, z + margin, x + 1 - margin, y + 1 + outset, z + 1 - margin);
            case DOWN  -> new AABB(x + margin, y - outset,     z + margin, x + 1 - margin, y + outset,     z + 1 - margin);
        };
    }
    // --- GRAY FACE BOX HELPER --- //
    private static PonderInstruction grayFaceInstruction(final Object slot, final AABB box, final int ticks) {
        return new ChaseAABBInstruction(PonderPalette.WHITE, slot, box, ticks) {
            @Override
            public void tick(final net.createmod.ponder.foundation.PonderScene scene) {
                super.tick(scene);
                scene.getOutliner()
                        .chaseAABB(slot, box)
                        .colored(ChatFormatting.DARK_GRAY.getColor());
            }
        };
    }
    // --- LINE DRAW HELPER --- //
    private static PonderInstruction coloredLineInstruction(final Object slot,
                                                            final Vec3 start, final Vec3 end, final int color, final int ticks) {
        final AABB dummyBox = new AABB(start.x, start.y, start.z, start.x, start.y, start.z);
        return new ChaseAABBInstruction(PonderPalette.WHITE, "__line_dummy_" + slot, dummyBox, ticks) {
            @Override
            public void tick(final net.createmod.ponder.foundation.PonderScene s) {
                super.tick(s);
                s.getOutliner()
                        .showLine(slot, start, end)
                        .colored(color)
                        .lineWidth(0.0625f);
            }
        };
    }
    // --- BIND TWEAKED CONTROLLER HELPER --- //
    private static void bindLecternController(final BlockEntity blockEntity, final ItemStack controller) {
        try {
            final Method setController = blockEntity.getClass().getMethod("setController", ItemStack.class);
            setController.invoke(blockEntity, controller);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            DriveBySableMod.LOGGER.debug("Failed to bind controller to lectern at {}", blockEntity.getBlockPos(), exception);
        }
    }

    //#region // --- CABLE HUB INTRO SCENE --- //
    public static void cableHubIntro(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var effects = scene.effects();
        final var select = util.select();
        final var vector = util.vector();

        final BlockPos hubPos       = new BlockPos(2, 2, 1);
        final BlockPos lampPos      = new BlockPos(3, 3, 3);
        final BlockPos noteBlockPos = new BlockPos(1, 2, 3);

        final String HUB_SLOT  = "hub";
        final String LAMP_SLOT = "lamp";
        final String NOTE_SLOT = "note";

        final String LINE_LAMP_SLOT = "line_lamp";
        final String LINE_NOTE_SLOT = "line_note";

        final Vec3 hubCenter      = new Vec3(hubPos.getX() + 0.5, hubPos.getY() + 0.5, hubPos.getZ() + 0.5);
        final Vec3 lampFaceCenter = new Vec3(lampPos.getX() + 0.5, lampPos.getY() + 0.5, lampPos.getZ());
        final Vec3 noteFaceCenter = new Vec3(noteBlockPos.getX() + 0.5, noteBlockPos.getY() + 1.0, noteBlockPos.getZ() + 0.5);

        final int RED  = Color.RED.getRGB();
        final int DARK_GRAY = ChatFormatting.DARK_GRAY.getColor();

        scene.title("cable_hub_intro", "Using a Cable Hub");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // REVEAL CABLE HUB
        world.showSection(select.fromTo(2, 1, 1, 2, 2, 1), Direction.UP);
        scene.idle(20);

        // POINT TO CABLE HUB AND LABEL
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_intro.text_1")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        // DESCRIBE CABLE HUB
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_intro.text_2")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // SHOW LAMP TOWER
        world.showSection(select.fromTo(3, 1, 3, 3, 3, 3), Direction.DOWN);
        scene.idle(20);
        // SHOW NOTEBLOCK TOWER
        world.showSection(select.fromTo(1, 1, 3, 1, 2, 3), Direction.DOWN);
        scene.idle(20);

        // USE CABLE ON HUB
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_intro.text_3")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.INPUT, HUB_SLOT,
                new AABB(hubPos.getX(), hubPos.getY(), hubPos.getZ(),
                        hubPos.getX() + 1, hubPos.getY() + 0.5, hubPos.getZ() + 1),
                350));
        scene.idle(7);
        // USE CABLE ON LAMP
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_intro.text_4")))
                .placeNearTarget()
                .pointAt(vector.centerOf(lampPos));
        scene.idle(80);
        overlay.showControls(vector.blockSurface(lampPos, Direction.NORTH), Pointing.RIGHT, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, LAMP_SLOT, faceBox(lampPos, Direction.NORTH), 35));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP_SLOT, hubCenter, lampFaceCenter, RED, 35));
        scene.idle(22);

        // SCROLL TO SELECT NEW CHANNEL
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_intro.text_5")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(20);
        scene.addInstruction(grayFaceInstruction(LAMP_SLOT, faceBox(lampPos, Direction.NORTH), 212));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP_SLOT, hubCenter, lampFaceCenter, DARK_GRAY, 212));
        scene.idle(60);
        // USE CABLE ON NOTEBLOCK
        overlay.showControls(vector.blockSurface(noteBlockPos, Direction.UP), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, NOTE_SLOT, faceBox(noteBlockPos, Direction.UP), 145));
        scene.addInstruction(coloredLineInstruction(LINE_NOTE_SLOT, hubCenter, noteFaceCenter, RED, 145));
        scene.idle(22);

        // EXIT SETUP MODE
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_intro.text_6")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(50);

        // USE LINKED CONTROLLER
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_intro.text_7")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 40)
                .withItem(new ItemStack(AllItems.LINKED_CONTROLLER.asItem()));
        scene.idle(50);

        // SHOW [W] CHANNEL
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_W);
        scene.idle(10);
        world.modifyBlock(lampPos, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
        effects.indicateRedstone(lampPos);
        scene.idle(25);
        world.modifyBlock(lampPos, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
        scene.idle(15);
        // SHOW [S] CHANNEL
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_S);
        scene.idle(10);
        world.modifyBlock(noteBlockPos, s -> s.setValue(NoteBlock.POWERED, true), false);
        effects.indicateRedstone(noteBlockPos);
        effects.emitParticles(
                vector.topOf(noteBlockPos).add(0, 0.5, 0),
                effects.simpleParticleEmitter(ParticleTypes.NOTE, Vec3.ZERO),
                1.0f, 1);
        scene.idle(25);
        world.modifyBlock(noteBlockPos, s -> s.setValue(NoteBlock.POWERED, false), false);
        scene.idle(10);

        // END SCENE
        scene.markAsFinished();
    }
    //#endregion
    //#region // --- CABLE HUB LECTERN SCENE --- //
    public static void cableHubLecternIntro(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var effects = scene.effects();
        final var select = util.select();
        final var vector = util.vector();

        final BlockPos hubPos     = new BlockPos(1, 2, 1);
        final BlockPos lamp1Pos   = new BlockPos(1, 3, 3);
        final BlockPos lamp2Pos   = new BlockPos(3, 3, 3);
        final BlockPos lecternPos = new BlockPos(3, 1, 1);

        final String HUB_SLOT   = "lc_hub";
        final String LAMP1_SLOT = "lc_lamp1";
        final String LAMP2_SLOT = "lc_lamp2";

        final String LINE_LAMP1_SLOT = "lc_line_lamp1";
        final String LINE_LAMP2_SLOT = "lc_line_lamp2";

        final Vec3 hubCenter       = new Vec3(hubPos.getX() + 0.5, hubPos.getY() + 0.5, hubPos.getZ() + 0.5);
        final Vec3 lamp1FaceCenter = new Vec3(lamp1Pos.getX() + 0.5, lamp1Pos.getY() + 1.0, lamp1Pos.getZ() + 0.5);
        final Vec3 lamp2FaceCenter = new Vec3(lamp2Pos.getX() + 0.5, lamp2Pos.getY() + 1.0, lamp2Pos.getZ() + 0.5);

        final int RED       = Color.RED.getRGB();
        final int DARK_GRAY = ChatFormatting.DARK_GRAY.getColor();

        scene.title("cable_hub_lectern", "Binding a Lectern Controller to a Cable Hub");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // REVEAL ALL
        world.showSection(select.fromTo(0, 1, 0, 4, 3, 4), Direction.DOWN);
        scene.idle(20);

        // INTRO TEXT
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_lectern.text_1")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_lectern.text_2")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // ENTER SETUP MODE
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.INPUT, HUB_SLOT,
                new AABB(hubPos.getX(), hubPos.getY(), hubPos.getZ(),
                        hubPos.getX() + 1, hubPos.getY() + 0.5, hubPos.getZ() + 1),
                124));
        scene.idle(7);

        // USE CABLE ON LAMP 1
        overlay.showControls(vector.blockSurface(lamp1Pos, Direction.UP), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, LAMP1_SLOT, faceBox(lamp1Pos, Direction.UP), 60));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP1_SLOT, hubCenter, lamp1FaceCenter, RED, 60));
        scene.idle(35);

        // SCROLL TO NEW CHANNEL
        scene.addInstruction(grayFaceInstruction(LAMP1_SLOT, faceBox(lamp1Pos, Direction.UP), 74));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP1_SLOT, hubCenter, lamp1FaceCenter, DARK_GRAY, 74));
        scene.idle(25);

        // USE CABLE ON LAMP 2
        overlay.showControls(vector.blockSurface(lamp2Pos, Direction.UP), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, LAMP2_SLOT, faceBox(lamp2Pos, Direction.UP), 41));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP2_SLOT, hubCenter, lamp2FaceCenter, RED, 41));
        scene.idle(35);

        // EXIT SETUP MODE
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(50);

        // BIND LINKED CONTROLLER
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_lectern.text_3")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 40)
                .withItem(new ItemStack(AllItems.LINKED_CONTROLLER.asItem()));
        scene.idle(50);

        // PLACE CONTROLLER
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.cable_hub_lectern.text_4")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(lecternPos));
        scene.idle(80);
        overlay.showControls(vector.blockSurface(lecternPos, Direction.UP), Pointing.DOWN, 40)
                .withItem(new ItemStack(AllItems.LINKED_CONTROLLER.asItem()));
        scene.idle(10);
        world.setBlock(lecternPos,
                AllBlocks.LECTERN_CONTROLLER.get().defaultBlockState()
                        .setValue(LecternBlock.FACING, Direction.NORTH)
                        .setValue(LecternBlock.POWERED, false),
                true);
        world.modifyBlockEntity(lecternPos, LecternControllerBlockEntity.class,
                be -> be.setController(new ItemStack(AllItems.LINKED_CONTROLLER.get())));
        scene.idle(40);

        // SHOW [W] CHANNEL
        overlay.showControls(vector.blockSurface(lecternPos, Direction.UP), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_W);
        scene.idle(10);
        world.modifyBlock(lamp1Pos, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
        effects.indicateRedstone(lamp1Pos);
        scene.idle(25);
        world.modifyBlock(lamp1Pos, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
        scene.idle(15);

        // SHOW [S] CHANNEL
        overlay.showControls(vector.blockSurface(lecternPos, Direction.UP), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_S);
        scene.idle(10);
        world.modifyBlock(lamp2Pos, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
        effects.indicateRedstone(lamp2Pos);
        scene.idle(25);
        world.modifyBlock(lamp2Pos, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
        scene.idle(10);

        // END SCENE
        scene.markAsFinished();
    }
    //#endregion

    //#region // --- ADVANCED CABLE HUB INTRO SCENE --- //
    public static void cableAdvancedHubIntro(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var effects = scene.effects();
        final var select = util.select();
        final var vector = util.vector();

        final BlockPos hubPos    = new BlockPos(2, 2, 1);
        final BlockPos lampPos   = new BlockPos(1, 2, 3);
        final BlockPos nixiePos  = new BlockPos(3, 3, 3);

        final String HUB_SLOT   = "adv_hub";
        final String NIXIE_SLOT = "adv_nixie";
        final String LAMP_SLOT  = "adv_lamp";

        final String LINE_NIXIE = "line_nixie";
        final String LINE_LAMP  = "line_lamp";

        final Vec3 hubCenter      = new Vec3(2.5, 2.25, 1.5);
        final Vec3 nixieFaceCenter = new Vec3(3.5, 3.5, 3.0);
        final Vec3 lampFaceCenter  = new Vec3(1.5, 2.5, 3.0);

        final int RED       = Color.RED.getRGB();
        final int DARK_GRAY = ChatFormatting.DARK_GRAY.getColor();

        scene.title("adv_hub_intro", "Using an Advanced Cable Hub");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // REVEAL ADVANCED HUB
        world.showSection(select.fromTo(2, 1, 1, 2, 2, 1), Direction.UP);
        scene.idle(20);

        // POINT TO ADVANCED HUB AND LABEL
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_1")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        // DESCRIBE ADVANCED HUB
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_2")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        // TWEAKED CONTROLLERS MENTIONED
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_3")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // SHOW LAMP TOWER
        world.showSection(select.fromTo(1, 1, 3, 1, 2, 3), Direction.DOWN);
        // SHOW NIXIE TOWER
        world.showSection(select.fromTo(3, 1, 3, 3, 3, 3), Direction.DOWN);
        scene.idle(20);

        // START SETUP
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_4")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // USE CABLE ON HUB
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_5")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.INPUT, HUB_SLOT,
                new AABB(hubPos.getX(), hubPos.getY(), hubPos.getZ(),
                        hubPos.getX() + 1, hubPos.getY() + 0.5, hubPos.getZ() + 1),
                350));
        scene.idle(7);
        // USE CABLE ON NIXIE
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_6")))
                .placeNearTarget()
                .pointAt(vector.centerOf(nixiePos));
        scene.idle(80);
        overlay.showControls(vector.blockSurface(nixiePos, Direction.NORTH), Pointing.RIGHT, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, NIXIE_SLOT,
                faceBox(nixiePos, Direction.NORTH), 35));
        scene.addInstruction(coloredLineInstruction(LINE_NIXIE, hubCenter, nixieFaceCenter, RED, 35));
        scene.idle(22);

        // SCROLL TO SELECT NEW CHANNEL
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_7")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(8);
        scene.addInstruction(grayFaceInstruction(NIXIE_SLOT, faceBox(nixiePos, Direction.NORTH), 220));
        scene.addInstruction(coloredLineInstruction(LINE_NIXIE, hubCenter, nixieFaceCenter, DARK_GRAY, 220));
        scene.idle(72);
        // USE CABLE ON LAMP
        overlay.showControls(vector.blockSurface(lampPos, Direction.NORTH), Pointing.RIGHT, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, LAMP_SLOT,
                faceBox(lampPos, Direction.NORTH), 145));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP, hubCenter, lampFaceCenter, RED, 145));
        scene.idle(22);

        // EXIT SETUP MODE
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_8")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(50);

        // USE TWEAKED CONTROLLER
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_9")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 40)
                .withItem(new ItemStack(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath(
                                "create_tweaked_controllers", "tweaked_linked_controller"))));
        scene.idle(50);

        // EXPLAIN SIGNAL STRENGTH
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_10")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(nixiePos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 48)
                .showing(CablePonderTextures.STICK_LEFT);
        scene.idle(10);
        // ANIMATE NIXIE
        for (int i = 0; i <= 15; i++) {
            final int value = i;
            world.modifyBlockEntityNBT(select.position(nixiePos), NixieTubeBlockEntity.class,
                    nbt -> nbt.putInt("RedstoneStrength", value));
            if (i == 0 || i == 7 || i == 15) {
                effects.indicateRedstone(nixiePos);
            }
            scene.idle(4);
        }
        scene.idle(20);
        world.modifyBlockEntityNBT(select.position(nixiePos), NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", 0));
        scene.idle(10);

        // EXPLAIN STRENGTH PART 2
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.adv_hub_intro.text_11")))
                .placeNearTarget()
                .pointAt(vector.centerOf(lampPos));
        scene.idle(80);
        // ACTIVATE LAMP
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_W);
        scene.idle(10);
        world.modifyBlock(lampPos, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
        effects.indicateRedstone(lampPos);
        scene.idle(25);
        world.modifyBlock(lampPos, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
        scene.idle(10);

        // END SCENE
        scene.markAsFinished();
    }
    //#endregion
    //#region // --- ADVANCED CABLE HUB LECTERN SCENE --- //
    public static void advancedCableHubLecternIntro(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var effects = scene.effects();
        final var select = util.select();
        final var vector = util.vector();

        final BlockPos hubPos     = new BlockPos(1, 2, 1);
        final BlockPos lamp1Pos   = new BlockPos(1, 3, 3);
        final BlockPos lamp2Pos   = new BlockPos(3, 3, 3);
        final BlockPos lecternPos = new BlockPos(3, 1, 1);

        final String HUB_SLOT   = "alc_hub";
        final String LAMP1_SLOT = "alc_lamp1";
        final String LAMP2_SLOT = "alc_lamp2";

        final String LINE_LAMP1_SLOT = "alc_line_lamp1";
        final String LINE_LAMP2_SLOT = "alc_line_lamp2";

        final Vec3 hubCenter       = new Vec3(hubPos.getX() + 0.5, hubPos.getY() + 0.5, hubPos.getZ() + 0.5);
        final Vec3 lamp1FaceCenter = new Vec3(lamp1Pos.getX() + 0.5, lamp1Pos.getY() + 1.0, lamp1Pos.getZ() + 0.5);
        final Vec3 lamp2FaceCenter = new Vec3(lamp2Pos.getX() + 0.5, lamp2Pos.getY() + 1.0, lamp2Pos.getZ() + 0.5);

        final int RED       = Color.RED.getRGB();
        final int DARK_GRAY = ChatFormatting.DARK_GRAY.getColor();

        final ItemStack tweakedController = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create_tweaked_controllers", "tweaked_linked_controller")));

        scene.title("advanced_cable_hub_lectern", "Binding a Tweaked Lectern Controller to an Advanced Cable Hub");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // REVEAL ALL
        world.showSection(select.fromTo(0, 1, 0, 4, 3, 4), Direction.DOWN);
        scene.idle(20);

        // INTRO TEXT
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.advanced_cable_hub_lectern.text_1")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.advanced_cable_hub_lectern.text_2")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // ENTER SETUP MODE
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.INPUT, HUB_SLOT,
                new AABB(hubPos.getX(), hubPos.getY(), hubPos.getZ(),
                        hubPos.getX() + 1, hubPos.getY() + 0.5, hubPos.getZ() + 1),
                124));
        scene.idle(7);

        // USE CABLE ON LAMP 1
        overlay.showControls(vector.blockSurface(lamp1Pos, Direction.UP), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, LAMP1_SLOT, faceBox(lamp1Pos, Direction.UP), 60));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP1_SLOT, hubCenter, lamp1FaceCenter, RED, 60));
        scene.idle(35);

        // SCROLL TO NEW CHANNEL
        scene.addInstruction(grayFaceInstruction(LAMP1_SLOT, faceBox(lamp1Pos, Direction.UP), 74));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP1_SLOT, hubCenter, lamp1FaceCenter, DARK_GRAY, 74));
        scene.idle(25);

        // USE CABLE ON LAMP 2
        overlay.showControls(vector.blockSurface(lamp2Pos, Direction.UP), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, LAMP2_SLOT, faceBox(lamp2Pos, Direction.UP), 41));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP2_SLOT, hubCenter, lamp2FaceCenter, RED, 41));
        scene.idle(35);

        // EXIT SETUP MODE
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(50);

        // BIND TWEAKED CONTROLLER
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.advanced_cable_hub_lectern.text_3")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 40)
                .withItem(tweakedController);
        scene.idle(50);

        // PLACE CONTROLLER
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.advanced_cable_hub_lectern.text_4")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(lecternPos));
        scene.idle(80);
        overlay.showControls(vector.blockSurface(lecternPos, Direction.UP), Pointing.DOWN, 40)
                .withItem(tweakedController);
        scene.idle(10);
        world.setBlock(lecternPos,
                BuiltInRegistries.BLOCK.get(
                                ResourceLocation.fromNamespaceAndPath("create_tweaked_controllers", "tweaked_lectern_controller"))
                        .defaultBlockState()
                        .setValue(LecternBlock.FACING, Direction.NORTH)
                        .setValue(LecternBlock.POWERED, false),
                true);
        world.modifyBlockEntity(lecternPos, BlockEntity.class, be -> bindLecternController(be, tweakedController));
        scene.idle(40);

        // SHOW [STICK_RIGHT] CHANNEL
        overlay.showControls(vector.blockSurface(lecternPos, Direction.UP), Pointing.DOWN, 20)
                .showing(CablePonderTextures.STICK_RIGHT);
        scene.idle(10);
        world.modifyBlock(lamp1Pos, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
        effects.indicateRedstone(lamp1Pos);
        scene.idle(25);
        world.modifyBlock(lamp1Pos, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
        scene.idle(15);

        // SHOW [STICK_LEFT] CHANNEL
        overlay.showControls(vector.blockSurface(lecternPos, Direction.UP), Pointing.DOWN, 20)
                .showing(CablePonderTextures.STICK_LEFT);
        scene.idle(10);
        world.modifyBlock(lamp2Pos, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
        effects.indicateRedstone(lamp2Pos);
        scene.idle(25);
        world.modifyBlock(lamp2Pos, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
        scene.idle(10);

        // END SCENE
        scene.markAsFinished();
    }
    //#endregion

    //#region // --- CABLE TYPEWRITER HUB INTRO SCENE --- //
    public static void cableTypewriterHubIntro(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var effects = scene.effects();
        final var select = util.select();
        final var vector = util.vector();

        final BlockPos hubPos   = new BlockPos(2, 2, 1);
        final BlockPos link1Pos = new BlockPos(4, 2, 4);
        final BlockPos link2Pos = new BlockPos(2, 2, 4);
        final BlockPos lampPos  = new BlockPos(0, 2, 4);

        final String HUB_SLOT  = "tw_hub";
        final String LAMP_SLOT = "tw_lamp";
        final String LINE_LAMP = "tw_line_lamp";

        final Vec3 hubCenter      = new Vec3(2.5, 2.25, 1.5);
        final Vec3 lampFaceCenter = new Vec3(0.5, 2.5, 4.0);

        final int RED       = Color.RED.getRGB();

        scene.title("typewriter_hub_intro", "Using a Typewriter Cable Hub");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // REVEAL TYPEWRITER HUB
        world.showSection(select.fromTo(2, 1, 1, 2, 2, 1), Direction.UP);
        scene.idle(20);

        // POINT TO TYPEWRITER HUB AND LABEL
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_1")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        // DESCRIBE TYPEWRITER HUB
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_2")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // SHOW LINK TOWER 1
        world.showSection(select.fromTo(4, 1, 4, 4, 2, 4), Direction.DOWN);
        scene.idle(20);

        // EXPLAIN GUI
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 40)
                .rightClick()
                .whileSneaking();
        scene.idle(10);
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_3")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // SET FREQUENCY LINK 1
        overlay.showControls(vector.centerOf(link1Pos), Pointing.DOWN, 25)
                .withItem(new ItemStack(Items.BIRCH_SAPLING));
        scene.idle(5);
        world.modifyBlockEntityNBT(select.position(link1Pos),
                com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity.class, nbt -> {
                    final var reg = scene.getScene().getWorld().registryAccess();
                    nbt.put("FrequencyFirst",
                            new ItemStack(Items.BIRCH_SAPLING).saveOptional(reg));
                });
        scene.idle(35);
        overlay.showControls(vector.centerOf(link1Pos), Pointing.DOWN, 25)
                .withItem(new ItemStack(Items.IRON_INGOT));
        scene.idle(5);
        world.modifyBlockEntityNBT(select.position(link1Pos),
                com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity.class, nbt -> {
                    final var reg = scene.getScene().getWorld().registryAccess();
                    nbt.put("FrequencyLast",
                            new ItemStack(Items.IRON_INGOT).saveOptional(reg));
                });
        scene.idle(35);

        // EXPLAIN COPY FREQUENCY FROM LINK
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_4")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(link1Pos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(link1Pos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE_TYPEWRITER_HUB.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.RED,
                "link1_flash",
                new AABB(
                        link1Pos.getX() + 0.125,
                        link1Pos.getY() - 0.0625,
                        link1Pos.getZ() + 0.125,
                        link1Pos.getX() + 0.875,
                        link1Pos.getY() + 0.1875,
                        link1Pos.getZ() + 0.875),
                35));
        scene.idle(25);

        // EXPLAIN COPY FREQUENCY FROM CONTROLLER
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_5")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // SHOW LINK TOWER 2
        world.showSection(select.fromTo(2, 1, 4, 2, 2, 4), Direction.DOWN);
        scene.idle(20);

        // USE CONTROLLER ON TYPEWRITER HUB
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_6")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 40)
                .withItem(new ItemStack(AllItems.LINKED_CONTROLLER.asItem()));
        scene.idle(50);

        // ENTER SETUP MODE
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_7")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);

        // SHOW LAMP TOWER
        world.showSection(select.fromTo(0, 1, 4, 0, 2, 4), Direction.DOWN);
        scene.idle(20);

        // USE CABLE ON TYPEWRITER HUB
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_8")))
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.INPUT, HUB_SLOT,
                new AABB(hubPos.getX(), hubPos.getY(), hubPos.getZ(),
                        hubPos.getX() + 1, hubPos.getY() + 0.5, hubPos.getZ() + 1),
                300));
        scene.idle(7);

        // USE CABLE ON LAMP
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_9")))
                .placeNearTarget()
                .pointAt(vector.centerOf(lampPos));
        scene.idle(80);
        overlay.showControls(vector.blockSurface(lampPos, Direction.NORTH), Pointing.RIGHT, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(8);
        scene.addInstruction(new ChaseAABBInstruction(PonderPalette.OUTPUT, LAMP_SLOT,
                faceBox(lampPos, Direction.NORTH), 130));
        scene.addInstruction(coloredLineInstruction(LINE_LAMP, hubCenter, lampFaceCenter, RED, 130));
        scene.idle(22);

        // EXIT SETUP MODE
        overlay.showText(70)
                .text(String.valueOf(Component.translatable("drivebysable.ponder.typewriter_hub_intro.text_10")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(hubPos));
        scene.idle(80);
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 25)
                .withItem(new ItemStack(CableItems.CABLE.get()));
        scene.idle(50);

        // SHOW [W] CHANNEL
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_W);
        scene.idle(10);
        world.modifyBlock(link1Pos, s -> s.setValue(RedstoneLinkBlock.POWERED, true), false);
        effects.indicateRedstone(link1Pos);
        scene.idle(25);
        world.modifyBlock(link1Pos, s -> s.setValue(RedstoneLinkBlock.POWERED, false), false);
        scene.idle(10);
        // SHOW [S] CHANNEL
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_S);
        scene.idle(10);
        world.modifyBlock(link2Pos, s -> s.setValue(RedstoneLinkBlock.POWERED, true), false);
        effects.indicateRedstone(link2Pos);
        scene.idle(25);
        world.modifyBlock(link2Pos, s -> s.setValue(RedstoneLinkBlock.POWERED, false), false);
        scene.idle(10);
        // SHOW [E] CHANNEL
        overlay.showControls(vector.centerOf(hubPos), Pointing.DOWN, 20)
                .showing(CablePonderTextures.KEY_E);
        scene.idle(10);
        world.modifyBlock(lampPos, s -> s.setValue(RedstoneLampBlock.LIT, true), false);
        effects.indicateRedstone(lampPos);
        scene.idle(25);
        world.modifyBlock(lampPos, s -> s.setValue(RedstoneLampBlock.LIT, false), false);
        scene.idle(10);

        // END SCENE
        scene.markAsFinished();
    }
    //#endregion
}