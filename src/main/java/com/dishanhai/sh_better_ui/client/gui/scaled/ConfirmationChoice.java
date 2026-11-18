// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Single-use confirmation ownership. All callbacks run on the client's UI thread. */
final class ConfirmationChoice<S> {
    private final S parent;
    private final Supplier<S> current;
    private final Consumer<S> show;
    private final Runnable restore, confirm, abandoned;
    private S modal;
    private boolean finished;
    ConfirmationChoice(S parent, Supplier<S> current, Consumer<S> show, Runnable restore, Runnable confirm) {
        this(parent,current,show,restore,confirm,()->{});
    }
    ConfirmationChoice(S parent, Supplier<S> current, Consumer<S> show, Runnable restore, Runnable confirm, Runnable abandoned) {
        this.parent=parent; this.current=current; this.show=show; this.restore=restore; this.confirm=confirm;this.abandoned=abandoned;
    }
    void attach(S modal) { this.modal=modal; }
    void abandon() { if(!finished){finished=true;abandoned.run();} }
    void finish(boolean accepted) {
        if (finished || modal == null || current.get() != modal) return;
        finished=true;
        show.accept(parent);
        if (current.get() != parent) return;
        restore.run();
        // Focus lookup is consumer code and may navigate elsewhere. Never confirm stale context.
        if (accepted && current.get() == parent) confirm.run();
    }
}
