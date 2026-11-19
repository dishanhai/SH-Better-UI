// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.io.IOException;

/** The provider explicitly rejected a mutation, rather than leaving its outcome unknown. */
final class MusicWriteRejected extends IOException {
    MusicWriteRejected(String message) { super(message); }
}
