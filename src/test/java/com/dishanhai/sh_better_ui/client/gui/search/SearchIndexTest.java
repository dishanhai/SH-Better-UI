package com.dishanhai.sh_better_ui.client.gui.search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：覆盖搜索范围、排序与可填充文本。 */
class SearchIndexTest {
    @Test
    void plainTextSearchTreatsFacetPrefixesAndQuotesAsLiteralText() {
        var numbered = metadataEntry(1, "Block #1", "demo:block", "Demo", "other:tag");
        var named = metadataEntry(2, "Block @home", "other:block", "Home", "forge:blocks");
        var quoted = entry(3, "Block \"quoted\"", "demo:quoted");
        var index = new SearchIndex<>(List.of(numbered, named, quoted));
        assertEquals(List.of(numbered), index.searchPlainText("#1"));
        assertEquals(List.of(named), index.searchPlainText("@home"));
        assertEquals(List.of(quoted), index.searchPlainText("\"quoted\""));
        assertEquals(List.of(), index.searchPlainText("#forge:blocks"));
        assertEquals(List.of(), index.searchPlainText("block", value -> value > 3));
        assertEquals(List.of(numbered, quoted), index.search("@demo"));
    }

    @Test
    void plainTextSuggestionsKeepLiteralCompletionsPinyinAndHostOrder() {
        var index = new SearchIndex<>(List.of(entry(1, "Block #1", "mod:block"),
                entry(2, "铁锭", "mod:iron"), entry(3, "铁锭块", "mod:iron_block"),
                entry(4, "Other", "mod:tieding")));
        var literal = index.suggestPlainText("#1", List.of(4, 999, 1), 8);
        assertEquals(List.of("Block #1"), completions(literal));
        var pinyin = index.suggestPlainText("tieding", List.of(3, 2, 4), 8);
        assertEquals(List.of(4, 3, 2), values(pinyin));
        assertEquals("mod:tieding", pinyin.getFirst().completion());
        assertEquals(List.of(), index.suggestPlainText(" ", List.of(1), 8));
        assertEquals(List.of(), index.suggestPlainText("block", List.of(1), 0));
        assertEquals("Block #1", index.suggestMatches("$external \"tag\"", List.of(1), 8).getFirst().completion());
    }

    @Test
    void preparedMatcherPreservesPlainTextSearchForReorderedCandidates() {
        var index = new SearchIndex<>(List.of(entry(1, "铁锭", "mod:iron"),
                entry(1, "Alias", "mod:alias"), entry(2, "铁锭块", "mod:iron_block"),
                entry(3, "Block @home", "mod:block")));
        for (String query : List.of("td", "alias", "@home", "mod:iron", "missing", "")) {
            var expected = index.searchPlainText(query).stream().map(SearchIndex.Entry::value).toList();
            assertEquals(List.of(3, 2, 1, 99).stream().filter(expected::contains).toList(),
                    List.of(3, 2, 1, 99).stream().filter(index.plainTextMatcher(query)).toList());
        }
    }

    @Test
    void orderedCandidatesKeepHostOrderWithinEachRankAndIgnoreUnknownValues() {
        var index = new SearchIndex<>(List.of(entry(1, "Iron A", "mod:a"),
                entry(2, "Iron B", "mod:b"), entry(3, "Iron", "mod:iron"),
                entry(4, "Copper", "mod:copper")));
        assertEquals(List.of(3, 2, 1), values(orderedSuggestions(index, "suggest", "iron",
                List.of(2, 4, 999, 1, 3), 8)));
        assertEquals(List.of(3, 2), values(orderedSuggestions(index, "suggest", "iron",
                List.of(2, 4, 999, 1, 3), 2)));
    }

    @Test
    void externallyMatchedSuggestionsRankWithoutReapplyingTextOrFacetFilters() {
        var index = new SearchIndex<>(List.of(entry(1, "Copper", "mod:copper"),
                entry(2, "Iron", "mod:iron"), entry(3, "Iron Plate", "mod:plate")));
        assertEquals(List.of(2, 3, 1), values(orderedSuggestions(index, "suggestMatches", "iron",
                List.of(1, 3, 999, 2), 8)));
        assertEquals(List.of(3, 1), values(orderedSuggestions(index, "suggestMatches", "#conductors",
                List.of(3, 1), 8)));
        assertEquals(List.of(), orderedSuggestions(index, "suggestMatches", " ", List.of(1), 8));
        assertEquals(List.of(), orderedSuggestions(index, "suggestMatches", "iron", List.of(1), 0));
    }

