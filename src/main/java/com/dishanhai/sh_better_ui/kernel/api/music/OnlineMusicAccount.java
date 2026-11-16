// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.Objects;

/** Public account identity only; credentials are never returned. */
public record OnlineMusicAccount(String source, String id, String name) {
    public OnlineMusicAccount {
        Objects.requireNonNull(source);
        Objects.requireNonNull(id);
        name = name == null ? "" : name;
    }
}
