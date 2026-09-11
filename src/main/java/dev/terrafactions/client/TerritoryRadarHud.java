package dev.terrafactions.client;

import com.digitscodecompendium.terralib.client.gui.HudPanelPlacement;
import com.digitscodecompendium.terralib.client.gui.TerraGui;
import com.digitscodecompendium.terralib.client.gui.TerraUiTheme;
import dev.terrafactions.factions.FactionRank;
import dev.terrafactions.journeymap.TerraFactionsClientConfig;
import dev.terrafactions.network.TerritoryRadarPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class TerritoryRadarHud {
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int ACCENT_WIDTH = 3;
    private static final int INDICATOR_SIZE = 10;
    private static final int INDICATOR_GAP = 3;
    private static final int STATUS_GAP = 8;
    private static final int ROW_GAP = 5;
    private static final int MIN_CONTENT_WIDTH = 96;
    private static final int VULNERABLE_RED = 0xFFFF5555;
    private static final int VULNERABLE_YELLOW = 0xFFFFFF55;
    private static TerritoryRadarPayload state = TerritoryRadarPayload.hidden();

    private TerritoryRadarHud() {
    }

    public static void accept(TerritoryRadarPayload payload) {
        state = payload;
    }

    static FactionRank playerFactionRank() {
        return state.factionRank();
    }

    /** Renders the independently configured territory radar. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null
                || !TerraFactionsClientConfig.RADAR_HUD.enabled() || !state.radarVisible()) {
            return;
        }

        String label = radarLabel();
        int contentWidth = Math.max(MIN_CONTENT_WIDTH, minecraft.font.width(label));
        boolean showFactionInfo = TerraFactionsClientConfig.FACTION_INFO_ENABLED.get()
                && state.factionInfoVisible();
        String power = "P: " + state.power() + "/" + state.maximumPower();
        if (showFactionInfo) {
            contentWidth = Math.max(contentWidth,
                    minecraft.font.width(power) + STATUS_GAP + statusWidth(minecraft));
        }
        int width = contentWidth + PADDING * 2 + ACCENT_WIDTH + 2;
        int contentHeight = LINE_HEIGHT + (showFactionInfo ? ROW_GAP + LINE_HEIGHT : 0);
        int height = contentHeight + PADDING * 2;
        HudPanelPlacement placement = TerraFactionsClientConfig.RADAR_HUD.placement();
        double screenX = graphics.guiWidth() * placement.horizontalPercent() / 100.0D;
        double screenY = graphics.guiHeight() * placement.verticalPercent() / 100.0D;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate((float) screenX, (float) screenY, 0.0F);
            graphics.pose().scale((float) placement.scale(), (float) placement.scale(), 1.0F);
            graphics.pose().translate((float) (-width * placement.anchor().horizontal()),
                    (float) (-height * placement.anchor().vertical()), 0.0F);

            TerraGui.raisedPanel(graphics, 0, 0, width, height, TerraUiTheme.VANILLA,
                    TerraFactionsClientConfig.RADAR_HUD.opacity());
            int lineY = PADDING;
            int textX = PADDING + ACCENT_WIDTH + 2;
            int accent = state.vulnerable() ? vulnerabilityColor(minecraft) : 0xFF000000 | state.relationColor();
            graphics.fill(PADDING, lineY, PADDING + ACCENT_WIDTH, lineY + LINE_HEIGHT - 1, accent);
            graphics.drawString(minecraft.font, label, textX, lineY,
                    0xFF000000 | state.relationColor(), false);
            if (showFactionInfo) {
                int separatorY = lineY + LINE_HEIGHT + 2;
                graphics.fill(textX, separatorY, width - PADDING, separatorY + 1,
                        TerraUiTheme.VANILLA.surfaceHighlight());
                renderFactionInfo(graphics, minecraft, power, textX,
                        lineY + LINE_HEIGHT + ROW_GAP);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static String radarLabel() {
        return state.faction().isBlank() || "Wilderness".equals(state.territory())
                ? state.territory()
                : state.faction() + " - " + state.territory();
    }

    private static int statusWidth(Minecraft minecraft) {
        return INDICATOR_SIZE + INDICATOR_GAP + minecraft.font.width("Border") + STATUS_GAP
                + INDICATOR_SIZE + INDICATOR_GAP + minecraft.font.width("Core");
    }

    private static void renderFactionInfo(GuiGraphics graphics, Minecraft minecraft, String power, int x, int y) {
        graphics.drawString(minecraft.font, power, x, y + 1, TerraUiTheme.VANILLA.text(), false);
        int borderX = x + minecraft.font.width(power) + STATUS_GAP;
        int coreX = drawStatus(graphics, minecraft, borderX, y, "Border", state.borderVulnerable());
        drawStatus(graphics, minecraft, coreX + STATUS_GAP, y, "Core", state.coreVulnerable());
    }

    private static int drawStatus(GuiGraphics graphics, Minecraft minecraft, int x, int y,
                                  String label, boolean vulnerable) {
        drawStatusPip(graphics, minecraft, x, y, vulnerable);
        int labelX = x + INDICATOR_SIZE + INDICATOR_GAP;
        graphics.drawString(minecraft.font, label, labelX, y + 1,
                TerraUiTheme.VANILLA.mutedText(), false);
        return labelX + minecraft.font.width(label);
    }

    private static void drawStatusPip(GuiGraphics graphics, Minecraft minecraft, int x, int y,
                                      boolean vulnerable) {
        if (!vulnerable) {
            TerraGui.indicator(graphics, x, y, true, TerraUiTheme.VANILLA);
            return;
        }

        int color = vulnerabilityColor(minecraft);
        int highlight = color == VULNERABLE_YELLOW ? 0xFFFFFFAA : 0xFFFFAAAA;
        graphics.fill(x, y, x + INDICATOR_SIZE, y + INDICATOR_SIZE, TerraUiTheme.VANILLA.outline());
        graphics.fill(x + 2, y + 2, x + 8, y + 8, color);
        graphics.fill(x + 3, y + 3, x + 6, y + 4, highlight);
    }

    private static int vulnerabilityColor(Minecraft minecraft) {
        if (!TerraFactionsClientConfig.VULNERABILITY_FLASH_ENABLED.get() || minecraft.level == null) {
            return VULNERABLE_RED;
        }
        return (minecraft.level.getGameTime() / 10L & 1L) == 0L ? VULNERABLE_RED : VULNERABLE_YELLOW;
    }
}
