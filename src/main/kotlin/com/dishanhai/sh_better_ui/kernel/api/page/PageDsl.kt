// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page

import com.dishanhai.sh_better_ui.kernel.api.UiId
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy

/** Scopes the implicit receiver to the current page or node builder. */
@DslMarker
@Target(AnnotationTarget.TYPE)
public annotation class UiPageDsl

/** Builds the same immutable definition as the Java SDK. */
public fun uiPage(
    id: UiId,
    configure: @UiPageDsl UiPageDefinition.Builder.() -> Unit,
): UiPageDefinition = UiPageDefinition.builder(id).apply(configure).build()

/** Builds the same client-local registration options as the Java SDK. */
public fun pageTextReadability(
    policy: TextReadabilityPolicy,
    sampling: UiPageTextReadability.Sampling? = null,
    nodes: Map<String, UiPageTextReadability.Options> = emptyMap(),
): UiPageTextReadability = UiPageTextReadability(UiPageTextReadability.Options(policy, sampling), nodes)

public fun UiPageDefinition.Builder.pageRoot(
    id: String,
    type: UiId,
    configure: @UiPageDsl UiNode.Builder.() -> Unit,
): UiPageDefinition.Builder = root(UiNode.builder(id, type).apply(configure).build())

public fun UiPageDefinition.Builder.pageRoot(
    id: String,
    type: String,
    configure: @UiPageDsl UiNode.Builder.() -> Unit,
): UiPageDefinition.Builder = pageRoot(id, UiBuiltinComponents.id(type), configure)

public fun UiNode.Builder.node(
    id: String,
    type: UiId,
    configure: @UiPageDsl UiNode.Builder.() -> Unit,
): UiNode.Builder = child(UiNode.builder(id, type).apply(configure).build())

public fun UiNode.Builder.node(
    id: String,
    type: String,
    configure: @UiPageDsl UiNode.Builder.() -> Unit,
): UiNode.Builder = node(id, UiBuiltinComponents.id(type), configure)
