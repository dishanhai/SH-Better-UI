// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class DynamicCapabilitiesTest {
    private static final UiId PROVIDER = UiId.parse("example:a_provider");
    private static final UiId CONSUMER = UiId.parse("example:c_consumer");
    private static final CapabilityKey CAPABILITY = new CapabilityKey(UiId.parse("example:capability"));
    private static final UiVersion VERSION = new UiVersion(1, 0, 0);
    private static final CapabilityRequest REQUEST = new CapabilityRequest(Map.of(CAPABILITY, VersionRange.ANY), Map.of());
    private static final ModuleDescriptor.CapabilityOffer OFFER = new ModuleDescriptor.CapabilityOffer(VERSION, -1, false);

    @ParameterizedTest
    @EnumSource(ModuleDescriptor.Platform.class)
    void observingStaticCapabilitiesBeforeTheFirstFrameDoesNotReplayStartup(ModuleDescriptor.Platform platform) {
        try (var fixture = new Fixture(platform)) {
            var resolver = fixture.context.engine().capabilities();
            var baseline = resolver.resolve(REQUEST);
            var changes = new ArrayList<CapabilityChange>();
            resolver.observe(fixture.context.scope(), REQUEST, changes::add);

            fixture.host.runFrame();

            assertTrue(baseline.requiredMissing().isEmpty());
            assertEquals(baseline, resolver.resolve(REQUEST));
            assertTrue(changes.isEmpty(), () -> "Unexpected startup notification: " + changes);
        }
    }

    @ParameterizedTest
    @EnumSource(ModuleDescriptor.Platform.class)
    void observersStartFromTheResolutionVisibleWhenTheyRegister(ModuleDescriptor.Platform platform) {
        try (var fixture = new Fixture(platform)) {
            fixture.host.runFrame();
            var resolver = fixture.context.engine().capabilities();
            var existing = new ArrayList<CapabilityChange>();
            resolver.observe(fixture.context.scope(), REQUEST, existing::add);
            fixture.host.stopModule(PROVIDER);
            var baseline = resolver.resolve(REQUEST);
            var newlyRegistered = new ArrayList<CapabilityChange>();
            resolver.observe(fixture.context.scope(), REQUEST, newlyRegistered::add);

            fixture.host.runFrame();

            assertEquals(Set.of(CAPABILITY), baseline.requiredMissing());
            assertEquals(1, existing.size());
            assertEquals(baseline, existing.get(0).current());
            assertTrue(newlyRegistered.isEmpty(), () -> "Replayed an earlier withdrawal: " + newlyRegistered);
        }
    }

    @ParameterizedTest
    @EnumSource(ModuleDescriptor.Platform.class)
    void pendingOffersStillNotifyObserversAfterRegistration(ModuleDescriptor.Platform platform) {
        try (var fixture = new Fixture(platform)) {
            var resolver = fixture.context.engine().capabilities();
            var baseline = resolver.resolve(REQUEST);
            var scope = fixture.context.scope();
            resolver.provide(scope, CAPABILITY, new ModuleDescriptor.CapabilityOffer(VERSION, 1, false));
            var changes = new ArrayList<CapabilityChange>();
            resolver.observe(scope, REQUEST, changes::add);
            assertEquals(baseline, resolver.resolve(REQUEST));

            fixture.host.runFrame();

            assertEquals(1, changes.size());
            assertEquals(baseline, changes.get(0).previous());
            assertEquals(fixture.context.scope().owner(), changes.get(0).current().selected().get(CAPABILITY).provider());
            assertEquals(changes.get(0).current(), resolver.resolve(REQUEST));
        }
    }

    @ParameterizedTest
    @EnumSource(ModuleDescriptor.Platform.class)
    void callbackReplacementKeepsRequiredConsumersRunningAcrossPublication(ModuleDescriptor.Platform platform) {
        try (var fixture = new Fixture(platform)) {
            var resolver = fixture.context.engine().capabilities();
            var scope = fixture.context.scope();
            var offer = resolver.provide(scope, CAPABILITY, OFFER);
            fixture.host.runFrame();
            var changes = new ArrayList<CapabilityChange>();
            var replacement = new AtomicReference<Subscription>();
            resolver.observe(scope, REQUEST, change -> {
                changes.add(change);
                offer.close();
                replacement.set(resolver.provide(scope, CAPABILITY, OFFER));
            });
            fixture.host.stopModule(PROVIDER);

            fixture.host.runFrame();

            assertEquals(1, changes.size());
            assertFalse(offer.isActive());
            assertNotNull(replacement.get());
            assertTrue(replacement.get().isActive());
            fixture.assertConsumerRunning();
            assertTrue(resolver.resolve(REQUEST).requiredMissing().isEmpty());

            fixture.host.runFrame();

            fixture.assertConsumerRunning();
            assertEquals(1, changes.size(), "An equivalent replacement does not change the resolution");
        }
    }

    @ParameterizedTest
    @EnumSource(ModuleDescriptor.Platform.class)
    void callbackWithdrawalStopsRequiredConsumersOnlyAtTheNextPublication(ModuleDescriptor.Platform platform) {
        try (var fixture = new Fixture(platform)) {
            var resolver = fixture.context.engine().capabilities();
            var scope = fixture.context.scope();
            var offer = resolver.provide(scope, CAPABILITY, OFFER);
            fixture.host.runFrame();
            var changes = new ArrayList<CapabilityChange>();
            resolver.observe(scope, REQUEST, change -> {
                changes.add(change);
                offer.close();
            });
            fixture.host.stopModule(PROVIDER);

            fixture.host.runFrame();

            fixture.assertConsumerRunning();
            assertEquals(1, changes.size());
            assertTrue(resolver.resolve(REQUEST).requiredMissing().isEmpty());

            fixture.host.runFrame();

            assertEquals(ModuleLifecycle.FAILED, fixture.host.lifecycle().snapshot().modules().get(CONSUMER));
            assertEquals(2, changes.size());
            assertEquals(Set.of(CAPABILITY), changes.get(1).current().requiredMissing());
            assertTrue(changes.get(1).generation() > changes.get(0).generation());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final KernelHost host;
        private final ModuleContext context;

        private Fixture(ModuleDescriptor.Platform platform) {
            host = new KernelHost(platform, new EnginePublication());
            var captured = new AtomicReference<ModuleContext>();
            host.start(List.of(
                    candidate(module(PROVIDER.path()).providedCapability(CAPABILITY, VERSION).build()),
                    candidate(module("b_replacement").build(), Set.of(), false, ctx -> {}, captured::set, ctx -> {}),
                    candidate(module(CONSUMER.path()).requiredCapability(CAPABILITY, VersionRange.ANY).build())));
            context = captured.get();
        }

        private void assertConsumerRunning() {
            assertEquals(ModuleLifecycle.RUNNING, host.lifecycle().snapshot().modules().get(CONSUMER));
        }

        @Override public void close() { host.close(); }
    }
}
