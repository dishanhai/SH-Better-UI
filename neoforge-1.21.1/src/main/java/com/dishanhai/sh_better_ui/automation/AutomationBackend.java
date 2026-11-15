// Author: dishanhai
package com.dishanhai.sh_better_ui.automation;

import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Optional adapters stay behind this interface so missing mods never load their classes. */
public interface AutomationBackend extends RuleAdapter, AutoCloseable {
    default void setSubmissionGuard(java.util.function.BiPredicate<java.util.UUID,
            com.dishanhai.sh_better_ui.kernel.api.automation.rule.AutomationAction> guard) {}
    String bind(ServerPlayer player);
    boolean cancelPending(java.util.UUID intentId);
    void tick();
    CompoundTag save();
    void restore(CompoundTag data);
    @Override void close();
}
