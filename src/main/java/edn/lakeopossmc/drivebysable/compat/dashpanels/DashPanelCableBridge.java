package edn.lakeopossmc.drivebysable.compat.dashpanels;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.compat.ControllerSignalStore;
import moth.boxxed.panels.api.module.IInput;
import moth.boxxed.panels.api.module.IMultiInput;
import moth.boxxed.panels.api.module.Module;
import moth.boxxed.panels.api.module.ModuleIOInfo;
import moth.boxxed.panels.api.module.ModuleType;
import moth.boxxed.panels.content.panel.PanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// --- BRIDGES CONTROL PANELS INTO CABLE NETWORK --- //
public final class DashPanelCableBridge {
    private static final String EXTENSION_SEPARATOR = " - ";
    private static final String SINGLE_EXTENSION = "";

    private static final Map<Level, Map<BlockPos, PanelState>> STATE = new WeakHashMap<>();

    private DashPanelCableBridge() {
    }

    // * List every input module as a channel, splitting multi inputs
    public static List<String> getChannels(final Level level, final BlockPos pos) {
        final PanelBlockEntity panel = getPanel(level, pos);
        if (panel == null) {
            return List.of();
        }

        final List<String> channels = new ArrayList<>();
        for (final ModuleIOInfo info : panel.modules.filterIOModules()) {
            switch (info.type()) {
                case INPUT -> channels.add(info.name());
                case MULTI_INPUT -> info.multiExtension()
                        .forEach(extension -> channels.add(info.name() + EXTENSION_SEPARATOR + extension));
                default -> {
                }
            }
        }
        return channels;
    }

    public static String nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = getChannels(level, pos);
        if (channels.isEmpty()) {
            return null;
        }

        final int currentIndex = channels.indexOf(current);
        if (currentIndex == -1) {
            return channels.getFirst();
        }
        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }

    private static final int BOOKKEEPING_INTERVAL_TICKS = 10;

    //#region // --- PER TICK PANEL SCAN --- //
    // * Push changed values every tick, cheap
    // * Every 10th tick also detect renamed or removed modules
    public static void tick(final Level level, final BlockPos pos, final PanelBlockEntity panel) {
        if (level.isClientSide) {
            return;
        }

        final PanelState state = STATE
                .computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(pos.immutable(), ignored -> new PanelState());

        final boolean runBookkeeping = state.tickCounter++ % BOOKKEEPING_INTERVAL_TICKS == 0;

        final Map<ModuleKey, Map<String, String>> currentChannelsByModule = runBookkeeping ? new HashMap<>() : null;
        final Set<String> seenChannels = runBookkeeping ? new HashSet<>() : null;

        for (final ModuleIOInfo info : panel.modules.filterIOModules()) {
            final Module module = panel.modules.normalGet(info.name());
            if (module == null) {
                continue;
            }

            switch (info.type()) {
                case INPUT -> {
                    if (module instanceof final IInput input) {
                        final String channel = info.name();
                        if (runBookkeeping) {
                            seenChannels.add(channel);
                            final ModuleKey key = new ModuleKey(module.type, module.getPos().x, module.getPos().y);
                            currentChannelsByModule.computeIfAbsent(key, ignored -> new HashMap<>())
                                    .put(SINGLE_EXTENSION, channel);
                        }
                        pushIfChanged(level, pos, channel, Math.clamp(input.getAnalog(), 0, 15), state.lastValues);
                    }
                }
                case MULTI_INPUT -> {
                    if (module instanceof final IMultiInput multiInput) {
                        state.scratchResults.clear();
                        multiInput.getValues(state.scratchResults::put);
                        for (final String extension : info.multiExtension()) {
                            final IMultiInput.AnalogResult result = state.scratchResults.get(extension);
                            if (result == null) {
                                continue;
                            }
                            final String channel = info.name() + EXTENSION_SEPARATOR + extension;
                            if (runBookkeeping) {
                                seenChannels.add(channel);
                                final ModuleKey key = new ModuleKey(module.type, module.getPos().x, module.getPos().y);
                                currentChannelsByModule.computeIfAbsent(key, ignored -> new HashMap<>())
                                        .put(extension, channel);
                            }
                            pushIfChanged(level, pos, channel, Math.clamp(result.getAnalog(), 0, 15), state.lastValues);
                        }
                    }
                }
                default -> {
                }
            }
        }

        if (!runBookkeeping) {
            return;
        }

        // * Same module, different name means it got renamed, remap it
        for (final Map.Entry<ModuleKey, Map<String, String>> entry : currentChannelsByModule.entrySet()) {
            final Map<String, String> previousExtensions = state.lastChannelsByModule.get(entry.getKey());
            if (previousExtensions == null) {
                continue;
            }
            for (final Map.Entry<String, String> extensionEntry : entry.getValue().entrySet()) {
                final String oldChannel = previousExtensions.get(extensionEntry.getKey());
                final String newChannel = extensionEntry.getValue();
                if (oldChannel != null && !oldChannel.equals(newChannel)) {
                    CableNetworkManager.remapSourceChannel(level, pos, oldChannel, newChannel);
                    final Integer previousValue = state.lastValues.remove(oldChannel);
                    if (previousValue != null) {
                        state.lastValues.put(newChannel, previousValue);
                    }
                }
            }
        }

        state.lastChannelsByModule.clear();
        state.lastChannelsByModule.putAll(currentChannelsByModule);

        // * Anything not seen this pass got removed, drop its connections
        state.lastValues.keySet().removeIf(channel -> {
            if (seenChannels.contains(channel)) {
                return false;
            }
            CableNetworkManager.removeAllFromSourceChannel(level, pos, channel);
            return true;
        });
    }
    //#endregion

    public static void clear(final Level level, final BlockPos pos) {
        final Map<BlockPos, PanelState> perLevel = STATE.get(level);
        if (perLevel != null) {
            perLevel.remove(pos);
        }
        ControllerSignalStore.clear(level, pos);
    }

    private static void pushIfChanged(
            final Level level,
            final BlockPos pos,
            final String channel,
            final int value,
            final Map<String, Integer> lastValues
    ) {
        final Integer previous = lastValues.put(channel, value);
        if (previous == null || previous != value) {
            ControllerSignalStore.setSignal(level, pos, channel, value);
        }
    }

    @Nullable
    private static PanelBlockEntity getPanel(final Level level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof final PanelBlockEntity panel ? panel : null;
    }

    private record ModuleKey(ModuleType<?> type, int x, int y) {
    }

    // * Per panel tick state, keyed off level and pos
    private static final class PanelState {
        private final Map<String, Integer> lastValues = new HashMap<>();
        private final Map<ModuleKey, Map<String, String>> lastChannelsByModule = new HashMap<>();
        private final Map<String, IMultiInput.AnalogResult> scratchResults = new HashMap<>();
        private int tickCounter;
    }
}