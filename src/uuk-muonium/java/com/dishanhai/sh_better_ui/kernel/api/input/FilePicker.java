// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Optional;

/** Native single-file chooser. Invoke on the client thread. */
public final class FilePicker {
    private FilePicker() {}

    public static Optional<Path> choose(Component title, String pattern) {
        try (var stack = MemoryStack.stackPush()) {
            var filters = stack.pointers(stack.UTF8(pattern));
            String selection = TinyFileDialogs.tinyfd_openFileDialog(title.getString(), "", filters, null, false);
            return Optional.ofNullable(selection).map(Path::of);
        }
    }
}