    @Test
    void orderedCandidatesReusePinyinMatchingAndKeepIdCompletions() {
        var index = new SearchIndex<>(List.of(entry(1, "铁锭", "mod:iron"),
                entry(2, "铁锭块", "mod:iron_block"), entry(3, "Other", "mod:tieding")));
        assertEquals(List.of(2, 1), values(orderedSuggestions(index, "suggest", "td",
                List.of(2, 1), 8)));
        var result = orderedSuggestions(index, "suggest", "tieding", List.of(2, 1, 3), 8);
        assertEquals(List.of(3, 2, 1), values(result));
        assertEquals("mod:tieding", result.getFirst().completion());
    }

    private static List<SearchIndex.Suggestion<Integer>> orderedSuggestions(SearchIndex<Integer> index,
            String method, String query, List<Integer> candidates, int limit) {
        return method.equals("suggestMatches") ? index.suggestMatches(query, candidates, limit)
                : index.suggest(query, candidates, limit);
    }

    @Test
    void derivesModSuggestionsFromExistingIdEntries() {
        var iron = entry(1, "Iron Ingot", "minecraft:iron_ingot");
        var copper = entry(2, "Copper Ingot", "minecraft:copper_ingot");
        var modded = entry(3, "Iron Plate", "example:iron_plate");
        var index = new SearchIndex<>(List.of(iron, copper, modded));

        assertEquals(List.of(iron, copper), index.search("@MINECRAFT"));
        assertEquals(List.of("@minecraft"), completions(index.suggest("@mine", 8)));
        assertEquals("craft", index.suggest("@mine", 1).getFirst().suffixFor("@mine"));
        assertEquals(List.of("@minecraft", "@example"), completions(index.suggest("@", 8)));
    }

    @Test
    void matchesModNamesPinyinAndTagsAlongsideOrdinaryTerms() {
        var iron = metadataEntry(1, "铁锭", "demo:iron_ingot", "测试模组", "forge:ingots/iron");
        var plate = metadataEntry(2, "铁板", "demo:iron_plate", "测试模组", "forge:plates/iron");
        var other = metadataEntry(3, "Iron Ingot", "other:iron_ingot", "Other Mods", "forge:ingots/iron");
        var index = new SearchIndex<>(List.of(iron, plate, other));

        assertEquals(List.of(iron, plate), index.search("@测试"));
        assertEquals(List.of(iron, plate), index.search("@CSMZ"));
        assertEquals(List.of(other), index.search("@\"Other Mods\""));
        assertEquals(List.of(iron), index.search("td @demo #forge:ingots/iron"));
        assertEquals(List.of(iron, other), index.search("#INGOTS/IRON"));
        assertEquals(List.of(), index.search("@demo #plates 铜"));
        assertEquals(List.of(), index.search("#missing"));
        assertEquals(List.of(), index.search("@missing"));
        assertEquals(List.of(), index.search("forge:ingots"));
    }

    @Test
    void completesOnlyTheActiveFacetAndPreservesTheExistingQuery() {
        var iron = metadataEntry(1, "铁锭", "demo:iron_ingot", "测试模组", "forge:ingots/iron");
        var plate = metadataEntry(2, "铁板", "demo:iron_plate", "测试模组", "forge:plates/iron");
        var other = metadataEntry(3, "铁锭", "other:iron_ingot", "Other Mods", "forge:ingots/iron");
        var index = new SearchIndex<>(List.of(iron, plate, other));

        assertEquals(List.of("  铁锭\t@demo"), completions(index.suggest("  铁锭\t@de", 8)));
        assertEquals(List.of("@demo\u3000#forge:ingots/iron", "@demo\u3000#forge:plates/iron"),
                completions(index.suggest("@demo\u3000#", 8)));
        assertEquals(List.of("@demo"), completions(index.suggest("@csmz", 8)));
        assertEquals(List.of("@other"), completions(index.suggest("@\"Other M", 8)));
        assertEquals(List.of("@demo #forge:ingots/iron"),
                completions(index.suggest("@demo #forge:ing", 8)));
        assertEquals(List.of("@demo 铁锭"), completions(index.suggest("@demo tieding", 8)));
        assertEquals(List.of("#forge:ingots/iron @other"),
                completions(index.suggest("#forge:ingots/iron @ot", 8)));
        assertEquals(List.of(), index.suggest("铜 @", 8));
    }

