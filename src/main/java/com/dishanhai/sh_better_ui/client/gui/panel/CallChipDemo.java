// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState;
import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState.Status;

final class CallChipDemo {
    static final String EXAMPLE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState;
            import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState.Status;
            import net.minecraft.network.chat.Component;

            private void addCallChip(int x, int y, CallChipState state, Runnable retry) {
                addRenderableWidget(ShBetterUiApi.controls().callChip(new Bounds(x, y, 210, 30),
                        Component.translatable("sh_better_ui.call_chip.demo_name"),
                        Component.translatable("sh_better_ui.call_chip.demo_argument"), GraphicIcon.CODE,
                        state, ShBetterUiApi.settings().preferences(), true, retry));
            }

            // Publish each transition on the client thread.
            private final CallChipState call = new CallChipState(2_500);
            private void beginCall() { call.setStatus(Status.RUNNING); }
            private void finishCall(boolean success) { call.setStatus(success ? Status.DONE : Status.ERROR); }
            """;

    final CallChipState state = new CallChipState(1_200);
    private boolean retrying;

    void start() {
        if (state.status() == Status.RUNNING) return;
        retrying = false;
        state.setStatus(Status.RUNNING);
    }

    void retry() {
        if (state.status() != Status.ERROR) return;
        retrying = true;
        state.setStatus(Status.RUNNING);
    }

    void update() {
        if (state.status() == Status.RUNNING && state.sample(true).elapsedMs() >= (retrying ? 1_050 : 800)) {
            state.setStatus(retrying ? Status.DONE : Status.ERROR);
        }
    }
}
