package edn.lakeopossmc.drivebysable.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// --- READS A @target OUT OF A COMMAND --- //
// * @coord[x, y, z], @coord[x, y, z, source], @look[output], @n[transceiver], @rad[32, source], @a
public final class TargetArgument implements ArgumentType<CableTarget> {

    private static final Collection<String> EXAMPLES =
            List.of("@n", "@look[source]", "@coord[12, 64, -30, output]", "@rad[32]", "@a[source]", "@n[transceiver]");

    private static final SimpleCommandExceptionType EXPECTED_TARGET = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.argument.target.expected"));
    private static final DynamicCommandExceptionType UNKNOWN_TARGET = new DynamicCommandExceptionType(
            selector -> Component.translatableEscape("commands.drivebysable.argument.target.unknown", selector));
    private static final DynamicCommandExceptionType UNKNOWN_SIDE = new DynamicCommandExceptionType(
            side -> Component.translatableEscape("commands.drivebysable.argument.target.side", side));
    private static final SimpleCommandExceptionType EXPECTED_COORDS = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.argument.target.coords"));
    private static final SimpleCommandExceptionType TOO_MANY_PARTS = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.argument.target.too_many"));

    private TargetArgument() {
    }

    public static TargetArgument target() {
        return new TargetArgument();
    }

    public static CableTarget getTarget(final CommandContext<?> context, final String name) {
        return context.getArgument(name, CableTarget.class);
    }

    @Override
    public CableTarget parse(final StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead() || reader.peek() != '@') {
            throw EXPECTED_TARGET.createWithContext(reader);
        }
        reader.skip();

        final int selectorStart = reader.getCursor();
        while (reader.canRead() && Character.isLetter(reader.peek())) {
            reader.skip();
        }
        final String selector = "@" + reader.getString().substring(selectorStart, reader.getCursor());

        CableTarget.Kind kind = null;
        for (final CableTarget.Kind candidate : CableTarget.Kind.values()) {
            if (candidate.selector().equals(selector)) {
                kind = candidate;
                break;
            }
        }
        if (kind == null) {
            throw UNKNOWN_TARGET.createWithContext(reader, selector);
        }

        final List<String> parts = readParts(reader);
        return build(reader, kind, parts);
    }

    private static List<String> readParts(final StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead() || reader.peek() != '[') {
            return List.of();
        }
        reader.skip();

        final List<String> parts = new java.util.ArrayList<>();
        final StringBuilder part = new StringBuilder();
        while (reader.canRead() && reader.peek() != ']') {
            final char next = reader.read();
            if (next == ',') {
                parts.add(part.toString().trim());
                part.setLength(0);
                continue;
            }
            part.append(next);
        }
        reader.expect(']');

        if (!part.toString().isBlank() || !parts.isEmpty()) {
            parts.add(part.toString().trim());
        }
        return parts;
    }

    private static CableTarget build(
            final StringReader reader,
            final CableTarget.Kind kind,
            final List<String> parts
    ) throws CommandSyntaxException {
        return switch (kind) {
            case COORD -> {
                if (parts.size() < 3) {
                    throw EXPECTED_COORDS.createWithContext(reader);
                }
                if (parts.size() > 4) {
                    throw TOO_MANY_PARTS.createWithContext(reader);
                }
                final BlockPos pos = new BlockPos(
                        readInt(reader, parts.get(0)),
                        readInt(reader, parts.get(1)),
                        readInt(reader, parts.get(2)));
                yield new CableTarget(kind, readSide(reader, parts, 3), pos, 0);
            }
            case RADIUS -> {
                if (parts.size() > 2) {
                    throw TOO_MANY_PARTS.createWithContext(reader);
                }
                final int radius = parts.isEmpty() || parts.get(0).isBlank()
                        ? CableTarget.DEFAULT_RADIUS
                        : readInt(reader, parts.get(0));
                yield new CableTarget(kind, readSide(reader, parts, 1), null, radius);
            }
            default -> {
                if (parts.size() > 1) {
                    throw TOO_MANY_PARTS.createWithContext(reader);
                }
                yield new CableTarget(kind, readSide(reader, parts, 0), null, 0);
            }
        };
    }

    private static int readInt(final StringReader reader, final String text) throws CommandSyntaxException {
        try {
            return Integer.parseInt(text.trim());
        } catch (final NumberFormatException ignored) {
            throw EXPECTED_COORDS.createWithContext(reader);
        }
    }

    private static CableTarget.Side readSide(
            final StringReader reader,
            final List<String> parts,
            final int index
    ) throws CommandSyntaxException {
        if (parts.size() <= index || parts.get(index).isBlank()) {
            return CableTarget.Side.BOTH;
        }

        final String side = parts.get(index).toLowerCase(java.util.Locale.ROOT);
        for (final CableTarget.Side candidate : CableTarget.Side.values()) {
            if (!candidate.word().isEmpty() && candidate.word().equals(side)) {
                return candidate;
            }
        }
        throw UNKNOWN_SIDE.createWithContext(reader, side);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            final CommandContext<S> context,
            final SuggestionsBuilder builder
    ) {
        final String written = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);

        if (!written.contains("[")) {
            for (final CableTarget.Kind kind : CableTarget.Kind.values()) {
                if (kind.selector().startsWith(written)) {
                    builder.suggest(kind.selector());
                }
            }
            return builder.buildFuture();
        }

        final int start = written.lastIndexOf('[') + 1;
        final int lastComma = written.lastIndexOf(',');
        final String prefix = written.substring(0, Math.max(start, lastComma + 1));
        final String typed = written.substring(Math.max(start, lastComma + 1)).trim();

        for (final CableTarget.Side candidate : CableTarget.Side.values()) {
            final String side = candidate.word();
            if (!side.isEmpty() && side.startsWith(typed)) {
                builder.suggest(builder.getRemaining().substring(0, prefix.length()) + side + "]");
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}