// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import java.io.File;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** A native directory dialog for Minecraft's client thread. An empty result means cancellation. */
public final class DirectoryPicker {
    private DirectoryPicker() {}

    public static Optional<File> choose(Component title, File initialDirectory) {
        String selection = TinyFileDialogs.tinyfd_selectFolderDialog(title.getString(), initialDirectory.getAbsolutePath());
        return Optional.ofNullable(selection).filter(path -> !path.isBlank()).map(File::new).filter(File::isDirectory);
    }
}
