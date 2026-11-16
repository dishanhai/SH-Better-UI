// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.search;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PhoneticTextTest {
    @Test
    void supportsFullInitialMixedAndInteriorQueries() {
        var text = new PhoneticText(List.of("da", "huo", "shi"));
        for (String query : List.of("dahuoshi", "dhs", "dahs", "dahuos", "dhuoshi", "huos", "uos", "hs")) {
            assertTrue(text.contains(query), query);
        }
    }

    @Test
    void preservesAmbiguousPrefixesAndRejectsSkippedOrInvalidSyllables() {
        assertTrue(new PhoneticText(List.of("da", "an", "shi")).contains("dans"));
        var text = new PhoneticText(List.of("da", "huo", "shi"));
        for (String query : List.of("", "dash", "dhhs", "dahx", "dahss", "dhsi", "dhshhu")) {
            assertFalse(text.contains(query), query);
        }
        assertFalse(new PhoneticText(List.of()).contains("d"));
        assertThrows(IllegalArgumentException.class, () -> new PhoneticText(List.of("da", "")));
    }

    @Test
    void snapshotsSyllablesForReusableConcurrentIndexes() {
        var syllables = new ArrayList<>(List.of("da", "huo", "shi"));
        var text = new PhoneticText(syllables);
        syllables.clear();
        assertTrue(text.contains("dahs"));
    }

    @Test
    void boundsAmbiguousLongNamesWithoutExpandingSpellingCombinations() {
        var text = new PhoneticText(java.util.Collections.nCopies(100, "aaaa"));
        assertFalse(text.contains("a".repeat(200) + "b"));
    }
}
