// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

/** An immutable inline node. IDs are supplied by the document owner and unique in a document. */
public sealed interface InlineNode permits TextRun, ImageNode, ParagraphBreak, LocalizedText, RichObjectNode,
        RichExtensionNode {
    UiId id();
}
