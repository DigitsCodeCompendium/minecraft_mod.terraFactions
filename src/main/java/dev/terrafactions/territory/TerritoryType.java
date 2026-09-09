package dev.terrafactions.territory;

public enum TerritoryType {
    CAPITAL,
    CORE,
    BORDER;

    public int cost() {
        return this == BORDER
                ? TerraFactionsConfig.BORDER_CLAIM_COST.get()
                : TerraFactionsConfig.CORE_CLAIM_COST.get();
    }
}
