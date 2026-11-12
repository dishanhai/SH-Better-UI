// Author: dishanhai
package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextMarkup;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;

/** Passive document construction for the current optional client script generation. */
public final class KubeJsRichTextApi {
    private final KubeJsUiApi owner;

    KubeJsRichTextApi(KubeJsUiApi owner) {
        this.owner = owner;
    }

    public RichDocument markup(String documentId, String markup) {
        owner.requireOpen();
        return RichTextMarkup.parse(UiId.parse(documentId), markup);
    }

    public RichDocument json(String json) {
        owner.requireOpen();
        return RichDocumentCodec.decode(json);
    }

    public String encode(RichDocument document) {
        owner.requireOpen();
        return RichDocumentCodec.encode(document);
    }

    /** Preview an immutable passive document. A reload invalidates still-queued preview requests. */
    public void open(RichDocument document) {
        encode(document);
        owner.run(() -> ClientUiKernel.openRichTextDocument(Minecraft.getInstance(), document));
    }
}
