package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.BackupDriveBounds;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

// --- THE CLIENT HALF OF THE DRIVE'S GOGGLE TOOLTIP --- //
// * Kept apart from the block entity because everything here is client only
public final class BackupDriveGoggleClient {

    private static final String PREFIX = "goggles.";

    private static final String PREFIX_FULL = DriveBySableMod.MOD_ID + "." + PREFIX;

    private static final String[] COUNT_KEYS = {"valid_sources", "partial_sources", "invalid_sources"};
    private static final String[] TRANSCEIVER_COUNT_KEYS =
            {"valid_transceivers", "partial_transceivers", "invalid_transceivers"};

    private static final int COLUMN_GAP = 8;

    private BackupDriveGoggleClient() {
    }

    private static LangBuilder lang(final String key, final Object... args) {
        return new LangBuilder(DriveBySableMod.MOD_ID).translate(key, args);
    }

    // * Whether this player is charged for cables at all
    public static boolean showsCableCost() {
        final Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && CableConfig.CONFIG.shouldConsumeCables.get()
                && !minecraft.player.hasInfiniteMaterials();
    }

    public static void appendRegionInfo(
            final BlockPos drivePos,
            final BlockPos offset,
            final BlockPos size,
            final int rotation,
            final List<Component> tooltip
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        final int[] tally = BackupDrivePreview.tallyFor(
                minecraft.level,
                drivePos,
                BackupDriveBounds.of(
                        drivePos,
                        new int[] {offset.getX(), offset.getY(), offset.getZ()},
                        new int[] {size.getX(), size.getY(), size.getZ()},
                        rotation
                )
        );

        final int valid = tally[BackupDrivePreview.TALLY_VALID];
        final int partial = tally[BackupDrivePreview.TALLY_PARTIAL];
        final int unreachable = tally[BackupDrivePreview.TALLY_SOURCE_UNREACHABLE];
        final int outputsOutside = tally[BackupDrivePreview.TALLY_OUTPUTS_OUTSIDE];
        final int invalid = unreachable + outputsOutside;

        // * Linked Gearboxes are neither sources nor outputs
        final int validTransceivers = tally[BackupDrivePreview.TALLY_TRANSCEIVERS_VALID];
        final int partialTransceivers = tally[BackupDrivePreview.TALLY_TRANSCEIVERS_PARTIAL];
        final int invalidTransceivers = tally[BackupDrivePreview.TALLY_TRANSCEIVERS_INVALID];
        final boolean anyTransceivers = validTransceivers + partialTransceivers + invalidTransceivers > 0;

        if (valid + partial + invalid == 0 && !anyTransceivers) {
            lang(PREFIX + "no_data").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            return;
        }

        final int column = widestLabel(anyTransceivers) + COLUMN_GAP;

        count(tooltip, "valid_sources", valid, ChatFormatting.GREEN, column);

        // * Each count brings only explanations that currently apply
        count(tooltip, "partial_sources", partial, ChatFormatting.GOLD, column);
        if (partial > 0) {
            reason(tooltip, "reason_outputs_outside", ChatFormatting.GOLD);
        }

        count(tooltip, "invalid_sources", invalid, ChatFormatting.RED, column);
        if (unreachable > 0) {
            reason(tooltip, "reason_other_level", ChatFormatting.RED);
        }
        if (outputsOutside > 0) {
            reason(tooltip, "reason_all_outside", ChatFormatting.RED);
        }

        if (anyTransceivers) {
            count(tooltip, "valid_transceivers", validTransceivers, ChatFormatting.GREEN, column);

            count(tooltip, "partial_transceivers", partialTransceivers, ChatFormatting.GOLD, column);
            if (partialTransceivers > 0) {
                reason(tooltip, "reason_transceivers_outside", ChatFormatting.GOLD);
            }

            count(tooltip, "invalid_transceivers", invalidTransceivers, ChatFormatting.RED, column);
            if (invalidTransceivers > 0) {
                reason(tooltip, "reason_transceiver_alone", ChatFormatting.RED);
            }
        }

        // * Only while there is something to correct
        if (partial + invalid + partialTransceivers + invalidTransceivers > 0) {
            lang(PREFIX + "fix_issues").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
    }

    // * How wide the longest of the labels being shown renders
    private static int widestLabel(final boolean withTransceivers) {
        final Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (final String key : COUNT_KEYS) {
            widest = Math.max(widest, font.width(Component.translatable(PREFIX_FULL + key)));
        }
        if (withTransceivers) {
            for (final String key : TRANSCEIVER_COUNT_KEYS) {
                widest = Math.max(widest, font.width(Component.translatable(PREFIX_FULL + key)));
            }
        }
        return widest;
    }

    // * Label then count
    private static void count(
            final List<Component> tooltip,
            final String key,
            final int amount,
            final ChatFormatting colour,
            final int column
    ) {
        final Font font = Minecraft.getInstance().font;
        final int deficit = column - font.width(Component.translatable(PREFIX_FULL + key));

        final LangBuilder line = lang(PREFIX + key).style(ChatFormatting.GRAY);
        appendPadding(line, deficit, font);

        line.add(lang(PREFIX + "number", amount).style(colour)).forGoggles(tooltip, 1);
    }

    private static void appendPadding(final LangBuilder line, final int deficit, final Font font) {
        if (deficit <= 0) {
            return;
        }

        final int plain = Math.max(1, font.width(Component.literal(" ")));
        final int bold = Math.max(plain + 1, font.width(Component.literal(" ").withStyle(ChatFormatting.BOLD)));

        for (int bolds = 0; bolds <= deficit / bold; bolds++) {
            final int remainder = deficit - bolds * bold;
            if (remainder % plain != 0) {
                continue;
            }

            if (bolds > 0) {
                line.add(Component.literal(" ".repeat(bolds)).withStyle(ChatFormatting.BOLD));
            }
            line.add(Component.literal(" ".repeat(remainder / plain)));
            return;
        }

        line.add(Component.literal(" ".repeat(deficit / plain)));
    }

    private static void reason(final List<Component> tooltip, final String key, final ChatFormatting colour) {
        lang(PREFIX + key).style(colour).forGoggles(tooltip, 2);
    }
}