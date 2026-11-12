// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.ActionPayload;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.ActionResult;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.Error;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.InteractionRejected;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.PageOpen;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.PageOpened;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.PagePayload;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.PageResumed;

import java.util.Optional;
import java.util.UUID;

/** Debug metadata for one encoded outbound or accepted inbound message. */
public record UiProtocolTrace(Direction direction, UiProtocolMessage.Type type, UiVersion version,
                              UUID sessionId, long sequence, Optional<UiId> pageId, Optional<UUID> instanceId,
                              Optional<UUID> requestId, Optional<UiErrorCode> error, int messageBytes,
                              long timestampNanos) {
    public enum Direction { INBOUND, OUTBOUND }

    static UiProtocolTrace capture(Direction direction, UiProtocolMessage message, int messageBytes) {
        var payload = message.payload();
        Optional<UiId> page = payload instanceof PageOpen open ? Optional.of(open.page().id()) : Optional.empty();
        Optional<UUID> instance = payload instanceof PagePayload pagePayload
                ? Optional.of(pagePayload.instanceId()) : Optional.empty();
        Optional<UUID> request = payload instanceof ActionPayload action
                ? Optional.of(action.requestId()) : Optional.empty();
        Optional<UiErrorCode> error = Optional.empty();
        if (payload instanceof Error reported) {
            error = Optional.of(reported.code());
        } else if (payload instanceof ActionResult result) {
            error = result.error();
        } else if (payload instanceof PageOpened opened) {
            error = opened.error();
        } else if (payload instanceof PageResumed resumed) {
            error = resumed.error();
        } else if (payload instanceof InteractionRejected rejected) {
            error = Optional.of(rejected.error());
        }
        return new UiProtocolTrace(direction, message.type(), message.version(), message.sessionId(),
                message.sequence(), page, instance, request, error, messageBytes, System.nanoTime());
    }
}
