package edn.lakeopossmc.drivebysable.client;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen;
import edn.lakeopossmc.drivebysable.CableMenus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// --- HANDHELD TYPEWRITER SCREEN BINDING --- //
// * Only called when Simulated is loaded
public final class HandheldTypewriterScreens {

    private HandheldTypewriterScreens() {
    }

    public static void register(final RegisterMenuScreensEvent event) {
        event.<LinkedTypewriterMenuCommon, LinkedTypewriterScreen>register(
                CableMenus.HANDHELD_TYPEWRITER.get(), LinkedTypewriterScreen::new);
    }
}