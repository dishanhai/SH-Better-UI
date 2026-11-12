// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class RichMediaRemoteTest {
    @Test void authorizationIsExactAndReappliesToResolvedRedirects() {
        var policy = RichTextMediaPolicy.allowHttps(Set.of("Pictures.Example"));
        var authorized = URI.create("https://pictures.example/icons/one.png");
        assertDoesNotThrow(() -> policy.validate(authorized.resolve("two.png")));
        assertDoesNotThrow(() -> policy.validate(URI.create("https://PICTURES.EXAMPLE:443/one.png?size=32")));
        for (String denied : List.of("http://pictures.example/one.png", "https://pictures.example:444/one.png",
                "https://pictures.example.evil.example/one.png", "https://sub.pictures.example/one.png",
                "https://pictures.example@127.0.0.1/one.png", "https://user@pictures.example/one.png",
                "https://pictures.example./one.png", "https://pictures.example/one.png#two",
                "file:///Pictures/one.png", "https://127.0.0.1/one.png", "https://[::1]/one.png",
                "//elsewhere.example/one.png")) {
            assertThrows(IllegalArgumentException.class, () -> policy.validate(authorized.resolve(denied)), denied);
        }
        for (String malformed : List.of("127.1", "*.example.com", "pictures.example:443", "localhost", "a..example")) {
            assertThrows(IllegalArgumentException.class, () -> RichTextMediaPolicy.allowHttps(Set.of(malformed)));
        }
    }

    @Test void rejectsPrivateLoopbackSharedAndMappedNetworkAddresses() throws UnknownHostException {
        for (String address : List.of("127.0.0.1", "0.0.0.0", "10.0.0.1", "100.64.0.1", "192.168.0.1",
                "172.16.0.1", "169.254.169.254", "192.0.2.1", "198.18.0.1", "224.0.0.1",
                "::1", "::ffff:127.0.0.1", "fc00::1", "fe80::1", "2001:db8::1", "2002::1")) {
            assertFalse(RichMediaRemote.publicAddress(InetAddress.getByName(address)), address);
        }
        assertTrue(RichMediaRemote.publicAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(RichMediaRemote.publicAddress(InetAddress.getByName("2606:4700::1111")));
    }
}
