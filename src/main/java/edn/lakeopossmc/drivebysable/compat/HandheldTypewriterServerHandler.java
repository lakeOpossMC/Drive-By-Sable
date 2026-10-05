package edn.lakeopossmc.drivebysable.compat;

import com.mojang.datafixers.util.Pair;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries.KeyboardEntry;
import edn.lakeopossmc.drivebysable.advancement.CableAdvancements;
import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// --- BRIDGES HANDHELD TYPEWRITER KEYS TO LINKS AND CABLES --- //
public final class HandheldTypewriterServerHandler {
    private static final int TIMEOUT = 30;
    private static final WorldAttached<Map<Pair<BlockPos, String>, Integer>> TIMEOUT_MAP =
            new WorldAttached<>(level -> new HashMap<>());

    private HandheldTypewriterServerHandler() {
    }

    public static void receiveKeys(
            final ServerPlayer player,
            final ItemStack controller,
            @Nullable final BlockPos lecternPos,
            final Collection<Integer> keys,
            final boolean pressed
    ) {
        final Level level = player.level();

        //#region // --- REDSTONE LINK FREQUENCIES --- //
        final LinkedTypewriterEntries entries = HandheldTypewriterData.readEntries(controller, level.registryAccess());
        final List<Couple<Frequency>> frequencies = new ArrayList<>();
        for (final Integer key : keys) {
            final KeyboardEntry entry = key == null ? null : entries.getEntry(key);
            if (entry != null) {
                frequencies.add(entry.getAsCouple());
            }
        }
        if (!frequencies.isEmpty()) {
            final BlockPos transmitFrom = lecternPos != null ? lecternPos : player.blockPosition();
            LinkedControllerServerHandler.receivePressed(level, transmitFrom, player.getUUID(), frequencies, pressed);
        }
        //#endregion

        //#region // --- CABLE CHANNELS --- //
        final Optional<BlockPos> hubPos = HubItem.getHubPos(controller);
        // * The keyless packets never load the hub's chunk, real key presses still reach it
        if (hubPos.isPresent()
                && (!keys.isEmpty() || level.isLoaded(hubPos.get()))
                && level.getBlockEntity(hubPos.get()) instanceof final CableTypewriterHubBlockEntity hub) {
            setChannels(level, hubPos.get(), keys, pressed);
            hub.receiveHandheldInput(player.getUUID(), keys, pressed);
            if (pressed && keys.stream().anyMatch(CableTypewriterHubServerHandler.KEY_TO_CHANNEL::containsKey)) {
                CableAdvancements.award(player, CableAdvancements.HANDHELD_SIGNAL);
            }
        }
        if (lecternPos != null) {
            setChannels(level, lecternPos, keys, pressed);
        }
        //#endregion
    }

    private static void setChannels(final Level level, final BlockPos pos, final Collection<Integer> keys, final boolean pressed) {
        final BlockPos source = pos.immutable();
        final Map<Pair<BlockPos, String>, Integer> timeouts = TIMEOUT_MAP.get(level);
        for (final Integer key : keys) {
            final String channel = key == null ? null : CableTypewriterHubServerHandler.KEY_TO_CHANNEL.get(key);
            if (channel == null) {
                continue;
            }

            ControllerSignalStore.setSignal(level, source, channel, pressed ? 15 : 0);
            if (pressed) {
                timeouts.put(Pair.of(source, channel), TIMEOUT);
            } else {
                timeouts.remove(Pair.of(source, channel));
            }
        }
    }

    // * Drop every typewriter channel held on this source
    public static void reset(final Level level, final BlockPos pos) {
        final BlockPos source = pos.immutable();
        final Map<Pair<BlockPos, String>, Integer> timeouts = TIMEOUT_MAP.get(level);
        for (final String channel : CableTypewriterHubServerHandler.CHANNELS) {
            if (timeouts.remove(Pair.of(source, channel)) != null) {
                ControllerSignalStore.setSignal(level, source, channel, 0);
            }
        }
    }

    // * Count down held channels, clear signal once expired
    public static void tick(final Level level) {
        final Map<Pair<BlockPos, String>, Integer> timeouts = TIMEOUT_MAP.get(level);
        if (timeouts.isEmpty()) {
            return;
        }

        final Iterator<Map.Entry<Pair<BlockPos, String>, Integer>> iterator = timeouts.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Pair<BlockPos, String>, Integer> entry = iterator.next();
            final int ttl = entry.getValue() - 1;
            if (ttl > 0) {
                entry.setValue(ttl);
                continue;
            }

            iterator.remove();
            ControllerSignalStore.setSignal(level, entry.getKey().getFirst(), entry.getKey().getSecond(), 0);
        }
    }
}