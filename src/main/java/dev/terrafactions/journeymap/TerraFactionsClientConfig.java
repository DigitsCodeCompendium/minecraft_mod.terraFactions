package dev.terrafactions.journeymap;

import com.digitscodecompendium.terralib.client.gui.HudAnchor;
import com.digitscodecompendium.terralib.client.gui.HudPanelConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class TerraFactionsClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;
    public static final HudPanelConfig RADAR_HUD;
    public static final ModConfigSpec.BooleanValue VULNERABILITY_FLASH_ENABLED;
    public static final ModConfigSpec.BooleanValue FACTION_INFO_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        OVERLAY_ENABLED = builder.comment("Show faction territory overlays in JourneyMap.")
                .translation("terrafactions.configuration.overlayEnabled")
                .define("overlayEnabled", true);
        builder.comment("Configure the territory radar HUD panel.")
                .translation("terrafactions.configuration.hud");
        RADAR_HUD = HudPanelConfig.define(builder, "hud", "terrafactions.configuration",
                new HudPanelConfig.Defaults(true, 1.0D, 1.0D, 1.0D, 1.0D, HudAnchor.TOP_LEFT));
        builder.push("hud");
        VULNERABILITY_FLASH_ENABLED = builder.comment("Flash the warning when the current territory is vulnerable.")
                .translation("terrafactions.configuration.vulnerabilityFlashEnabled")
                .define("vulnerabilityFlashEnabled", true);
        FACTION_INFO_ENABLED = builder.comment("Show your faction power and claim security in the radar panel.")
                .translation("terrafactions.configuration.factionInfoEnabled")
                .define("factionInfoEnabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private TerraFactionsClientConfig() {
    }
}
