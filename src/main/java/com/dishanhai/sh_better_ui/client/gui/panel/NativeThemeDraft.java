// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import java.util.Objects;
import java.util.function.Predicate;

/** Isolated theme transaction. Only a successful host save advances the baseline. */
public final class NativeThemeDraft {
    private ClientUiSettings saved, current;
    private final java.util.ArrayDeque<ClientUiSettings> history = new java.util.ArrayDeque<>();
    public NativeThemeDraft(ClientUiSettings initial) { saved = current = Objects.requireNonNull(initial); }
    public ClientUiSettings current() { return current; }
    public boolean dirty() { return !current.equals(saved); }
    public void stage(ThemeSettings theme) {
        var next = current.withTheme(Objects.requireNonNull(theme));
        if (next.equals(current)) return;
        if (history.size() == 16) history.removeFirst();
        history.addLast(current);
        current = next;
    }
    public boolean canUndo() { return !history.isEmpty(); }
    public boolean undo() {
        if (history.isEmpty()) return false;
        current = history.removeLast();
        return true;
    }
    public void cancel() { current = saved; history.clear(); }
    public boolean apply(Predicate<ClientUiSettings> save) {
        if (!dirty()) { history.clear(); return true; }
        if (!save.test(current)) return false;
        saved = current;
        history.clear();
        return true;
    }
}
