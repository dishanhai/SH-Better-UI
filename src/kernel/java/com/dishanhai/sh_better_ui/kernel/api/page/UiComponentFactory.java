// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import java.util.Map;

/**
 * Projects validated component inputs onto an available built-in widget.
 * The page runtime keeps the original stable ID, children and action.
 */
@FunctionalInterface
public interface UiComponentFactory {
    /** Must be deterministic and free of side effects. A definition can be checked before it is opened. */
    UiComponentAlternative project(Inputs input);

    record Inputs(Map<String, UiValue> properties, Map<String, String> bindings) {
        public Inputs {
            properties = Map.copyOf(properties);
            bindings = Map.copyOf(bindings);
        }
    }
}
