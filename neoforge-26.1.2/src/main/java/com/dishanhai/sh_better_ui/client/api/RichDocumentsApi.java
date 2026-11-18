// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.panel.NativeRichDocumentScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.NativeRichDocumentEditorScreen;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native passive document viewing and local authoring, with caller-owned resource-pack assets. */
public final class RichDocumentsApi {
    RichDocumentsApi() {}
    /** Creates but does not open a viewer. Caller retains ownership of document assets. */
    public Screen screen(Screen parent, Component title, RichDocument document) {
        return new NativeRichDocumentScreen(parent, title, document);
    }
    /** Creates a local editor without opening it or writing files. Export is explicit. */
    public NativeRichDocumentEditorScreen editor(Screen parent, Component title, RichDocument document) {
        return new NativeRichDocumentEditorScreen(parent, title, document);
    }
}
