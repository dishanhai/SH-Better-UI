// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.system.ClientComputerStatus;
import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;

import java.util.Set;

/** Physical client access to the computer status cache, including when the UI kernel is disabled. */
public final class ComputerStatusApi {
    private ComputerStatusApi() {}

    public static ComputerStatusService.Snapshot read(Set<ComputerStatusService.Metric> metrics) {
        return ClientComputerStatus.INSTANCE.read(metrics);
    }
}
