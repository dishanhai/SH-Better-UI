// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.rule;

/** 可信服务端时钟，不接收客户端时间。 */
@FunctionalInterface
public interface ServerClock {
    long nowMillis();
}
