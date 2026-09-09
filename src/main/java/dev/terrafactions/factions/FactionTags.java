package dev.terrafactions.factions;

import java.util.Locale;
import java.util.UUID;

public final class FactionTags {
    public static final String FACTIONLESS = "NF";
    public static final int MAX_LENGTH = 4;

    private FactionTags() {
    }

    public static String defaultFor(String factionName, UUID factionId) {
        StringBuilder tag = new StringBuilder(MAX_LENGTH);
        if (factionName != null) {
            String upperName = factionName.toUpperCase(Locale.ROOT);
            for (int i = 0; i < upperName.length() && tag.length() < MAX_LENGTH; i++) {
                char character = upperName.charAt(i);
                if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
                    tag.append(character);
                }
            }
        }
        if (!tag.isEmpty()) {
            return tag.toString();
        }
        return factionId.toString().replace("-", "").substring(0, MAX_LENGTH).toUpperCase(Locale.ROOT);
    }
}
