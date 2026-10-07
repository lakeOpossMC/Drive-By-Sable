package edn.lakeopossmc.drivebysable;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;

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
    public final ModConfigSpec.DoubleValue linkedGearboxTransmitterStress;
    public final ModConfigSpec.EnumValue<ReceiverCost> linkedGearboxReceiverCost;
    public final ModConfigSpec.DoubleValue linkedGearboxReceiverFlatCost;
    public final ModConfigSpec.DoubleValue linkedGearboxReceiverStress;
    public final ModConfigSpec.BooleanValue linkedGearboxLimitTransfer;
    public final ModConfigSpec.DoubleValue linkedGearboxStressPerRpm;
    public final ModConfigSpec.DoubleValue linkedGearboxRangePerRpm;
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
    public final ModConfigSpec.BooleanValue fancyCables;
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
                        "Whether the Cable itself can remove an existing connection in survival.",
                        "When false, pointing a Cable at a connection it already made does nothing,",
                        "and the Cable Cutter becomes the only way to remove connections.",
                        "Creative mode players can always remove connections with the Cable.",
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
                .define("allowCrossLevelConnectionSaving", true);

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

        linkedGearboxTransmitterStress = builder
                .comment(
                        "Stress, in SU per RPM, a Radio-Kinetic Transceiver costs it's power source.",
                        "Calculated as transmitterStressPerRpm * Input RPM."
                )
                .translation("drivebysable.config.linkedGearboxTransmitterStress")
                .defineInRange("transmitterStressPerRpm", 2.0, 0.0, 64.0);

        linkedGearboxReceiverCost = builder
                .comment(
                        "What each Receiver unit adds to the network cost.",
                        "NONE: nothing. FLAT: receiverFlatCost, static cost regardless of RPM.",
                        "PER_RPM: receiverStressPerRpm, cost directly scales with RPM."
                )
                .translation("drivebysable.config.linkedGearboxReceiverCost")
                .defineEnum("receiverCost", ReceiverCost.NONE);

        linkedGearboxReceiverFlatCost = builder
                .comment("Stress, in SU, each driven Receiver unit adds while receiverCost is FLAT.")
                .translation("drivebysable.config.linkedGearboxReceiverFlatCost")
                .defineInRange("receiverFlatCost", 64.0, 0.0, 16384.0);

        linkedGearboxReceiverStress = builder
                .comment("Stress, in SU per RPM, each Receiver unit adds while receiverCost is PER_RPM.")
                .translation("drivebysable.config.linkedGearboxReceiverStress")
                .defineInRange("receiverStressPerRpm", 1.5, 0.0, 64.0);

        linkedGearboxCostBySpeed = builder
                .comment(
                        "The cost of a linked network is added up and paid by Transmitter units in the network.",
                        "True: each pays in proportion to its speed, so a faster one pays more. False: split evenly."
                )
                .translation("drivebysable.config.linkedGearboxCostBySpeed")
                .define("splitCostBySpeed", true);

        linkedGearboxLimitTransfer = builder
                .comment(
                        "Whether there is a limit on the stress a network passes on.",
                        "Calculated as transferStressPerRpm * Input RPM. More RPM = more output stress."
                )
                .translation("drivebysable.config.linkedGearboxLimitTransfer")
                .define("limitTransfer", false);

        linkedGearboxStressPerRpm = builder
                .comment(
                        "With limitTransfer on: stress, in SU, a network passes on for each RPM of the Transmitter",
                        "units in network. Every Transmitter adds its limited output stress to the shared capacity."
                )
                .translation("drivebysable.config.linkedGearboxStressPerRpm")
                .defineInRange("transferStressPerRpm", 16.0, 0.0, 1024.0);

        linkedGearboxRangePerRpm = builder
                .comment(
                        "How far, in blocks, a Transmitter unit can reach Receivers for each RPM it turns at.",
                        "Number directly translates to blocks per Input RPM. 0RPM = 0 blocks of connection range.",
                        "A linked Transceiver outside that reach stays linked, but is not driven until it is back in",
                        "reach or the driving side speeds up. Measured where each Transceiver appears in the world."
                )
                .translation("drivebysable.config.linkedGearboxRangePerRpm")
                .defineInRange("rangePerRpm", 1.5, 0.0, 64.0);

        linkedGearboxRange = builder
                .comment(
                        "The most reach a Transmitter unit can ever have, in blocks.",
                        "Also the furthest apart two Transceivers may be to stay linked at all.",
                        "Applies on top of the general networkConstraints range limit."
                )
                .translation("drivebysable.config.linkedGearboxRange")
                .defineInRange("maxRange", 256, 0, 2048);

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

        fancyCables = builder
                .comment(
                        "Whether the connections shown in Setup Mode are drawn as hanging cables",
                        "instead of straight lines. Covers a selected Source's connections and the",
                        "links of a selected Transceiver. Purely visual, and read on the client,",
                        "so each player can choose for themselves."
                )
                .translation("drivebysable.config.fancyCables")
                .define("fancyCables", true);

        builder.pop();
        //#endregion
    }

    // * What a driven Transceiver adds to its network's cost
    public enum ReceiverCost {
        NONE, FLAT, PER_RPM
    }

    // * Build config and spec together
    public static boolean cableCanDisconnect(@Nullable final Player player) {
        return CONFIG.allowCableDisconnect.get() || (player != null && player.hasInfiniteMaterials());
    }

    static {
        Pair<CableConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(CableConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

}