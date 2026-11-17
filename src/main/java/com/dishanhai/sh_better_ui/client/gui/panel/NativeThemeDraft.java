// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import java.util.Objects;
import java.util.function.Predicate;

/** Isolated theme transaction. Only a successful host save advances the baseline. */
public final class NativeThemeDraft {
    private ClientUiSettings saved, current;
    public NativeThemeDraft(ClientUiSettings initial) { saved = current = Objects.requireNonNull(initial); }
    public ClientUiSettings current() { return current; }
    public boolean dirty() { return !current.equals(saved); }
    public void stage(ThemeSettings theme) { current = current.withTheme(theme); }
    public void cancel() { current = saved; }
    public boolean apply(Predicate<ClientUiSettings> save) {
        if (!dirty()) return true;
        if (!save.test(current)) return false;
        saved = current;
        return true;
    }
}
