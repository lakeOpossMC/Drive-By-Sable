package edn.lakeopossmc.drivebysable.ponder;

import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import edn.lakeopossmc.drivebysable.CableBlocks;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlock;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxFrequencySlot;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// --- PONDER SCENES FOR THE LINKED GEARBOX --- //
public class LinkedGearboxScenes {

    private static final int BLINK_A = 0x708DAD;
    private static final int BLINK_B = 0x90ADCD;

    //#region // --- HELPERS --- //
    private static final float PACE = 1.75F;

    private static int pace(final int ticks) {
        return Math.round(ticks * PACE);
    }

    private static int blinkColour() {
        return AnimationTickHolder.getTicks() % 16 < 8 ? BLINK_A : BLINK_B;
    }

    private static Vec3 centre(final BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    // --- THE OPEN NETWORK --- //
    private static final class NetworkView extends PonderInstruction {
        private List<BlockPos> loop = List.of();
        private boolean closed;

        @Override
        public boolean isComplete() {
            return closed;
        }

        @Override
        public void reset(final PonderScene scene) {
            super.reset(scene);
            loop = List.of();
            closed = false;
        }

        @Override
        public void tick(final PonderScene scene) {
            final int colour = blinkColour();
            for (final BlockPos pos : loop) {
                scene.getOutliner().chaseAABB("gearbox_network_box_" + pos.asLong(), new AABB(pos))
                        .colored(colour)
                        .lineWidth(1 / 32f);
            }
            for (int i = 0; i + 1 < loop.size(); i++) {
                drawLink(scene, loop.get(i), loop.get(i + 1), colour);
            }
            if (loop.size() > 2) {
                drawLink(scene, loop.getLast(), loop.getFirst(), colour);
            }
        }

        private static void drawLink(final PonderScene scene, final BlockPos from, final BlockPos to, final int colour) {
            final long low = Math.min(from.asLong(), to.asLong());
            final long high = Math.max(from.asLong(), to.asLong());
            scene.getOutliner().showLine("gearbox_network_line_" + low + "_" + high, centre(from), centre(to))
                    .colored(colour)
                    .lineWidth(1 / 32f);
        }
    }

    // * Opens a network on these gearboxes
    private static NetworkView openNetwork(final CreateSceneBuilder scene, final List<BlockPos> loop) {
        final NetworkView view = new NetworkView();
        scene.addInstruction(view);
        setNetwork(scene, view, loop);
        return view;
    }

    // * Changes who is in the open network
    private static void setNetwork(final CreateSceneBuilder scene, final NetworkView view, final List<BlockPos> loop) {
        scene.addInstruction(PonderInstruction.simple(ponderScene -> view.loop = loop));
    }

    // * Leaves setup mode
    private static void closeNetwork(final CreateSceneBuilder scene, final NetworkView view) {
        scene.addInstruction(PonderInstruction.simple(ponderScene -> view.closed = true));
    }

    // * Writes frequency items
    private static void setFrequency(final CreateSceneBuilder scene, final SceneBuildingUtil util, final BlockPos pos,
                                     final String firstKey, final String lastKey, final ItemStack first,
                                     final ItemStack last) {
        final HolderLookup.Provider registries = scene.world().getHolderLookupProvider();
        scene.world().modifyBlockEntityNBT(util.select().position(pos), LinkedGearboxBlockEntity.class, nbt -> {
            nbt.put(firstKey, first.saveOptional(registries));
            nbt.put(lastKey, last.saveOptional(registries));
        });
    }

    private static ItemStack cable() {
        return new ItemStack(CableItems.CABLE.get());
    }

    private static ItemStack cutter() {
        return new ItemStack(CableItems.CABLE_CUTTER.get());
    }
    //#endregion

    //#region // --- SCENES 1 AND 2 --- //
    private static final BlockPos SOURCE_SHAFT = new BlockPos(1, 0, 4);
    private static final BlockPos SOURCE_COG = new BlockPos(1, 1, 4);
    private static final BlockPos GEARBOX_1 = new BlockPos(1, 1, 3);
    private static final BlockPos GEARBOX_2 = new BlockPos(3, 1, 4);
    private static final BlockPos GEARBOX_3 = new BlockPos(5, 1, 4);
    private static final BlockPos LEVER = new BlockPos(3, 2, 4);

    private static final float SOURCE_SPEED = 32;
    private static final float LINK_SPEED = -SOURCE_SPEED;

    private static Vec3 slotOf(final BlockPos pos, final Direction facing, final Direction rotation,
                               final boolean back, final boolean first) {
        final BlockState state = CableBlocks.LINKED_GEARBOX.get().defaultBlockState()
                .setValue(LinkedGearboxBlock.FACING, facing)
                .setValue(LinkedGearboxBlock.ROTATION, rotation);
        final LinkedGearboxFrequencySlot slot = back
                ? LinkedGearboxFrequencySlot.back(first)
                : new LinkedGearboxFrequencySlot(first);
        return Vec3.atLowerCornerOf(pos).add(slot.getLocalOffset(null, pos, state));
    }

    private static Vec3 gearbox1Slot(final boolean first) {
        return slotOf(GEARBOX_1, Direction.UP, Direction.SOUTH, false, first);
    }

    private static Vec3 gearbox2Slot(final boolean first) {
        return slotOf(GEARBOX_2, Direction.SOUTH, Direction.NORTH, false, first);
    }

    private static void showSource(final CreateSceneBuilder scene, final SceneBuildingUtil util) {
        final var world = scene.world();
        final var select = util.select();
        world.setKineticSpeed(select.position(SOURCE_SHAFT), SOURCE_SPEED);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(pace(10));

        world.setKineticSpeed(select.position(SOURCE_COG), SOURCE_SPEED);
        world.setKineticSpeed(select.position(GEARBOX_1), LINK_SPEED);
        world.showSection(select.position(SOURCE_COG).add(select.position(GEARBOX_1)), Direction.DOWN);
        scene.idle(pace(20));
    }

    private static Selection gearbox2Line(final SceneBuildingUtil util) {
        return util.select().fromTo(3, 1, 1, 3, 1, 4);
    }

    private static Selection gearbox3Line(final SceneBuildingUtil util) {
        return util.select().fromTo(5, 1, 1, 5, 1, 4);
    }
    //#endregion

    //#region // --- SCENE 1: WIRELESS ROTATION --- //
    public static void wirelessRotation(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var vector = util.vector();

        scene.title("linked_gearbox_intro", "Transferring Rotation Wirelessly");
        showSource(scene, util);

        overlay.showText(pace(70))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_intro.text_1")))
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_1));
        scene.idle(pace(80));

