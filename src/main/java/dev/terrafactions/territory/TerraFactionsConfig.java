package dev.terrafactions.territory;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TerraFactionsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CORE_CLAIM_COST;
    public static final ModConfigSpec.IntValue BORDER_CLAIM_COST;
    public static final ModConfigSpec.DoubleValue BORDER_VULNERABILITY_PERCENT;
    public static final ModConfigSpec.BooleanValue REQUIRE_SIDE_CONNECTIVITY;
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
        DEATH_POWER_PENALTY = builder.comment("Power lost whenever a faction member dies.")
                .defineInRange("deathPowerPenalty", 10, 0, Integer.MAX_VALUE);
        POWER_REGEN_INTERVAL_TICKS = builder.comment("Ticks between online member power regeneration pulses.")
                .defineInRange("regenerationIntervalTicks", 12000, 1, Integer.MAX_VALUE);
        POWER_REGEN_AMOUNT = builder.comment("Faction power restored per online member at each regeneration pulse.")
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
        builder.pop();
        SPEC = builder.build();
    }

    private TerraFactionsConfig() {
    }
}
