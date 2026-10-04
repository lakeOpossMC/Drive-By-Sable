package edn.lakeopossmc.drivebysable.command;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// --- HOW A SOURCE READS IN CHAT --- //
// * Everything shown here is where the block appears in the world
public final class SourceText {

    public static final String CHAT_MARKER = DriveBySableMod.MOD_ID + ":chat";
    private static final String SEPARATOR = "-".repeat(32);

    public static final String BULLET = "\u2022";
    public static final String BULLET_PREFIX = " " + BULLET + " ";

    private SourceText() {
    }

    public static Component separator() {
        return Component.literal(SEPARATOR).withStyle(ChatFormatting.WHITE);
    }

    public static MutableComponent message(final MutableComponent message) {
        return Component.empty()
                .append(Component.literal("").withStyle(style -> style.withInsertion(CHAT_MARKER)))
                .append(separator())
                .append("\n")
                .append(message);
    }

    // * Whether a chat message came from one of our commands
    public static boolean isOurs(final Component message) {
        final List<Component> siblings = message.getSiblings();
        return !siblings.isEmpty() && CHAT_MARKER.equals(siblings.get(0).getStyle().getInsertion());
    }

    // * Where the centre of the block appears in the world
    public static Vec3 worldCentre(final Level level, final BlockPos pos) {
        return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
    }

    private static BlockPos worldBlock(final Level level, final BlockPos pos) {
        return BlockPos.containing(worldCentre(level, pos));
    }

    private static final int BLOCK_COLOR = ArmInteractionPoint.Mode.TAKE.getColor();
    private static final int OUTPUT_COLOR = ArmInteractionPoint.Mode.DEPOSIT.getColor();

    //#region // --- CLICKABLE NAMES --- //
    public static Component id(final Level level, final BlockPos pos, final boolean source) {
        return bracketed(blockNameOf(level, pos).withStyle(style -> style.withColor(source ? BLOCK_COLOR : OUTPUT_COLOR)));
    }

    public static Component idLink(final Level level, final BlockPos pos, final boolean source) {
        return runs(id(level, pos, source), "/dbs info " + coordSelector(pos, source) + " summarize",
                Component.translatable("commands.drivebysable.click.summarize"));
    }

    public static Component moduleName(final CableEndpoint endpoint) {
        return bracketed(Component.literal(endpoint.module())
                .withStyle(style -> style.withColor(endpoint.source() ? BLOCK_COLOR : OUTPUT_COLOR)));
    }

    public static Component moduleLink(final Level level, final CableEndpoint endpoint) {
        return runs(moduleName(endpoint),
                "/dbs info " + coordSelector(endpoint.pos(), endpoint.source())
                        + " @mod[" + endpoint.module() + "] summarize",
                Component.translatable("commands.drivebysable.click.summarize"));
    }

    public static Component channelLink(
            final Level level,
            final CableEndpoint endpoint,
            final String channel,
            final boolean source
    ) {
        final Component name = source ? channel(channel) : outputChannel(channel);
        final String module = endpoint.hasModule() ? " @mod[" + endpoint.module() + "]" : "";
        return runs(name,
                "/dbs info " + coordSelector(endpoint.pos(), source) + module + " getChannel[" + channel + "]",
                Component.translatable("commands.drivebysable.click.channel"));
    }

    private static final ChatFormatting TRANSCEIVER_COLOR = ChatFormatting.AQUA;

    public static Component transceiver(final Level level, final BlockPos pos) {
        return bracketed(blockNameOf(level, pos).withStyle(TRANSCEIVER_COLOR));
    }

    public static Component transceiverLink(final Level level, final BlockPos pos) {
        return runs(transceiver(level, pos),
                "/dbs info @coord[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ", transceiver] summarize",
                Component.translatable("commands.drivebysable.click.summarize"));
    }

    public static Component transceiverNumber(final int value) {
        return bracketed(Component.literal(String.valueOf(value)).withStyle(TRANSCEIVER_COLOR));
    }

    public static Component transceiverType() {
        return bracketed(Component.translatable("commands.drivebysable.type.transceiver")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static String coordSelector(final BlockPos pos, final boolean source) {
        return "@coord[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + ", " + (source ? "source" : "output") + "]";
    }

    private static Component runs(final Component text, final String command, final Component hover) {
        return text.copy().withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }
    //#endregion

    public static Component channel(final String name) {
        return bracketed(Component.literal(name).withStyle(style -> style.withColor(BLOCK_COLOR)));
    }

    public static Component outputChannel(final String name) {
        return bracketed(Component.literal(name).withStyle(style -> style.withColor(OUTPUT_COLOR)));
    }

    public static Component signal(final int value) {
        return bracketed(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.RED));
    }

    public static Component sourceType(final Level level, final BlockPos pos) {
        return bracketed(Component.translatable(
                "commands.drivebysable.type." + SourceKind.of(level, pos)).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static Component side(final Direction direction) {
        return bracketed(Component.literal(direction.getName()).withStyle(style -> style.withColor(OUTPUT_COLOR)));
    }

    public static Component dimension(final ServerLevel level) {
        return bracketed(Component.literal(level.dimension().location().toString())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // * Green like vanilla /locate, clicking fills in a teleport
    public static Component coordinates(final Level level, final BlockPos pos) {
        final BlockPos world = worldBlock(level, pos);
        return bracketed(Component.translatable("chat.coordinates", world.getX(), world.getY(), world.getZ())
                .withStyle(ChatFormatting.GREEN))
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND,
                                "/tp @s " + world.getX() + " " + world.getY() + " " + world.getZ()))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.drivebysable.coordinates.hover"))));
    }

    public static Component levelLabel(final Level level, final BlockPos pos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) {
            return bracketed(Component.translatable("commands.drivebysable.level.world")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        final String name = subLevel.getName();
        final MutableComponent label = name == null || name.isBlank()
                ? Component.translatable("commands.drivebysable.level.unnamed")
                : Component.literal(name);
        final String id = subLevel.getUniqueId().toString();

        return bracketed(label.withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.drivebysable.level.hover", id))));
    }

    public static Component number(final int value) {
        return bracketed(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.GREEN));
    }

    public static Component sourceNumber(final int value) {
        return number(value, BLOCK_COLOR);
    }

    public static Component outputNumber(final int value) {
        return number(value, OUTPUT_COLOR);
    }

    public static Component number(final int value, final int color) {
        return bracketed(Component.literal(String.valueOf(value)).withStyle(style -> style.withColor(color)));
    }

    public static Component clickable(final Component word, final String command, final Component hover) {
        return bracketed(word.copy().withStyle(ChatFormatting.GREEN))
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static MutableComponent blockNameOf(final Level level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        // * A sublevel that is not loaded reads as air
        return state.isAir()
                ? Component.translatable("commands.drivebysable.source.unloaded")
                : state.getBlock().getName();
    }

    private static MutableComponent bracketed(final Component inner) {
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.WHITE))
                .append(inner)
                .append(Component.literal("]").withStyle(ChatFormatting.WHITE));
    }
}