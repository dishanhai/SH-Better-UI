// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Request;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Result;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientTextReadabilityTest {
    private static final Rect BOUNDS = new Rect(1, 2, 20, 9);
    private static final Rect VIEWPORT = new Rect(0, 0, 100, 100);
    private static final TextId TITLE = new TextId(id("title"));

    @Test
    void registersPolicyAndResolvesIndependentSwitchesAndPopupLayers() {
        try (var fixture = fixture()) {
            var engine = fixture.module.engine();
            assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.TEXT_READABILITY_POLICY));
            var service = engine.require(UIApis.TEXT_READABILITY);
            var module = fixture.module;
            var page = fixture.tree();
            var label = child(page, "label");
            var popup = child(page, "popup");
            var popupLabel = child(popup, "label");
            var popupTheme = fixture.themes.global().child("popup");
            popupTheme.configure(builder -> builder.override(ThemeTokens.TEXT_FOREGROUND_PROTECTION, true));

            assertFalse(service.policy(module, page).adaptiveColor());
            assertFalse(service.policy(module, page).foregroundProtection());
            service.configure(module, page, new TextReadabilityPolicy(true, null,
                    new TextReadabilityPolicy.FixedContrast(6), null, null));
            assertTrue(service.policy(module, label).adaptiveColor());
            assertFalse(service.policy(module, label).foregroundProtection());

            service.configurePopup(module, popup, popupTheme, null, TextReadabilityPolicy.inherit());
            assertFalse(service.policy(module, popupLabel).adaptiveColor());
            assertTrue(service.policy(module, popupLabel).foregroundProtection());
            assertEquals(4.5, service.policy(module, popupLabel).targetContrast(TextReadabilityPolicy.Usage.BODY, 0));

            service.configurePopup(module, popup, popupTheme, page, TextReadabilityPolicy.inherit());
            assertTrue(service.policy(module, popupLabel).adaptiveColor());
            assertTrue(service.policy(module, popupLabel).foregroundProtection());
            assertEquals(6, service.policy(module, popupLabel).targetContrast(TextReadabilityPolicy.Usage.BODY, 0));

            service.configure(module, popupLabel, new TextReadabilityPolicy(false, null,
                    TextReadabilityPolicy.ClassifiedContrast.INSTANCE, null, null));
            assertFalse(service.policy(module, popupLabel).adaptiveColor());
            assertTrue(service.policy(module, popupLabel).foregroundProtection());
            service.submit(module, request(popupLabel, TITLE));
            var decision = service.diagnostics(module, popupLabel, TITLE).orElseThrow();
            assertEquals(popup.generation(), decision.renderLayerGeneration());
            assertEquals(popupLabel.generation(), decision.scopeGeneration());
            assertEquals(4.5, decision.targetContrast());
            assertThrows(IllegalArgumentException.class, () -> service.configurePopup(module, popup, popupTheme,
                    fixture.module.scope().openChild(ScopeKind.SCREEN, id("other_screen"))
                            .openChild(ScopeKind.UI_TREE, id("other_tree")), TextReadabilityPolicy.inherit()));
        }
    }

    @Test
    void keepsStableScopeItemAndFragmentIdentitiesAndRejectsClosedScopes() {
        try (var fixture = fixture()) {
            var service = fixture.module.engine().require(UIApis.TEXT_READABILITY);
            var module = fixture.module;
            var page = fixture.tree();
            var row = child(page, "row");
            var other = child(page, "other");
            var one = new TextId(TITLE.text(), id("item_1"), id("fragment_1"));
            var next = new TextId(TITLE.text(), id("item_2"), id("fragment_1"));
            var fragment = new TextId(TITLE.text(), id("item_1"), id("fragment_2"));

            assertEquals(Result.RENDER_UNAVAILABLE, service.submit(module, request(row, one)));
            assertTrue(service.diagnostics(module, row, next).isEmpty());
            assertTrue(service.diagnostics(module, row, fragment).isEmpty());
            assertTrue(service.diagnostics(module, other, one).isEmpty());
            assertEquals(Result.RENDER_UNAVAILABLE, service.submit(module, request(row, null)));
            assertTrue(service.diagnostics(module, row, null).isEmpty());

            service.submit(module, request(other, one));
            service.submit(module, request(row, fragment));
            service.submit(module, request(row, next));
            service.retireDataItem(module, row, one.dataItem());
            assertTrue(service.diagnostics(module, row, one).isEmpty());
            assertTrue(service.diagnostics(module, row, fragment).isEmpty());
            assertTrue(service.diagnostics(module, other, one).isPresent());
            assertTrue(service.diagnostics(module, row, next).isPresent());

            row.close();
            var replacement = child(page, "row");
            assertEquals(Result.SCOPE_CLOSED, service.submit(module, request(row, next)));
            assertTrue(service.diagnostics(module, row, next).isEmpty());
            assertNotEquals(row.generation(), replacement.generation());
            assertTrue(service.diagnostics(module, replacement, next).isEmpty());
        }
    }

    @Test
    void rejectsExpiredOrOrdinaryContextsAndReportsClipAndOpacity() {
        try (var fixture = fixture()) {
            var service = fixture.module.engine().require(UIApis.TEXT_READABILITY);
            var scope = fixture.tree();
            var prepared = request(scope, TITLE);
            var expired = new RenderService.Context() {
                @Override public RenderService.Frame frame() { return fail("Readability inspected an expired context"); }
                @Override public void fill(int x, int y, int right, int bottom, int argb) { fail("Drew text"); }
                @Override public void clip(int x, int y, int right, int bottom, Runnable draw) { fail("Changed clip"); }
                @Override public void transform(double x, double y, float scale, Runnable draw) { fail("Changed pose"); }
            };
            assertEquals(Result.INVALID_REQUEST, service.submit(fixture.module, null));
            assertEquals(Result.INVALID_STAGE, service.drawNow(fixture.module, expired, prepared));
            assertEquals(Result.INVALID_STAGE, service.diagnostics(fixture.module, scope, TITLE).orElseThrow().result());
            assertEquals(Result.CLIPPED, service.submit(fixture.module, new Request(scope, TITLE, prepared.run(),
                    prepared.style(), Transform.translation(100, 0), VIEWPORT, 1)));
            assertEquals(Result.HIDDEN, service.submit(fixture.module, new Request(scope, TITLE, prepared.run(),
                    prepared.style(), Transform.IDENTITY, VIEWPORT, 0)));
        }
    }

    @Test
    void expiresIdleHistoryAndInvalidatesItWhenTheTargetChanges() {
        try (var fixture = fixture()) {
            var service = (ClientTextReadability) fixture.module.engine().require(UIApis.TEXT_READABILITY);
            var node = fixture.tree();
            var module = fixture.module;
            service.configure(module, node, new TextReadabilityPolicy(true, null, null,
                    TextReadabilityPolicy.Classification.FRAMEBUFFER_SIZE, 20d));
            var target = new Object();
            service.targetChanged(target, 1, 800, 480, 2);
            var prepared = request(node, TITLE);
            var scaled = new Request(node, TITLE, prepared.run(), prepared.style(), Transform.scale(1.2, 1.2), VIEWPORT, 1);
            fixture.client.runFrame();
            service.submit(module, scaled);
            var diagnostic = service.diagnostics(module, node, TITLE).orElseThrow();
            assertEquals(1, diagnostic.sourceFrame());
            assertEquals(3, diagnostic.targetContrast());
            service.targetChanged(target, 1, 800, 480, 2);
            assertTrue(service.diagnostics(module, node, TITLE).isPresent());
            service.targetChanged(target, 2, 800, 480, 2);
            assertTrue(service.diagnostics(module, node, TITLE).isEmpty());
            assertTrue(service.policy(module, node).adaptiveColor());

            service.submit(module, prepared);
            fixture.time.addAndGet(ClientTextReadability.IDLE_NANOS - 1);
            assertTrue(service.diagnostics(module, node, TITLE).isPresent());
            fixture.time.incrementAndGet();
            assertTrue(service.diagnostics(module, node, TITLE).isEmpty());

            service.submit(module, prepared);
            service.invalidateTarget();
            assertTrue(service.diagnostics(module, node, TITLE).isEmpty());
        }
    }

    @Test
    void boundsHistoryCapacityAndKeepsTheMostRecentlySubmittedState() {
        try (var fixture = fixture()) {
            var service = fixture.module.engine().require(UIApis.TEXT_READABILITY);
            var node = fixture.tree();
            var module = fixture.module;
            service.submit(module, request(node, TITLE));
            for (int i = 0; i < ClientTextReadability.MAX_STATES; i++) {
                service.submit(module, request(node, new TextId(id("entry_" + i))));
            }
            assertTrue(service.diagnostics(module, node, TITLE).isEmpty());
            assertTrue(service.diagnostics(module, node,
                    new TextId(id("entry_" + (ClientTextReadability.MAX_STATES - 1)))).isPresent());
        }
    }

    private static ClientServiceFixture fixture() {
        return new ClientServiceFixture(Set.of(), Set.of());
    }

    private static Request request(ScopeHandle scope, TextId id) {
        return new Request(scope, id, new TextReadabilityService.Run(FormattedCharSequence.EMPTY, 1, 2, BOUNDS, 9),
                new TextReadabilityService.Style(0xffffffff, false, TextReadabilityPolicy.Usage.BODY),
                Transform.IDENTITY, VIEWPORT, 1);
    }

    private static ScopeHandle child(ScopeHandle parent, String name) {
        return parent.openChild(ScopeKind.COMPONENT, id(name));
    }

    private static UiId id(String path) {
        return new UiId("example", path);
    }
}
