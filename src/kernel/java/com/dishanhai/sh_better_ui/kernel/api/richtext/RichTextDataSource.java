// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

/** An owner-thread source of immutable data, sampled at a presentation boundary by the host. */
@FunctionalInterface
public interface RichTextDataSource {
    Snapshot sample();

    /** Revision identifies a producer-defined state. Text is bounded to 4 KiB in UTF-8. */
    record Snapshot(long revision, String value) {
        public Snapshot {
            if (revision < 0) throw new IllegalArgumentException("Negative rich text data revision");
            RichTextInput.utf8Bytes(value, 4096);
        }
    }
}
