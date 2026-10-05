package edn.lakeopossmc.drivebysable.advancement;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// --- WHERE EACH ADVANCEMENT SITS IN THE TAB --- //
// * The game lays branches out in whatever order a hash set hands them over, which changes between launches
// * This puts them in a fixed order instead
public final class CableAdvancementLayout {

    private static final ResourceLocation ROOT = DriveBySableMod.asResource("root");

    private static final String MAIN_LINE = "cable";

    // * Distance between neighbours
    private static final float COLUMN_SPACING = 1.5F;
    private static final float ROW_SPACING = 1.15F;

    private static final List<String> ORDER = List.of(
            "cable_cutter", "network_cut",
            "cable",
            "first_connection", "cross_level_connection", "long_connection",
            "cable_io_bus",
            "cable_hub", "intermediate_cable_hub", "advanced_cable_hub", "advanced_cable_hub_no_intermediate",
            "cable_typewriter_hub", "handheld_typewriter_controller", "handheld_signal",
            "multi_channel_cable_bus", "integrated_sensor_bus", "all_sensor_channels",
            "integrated_sensor_bus_no_bus", "all_sensor_channels_no_bus",
            "linked_gearbox", "opposing_forces", "double_speed", "overstressed",
            "backup_drive", "network_saved",
            "eat_honeyed_clipboard", "honeyed_clipboard_seconds", "place_honeyed_clipboard"
    );

    private static final Comparator<AdvancementNode> BY_ORDER = Comparator
            .comparingInt((AdvancementNode node) -> rank(node))
            .thenComparing(node -> node.holder().id().toString());

    private CableAdvancementLayout() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((final ServerAboutToStartEvent event) -> apply(event.getServer()));
        NeoForge.EVENT_BUS.addListener((final OnDatapackSyncEvent event) -> apply(event.getPlayerList().getServer()));
    }

    private static void apply(final MinecraftServer server) {
        final AdvancementNode root = server.getAdvancements().tree().get(ROOT);
        if (root != null) {
            place(root, 0, new int[]{0});
        }
    }

    // * Returns the row the advancement ended up on
    private static float place(final AdvancementNode node, final int column, final int[] nextRow) {
        final List<AdvancementNode> children = new ArrayList<>();
        node.children().forEach(children::add);
        children.sort(BY_ORDER);

        float row = -1;
        for (final AdvancementNode child : children) {
            final float childRow = place(child, column + 1, nextRow);
            if (row < 0 || column == 0 && MAIN_LINE.equals(child.holder().id().getPath())) {
                row = childRow;
            }
        }
        if (children.isEmpty()) {
            row = nextRow[0]++;
        }

        final float finalRow = row;
        node.advancement().display().ifPresent(display -> display.setLocation(column * COLUMN_SPACING, finalRow * ROW_SPACING));
        return row;
    }

    private static int rank(final AdvancementNode node) {
        final ResourceLocation id = node.holder().id();
        final int index = DriveBySableMod.MOD_ID.equals(id.getNamespace()) ? ORDER.indexOf(id.getPath()) : -1;
        return index < 0 ? ORDER.size() : index;
    }
}