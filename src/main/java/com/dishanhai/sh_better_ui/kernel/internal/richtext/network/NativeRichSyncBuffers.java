// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ClientMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ServerMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncWire;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;

final class NativeRichSyncBuffers {
    private NativeRichSyncBuffers() {}

    static ClientMessage readClient(FriendlyByteBuf buffer) {
        try {
            return RichSyncWire.decodeClient(read(buffer, RichSyncWire.MAX_CLIENT_BYTES));
        } catch (IOException invalid) {
            throw new DecoderException(invalid);
        }
    }

    static ServerMessage readServer(FriendlyByteBuf buffer) {
        try {
            return RichSyncWire.decodeServer(read(buffer, RichSyncWire.MAX_SERVER_BYTES));
        } catch (IOException invalid) {
            throw new DecoderException(invalid);
        }
    }

    private static byte[] read(FriendlyByteBuf buffer, int limit) {
        int length = buffer.readableBytes();
        if (length == 0 || length > limit) throw new DecoderException("Invalid rich text sync payload length");
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }
}