        world.showSection(gearbox2Line(util), Direction.DOWN);
        scene.idle(pace(20));

        overlay.showText(pace(60))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_intro.text_2")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.topOf(GEARBOX_1));
        scene.idle(pace(70));
        overlay.showText(pace(60))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_intro.text_3")))
                .placeNearTarget()
                .pointAt(vector.centerOf(3, 1, 2));
        scene.idle(pace(70));

        overlay.showText(pace(70))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_intro.text_4")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_1));
        scene.idle(pace(30));
        overlay.showControls(vector.topOf(GEARBOX_1), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        final NetworkView network = openNetwork(scene, List.of(GEARBOX_1));
        scene.idle(pace(25));
        overlay.showControls(vector.topOf(GEARBOX_2), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        setNetwork(scene, network, List.of(GEARBOX_1, GEARBOX_2));
        world.setKineticSpeed(gearbox2Line(util), LINK_SPEED);
        scene.effects().rotationSpeedIndicator(GEARBOX_2);
        scene.idle(pace(80));

        overlay.showControls(vector.topOf(GEARBOX_1), Pointing.DOWN, pace(25)).rightClick().withItem(cutter());
        scene.idle(pace(12));
        closeNetwork(scene, network);
        world.setKineticSpeed(gearbox2Line(util), 0);
        scene.idle(pace(50));

        overlay.showText(pace(70))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_intro.text_5")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_1));
        scene.idle(pace(20));
        final ItemStack iron = new ItemStack(Items.IRON_INGOT);
        final ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        overlay.showControls(gearbox1Slot(true), Pointing.DOWN, pace(15)).rightClick().withItem(iron);
        scene.idle(pace(12));
        setFrequency(scene, util, GEARBOX_1, "FrequencyFirst", "FrequencyLast", iron, ItemStack.EMPTY);
        scene.idle(pace(16));
        overlay.showControls(gearbox1Slot(false), Pointing.DOWN, pace(15)).rightClick().withItem(gold);
        scene.idle(pace(12));
        setFrequency(scene, util, GEARBOX_1, "FrequencyFirst", "FrequencyLast", iron, gold);
        scene.idle(pace(25));
        overlay.showControls(gearbox2Slot(true), Pointing.DOWN, pace(15)).rightClick().withItem(iron);
        scene.idle(pace(12));
        setFrequency(scene, util, GEARBOX_2, "FrequencyFirst", "FrequencyLast", iron, ItemStack.EMPTY);
        scene.idle(pace(16));
        overlay.showControls(gearbox2Slot(false), Pointing.DOWN, pace(15)).rightClick().withItem(gold);
        scene.idle(pace(12));
        setFrequency(scene, util, GEARBOX_2, "FrequencyFirst", "FrequencyLast", iron, gold);
        scene.idle(pace(16));
        world.setKineticSpeed(gearbox2Line(util), LINK_SPEED);
        scene.effects().rotationSpeedIndicator(GEARBOX_2);
        scene.idle(pace(60));

        overlay.showText(pace(90))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_intro.text_6")))
                .colored(PonderPalette.RED)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_1));
        scene.idle(pace(100));
    }
    //#endregion

    //#region // --- SCENE 2: SPEED, STRESS AND POWER --- //
    public static void speedAndStress(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var select = util.select();
        final var vector = util.vector();

        scene.title("linked_gearbox_speed", "Output Speed, Stress and Power");
        showSource(scene, util);
        world.setKineticSpeed(gearbox2Line(util), LINK_SPEED);
        world.showSection(gearbox2Line(util), Direction.DOWN);
        scene.idle(pace(30));

        world.showSection(gearbox3Line(util), Direction.DOWN);
        scene.idle(pace(20));
        overlay.showControls(vector.topOf(GEARBOX_1), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        final NetworkView network = openNetwork(scene, List.of(GEARBOX_1, GEARBOX_2));
        scene.idle(pace(25));
        overlay.showControls(vector.topOf(GEARBOX_3), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        setNetwork(scene, network, List.of(GEARBOX_1, GEARBOX_2, GEARBOX_3));
        world.setKineticSpeed(gearbox3Line(util), LINK_SPEED);
        scene.effects().rotationSpeedIndicator(GEARBOX_3);
        scene.idle(pace(70));

        closeNetwork(scene, network);
        scene.idle(pace(15));

        scene.rotateCameraY(180);
        scene.idle(pace(30));
        overlay.showText(pace(80))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_speed.text_1")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.blockSurface(GEARBOX_3, Direction.SOUTH));
        scene.idle(pace(90));
        overlay.showText(pace(70))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_speed.text_2")))
                .placeNearTarget()
                .pointAt(vector.blockSurface(GEARBOX_3, Direction.SOUTH));
        scene.idle(pace(20));
        overlay.showCenteredScrollInput(GEARBOX_3, Direction.SOUTH, pace(50));
        scene.idle(pace(30));
        world.setKineticSpeed(gearbox3Line(util), LINK_SPEED * 2);
        scene.effects().rotationSpeedIndicator(GEARBOX_3);
        scene.idle(pace(40));
        scene.rotateCameraY(180);
        scene.idle(pace(30));

        overlay.showText(pace(70))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_speed.text_3")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_3));
        scene.idle(pace(80));
        overlay.showText(pace(70))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_speed.text_4")))
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_3));
        scene.idle(pace(80));
        overlay.showText(pace(80))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_speed.text_5")))
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_1));
        scene.idle(pace(90));

        world.showSection(select.fromTo(3, 1, 5, 3, 2, 5).add(select.position(LEVER)), Direction.DOWN);
        scene.idle(pace(20));
        overlay.showText(pace(70))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_speed.text_6")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.centerOf(GEARBOX_2));
        scene.idle(pace(40));
        world.toggleRedstonePower(select.position(LEVER));
        scene.effects().indicateRedstone(LEVER);
        world.modifyBlock(GEARBOX_2, state -> state.setValue(LinkedGearboxBlock.POWERED, true), false);
        world.setKineticSpeed(gearbox2Line(util), 0);
        scene.idle(pace(70));
    }
    //#endregion

    //#region // --- SCENE 3: NETWORKS AND THE POWER FREQUENCY --- //
    public static void networksAndFrequencies(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        final var world = scene.world();
        final var overlay = scene.overlay();
        final var select = util.select();
        final var vector = util.vector();

        final BlockPos gearbox1 = new BlockPos(1, 1, 1);
        final BlockPos gearbox2 = new BlockPos(5, 1, 1);
        final BlockPos gearbox3 = new BlockPos(5, 1, 5);
        final BlockPos gearbox4 = new BlockPos(1, 1, 5);
        final BlockPos link = new BlockPos(3, 3, 3);
        final BlockPos lever = new BlockPos(3, 2, 2);
        final float speed = 32;

        final ItemStack redstone = new ItemStack(Items.REDSTONE);
        final ItemStack torch = new ItemStack(Items.REDSTONE_TORCH);

        scene.title("linked_gearbox_network", "Radio-Kinetic Transceiver Networks");
        world.setKineticSpeed(select.position(gearbox1.below()), speed);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(pace(15));

        world.setKineticSpeed(select.position(gearbox1), speed);
        world.showSection(select.position(gearbox1), Direction.DOWN);
        scene.idle(pace(5));
        world.showSection(select.position(gearbox2), Direction.DOWN);
        scene.idle(pace(5));
        world.showSection(select.position(gearbox3), Direction.DOWN);
        scene.idle(pace(5));
        world.showSection(select.position(gearbox4), Direction.DOWN);
        scene.idle(pace(20));

        overlay.showText(pace(80))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_network.text_1")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.topOf(gearbox1));
        scene.idle(pace(40));
        overlay.showControls(vector.topOf(gearbox1), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        final NetworkView network = openNetwork(scene, List.of(gearbox1));
        scene.idle(pace(25));

        overlay.showControls(vector.topOf(gearbox2), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        setNetwork(scene, network, List.of(gearbox1, gearbox2));
        world.setKineticSpeed(select.position(gearbox2).add(select.position(gearbox2.below())), speed);
        scene.idle(pace(25));

        overlay.showControls(vector.topOf(gearbox3), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        setNetwork(scene, network, List.of(gearbox1, gearbox2, gearbox3));
        world.setKineticSpeed(select.position(gearbox3).add(select.position(gearbox3.below())), speed);
        scene.idle(pace(25));

        overlay.showControls(vector.topOf(gearbox4), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        setNetwork(scene, network, List.of(gearbox1, gearbox2, gearbox3, gearbox4));
        world.setKineticSpeed(select.position(gearbox4).add(select.position(gearbox4.below())), speed);
        scene.idle(pace(40));

        overlay.showText(pace(80))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_network.text_2")))
                .placeNearTarget()
                .pointAt(vector.topOf(gearbox3));
        scene.idle(pace(90));

        overlay.showText(pace(90))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_network.text_3")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.topOf(gearbox2));
        scene.idle(pace(30));
        overlay.showControls(vector.topOf(gearbox2), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        world.setKineticSpeed(select.position(gearbox2).add(select.position(gearbox2.below())), 0);
        setNetwork(scene, network, List.of(gearbox1, gearbox4, gearbox3));
        scene.idle(pace(70));
        overlay.showControls(vector.topOf(gearbox2), Pointing.DOWN, pace(25)).rightClick().withItem(cable());
        scene.idle(pace(8));
        world.setKineticSpeed(select.position(gearbox2).add(select.position(gearbox2.below())), speed);
        setNetwork(scene, network, List.of(gearbox1, gearbox2, gearbox3, gearbox4));
        scene.idle(pace(50));
        closeNetwork(scene, network);
        scene.idle(pace(15));

        world.showSection(select.fromTo(3, 1, 3, 3, 3, 3).add(select.position(lever)), Direction.DOWN);
        scene.idle(pace(25));
        overlay.showText(pace(80))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_network.text_4")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.topOf(link));
        scene.idle(pace(90));

        final Vec3 linkFirst = Vec3.atLowerCornerOf(link).add(8 / 16.0, 3 / 16.0, 10.5 / 16.0);
        final Vec3 linkSecond = Vec3.atLowerCornerOf(link).add(8 / 16.0, 3 / 16.0, 5.5 / 16.0);
        overlay.showControls(linkFirst, Pointing.DOWN, pace(15)).rightClick().withItem(redstone);
        scene.idle(pace(12));
        overlay.showControls(linkSecond, Pointing.DOWN, pace(15)).rightClick().withItem(torch);
        scene.idle(pace(12));
        world.modifyBlockEntityNBT(select.position(link), RedstoneLinkBlockEntity.class, nbt -> {
            final HolderLookup.Provider registries = world.getHolderLookupProvider();
            nbt.put("FrequencyFirst", redstone.saveOptional(registries));
            nbt.put("FrequencyLast", torch.saveOptional(registries));
        });
        scene.idle(pace(25));

        scene.rotateCameraY(180);
        scene.idle(pace(30));
        overlay.showText(pace(80))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_network.text_5")))
                .placeNearTarget()
                .pointAt(slotOf(gearbox3, Direction.UP, Direction.SOUTH, true, true));
        scene.idle(pace(30));
        overlay.showControls(slotOf(gearbox3, Direction.UP, Direction.SOUTH, true, true), Pointing.DOWN, pace(15))
                .rightClick().withItem(redstone);
        scene.idle(pace(12));
        setFrequency(scene, util, gearbox3, "PowerFrequencyFirst", "PowerFrequencyLast", redstone, ItemStack.EMPTY);
        scene.idle(pace(16));
        overlay.showControls(slotOf(gearbox3, Direction.UP, Direction.SOUTH, true, false), Pointing.DOWN, pace(15))
                .rightClick().withItem(torch);
        scene.idle(pace(12));
        setFrequency(scene, util, gearbox3, "PowerFrequencyFirst", "PowerFrequencyLast", redstone, torch);
        scene.idle(pace(40));

        scene.rotateCameraY(180);
        scene.idle(pace(30));

        world.toggleRedstonePower(select.position(lever).add(select.position(link)));
        scene.effects().indicateRedstone(lever);
        scene.idle(pace(10));
        scene.effects().indicateRedstone(gearbox3);
        world.modifyBlock(gearbox3, state -> state.setValue(LinkedGearboxBlock.POWERED, true), false);
        world.setKineticSpeed(select.position(gearbox3).add(select.position(gearbox3.below())), 0);
        overlay.showText(pace(90))
                .text(String.valueOf(Component.translatable("drivebysable.ponder.linked_gearbox_network.text_6")))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vector.topOf(gearbox3));
        scene.idle(pace(100));
    }
    //#endregion
}