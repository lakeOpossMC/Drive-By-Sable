package edn.lakeopossmc.drivebysable;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

// --- MOD CONFIG DEF --- //
public class CableConfig {

    public static final CableConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    //#region // --- UNGROUPED --- //
    public final ModConfigSpec.BooleanValue shouldConsumeCables;
    public final ModConfigSpec.BooleanValue allowCableDisconnect;
    //#endregion

    //#region // --- NETWORK CONSTRAINTS --- //
    public final ModConfigSpec.BooleanValue forbidCrossLevelConnections;
    public final ModConfigSpec.BooleanValue allowCrossLevelConnectionSaving;
    public final ModConfigSpec.IntValue rangeLimit;
    public final ModConfigSpec.BooleanValue rangeLimitEnforced;
    public final ModConfigSpec.IntValue maxSourcesInWorld;
    public final ModConfigSpec.IntValue maxSourcesPerSubLevel;
    public final ModConfigSpec.IntValue maxOutputsPerChannel;
    //#endregion

    //#region // --- EXTENSIONS --- //
    public final ModConfigSpec.BooleanValue networkAnchor;
    public final ModConfigSpec.BooleanValue multiChannelCableBus;
    public final ModConfigSpec.BooleanValue integratedSensorBus;
    //#endregion

    //#region // --- RECIPES AND TEXTURES --- //
    public final ModConfigSpec.BooleanValue expensiveBackupDrive;
    public final ModConfigSpec.BooleanValue andesiteHub;
    //#endregion

