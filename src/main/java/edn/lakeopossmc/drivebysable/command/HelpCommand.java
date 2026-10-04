package edn.lakeopossmc.drivebysable.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Set;

// --- /dbs help [targets | info | highlight | remove | listNext] --- //
public final class HelpCommand {

    private record Topic(String name, List<Integer> parts, Set<Integer> defined) {
    }

    private static final List<Topic> TOPICS = List.of(
            new Topic("targets", List.of(18), Set.of(3, 4, 5, 6, 7, 14)),
            new Topic("info", List.of(13), Set.of(5, 6, 7, 8)),
            new Topic("highlight", List.of(10, 3), Set.of(5, 6, 7)),
            new Topic("remove", List.of(14), Set.of(9, 14)),
            new Topic("listNext", List.of(5), Set.of())
    );

    private HelpCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        final LiteralArgumentBuilder<CommandSourceStack> help = Commands.literal("help")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(HelpCommand::homepage);

        for (final Topic topic : TOPICS) {
            help.then(Commands.literal(topic.name()).executes(context -> show(context, topic)));
        }
        return help;
    }

    private static int homepage(final CommandContext<CommandSourceStack> context) {
        final MutableComponent message = Component
                .translatable("commands.drivebysable.help.title")
                .withStyle(ChatFormatting.WHITE);

        message.append("\n\n").append(Component
                .translatable("commands.drivebysable.help.lead")
                .withStyle(ChatFormatting.GRAY));

        for (final Topic topic : TOPICS) {
            message.append("\n").append(defined(
                    SourceText.clickable(
                            Component.literal(topic.name()),
                            "/dbs help " + topic.name(),
                            Component.translatable("commands.drivebysable.help.click")),
                    Component.translatable("commands.drivebysable.help.topic." + topic.name())));
        }

        return send(context, message);
    }

    private static int show(final CommandContext<CommandSourceStack> context, final Topic topic) {
        final MutableComponent message = Component.empty();

        int line = 0;
        for (int part = 0; part < topic.parts().size(); part++) {
            if (part > 0) {
                message.append("\n").append(SourceText.separator()).append("\n");
            }

            for (int i = 0; i < topic.parts().get(part); i++) {
                line++;
                if (line > 1) {
                    message.append("\n");
                }

                final String key = "commands.drivebysable.help." + topic.name() + "." + line;
                message.append(topic.defined().contains(line)
                        ? defined(
                        Component.translatable(key + ".term"),
                        Component.translatable(key + ".meaning"))
                        : Component.translatable(key).withStyle(ChatFormatting.GRAY));
            }
        }

        return send(context, message);
    }

    private static Component defined(final Component term, final Component meaning) {
        return Component.translatable(
                "commands.drivebysable.help.defined",
                term.copy().withStyle(ChatFormatting.WHITE),
                meaning.copy().withStyle(ChatFormatting.GRAY)
        ).withStyle(ChatFormatting.GRAY);
    }

    private static int send(final CommandContext<CommandSourceStack> context, final MutableComponent message) {
        final Component feedback = SourceText.message(message);
        context.getSource().sendSuccess(() -> feedback, false);
        return 1;
    }
}