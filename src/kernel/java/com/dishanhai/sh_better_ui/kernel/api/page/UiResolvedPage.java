// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A fixed effective tree and the outcome of pre-open validation and degradation. */
public record UiResolvedPage(Optional<UiPageDefinition> page, List<UiPageProblem> problems,
                             List<String> degradations) {
    public UiResolvedPage {
        Objects.requireNonNull(page, "page");
        problems = List.copyOf(problems);
        degradations = List.copyOf(degradations);
        if (page.isPresent() == !problems.isEmpty()) {
            throw new IllegalArgumentException("An effective page must have no validation problems");
        }
    }
}
