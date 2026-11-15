// Author: dishanhai
package com.dishanhai.sh_better_ui.client.system;

import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.Set;

public final class ClientComputerStatus implements ComputerStatusService {
    public static final ClientComputerStatus INSTANCE = new ClientComputerStatus();

    private final AsyncComputerStatus status = new AsyncComputerStatus(
            () -> Minecraft.getInstance().gameDirectory.toPath(), task -> Util.ioPool().execute(task));

    private ClientComputerStatus() {}

    @Override
    public Snapshot read(Set<Metric> metrics) {
        return status.read(metrics);
    }
}
