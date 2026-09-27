package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.ponder.DriveBySablePonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.config.ModConfigEvent;

// --- RELOADS PONDER WHEN AN EXTENSION IS TOGGLED --- //
// * Ponder tags are only built during registration, so a toggle needs a full Ponder reload
// * Skipped unless one of the gated toggles actually changed
public final class PonderTagRefresh {

    private PonderTagRefresh() {
    }

    public static void onConfigChanged(final ModConfigEvent event) {
        if (!DriveBySableMod.MOD_ID.equals(event.getConfig().getModId())) {
            return;
        }

        // * Config events arrive off the client thread
        Minecraft.getInstance().execute(PonderTagRefresh::reloadIfStale);
    }

    private static void reloadIfStale() {
        if (!DriveBySablePonderPlugin.isTagStale()) {
            return;
        }

        try {
            PonderIndex.reload();
        } catch (final Exception e) {
            DriveBySableMod.LOGGER.warn("Could not reload Ponder after an extension was toggled", e);
        }
    }
}