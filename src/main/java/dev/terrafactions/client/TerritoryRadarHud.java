package dev.terrafactions.client;

import com.digitscodecompendium.terralib.client.gui.TerraGui;
import com.digitscodecompendium.terralib.client.gui.TerraUiTheme;
import dev.terrafactions.network.TerritoryRadarPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class TerritoryRadarHud {
    private static final int HEIGHT = 30;
    private static TerritoryRadarPayload state = TerritoryRadarPayload.hidden();

    private TerritoryRadarHud() {
    }

    public static void accept(TerritoryRadarPayload payload) {
        state = payload;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!state.visible() || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }

        int contentWidth = Math.max(minecraft.font.width(state.territory()), minecraft.font.width(state.faction()));
        int width = Math.max(92, contentWidth + 24);
        int x = (graphics.guiWidth() - width) / 2;
        int y = 12;

        TerraGui.raisedPanel(graphics, x, y, width, HEIGHT, TerraUiTheme.VANILLA);
        graphics.fill(x + 4, y + 4, x + 7, y + HEIGHT - 4, 0xFF000000 | state.color());
        graphics.drawCenteredString(minecraft.font, state.territory(), x + width / 2, y + 5,
                TerraUiTheme.VANILLA.text());
        graphics.drawCenteredString(minecraft.font, state.faction(), x + width / 2, y + 16,
                0xFF000000 | state.color());
    }
}
