package dev.terrafactions.journeymap;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TerraFactionsClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        OVERLAY_ENABLED = builder.comment("Show faction territory overlays in JourneyMap.")
                .define("overlayEnabled", true);
        SPEC = builder.build();
    }

    private TerraFactionsClientConfig() {
    }
}
