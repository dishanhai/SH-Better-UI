// Author: dishanhai
package com.dishanhai.sh_better_ui.compat.kubejs.client;

import java.util.Map;

/** An event from a page opened by the current client script generation. */
public record KubeJsPageEvent(String type, String pageId, String instanceId, String actionId,
                              String state, long revision, Map<String, Object> data) {
    public KubeJsPageEvent {
        data = Map.copyOf(data);
    }
}
