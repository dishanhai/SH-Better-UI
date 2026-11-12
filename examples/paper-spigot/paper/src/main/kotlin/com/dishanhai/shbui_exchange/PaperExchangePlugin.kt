// Author: dishanhai
package com.dishanhai.shbui_exchange

import org.bukkit.plugin.java.JavaPlugin

class PaperExchangePlugin : JavaPlugin() {
    private var exchange: ExchangeExample? = null

    override fun onEnable() {
        val example = ExchangeExample(this)
        exchange = example
        requireNotNull(getCommand("shbui-exchange")).setExecutor(example)
    }

    override fun onDisable() {
        exchange?.close()
        exchange = null
    }
}
