// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record UiProtocolCapabilities(Set<UiVersion> protocolVersions, Set<UiVersion> schemaVersions,
                                     Map<UiId, UiVersion> capabilities, Set<UiPageType> pageTypes,
                                     int maxMessageBytes) {
    private static final int MAX_VERSIONS = 16;
    public static final int MAX_CAPABILITIES = 128;

    public UiProtocolCapabilities {
        protocolVersions = Set.copyOf(protocolVersions);
        schemaVersions = Set.copyOf(schemaVersions);
        capabilities = Map.copyOf(capabilities);
        pageTypes = Set.copyOf(pageTypes);
        if (protocolVersions.isEmpty() || protocolVersions.size() > MAX_VERSIONS
                || schemaVersions.isEmpty() || schemaVersions.size() > MAX_VERSIONS
                || capabilities.size() > MAX_CAPABILITIES
                || maxMessageBytes < 1024 || maxMessageBytes > UiProtocol.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid page protocol capabilities");
        }
    }

    public static UiProtocolCapabilities standard(Map<UiId, UiVersion> capabilities, Set<UiPageType> pageTypes) {
        return new UiProtocolCapabilities(Set.of(UiProtocol.VERSION), Set.of(UiPageDefinition.SCHEMA_VERSION),
                capabilities, pageTypes, UiProtocol.MAX_MESSAGE_BYTES);
    }

    /** Selects the exact common versions understood by this implementation. */
    public Optional<Negotiated> negotiate(UiProtocolCapabilities peer) {
        Objects.requireNonNull(peer, "peer");
        if (!protocolVersions.contains(UiProtocol.VERSION) || !peer.protocolVersions.contains(UiProtocol.VERSION)) {
            return Optional.empty();
        }
        UiVersion schema = UiPageDefinition.SCHEMA_VERSION;
        if (!schemaVersions.contains(schema) || !peer.schemaVersions.contains(schema)) {
            return Optional.empty();
        }
        return Optional.of(new Negotiated(UiProtocol.VERSION, schema, Math.min(maxMessageBytes, peer.maxMessageBytes)));
    }

    public boolean supports(UiId id, UiVersion minimum) {
        UiVersion version = capabilities.get(id);
        return version != null && version.major() == minimum.major() && version.compareTo(minimum) >= 0;
    }

    public record Negotiated(UiVersion protocolVersion, UiVersion schemaVersion, int maxMessageBytes) {}
}
