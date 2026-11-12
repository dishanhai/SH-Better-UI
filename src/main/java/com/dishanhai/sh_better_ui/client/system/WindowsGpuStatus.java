// Author: dishanhai
package com.dishanhai.sh_better_ui.client.system;

import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import oshi.util.platform.windows.WmiQueryHandler;
import oshi.util.platform.windows.WmiUtil;

import java.util.HashMap;
import java.util.OptionalDouble;

final class WindowsGpuStatus {
    private static final WmiQuery<Counter> QUERY =
            new WmiQuery<>("Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine", Counter.class);
    private static final WmiQueryHandler WMI = WmiQueryHandler.createInstance();

    static {
        WMI.setWmiTimeout(1000);
    }

    private enum Counter { NAME, UTILIZATIONPERCENTAGE }

    private WindowsGpuStatus() {}

    static OptionalDouble load() {
        var rows = WMI.queryWMI(QUERY);
        var engines = new HashMap<String, Long>();
        for (int index = 0; index < rows.getResultCount(); index++) {
            String instance = WmiUtil.getString(rows, Counter.NAME, index);
            int engineStart = instance.indexOf("_luid_");
            if (engineStart < 0) continue;
            // Different process instances on the same adapter and engine share its capacity.
            long load = WmiUtil.getUint64(rows, Counter.UTILIZATIONPERCENTAGE, index);
            engines.merge(instance.substring(engineStart), load, Long::sum);
        }
        return engines.values().stream().mapToDouble(percent -> Math.min(100, percent) / 100.0).max();
    }
}
