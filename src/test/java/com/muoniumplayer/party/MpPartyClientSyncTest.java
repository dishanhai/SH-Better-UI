// Author: dishanhai
package com.muoniumplayer.party;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MpPartyClientSyncTest {
    @BeforeEach
    void establishClockOffset() {
        MpSyncPolicy.compensate(0L, System.currentTimeMillis() + 10_000L);
    }

    @AfterEach
    void leaveRoom() {
        receive(MpProto.msg(MpProto.S_LEFT));
    }

    @Test
    void pausedPlayAndHeartbeatKeepTheExactPosition() {
        receive(track(1, 10_000L, false));
        assertEquals(10_000L, MpPartyClient.positionMs());

        JsonObject heartbeat = frame(MpProto.S_TIME, 1, 10_000L);
        heartbeat.addProperty("playing", false);
        receive(heartbeat);
        assertEquals(10_000L, MpPartyClient.positionMs());
    }

    @Test
    void pausedSeekUsesTheExactPositionWithOrWithoutSequence() {
        receive(track(1, 10_000L, false));
        receive(frame(MpProto.S_SEEK, 2, 45_000L));
        assertEquals(45_000L, MpPartyClient.positionMs());

        JsonObject legacySeek = frame(MpProto.S_SEEK, 2, 20_000L);
        legacySeek.remove("seq");
        receive(legacySeek);
        assertEquals(20_000L, MpPartyClient.positionMs());
    }

    @ParameterizedTest
    @ValueSource(strings = {MpProto.S_TIME, MpProto.S_PAUSE, MpProto.S_SEEK, MpProto.S_STOP})
    void oldFramesCannotRollBackPlayback(String op) {
        receive(track(5, 45_000L, true));

        JsonObject stale = frame(op, 4, 10_000L);
        stale.addProperty("playing", false);
        receive(stale);

        assertTrue(MpPartyClient.playing());
        assertTrue(MpPartyClient.positionMs() >= 45_000L);
    }

    @Test
    void stopRejectsLatePlayAndHeartbeats() {
        receive(track(5, 45_000L, true));
        receive(frame(MpProto.S_STOP, 6, 0L));
        receive(track(5, 45_000L, true));
        JsonObject heartbeat = frame(MpProto.S_TIME, 5, 46_000L);
        heartbeat.addProperty("playing", true);
        receive(heartbeat);

        assertFalse(MpPartyClient.playing());
        assertEquals(0L, MpPartyClient.positionMs());
    }

    @Test
    void personalPauseSurvivesOrdinaryTrackChangesUntilResync() throws ReflectiveOperationException {
        receive(track(2, 10_000L, true));
        setPersonalPause();

        receive(track(3, 0L, true));
        assertTrue(MpPartyClient.selfPaused());

        JsonObject staleResync = track(2, 10_000L, true);
        staleResync.addProperty("resync", true);
        receive(staleResync);
        assertTrue(MpPartyClient.selfPaused());

        JsonObject resync = track(3, 15_000L, true);
        resync.addProperty("resync", true);
        receive(resync);
        assertFalse(MpPartyClient.selfPaused());
    }

    @Test
    void idleResyncClearsPersonalPause() throws ReflectiveOperationException {
        setPersonalPause();
        JsonObject stop = frame(MpProto.S_STOP, 4, 0L);
        stop.addProperty("resync", true);
        receive(stop);
        assertFalse(MpPartyClient.selfPaused());
    }

    private static void setPersonalPause() throws ReflectiveOperationException {
        // Simulate the local half of the request without binding the process-wide ticker.
        var field = MpPartyClient.class.getDeclaredField("selfPaused");
        field.setAccessible(true);
        field.setBoolean(null, true);
    }

    private static JsonObject track(long seq, long pos, boolean playing) {
        JsonObject message = frame(MpProto.S_PLAY, seq, pos);
        message.addProperty("playing", playing);
        JsonObject track = new JsonObject();
        track.addProperty("songId", "track-" + seq);
        track.addProperty("duration", 180_000L);
        // A track waiting for its URL exercises the room clock without starting audio.
        message.add("track", track);
        return message;
    }

    private static JsonObject frame(String op, long seq, long pos) {
        JsonObject message = MpProto.msg(op);
        message.addProperty("seq", seq);
        message.addProperty("pos", pos);
        message.addProperty("serverTime", System.currentTimeMillis());
        return message;
    }

    private static void receive(JsonObject message) {
        MpPartyClient.onFrame(MpProto.encode(message));
    }
}
