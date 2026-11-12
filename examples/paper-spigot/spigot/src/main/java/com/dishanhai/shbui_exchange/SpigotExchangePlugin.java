// Author: dishanhai
package com.dishanhai.shbui_exchange;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SpigotExchangePlugin extends JavaPlugin {
    private ExchangeExample exchange;

    @Override
    public void onEnable() {
        exchange = new ExchangeExample(this);
        Objects.requireNonNull(getCommand("shbui-exchange")).setExecutor(exchange);
    }

    @Override
    public void onDisable() {
        if (exchange != null) exchange.close();
    }
}