    private CableConfig(ModConfigSpec.Builder builder) {
        shouldConsumeCables = builder
                .comment(
                        "Whether making a connection consumes a Cable from the player's stack.",
                        "Cables are refunded when a connection is removed.",
                        "Creative mode players are never charged."
                )
                .translation("drivebysable.config.shouldConsumeCables")
                .define("shouldConsumeCables", true);

        allowCableDisconnect = builder
                .comment(
                        "Whether the Cable itself can remove an existing connection.",
                        "When false, pointing a Cable at a connection it already made does nothing,",
                        "and the Cable Cutter becomes the only way to remove connections.",
                        "The Cable Cutter is unaffected by this option."
                )
                .translation("drivebysable.config.allowCableDisconnect")
                .define("allowCableDisconnect", false);

        //#region // --- NETWORK CONSTRAINTS --- //
        builder
                .comment(
                        "How far connections may reach and how many of them may exist.",
                        "Raising these costs more memory and may affect loading times."
                )
                .translation("drivebysable.config.networkConstraints")
                .push("networkConstraints");

        forbidCrossLevelConnections = builder
                .comment(
                        "Whether a connection may cross between different levels.",
                        "When true, both ends must sit in the same place: both loose in the world,",
                        "or both inside the same sublevel."
                )
                .translation("drivebysable.config.forbidCrossLevelConnections")
                .define("forbidCrossLevelConnections", false);

        allowCrossLevelConnectionSaving = builder
                .comment(
                        "Whether a Backup Drive or Network Anchor may save connections whose ends",
                        "sit on a different level to itself, such as a sublevel beside it.",
                        "The region still applies: an end is only saved when it falls inside the",
                        "region, measured where the block appears in the world rather than where",
                        "it is stored. When false, anything on another level is skipped and",
                        "reported to the player."
                )
                .translation("drivebysable.config.allowCrossLevelConnectionSaving")
                .define("allowCrossLevelConnectionSaving", false);

        rangeLimit = builder
                .comment(
                        "Furthest a connection may reach, in blocks, when rangeLimitEnforced is true.",
                        "Measured as straight line distance between the two block positions.",
                        "0 blocks any connection between separate blocks. Has no effect while",
                        "rangeLimitEnforced is false."
                )
                .translation("drivebysable.config.rangeLimit")
                .defineInRange("rangeLimit", 512, 0, 512);

        rangeLimitEnforced = builder
                .comment(
                        "Whether connections are limited by distance between the source and the output.",
                        "When false, connections can be made at any distance and rangeLimit is ignored."
                )
                .translation("drivebysable.config.rangeLimitEnforced")
                .define("rangeLimitEnforced", false);

        maxSourcesInWorld = builder
                .comment(
                        "How many distinct Cable Sources may exist loose in the world.",
                        "Everything outside a sublevel shares this single budget."
                )
                .translation("drivebysable.config.maxSourcesInWorld")
                .defineInRange("maxSourcesInWorld", 128, 0, 2048);

        maxSourcesPerSubLevel = builder
                .comment(
                        "How many distinct Cable Sources may exist inside one sublevel.",
                        "Each sublevel gets its own budget. Counted per Source block, not per",
                        "connection, so a Source already in use is never counted again."
                )
                .translation("drivebysable.config.maxSourcesPerSubLevel")
                .defineInRange("maxSourcesPerSubLevel", 64, 0, 2048);

        maxOutputsPerChannel = builder
                .comment(
                        "How many Outputs a single Channel on one Source may drive.",
                        "Counted per Source and per Channel, so a Hub with several Channels",
                        "gets this budget on each of them independently."
                )
                .translation("drivebysable.config.maxOutputsPerChannel")
                .defineInRange("maxOutputsPerChannel", 64, 0, 2048);

        builder.pop();
        //#endregion

        //#region // --- EXTENSIONS --- //
        builder
                .comment(
                        "Optional blocks that act as extensions of existing features.",
                        "Disabling one makes it unobtainable, including through /give."
                )
                .translation("drivebysable.config.extensions")
                .push("extensions");

        networkAnchor = builder
                .comment(
                        "Whether the Network Anchor is available.",
                        "A creative only block that stores connections within a radius and",
                        "restores them wherever it is pasted.",
                        "Existing Anchors already placed in a world are unaffected."
                )
                .translation("drivebysable.config.networkAnchor")
                .define("networkAnchor", true);

        multiChannelCableBus = builder
                .comment(
                        "Whether the Multi-Channel Cable Bus is available.",
                        "A hundred channel source a ComputerCraft computer can drive directly.",
                        "Needs ComputerCraft to be useful, though the block still places without it.",
                        "Existing Buses already placed in a world are unaffected."
                )
                .translation("drivebysable.config.multiChannelCableBus")
                .define("multiChannelCableBus", true);

        integratedSensorBus = builder
                .comment(
                        "Whether the Integrated Sensor Bus is available.",
                        "Reports position, velocity, altitude and gimbal data for the sublevel it",
                        "rides on, and carries a hundred output channels of its own.",
                        "Has no effect unless Simulated is installed, which supplies the",
                        "sensors this block reads and everything its recipe needs.",
                        "ComputerCraft is optional, and adds raw telemetry reads.",
                        "Existing Sensor Buses already placed in a world are unaffected."
                )
                .translation("drivebysable.config.integratedSensorBus")
                .define("integratedSensorBus", true);

        builder.pop();
        //#endregion

        //#region // --- RECIPES AND TEXTURES --- //
        builder
                .comment(
                        "Alternate recipes and textures for certain blocks.",
                        "Everything here is reloaded automatically when it changes."
                )
                .translation("drivebysable.config.recipesAndTextures")
                .push("recipesAndTextures");

        expensiveBackupDrive = builder
                .comment(
                        "Whether the Network Backup Drive uses its expensive recipe.",
                        "Recipes are reloaded automatically when this changes."
                )
                .translation("drivebysable.config.expensiveBackupDrive")
                .define("expensiveBackupDrive", false);

        andesiteHub = builder
                .comment(
                        "Whether the Cable Hub and Advanced Cable Hub use Andesite and Brass theming.",
                        "Changes recipes, retextures blocks, and swaps the hub block sounds.",
                        "On a server, clients may need to enable the resourcepack individually.",
                        "Recipes and textures are reloaded automatically when this changes."
                )
                .translation("drivebysable.config.andesiteHub")
                .define("andesiteHub", false);

        builder.pop();
        //#endregion
    }

    // * Build config and spec together
    static {
        Pair<CableConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(CableConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

}