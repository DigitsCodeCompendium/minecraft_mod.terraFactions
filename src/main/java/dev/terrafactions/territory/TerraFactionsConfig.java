package dev.terrafactions.territory;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TerraFactionsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CORE_CLAIM_COST;
    public static final ModConfigSpec.IntValue BORDER_CLAIM_COST;
    public static final ModConfigSpec.DoubleValue BORDER_VULNERABILITY_PERCENT;
    public static final ModConfigSpec.BooleanValue REQUIRE_SIDE_CONNECTIVITY;
    public static final ModConfigSpec.IntValue JOURNEYMAP_CLAIM_RADIUS;
    public static final ModConfigSpec.IntValue MAX_ANCHOR_POWER;
    public static final ModConfigSpec.IntValue ANCHOR_ISOLATION_GRACE_TICKS;
    public static final ModConfigSpec.IntValue ANCHOR_RECALCULATION_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue BASE_POWER;
    public static final ModConfigSpec.IntValue POWER_PER_MEMBER;
    public static final ModConfigSpec.IntValue DEATH_POWER_PENALTY;
    public static final ModConfigSpec.IntValue POWER_REGEN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue POWER_REGEN_AMOUNT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("power");
        BASE_POWER = builder.comment("Base maximum power available to every faction.")
                .defineInRange("basePower", 20, 0, Integer.MAX_VALUE);
        POWER_PER_MEMBER = builder.comment("Additional maximum power supplied by each faction member.")
                .defineInRange("powerPerMember", 20, 0, Integer.MAX_VALUE);
        DEATH_POWER_PENALTY = builder.comment("Power lost whenever a faction member dies in PvP.")
                .defineInRange("deathPowerPenalty", 10, 0, Integer.MAX_VALUE);
        POWER_REGEN_INTERVAL_TICKS = builder.comment("Ticks between faction power regeneration pulses.")
                .defineInRange("regenerationIntervalTicks", 12000, 1, Integer.MAX_VALUE);
        POWER_REGEN_AMOUNT = builder.comment("Power restored to each outstanding player-attributed loss at every regeneration pulse.")
                .defineInRange("regenerationAmount", 1, 0, Integer.MAX_VALUE);
        CORE_CLAIM_COST = builder.comment("Power consumed by one capital or core claim.")
                .defineInRange("coreClaimCost", 10, 1, Integer.MAX_VALUE);
        BORDER_CLAIM_COST = builder.comment("Power consumed by one border claim.")
                .defineInRange("borderClaimCost", 1, 1, Integer.MAX_VALUE);
        BORDER_VULNERABILITY_PERCENT = builder.comment("Border claims can be attacked at or below this fraction of maximum faction power.")
                .defineInRange("borderVulnerabilityPercent", 0.30D, 0.0D, 1.0D);
        builder.pop();
        builder.push("claims");
        REQUIRE_SIDE_CONNECTIVITY = builder.comment("Require new claims to share a cardinal edge with existing territory in that dimension.")
                .define("requireSideConnectivity", true);
        JOURNEYMAP_CLAIM_RADIUS = builder.comment("Maximum chunk radius in which players can claim territory from JourneyMap.")
                .defineInRange("journeyMapClaimRadius", 3, 0, Integer.MAX_VALUE);
        MAX_ANCHOR_POWER = builder.comment("Maximum power that can be allocated to one faction anchor.")
                .defineInRange("maximumAnchorPower", 1000, 0, Integer.MAX_VALUE);
        ANCHOR_ISOLATION_GRACE_TICKS = builder.comment("Ticks an isolated anchor remains protected before becoming vulnerable.")
                .defineInRange("anchorIsolationGraceTicks", 24000, 0, Integer.MAX_VALUE);
        ANCHOR_RECALCULATION_INTERVAL_TICKS = builder.comment(
                        "Ticks between periodic anchor border reconciliations. Each reconciliation rebuilds projected borders from anchor circles.")
                .defineInRange("anchorRecalculationIntervalTicks", 200, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private TerraFactionsConfig() {
    }
}
