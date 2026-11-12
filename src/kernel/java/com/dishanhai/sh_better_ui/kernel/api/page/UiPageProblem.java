// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

public record UiPageProblem(Code code, String location, String property, String expected,
                            String actual, String suggestion) {
    public enum Code {
        SCHEMA_VERSION, RESOURCE_LIMIT, DUPLICATE_ID, INVALID_ACTION, COMPONENT_MISSING,
        CAPABILITY_MISSING, REMOTE_DENIED, INVALID_PROPERTY, INVALID_BINDING, INVALID_CHILDREN
    }

    @Override
    public String toString() {
        return code + " at " + location + (property.isEmpty() ? "" : "." + property)
                + " (expected " + expected + ", got " + actual + "). " + suggestion;
    }
}
