// Author: dishanhai
package com.muoniumplayer.party;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MpPartyClientSyncTest {
    private final List<JsonObject> sent = new ArrayList<>();
    private Field hostField;
    private MpHost previousHost;

    @BeforeEach
    void establishClockOffset() throws ReflectiveOperationException {
        // Bind only the transport; these frame tests do not need the wall-clock ticker.
        hostField = MpPartyClient.class.getDeclaredField("host");
        hostField.setAccessible(true);
        previousHost = (MpHost) hostField.get(null);
        hostField.set(null, new MpHost() {
            private final UUID player = UUID.randomUUID();

            @Override public boolean canSend() { return true; }
            @Override public void send(byte[] frame) { sent.add(MpProto.decode(frame)); }
            @Override public UUID selfUuid() { return player; }
            @Override public void chat(String message) {}
        });
        MpSyncPolicy.compensate(0L, System.currentTimeMillis() + 10_000L);
    }

    @AfterEach
    void leaveRoom() throws ReflectiveOperationException {
        receive(MpProto.msg(MpProto.S_LEFT));
        hostField.set(null, previousHost);
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
    void personalPauseSurvivesOrdinaryTrackChangesUntilResync() {
        receive(track(2, 10_000L, true));
        MpPartyClient.setSelfPause(true);

        receive(track(3, 0L, true));
        assertTrue(MpPartyClient.selfPaused());

        MpPartyClient.setSelfPause(false);
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
    void idleResyncClearsPersonalPause() {
        MpPartyClient.setSelfPause(true);
        MpPartyClient.setSelfPause(false);
        JsonObject stop = frame(MpProto.S_STOP, 4, 0L);
        stop.addProperty("resync", true);
        receive(stop);
        assertFalse(MpPartyClient.selfPaused());
    }

    @Test
    void legacyIdleStopCompletesAResume() {
        MpPartyClient.setSelfPause(true);
        MpPartyClient.setSelfPause(false);
        receive(frame(MpProto.S_STOP, 4, 0L));
        assertFalse(MpPartyClient.selfPaused());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void duplicateResumeDoesNotSendAndALateResyncCannotClearANewerPause(boolean emptyRoom) {
        if (!emptyRoom) receive(track(4, 0L, true));
        MpPartyClient.toggleSelfPause();
        MpPartyClient.toggleSelfPause();
        MpPartyClient.toggleSelfPause();
        assertEquals(List.of(true, false), sent.stream().map(m -> m.get("paused").getAsBoolean()).toList());
        assertTrue(MpPartyClient.selfPaused());

        JsonObject resync = emptyRoom ? frame(MpProto.S_STOP, 4, 0L) : track(4, 15_000L, true);
        resync.addProperty("resync", true);
        receive(resync);
        assertFalse(MpPartyClient.selfPaused());

        MpPartyClient.toggleSelfPause();
        receive(resync);
        assertTrue(MpPartyClient.selfPaused());
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
