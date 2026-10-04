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
    public final ModConfigSpec.BooleanValue handheldTypewriterController;

    // * Linked Gearbox subgroup
    public final ModConfigSpec.BooleanValue linkedGearbox;
    public final ModConfigSpec.DoubleValue linkedGearboxStressImpact;
    public final ModConfigSpec.DoubleValue linkedGearboxStressPerRpm;
    public final ModConfigSpec.BooleanValue linkedGearboxCostBySpeed;
    public final ModConfigSpec.IntValue linkedGearboxRange;
    public final ModConfigSpec.BooleanValue linkedGearboxSubLevelOnly;
    //#endregion

    //#region // --- COMMANDS --- //
    public final ModConfigSpec.IntValue commandRadiusLimit;
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

        handheldTypewriterController = builder
                .comment(
                        "Whether the Handheld Typewriter Controller is available.",
                        "A portable Linked Typewriter that can drive redstone links, a Typewriter",
                        "Cable Hub's channels, or sit on a lectern.",
                        "Has no effect unless Simulated is installed.",
                        "Controllers already placed on lecterns are unaffected."
                )
                .translation("drivebysable.config.handheldTypewriterController")
                .define("handheldTypewriterController", true);

        //#region // --- LINKED GEARBOX (NESTED IN EXTENSIONS) --- //
        builder
                .comment("Stress, range and placement rules for Radio-Kinetic Transceivers.")
                .translation("drivebysable.config.linkedGearbox")
                .push("radioKineticTransceiver");

        linkedGearbox = builder
                .comment(
                        "Whether the Radio-Kinetic Transceiver is available.",
                        "A block that sends rotation and stress wirelessly to others of its kind, linked by Cable or frequency.",
                        "Existing Radio-Kinetic Transceivers already placed in a world are unaffected."
                )
                .translation("drivebysable.config.linkedGearboxEnabled")
                .define("enabled", true);

        linkedGearboxStressImpact = builder
                .comment(
                        "Stress, in SU, a Radio-Kinetic Transceiver consumes itself at Create's maximum rotation speed.",
                        "Scales linearly with speed."
                )
                .translation("drivebysable.config.linkedGearboxStressImpact")
                .defineInRange("stressImpactAtMaxSpeed", 512.0, 0.0, 16384.0);

        linkedGearboxStressPerRpm = builder
                .comment(
                        "Stress capacity, in SU, a Radio-Kinetic Transceiver passes through its network for each RPM of",
                        "the Transceiver driving the network.",
                        "Every Transceiver turned by its own side adds its part to one pool, shared between the",
                        "ones it drives.",
                        "0 turns the limit off."
                )
                .translation("drivebysable.config.linkedGearboxStressPerRpm")
                .defineInRange("transferStressPerRpm", 16.0, 0.0, 1024.0);

        linkedGearboxCostBySpeed = builder
                .comment(
                        "The stress impact of every Transceiver in a linked network is added up and paid by the",
                        "Transceivers turned by their own side. True: each pays in proportion to its speed, the",
                        "same way it adds capacity, so a faster driver pays more. False: split evenly."
                )
                .translation("drivebysable.config.linkedGearboxCostBySpeed")
                .define("splitCostBySpeed", true);

        linkedGearboxRange = builder
                .comment(
                        "Furthest, in blocks, two Transceivers may be from each other.",
                        "Measured where each Transceiver appears in the world, so a sublevel that moves",
                        "out of range drops that unit until it comes back.",
                        "Applies on top of the general networkConstraints range limit."
                )
                .translation("drivebysable.config.linkedGearboxRange")
                .defineInRange("rangeLimit", 64, 0, 512);

        linkedGearboxSubLevelOnly = builder
                .comment(
                        "Whether Radio-Kinetic Transceivers only link while they sit inside a sublevel.",
                        "Applies to every Transceiver: one outside a sublevel neither links nor passes rotation on."
                )
                .translation("drivebysable.config.linkedGearboxSubLevelOnly")
                .define("subLevelOnly", true);

        builder.pop();
        //#endregion

        builder.pop();
        //#endregion

        //#region // --- COMMANDS --- //
        builder
                .comment("Limits for the /dbs commands.")
                .translation("drivebysable.config.commands")
                .push("commands");

        commandRadiusLimit = builder
                .comment(
                        "Largest radius, in blocks, the radius target of a /dbs command may use.",
                        "Measured from the command's position to where each Source appears in the world."
                )
                .translation("drivebysable.config.commandRadiusLimit")
                .defineInRange("commandRadiusLimit", 1000, 1, 10000);

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