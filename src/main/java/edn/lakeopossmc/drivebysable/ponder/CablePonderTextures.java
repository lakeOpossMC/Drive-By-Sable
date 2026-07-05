package edn.lakeopossmc.drivebysable.ponder;

import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.gui.TextureSheetSegment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import edn.lakeopossmc.drivebysable.DriveBySableMod;

public enum CablePonderTextures implements ScreenElement {

    KEY_W   (0,  0),
    KEY_S   (16, 0),
    KEY_E   (32, 0),
    STICK_LEFT  (48, 0),
    STICK_RIGHT (64, 0);

    private static final ResourceLocation SHEET =
            DriveBySableMod.asResource("textures/gui/widgets.png");
    private static final int ICON_SIZE   = 16;
    private static final int SHEET_WIDTH = 256;
    private static final int SHEET_HEIGHT = 256;

    private final int u, v;

    CablePonderTextures(final int u, final int v) {
        this.u = u;
        this.v = v;
    }

    @Override
    public void render(final GuiGraphics graphics, final int x, final int y) {
        graphics.blit(SHEET, x, y, u, v, ICON_SIZE, ICON_SIZE,
                SHEET_WIDTH, SHEET_HEIGHT);
    }
}