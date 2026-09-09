package dev.terrafactions.factions;

public enum FactionRank {
    OWNER,
    LEADER,
    COMMANDER,
    MEMBER,
    GUEST;

    public boolean isLeadership() {
        return this == OWNER || this == LEADER || this == COMMANDER;
    }

    public boolean canBuild() {
        return this != GUEST;
    }
}
