package com.muoniumplayer.core.ncm.music;

import com.google.gson.JsonParser;
import com.muoniumplayer.core.screens.ncm.LyricDuetGroups;
import com.muoniumplayer.core.screens.ncm.LyricLine;
import com.muoniumplayer.core.screens.ncm.LyricParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LyricVocalistTimelineTest {
    @Test
    void playbackLookupKeepsEarlyJumpsBreaksAndEqualTimestampsWhenSeeking() {
        var first = new LyricLine(1000, "first");
        first.duration = 500;
        first.words.add(new LyricLine.Word("first", 1000, 500));
        var second = new LyricLine(2000, "second");
        var harmony = new LyricLine(2000, "harmony");
        harmony.duration = 500;
        harmony.words.add(new LyricLine.Word("harmony", 2000, 500));
        var rest = new LyricLine(3000, "rest");
        rest.isBreakLine = true;
        var lines = List.of(first, second, harmony, rest);
        assertSame(rest, LyricTimelineSupport.findCurrentLyric(lines, false, 9000));
        assertSame(harmony, LyricTimelineSupport.findCurrentLyric(lines, false, 2999));
        assertSame(harmony, LyricTimelineSupport.findCurrentLyric(lines, false, 2000));
        assertSame(second, LyricTimelineSupport.findCurrentLyric(lines, false, 1700));
        assertSame(first, LyricTimelineSupport.findCurrentLyric(lines, false, 1699.9));
        assertSame(first, LyricTimelineSupport.findCurrentLyric(lines, true, 1999));
        assertSame(first, LyricTimelineSupport.findCurrentLyric(lines, false, 0));
        assertNull(LyricTimelineSupport.findCurrentLyric(List.of(), false, 0));
    }

    @Test
    void plainLyricsFromAnUnsortedSourceHaveAChronologicalLookup() {
        var first = new LyricLine(3200, "first");
        var last = new LyricLine(7000, "last");
        var timeline = LyricTimelineSupport.prepare(List.of(last, first));
        assertTrue(timeline.lines.get(0).isBreakLine);
        assertSame(first, LyricTimelineSupport.findCurrentLyric(timeline.lines, true, 3500));
        assertSame(last, LyricTimelineSupport.findCurrentLyric(timeline.lines, true, 8000));
    }

    @Test
    void compactLyricsFollowSeeksAndLeaveSpaceForTheTitleWhenNoLineIsAvailable() {
        var first = new LyricLine(1000, "first");
        var rest = new LyricLine(3000, "......");
        rest.isBreakLine = true;
        var last = new LyricLine(7500, "last");
        var lines = List.of(first, rest, last);
        assertNull(LyricTimelineSupport.findDisplayLyric(lines, true, 999));
        assertSame(first, LyricTimelineSupport.findDisplayLyric(lines, true, 1000));
        assertNull(LyricTimelineSupport.findDisplayLyric(lines, true, 5000));
        assertSame(last, LyricTimelineSupport.findDisplayLyric(lines, true, 7500));
        assertSame(first, LyricTimelineSupport.findDisplayLyric(lines, true, 2000));

        var missing = LyricTimelineSupport.prepare(List.of());
        assertNull(LyricTimelineSupport.findDisplayLyric(missing.lines, missing.haveNoWords, 5000));
        assertNull(LyricTimelineSupport.findDisplayLyric(List.of(new LyricLine(0, "instrumental")), true, 5000));
    }

    @Test
    void alternatingSingersKeepTheirSidesAndAnEnsembleUsesOrdinaryLayout() {
        var lines = read("""
                <div m:agent="alpha">
                  <p begin="0s" end="1s">第一句</p>
                  <p begin="1s" end="2s" m:agent="beta">第二句</p>
                  <p begin="2s" end="3s" m:agent="beta">第三句</p>
                  <p begin="3s" end="4s">第四句</p>
                  <p begin="4s" end="5s" m:agent="all">合唱</p>
                  <p begin="5s" end="6s" m:agent="alpha beta">合唱</p>
                </div>
                """);
        assertEquals(List.of(0, 1, 1, 0, -1, -1), slots(lines));
        assertEquals(List.of(false, true, true, false, false, false), lines.stream()
                .map(line -> LyricDuetGroups.rightAligned(line.duetSlot)).toList());
        assertTrue(lines.stream().noneMatch(line -> line.showVocalistLabel || line.duetGroup >= 0));
        assertEquals("歌手甲", lines.get(0).vocalist.name());
        assertTrue(lines.get(4).vocalist.ensemble());
        assertTrue(lines.get(5).vocalist.ensemble());
    }

    @Test
    void overlappingVoicesKeepSeparateTimingAndSurviveCacheReload() {
        var original = read("""
                <div>
                  <p begin="0s" end="3s" m:agent="alpha">主声部</p>
                  <p begin="1s" end="2s" m:agent="beta">第二声部</p>
                  <p begin="1.4s" end="2.5s" m:agent="gamma">第三声部</p>
                  <p begin="3s" end="4s" m:agent="beta">第二声部继续</p>
                </div>
                """);
        var lines = LyricTimelineSupport.prepare(LyricCacheStore.deserialize(
                LyricCacheStore.serialize("apple_test", original))).lines;
        assertEquals(List.of(0, 1, 2, 1), slots(lines));
        assertTrue(lines.stream().allMatch(line -> line.showVocalistLabel));
        assertEquals("歌手乙", lines.get(1).vocalist.name());
        assertEquals("gamma", lines.get(2).vocalist.id());
        assertTrue(LyricDuetGroups.sharesGroup(lines.get(0), lines.get(2)));
        assertTrue(LyricDuetGroups.isVocalistActive(lines.get(0), lines.get(2), 1500));
        assertTrue(LyricDuetGroups.isVocalistActive(lines.get(1), lines.get(2), 1500));
        assertFalse(LyricDuetGroups.isVocalistActive(lines.get(1), lines.get(2), 2000));
    }

    @Test
    void aBackingVocalTailDoesNotCreateAnOverlappingLeadGroup() {
        var lines = read("""
                <div>
                  <p begin="0s" end="1s" m:agent="alpha">
                    <span begin="0s" end="1s">独唱</span>
                    <span m:role="x-bg" begin="0.8s" end="2s">和声</span>
                  </p>
                  <p begin="1.1s" end="2.1s" m:agent="beta">接唱</p>
                </div>
                """);
        assertEquals(List.of(0, 1), slots(lines));
        assertTrue(lines.get(0).words.get(1).background);
        assertFalse(LyricDuetGroups.sharesGroup(lines.get(0), lines.get(1)));
    }

    @Test
    void olderCacheWithNoVoiceKeepsItsTimingFallback() {
        var oldCache = JsonParser.parseString("""
                {"lines":[{"t":0,"d":2000,"x":"甲"},{"t":1000,"d":2000,"x":"乙"}]}
                """).getAsJsonObject();
        var lines = LyricTimelineSupport.prepare(LyricCacheStore.deserialize(oldCache)).lines;
        assertEquals(List.of(0, 1), slots(lines));
        assertNull(lines.get(0).vocalist);
        assertTrue(LyricDuetGroups.sharesGroup(lines.get(0), lines.get(1)));
    }

    private static List<LyricLine> read(String body) {
        String document = """
                <tt xmlns="http://www.w3.org/ns/ttml" xmlns:m="http://www.w3.org/ns/ttml#metadata">
                  <head><metadata>
                    <m:agent type="person" xml:id="alpha"><m:name>歌手甲</m:name></m:agent>
                    <m:agent type="person" xml:id="beta"><m:name>歌手乙</m:name></m:agent>
                    <m:agent type="person" xml:id="gamma"/>
                    <m:agent type="group" xml:id="all"/>
                  </metadata></head>
                  <body>%s</body>
                </tt>
                """.formatted(body);
        List<LyricLine> parsed = new ArrayList<>();
        LyricParser.parseTtml(document, parsed);
        return LyricTimelineSupport.prepare(parsed).lines;
    }

    private static List<Integer> slots(List<LyricLine> lines) {
        return lines.stream().map(line -> line.duetSlot).toList();
    }
}
