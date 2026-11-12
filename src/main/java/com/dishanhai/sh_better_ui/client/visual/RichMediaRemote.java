// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Restricted transport. The connection's own resolver filters the addresses actually used. */
final class RichMediaRemote {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 2;
    private static final int TOTAL_TIMEOUT_MS = 6000;
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "shb-rich-media-deadline");
        thread.setDaemon(true);
        return thread;
    });

    static {
        DEADLINES.setRemoveOnCancelPolicy(true);
        DEADLINES.setKeepAliveTime(10, TimeUnit.SECONDS);
        DEADLINES.allowCoreThreadTimeOut(true);
    }

    private final URI initial;
    private final RichTextMediaPolicy policy;
    private volatile HttpGet active;
    private volatile boolean cancelled;

    RichMediaRemote(URI uri, RichTextMediaPolicy policy) {
        policy.validate(uri);
        initial = uri;
        this.policy = policy;
    }

    byte[] download() throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TOTAL_TIMEOUT_MS);
        var limits = MessageConstraints.custom().setMaxHeaderCount(32).setMaxLineLength(4096).build();
        var timeouts = RequestConfig.custom().setConnectTimeout(2000).setSocketTimeout(1500)
                .setConnectionRequestTimeout(1000).setNormalizeUri(false).build();
        try (var client = HttpClients.custom().setDnsResolver(RichMediaRemote::publicAddresses)
                .setDefaultConnectionConfig(ConnectionConfig.custom().setMessageConstraints(limits).build())
                .setDefaultRequestConfig(timeouts).disableAutomaticRetries().disableRedirectHandling()
                .disableCookieManagement().disableAuthCaching().disableContentCompression()
                .setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE).setUserAgent("SH-Better-UI")
                .build()) {
            URI uri = initial;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                policy.validate(uri);
                RichMediaDecoder.checkInterrupted();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || cancelled) throw new IOException("Inline image request expired");
                var request = new HttpGet(uri);
                request.setHeader("Accept", "image/png, image/apng, image/gif");
                request.setHeader("Accept-Encoding", "identity");
                active = request;
                if (cancelled) request.abort();
                var timeout = DEADLINES.schedule(request::abort, remaining, TimeUnit.NANOSECONDS);
                try (var response = client.execute(request)) {
                    int status = response.getStatusLine().getStatusCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        var location = response.getFirstHeader("Location");
                        if (hop == MAX_REDIRECTS || location == null || location.getValue().length() > 2048) {
                            throw new IOException("Inline image redirect limit or invalid redirect");
                        }
                        uri = uri.resolve(location.getValue());
                        continue;
                    }
                    if (status != 200) throw new IOException("Inline image request failed");
                    var entity = response.getEntity();
                    if (entity == null || entity.getContentLength() > MAX_BYTES || entity.getContentType() == null
                            || entity.getContentEncoding() != null
                            && !"identity".equalsIgnoreCase(entity.getContentEncoding().getValue())) {
                        throw new IOException("Invalid inline image response");
                    }
                    String mime = entity.getContentType().getValue().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                    if (!mime.equals("image/png") && !mime.equals("image/apng") && !mime.equals("image/gif")) {
                        throw new IOException("Unsupported inline image response format");
                    }
                    // Closing the response aborts an unread body, avoiding an unbounded drain.
                    return RichMediaDecoder.read(entity.getContent(), MAX_BYTES);
                } finally {
                    timeout.cancel(false);
                    active = null;
                }
            }
            throw new IOException("Inline image redirect limit");
        }
    }

    void cancel() {
        cancelled = true;
        var request = active;
        if (request != null) request.abort();
    }

    private static InetAddress[] publicAddresses(String host) throws UnknownHostException {
        var addresses = InetAddress.getAllByName(host);
        if (addresses.length > 16) throw new UnknownHostException("Inline image origin returned too many addresses");
        for (var address : addresses) {
            if (!publicAddress(address)) throw new UnknownHostException("Inline image origin resolved outside public DNS");
        }
        return addresses;
    }

    static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = Byte.toUnsignedInt(bytes[0]), b = Byte.toUnsignedInt(bytes[1]);
        int c = Byte.toUnsignedInt(bytes[2]);
        if (bytes.length == 4) {
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && (b == 0 && (c == 0 || c == 2) || b == 168 || b == 88 && c == 99))
                    && !(a == 198 && (b == 18 || b == 19 || b == 51 && c == 100))
                    && !(a == 203 && b == 0 && c == 113);
        }
        // Global-unicast only. Exclude the special-use 2001::/23, documentation and 6to4 ranges.
        return (a & 0xe0) == 0x20
                && !(a == 0x20 && b == 1 && (c < 2 || c == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8))
                && !(a == 0x20 && b == 2) && !(a == 0x3f && b == 0xff && c < 0x10);
    }
}
