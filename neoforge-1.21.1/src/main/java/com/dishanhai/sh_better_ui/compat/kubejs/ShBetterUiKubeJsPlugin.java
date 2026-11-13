package com.dishanhai.sh_better_ui.compat.kubejs;

import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import com.dishanhai.sh_better_ui.compat.kubejs.client.KubeJsUiApi;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;

/** 山海：KubeJS 7 客户端绑定，同一脚本世代的多个 Rhino 上下文共享一份门面。 */
public final class ShBetterUiKubeJsPlugin implements KubeJSPlugin {
    private KubeJsUiApi clientApi;

    @Override
    public synchronized void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.CLIENT) return;
        if (clientApi != null) clientApi.close();
        clientApi = new KubeJsUiApi();
    }

    @Override
    public synchronized void registerBindings(BindingRegistry bindings) {
        if (bindings.type() != ScriptType.CLIENT) return;
        if (clientApi == null) clientApi = new KubeJsUiApi();
        bindings.add("SHUI", clientApi);
        if (VanillaTextEffects.handlesLegacy()) {
            bindings.add("ShanhaiText", clientApi.text());
            bindings.add("ShanhaiStyleRegistry", clientApi.text());
        }
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        if (filter.scriptType != ScriptType.CLIENT) return;
        filter.allow("com.dishanhai.sh_better_ui.client.");
        filter.allow("com.dishanhai.sh_better_ui.config.");
        filter.allow("com.dishanhai.sh_better_ui.compat.kubejs.client.");
    }
}
