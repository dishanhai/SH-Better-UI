// Author: dishanhai
package com.dishanhai.shbui_kernel_example

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle
import com.dishanhai.sh_better_ui.kernel.api.UIApis
import com.dishanhai.sh_better_ui.kernel.api.page.PageService
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageTextReadability
import com.dishanhai.sh_better_ui.kernel.api.page.pageTextReadability
import com.dishanhai.sh_better_ui.kernel.api.text.TextId
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence

/** Call from a native Screen after mounting the module-owned tree. */
object TextReadabilityKotlinExample {
    fun configure(module: ModuleContext, tree: ScopeHandle) {
        module.engine().require(UIApis.TEXT_READABILITY)
            .configure(module, tree, TextReadabilityPolicy(true, true, null, null, null))
    }

    /** Enable readability for a declarative Screen or modal when registering it. */
    fun registerPage(module: ModuleContext, page: UiPageDefinition): RegistrationHandle {
        val readability = pageTextReadability(
            TextReadabilityPolicy(true, true, null, null, null),
            nodes = mapOf(page.root().id() to UiPageTextReadability.Options(
                TextReadabilityPolicy.inherit(), UiPageTextReadability.Sampling.IMPORTANT,
            )),
        )
        return module.engine().require(PageService.KEY).register(module, page, readability)
    }

    /** Adapt an already shaped visual-order line supplied by the caller's layout. */
    fun request(
        tree: ScopeHandle,
        id: TextId,
        font: Font,
        line: FormattedCharSequence,
        x: Double,
        y: Double,
        clip: Rect,
        declaredBackground: Int?,
    ): TextReadabilityService.Request {
        val height = font.lineHeight.toDouble()
        val run = TextReadabilityService.Run(line, 0.0, 0.0, Rect(0.0, 0.0, font.width(line).toDouble(), height), height)
        val style = TextReadabilityService.Style(0xffffffff.toInt(), false, TextReadabilityPolicy.Usage.BODY)
        return TextReadabilityService.Request(tree, id, run, style, Transform.translation(x, y), clip, 1f, declaredBackground)
    }

    fun submit(module: ModuleContext, request: TextReadabilityService.Request): TextReadabilityService.Result =
        module.engine().require(UIApis.TEXT_READABILITY).submit(module, request)

    fun foreground(module: ModuleContext, request: TextReadabilityService.Request): TextReadabilityService.Result {
        val service = module.engine().require(UIApis.TEXT_READABILITY)
        return service.foreground(module, request.scope(), 0) { context ->
            service.drawNow(module, context, request)
        }
    }
}
