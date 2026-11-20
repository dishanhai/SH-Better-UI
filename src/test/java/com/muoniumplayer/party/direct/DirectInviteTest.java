package com.muoniumplayer.party.direct;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DirectInviteTest {
    @Test
    void addressOnlyAndBothRoomCodeFormsKeepTheSameEndpoint() {
        assertArrayEquals(new String[]{"localhost", "1234", "qa"}, MpDirect.parseInvite("localhost:1234#qa"));
        String[] joined = {"localhost", "1234", "qa", "563918"};
        assertArrayEquals(joined, MpDirect.parseInvite("localhost:1234#qa#563918"));
        assertArrayEquals(joined, MpDirect.parseInvite("localhost:1234#qa##563918"));
        assertNull(MpDirect.parseInvite("localhost:1234##563918"));
    }
}