    @Test
    void facetSuggestionsDeduplicateWithinScopeBeforeApplyingLimitAndFillability() {
        var first = metadataEntry(1, "Iron Ingot", "minecraft:iron_ingot", "Minecraft", "forge:ingots/iron");
        var second = metadataEntry(2, "Copper Ingot", "minecraft:copper_ingot", "Minecraft", "forge:ingots/copper");
        var other = metadataEntry(3, "Iron Plate", "other:iron_plate", "Other Mods", "forge:ingots/iron");
        var index = new SearchIndex<>(List.of(first, second, other));

        assertEquals(List.of("#forge:ingots/iron", "#forge:ingots/copper"),
                completions(index.suggest("#", 8)));
        assertEquals(List.of(3), values(index.suggest("#iron", value -> value == 3, 8)));
        assertEquals(List.of("@minecraft"), completions(index.suggest("@", 1)));
        assertEquals(List.of("@other"), completions(index.suggest("@", value -> value != 1 && value != 2, 8)));
        assertEquals(List.of("@other"),
                completions(index.suggest("@", value -> true, 1, text -> text.length() <= 6)));
        assertEquals(List.of("#forge:ingots/copper"),
                completions(index.suggest("#", value -> true, 1, text -> !text.endsWith("iron"))));
        assertEquals(List.of(), index.suggest("#", 0));
    }

    @Test
    void literalModMatchesRankBeforePinyinWithStableTies() {
        var pinyin = metadataEntry(1, "铁锭", "demo:iron_ingot", "测试模组");
        var contains = metadataEntry(2, "Plate", "mod_csmz:plate", "Other");
        var prefix = metadataEntry(3, "Dust", "csmz_tools:dust", "Tools");
        var exact = metadataEntry(4, "Gem", "csmz:gem", "Gems");
        var index = new SearchIndex<>(List.of(pinyin, contains, prefix, exact));

        assertEquals(List.of("@csmz", "@csmz_tools", "@mod_csmz", "@demo"),
                completions(index.suggest("@csmz", 8)));
    }

    @Test
    void completedNamesWithReservedWordsQuotesAndBackslashesStillMatchTheirEntry() {
        var names = List.of("Block #1", "Block @home", "Block \"quoted\"", "Block \\path",
                "Block \"quoted\" #1", "Block \\path @home", "Block \"\\path\" #1");
        for (String name : names) {
            var item = metadataEntry(1, name, "demo:block", "Demo");
            var index = new SearchIndex<>(List.of(item));
            for (String query : List.of("Blo", "@demo Blo")) {
                var suggestions = index.suggest(query, 8);
                assertEquals(1, suggestions.size(), name + " queried by " + query);
                String completion = suggestions.getFirst().completion();
                assertTrue(index.search(completion).contains(item), name + " completed to " + completion);
            }
        }
    }

    @Test
    void bareModSuggestionsKeepSourceOrderAcrossNamedAndLegacyEntries() {
        var named = metadataEntry(1, "Block", "demo:block", "Demo");
        var legacy = entry(2, "Iron Ingot", "minecraft:iron_ingot");
        var nextNamed = metadataEntry(3, "Plate", "other:plate", "Other Mods");
        var index = new SearchIndex<>(List.of(named, legacy, nextNamed));

        assertEquals(List.of("@demo", "@minecraft", "@other"), completions(index.suggest("@", 8)));
        assertEquals(List.of("@demo"), completions(index.suggest("@", 1)));
        assertEquals(List.of("@minecraft", "@other"),
                completions(index.suggest("@", value -> value != 1, 8)));
    }

