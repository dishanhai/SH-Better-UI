// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.DeveloperPreviewProvider;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;

import java.util.List;

/** Connects loader-discovered client modules to the developer gallery. */
final class ClientDeveloperPreviews {
    private ClientDeveloperPreviews() {}

    static void mount(KernelHost kernel, UIModule module, ModuleContext context) {
        if (!(module instanceof DeveloperPreviewProvider provider)) return;
        try {
            var previews = List.copyOf(provider.developerPreviews());
            if (previews.isEmpty()) return;
            String owner = context.descriptor().id().namespace();
            for (var preview : previews) {
                if (!preview.id().startsWith(owner + ":")) {
                    throw new SecurityException("Developer preview belongs to another mod: " + preview.id());
                }
            }
            var registration = ShBetterUiApi.developerPreviews().registerAll(previews);
            try {
                context.scope().track(registration);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                registration.close();
                throw failure;
            }
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            kernel.report("Developer previews of " + context.descriptor().id(), failure);
        }
    }
}
