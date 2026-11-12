// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record UiPropertyDescriptor(UiValueSchema schema, Optional<UiValue> defaultValue,
                                   boolean required, boolean bindable, boolean remoteConfigurable) {
    public UiPropertyDescriptor {
        Objects.requireNonNull(schema, "schema");
        UiValueSchema.checkSchemaBudget(List.of(schema));
        Objects.requireNonNull(defaultValue, "defaultValue").ifPresent(schema::validate);
    }
}
