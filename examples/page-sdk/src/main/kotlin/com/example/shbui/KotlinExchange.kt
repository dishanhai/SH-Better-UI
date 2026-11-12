// Author: dishanhai
package com.example.shbui

import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageCodec
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageValidator
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema
import com.dishanhai.sh_better_ui.kernel.api.page.node
import com.dishanhai.sh_better_ui.kernel.api.page.pageRoot
import com.dishanhai.sh_better_ui.kernel.api.page.uiPage
import java.nio.file.Files
import java.nio.file.Path

public fun exchangePage(): UiPageDefinition = uiPage(ExchangePage.ID) {
    title(ExchangePage.text("title"))
    field(ExchangePage.BALANCE, UiValueSchema.integer(0, 100_000))
    field(ExchangePage.BALANCE_LABEL, UiValueSchema.text(128))
    field(ExchangePage.STATUS, UiValueSchema.text(128))
    action(UiActionDefinition(ExchangePage.EXCHANGE, UiActionPolicy.SERVER, UiDataSchema.EMPTY))
    pageRoot("root", "column") {
        node("target", "text") { text(ExchangePage.text("target")) }
        node("balance", "text") { bind("text", ExchangePage.BALANCE_LABEL) }
        node("exchange", "button") {
            label(ExchangePage.text("exchange"))
            action(ExchangePage.EXCHANGE)
        }
        node("status", "text") { bind("text", ExchangePage.STATUS) }
    }
}

public fun main(arguments: Array<String>) {
    val page = exchangePage()
    UiPageValidator().requireValid(page, true)
    page.dataSchema().validate(ExchangePage.initialData(100), true)
    val kotlinBytes = UiPageCodec.encode(page)
    val javaBytes = UiPageCodec.encode(ExchangePage.define())
    check(kotlinBytes.contentEquals(javaBytes))
    if (arguments.isNotEmpty()) Files.write(Path.of(arguments[0]), kotlinBytes)
    println("${page.id()} ${page.schemaVersion()} ${kotlinBytes.size}")
}