    @Test
    void snapshotsTagMetadataAndKeepsEntriesWithoutNamespacesUsable() {
        var tags = new ArrayList<>(List.of("forge:ingots/iron"));
        var iron = new SearchIndex.Entry<>(1, "Iron", List.of("demo:iron"), List.of(), "Demo", tags);
        var noNamespace = entry(2, "Iridium", "iridium");
        var index = new SearchIndex<>(List.of(iron, noNamespace));
        tags.clear();

        assertEquals(List.of(iron), index.search("#forge:ingots/iron"));
        assertEquals(List.of("@demo"), completions(index.suggest("@", 8)));
        assertEquals(List.of(noNamespace), index.search("iridium"));
        assertThrows(UnsupportedOperationException.class, () -> iron.tags().clear());
    }

    @Test
    void matchesTokensAcrossFieldsAndKeepsScopeAndSourceOrder() {
        var copper = entry(1, "铜锭", "minecraft:copper_ingot", "tongding");
        var iron = entry(2, "铁锭", "minecraft:iron_ingot", "tieding");
        var index = new SearchIndex<>(List.of(copper, iron));

        assertEquals(List.of(copper), index.search("  铜\u3000MINECRAFT:cop\tTONG "));
        assertEquals(List.of(), index.search("铜 iron"));
        assertEquals(List.of(copper, iron), index.search("\u3000\t"));
        assertEquals(List.of(iron), index.search("ingot", value -> value == 2));
        assertEquals(List.of(iron), index.search("", value -> value == 2));
        assertEquals(List.of(), index.suggest("copper", value -> value == 2, 8));
    }

    @Test
    void matchesMixedPinyinPrefixesWithoutSkippingSyllables() {
        var flint = metadataEntry(1, "打火石", "minecraft:flint_and_steel", "打火工具");
        var alias = entry(2, "Flint and Steel", "other:tool", "打火石");
        var separated = entry(3, "打-火石", "other:separated");
        var index = new SearchIndex<>(List.of(flint, alias, separated));
        for (String query : List.of("dah", "dahs", "dahuos", "dhuoshi", "dahuoshi", "dhs", "DAHS", "dǎhs")) {
            assertEquals(List.of(flint, alias), index.search(query), query);
            assertEquals(List.of(1, 2), values(index.suggest(query, 8)), query);
            assertEquals("打火石", index.suggest(query, 8).getFirst().completion(), query);
        }
        assertEquals(List.of(flint, alias, separated), index.search("huos"));
        assertEquals(List.of(flint), index.search("@minecraft dahs"));
        assertEquals(List.of(flint), index.search("@dahgj dahs"));
        assertEquals(List.of(2), values(index.suggest("dahs", value -> value == 2, 8)));
        assertEquals(List.of(2, 1), values(index.suggestPlainText("dahs", List.of(3, 2, 1), 8)));
        for (String query : List.of("dash", "dhhs", "dahx", "dahss", "dhsi", "dhshhu")) {
            assertEquals(List.of(), index.search(query), query);
        }
    }

    @Test
    void matchesPinyinFromLocalizedNamesAndAliases() {
        var iron = entry(1, "鐵錠", "mod:iron");
        var copper = entry(2, "铜锭", "mod:copper", "赤金属");
        var gem = entry(3, "绿宝石", "mod:gem");
        var index = new SearchIndex<>(List.of(iron, copper, gem));

        assertEquals(List.of(iron), index.search("TIEDING"));
        assertEquals(List.of(iron), index.search("tiě dìng"));
        assertEquals(List.of(iron, copper), index.search("td"));
        assertEquals(List.of(iron), index.search("鐵 ding mod:iron"));
        assertEquals(List.of(copper), index.search("tongd"));
        assertEquals(List.of(copper), index.search("cjs"));
        assertEquals(List.of(gem), index.search("lǜbǎoshí"));
        assertEquals(List.of(gem), index.search("lvbaoshi"));
        assertEquals(List.of(gem), index.search("lbs"));
        assertEquals(List.of(copper), index.search("td", value -> value == 2));
        assertEquals(List.of(), index.search("td gem"));
    }

