package dev.terrafactions.factions;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactionTagsTest {
    private static final UUID ID = UUID.fromString("abcd1234-0000-0000-0000-000000000000");

    @Test
    void usesFirstFourLettersAndNumbers() {
        assertEquals("THED", FactionTags.defaultFor("The Digital Empire", ID));
        assertEquals("A12B", FactionTags.defaultFor("A-12 Base", ID));
    }

    @Test
    void keepsShortNamesShort() {
        assertEquals("OX", FactionTags.defaultFor("Ox", ID));
    }

    @Test
    void fallsBackToFactionIdWhenNameHasNoSupportedCharacters() {
        assertEquals("ABCD", FactionTags.defaultFor("---", ID));
    }
}
