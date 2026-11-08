package edn.lakeopossmc.drivebysable.cable;

import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;

// --- KEEPS THE CABLE NETWORK OUT OF FREED SUBLEVEL PLOTS --- //
// * Sable reuses the first free plot for the next sublevel it creates
// * Anything still connected inside a removed sublevel's plot would come back as ghosts
public final class SubLevelPlotCleanup {
    private SubLevelPlotCleanup() {
    }

    // * Must run before any level is constructed
    public static void register() {
        SubLevelDimensionTransfer.register();
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> {
            if (level instanceof final ServerLevel serverLevel) {
                container.addObserver(new Observer(serverLevel));
            }
        });
    }

    private record Observer(ServerLevel level) implements SubLevelObserver {
        // * A loaded sublevel brings its cable ends with it, so their signals are worked out again
        @Override
        public void onSubLevelAdded(final SubLevel subLevel) {
            CableNetworkManager.onSubLevelLoaded(this.level, subLevel.getPlot());
            SubLevelDimensionTransfer.arrived(this.level, subLevel);
        }

        @Override
        public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
            // * UNLOADED keeps the plot reserved and the blocks come back to the same spot
            if (reason != SubLevelRemovalReason.REMOVED) {
                return;
            }

            // * Kept for a moment in case it reappears in another dimension
            SubLevelDimensionTransfer.departing(this.level, subLevel);

            // * Called before Sable forgets the plot
            CableNetworkManager.purgeRemovedPlot(
                    this.level,
                    subLevel.getPlot(),
                    "sub-level " + subLevel.getUniqueId() + " was removed"
            );
        }
    }
}