    @Test
    void keepsLiteralSuggestionsAheadOfPinyinAndFillsTheOriginalName() {
        var iron = entry(1, "铁锭", "mod:iron");
        var byId = entry(2, "Iron", "mod:tieding");
        var byName = entry(3, "Tieding", "mod:translation");
        var index = new SearchIndex<>(List.of(iron, byId, byName));

        assertEquals(List.of(3, 2, 1), values(index.suggest("tieding", 3)));
        var suggestion = index.suggest("td", 1).getFirst();
        assertEquals("铁锭", suggestion.completion());
        assertEquals("", suggestion.suffixFor("td"));
        assertEquals(List.of(), index.suggest("td", value -> true, 1, text -> text.length() <= 1));
    }

    @Test
    void ranksNameBeforeIdsAndAliasesWithStableTiesAndLimits() {
        var index = new SearchIndex<>(List.of(entry(7, "Other", "mod:other", "iron"),
                entry(6, "Metal", "mod:iron"), entry(5, "Refined", "iron_plate"),
                entry(4, "Element", "iron"), entry(3, "Raw Iron", "mod:raw"),
                entry(2, "Iron Plate", "mod:plate"), entry(1, "Iron Alloy", "mod:alloy"),
                entry(0, "Iron", "mod:ingot")));

        assertEquals(List.of(0, 2, 1, 3, 4, 5, 6, 7), values(index.suggest("iron", 12)));
        assertEquals(List.of(0, 2, 1), values(index.suggest("iron", 3)));
        assertEquals(List.of(3, 4), values(index.suggest("iron", value -> value >= 3, 2)));
        assertEquals(List.of(), index.suggest(" ", 8));
        assertEquals(List.of(), index.suggest("iron", 0));
        assertEquals(List.of("iron", "iron_plate", "mod:iron", "Other"),
                index.suggest("iron", value -> value >= 4, 8).stream().map(SearchIndex.Suggestion::completion).toList());
    }

    @Test
    void predictsOnlyLiteralPrefixesAndSkipsUnfillableResults() {
        var iron = entry(1, "Iron Ingot", "mod:iron_ingot", "tieding");
        var shortName = entry(2, "Iron", "mod:iron");
        var index = new SearchIndex<>(List.of(iron, shortName));

        var name = index.suggest("IRON I", 1).getFirst();
        assertEquals("ngot", name.suffixFor("IRON I"));
        assertEquals("", name.suffixFor(" iron i"));
        assertEquals("", name.suffixFor(""));
        assertEquals("", index.suggest("tieding", 1).getFirst().suffixFor("tieding"));
        assertEquals("ingot", index.suggest("mod:iron_", 1).getFirst().suffixFor("mod:iron_"));
        assertEquals(List.of(2), values(index.suggest("iro", value -> true, 1, text -> text.length() <= 4)));
        var withShortId = new SearchIndex<>(List.of(entry(3, "Iron Ingot", "iron")));
        assertEquals("iron", withShortId.suggest("iro", value -> true, 1, text -> text.length() <= 4)
                .getFirst().completion());
    }

    @Test
    void snapshotsTheEntriesAndSearchFields() {
        var ids = new ArrayList<>(List.of("mod:iron"));
        var entries = new ArrayList<>(List.of(new SearchIndex.Entry<>(1, "Iron", ids, List.of())));
        var index = new SearchIndex<>(entries);
        ids.clear();
        entries.clear();

        assertEquals(1, index.search("mod:iron").size());
        assertThrows(UnsupportedOperationException.class, () -> index.search("").clear());
    }

    private static SearchIndex.Entry<Integer> entry(int value, String name, String id, String... aliases) {
        return new SearchIndex.Entry<>(value, name, List.of(id), List.of(aliases));
    }

    private static SearchIndex.Entry<Integer> metadataEntry(int value, String name, String id,
                                                            String modName, String... tags) {
        return new SearchIndex.Entry<>(value, name, List.of(id), List.of(), modName, List.of(tags));
    }

    private static List<Integer> values(List<SearchIndex.Suggestion<Integer>> suggestions) {
        return suggestions.stream().map(suggestion -> suggestion.entry().value()).toList();
    }

    private static List<String> completions(List<SearchIndex.Suggestion<Integer>> suggestions) {
        return suggestions.stream().map(SearchIndex.Suggestion::completion).toList();
    }
}
