// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.context.ContextChangeDetector;
import com.dishanhai.sh_better_ui.theme.context.ContextSnapshot;
import com.dishanhai.sh_better_ui.theme.context.ThemeRule;
import com.dishanhai.sh_better_ui.theme.context.ThemeRuleResolver;
import com.dishanhai.sh_better_ui.theme.event.Subscription;
import com.dishanhai.sh_better_ui.theme.event.ThemeEvent;
import com.dishanhai.sh_better_ui.theme.event.ThemeEventDispatcher;
import com.dishanhai.sh_better_ui.theme.event.ThemeObserver;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.token.TokenExpression;
import com.dishanhai.sh_better_ui.theme.token.TokenRegistry;
import com.dishanhai.sh_better_ui.theme.transition.InterruptPolicy;
import com.dishanhai.sh_better_ui.theme.transition.TransitionManager;
import com.dishanhai.sh_better_ui.theme.transition.TransitionNotice;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import com.dishanhai.sh_better_ui.theme.transition.TransitionState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Owns one dynamic theme runtime. Mutations, animation and callbacks run on the creating thread.
 * Registry, catalog and displayed snapshots may be read by workers after publication.
 */
public final class ThemeManager implements AutoCloseable {
    private static final int MAX_CALLBACK_OPERATIONS = 64;
    private static final int MAX_DIAGNOSTICS = 32;
    private final Thread owner = Thread.currentThread();
    private final TokenRegistry tokens = new TokenRegistry();
    private final ThemeResolver resolver = new ThemeResolver();
    private final ScopeManager scopes;
    private final ThemeRuleResolver rules;
    private final ContextChangeDetector detector = new ContextChangeDetector(Set.of());
    private final ThemeEventDispatcher events;
    private final TransitionManager<ThemeSnapshot> motion;
    private final Consumer<RuntimeException> errors;
    private final ArrayDeque<String> diagnostics = new ArrayDeque<>();
    private final ArrayDeque<Runnable> commands = new ArrayDeque<>();
    private final ArrayDeque<ThemeEvent> outbox = new ArrayDeque<>();
    private final ArrayDeque<Notice> notices = new ArrayDeque<>();
    private volatile ThemeRegistry registry = BuiltinThemes.registry();
    private volatile ThemeSnapshot displayed;
    private ThemePreferences preferences = ThemePreferences.DEFAULT;
    private Map<ThemeToken<?>, ThemeResolver.Definition> environmentDefaults = Map.of();
    private ContextSnapshot captured = ContextSnapshot.empty();
    private ThemeRuleResolver.Selection selection;
    private ThemeSnapshot requested;
    private long resourceVersion;
    private long resolutions;
    private long evaluatedTokens;
    private boolean processing;
    private volatile boolean closed;

    /** Creates a standalone manager with a monotonic clock. */
    public ThemeManager() {
        this(() -> System.nanoTime() / 1_000_000L,
                failure -> System.getLogger(ThemeManager.class.getName()).log(System.Logger.Level.WARNING,
                        failure.getMessage(), failure));
    }

    public ThemeManager(LongSupplier clockMillis, Consumer<RuntimeException> errors) {
        this.errors = Objects.requireNonNull(errors);
        ThemeTokens.all().forEach(tokens::register);
        displayed = resolver.resolve(registry, tokens.snapshot(), List.of(BuiltinThemes.DARK), null);
        requested = displayed;
        rules = new ThemeRuleResolver(clockMillis);
        selection = rules.selection();
        events = new ThemeEventDispatcher(this::report);
        scopes = new ScopeManager(resolver, tokens::snapshot, displayed, this::report, this::scopeChanged);
        motion = new TransitionManager<>(displayed, clockMillis, this::interpolate, this::transitionNotice, this::report);
    }

    public ThemeRegistry registry() { return registry; }
    public Map<String, ThemeToken<?>> tokenCatalog() { return tokens.snapshot(); }
    public ThemeSnapshot snapshot() { return displayed; }

    /** Global parent scope for screens and other opt-in UI trees. */
    public ThemeScope global() { requireOpen(); return scopes.root(); }

    public ThemePreferences preferences() { requireOpen(); return preferences; }
    public long resourceVersion() { requireOpen(); return resourceVersion; }

    /** Supplies the palette used in Follow Environment before an environment rule selects a theme. */
    public void environmentDefaults(Map<ThemeToken<?>, TokenExpression<?>> defaults) {
        var copied = Map.copyOf(defaults);
        execute(() -> {
            var definitions = new LinkedHashMap<ThemeToken<?>, ThemeResolver.Definition>();
            var catalog = tokens.snapshot();
            copied.forEach((token, expression) -> {
                ThemeResolver.requireRegistered(catalog, token);
                definitions.put(token, new ThemeResolver.Definition(expression, "environment-default"));
            });
            if (environmentDefaults.equals(definitions)) return;
            var before = environmentDefaults;
            environmentDefaults = Map.copyOf(definitions);
            if (isEnvironmentDefault(preferences, themeIds(registry, preferences))) {
                try {
                    refreshTarget(registry, TransitionSpec.immediate(), true);
                } catch (RuntimeException failure) {
                    environmentDefaults = before;
                    throw failure;
                }
            }
        });
    }

    public boolean showingEnvironmentDefault() {
        return displayed == requested && isEnvironmentDefault(preferences, displayed.themeIds());
    }

    /** Adds a token to the immutable catalog and makes its fallback available to existing scopes. */
    public <T> ThemeToken<T> registerToken(ThemeToken<T> token) {
        execute(() -> {
            var before = tokens.snapshot();
            tokens.register(token);
            if (before != tokens.snapshot()) refreshTarget(registry, TransitionSpec.immediate(), true);
        });
        return token;
    }

    public void register(Theme theme) { register(theme, ThemeRegistry.DuplicatePolicy.REJECT); }

    /** Validates the entire candidate before publishing. Built-in IDs are reserved. */
    public void register(Theme theme, ThemeRegistry.DuplicatePolicy policy) {
        execute(() -> {
            if (BuiltinThemes.IDS.contains(theme.id())) throw new IllegalArgumentException("Reserved theme: " + theme.id());
            var next = registry.with(theme, policy);
            replaceRegistry(next, ThemeEvent.Kind.REGISTERED, theme.id(), TransitionSpec.immediate());
        });
    }

    public void unregister(String id) {
        execute(() -> {
            if (BuiltinThemes.IDS.contains(id)) throw new IllegalArgumentException("Reserved theme: " + id);
            var next = registry.without(id);
            if (next != registry) replaceRegistry(next, ThemeEvent.Kind.UNREGISTERED, id, TransitionSpec.immediate());
        });
    }

    /** Atomically replaces all resource themes, with the current code registrations as the base layer. */
    public void replaceResourceThemes(Collection<Theme> definitions, Collection<String> diagnostics) {
        replaceResourceThemes(definitions, diagnostics, () -> true);
    }

    /** Rechecks freshness when a reload was queued by a reentrant callback. */
    public void replaceResourceThemes(Collection<Theme> definitions, Collection<String> diagnostics,
                                      BooleanSupplier currentGeneration) {
        var copied = List.copyOf(definitions);
        var messages = List.copyOf(diagnostics);
        execute(() -> {
            if (!currentGeneration.getAsBoolean()) return;
            var next = registry.withResources(copied);
            var target = validateRegistry(next);
            if (!currentGeneration.getAsBoolean()) return;
            var before = registry;
            registry = next;
            resourceVersion++;
            messages.forEach(this::remember);
            before.themes().keySet().stream().filter(id -> !next.themes().containsKey(id)).forEach(id ->
                    emit(ThemeEvent.Kind.UNREGISTERED, List.of(id), null, Set.of(), null, null));
            next.themes().forEach((id, theme) -> {
                if (before.themes().get(id) != theme) emit(ThemeEvent.Kind.REGISTERED, List.of(id), null, Set.of(), null, null);
            });
            applyTarget(target, TransitionSpec.immediate());
            emit(ThemeEvent.Kind.RESOURCES_RELOADED, displayed.themeIds(), null, Set.of(), null,
                    Long.toString(resourceVersion));
        });
    }

    private void replaceRegistry(ThemeRegistry next, ThemeEvent.Kind kind, String id, TransitionSpec spec) {
        ThemeSnapshot target = validateRegistry(next);
        registry = next;
        emit(kind, List.of(id), null, Set.of(), null, null);
        if (target != requested) applyTarget(target, spec);
    }

    private ThemeSnapshot validateRegistry(ThemeRegistry candidate) {
        var ids = themeIds(candidate, preferences);
        var target = resolveTarget(candidate, ids);
        var changed = new LinkedHashSet<String>();
        candidate.themes().forEach((id, theme) -> {
            if (registry.themes().get(id) != theme) changed.add(id);
        });
        registry.themes().keySet().forEach(id -> {
            if (!candidate.themes().containsKey(id)) changed.add(id);
        });
        // Revalidate changed definitions and their descendants, including inactive ones.
        for (var theme : candidate.themes().values()) {
            if (ids.size() == 1 && ids.get(0).equals(theme.id())) continue;
            if (ancestryChanged(candidate, theme, changed)) {
                resolver.resolve(candidate, tokens.snapshot(), List.of(theme.id()), null);
            }
        }
        scopes.validate(target);
        return target;
    }

    private static boolean ancestryChanged(ThemeRegistry registry, Theme theme, Set<String> changed) {
        for (Theme node = theme; node != null; node = registry.themes().get(node.parent())) {
            if (changed.contains(node.id()) || changed.contains(node.parent())) return true;
        }
        return false;
    }

    /** Fixes the theme selection and commits it immediately. */
    public void setTheme(String id) { transitionTo(id, TransitionSpec.immediate()); }

    /** Fixes the theme selection and uses the supplied transition, subject to user motion preferences. */
    public void transitionTo(String id, TransitionSpec spec) {
        ThemeId.require(id);
        Objects.requireNonNull(spec);
        execute(() -> requestTheme(id, spec));
    }

    /** Switches to the theme's resolved normal duration and easing. */
    public void transitionTo(String id) {
        ThemeId.require(id);
        execute(() -> requestTheme(id, null));
    }

    private void requestTheme(String id, TransitionSpec spec) {
        if (registry.find(id).isEmpty()) throw new ThemeResolutionException("Unknown theme: " + id);
        var ids = List.of(id);
        var target = resolveTarget(registry, ids, ThemePreferences.Mode.FIXED);
        scopes.validate(target);
        preferences = preferences.withFixedTheme(id);
        emit(ThemeEvent.Kind.REQUESTED, ids, null, Set.of(), null, null);
        applyTarget(target, spec == null ? transitionFor(target) : spec);
    }

    /**
     * Changes selection, motion and accessibility. A fixed pack ID remains selected even while
     * unavailable so that it can recover on reload. The displayed theme falls back to Slate.
     */
    public void preferences(ThemePreferences next) {
        Objects.requireNonNull(next);
        execute(() -> {
            if (preferences.equals(next)) return;
            var target = resolveTarget(registry, themeIds(registry, next), next.mode());
            scopes.validate(target);
            preferences = next;
            scopes.preferences(next);
            applyTarget(target, next.animate() ? transitionFor(target) : TransitionSpec.immediate());
        });
    }

    /** Registers or explicitly replaces an environment rule by ID. */
    public void registerRule(ThemeRule rule) {
        execute(() -> {
            rules.register(rule);
            observeContext(captured, true);
        });
    }

    public void removeRule(String id) {
        execute(() -> {
            if (rules.remove(id)) observeContext(captured, true);
        });
    }

    public void setRuleEnabled(String id, boolean enabled) {
        execute(() -> {
            if (rules.setEnabled(id, enabled)) observeContext(captured, true);
        });
    }

    /** Accepts one immutable platform capture and evaluates only the relevant changes. */
    public void environment(ContextSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        execute(() -> observeContext(snapshot, false));
    }

    private void observeContext(ContextSnapshot snapshot, boolean force) {
        captured = snapshot;
        if (force) detector.setRelevantKeys(rules.dependencies());
        var changed = detector.update(snapshot);
        if (force || !changed.changedKeys().isEmpty() || rules.pending()) {
            updateSelection(rules.resolve(changed.snapshot(), changed.changedKeys()));
        }
    }

    private void updateSelection(ThemeRuleResolver.Selection next) {
        if (next == selection) return;
        selection = next;
        emit(ThemeEvent.Kind.RULES_RESOLVED, next.themeIds(), null, Set.of(), null,
                String.join(", ", next.activeRuleIds()));
        if (preferences.mode() == ThemePreferences.Mode.FOLLOW_ENVIRONMENT) {
            try {
                refreshTarget(registry, next.transition(), false);
            } catch (ThemeResolutionException failure) {
                resolutionFailed(failure);
            }
        }
    }

    private void refreshTarget(ThemeRegistry source, TransitionSpec spec, boolean force) {
        var ids = themeIds(source, preferences);
        if (!force && requested != null && requested.themeIds().equals(ids)) return;
        var target = resolveTarget(source, ids);
        scopes.validate(target);
        applyTarget(target, spec);
    }

    private List<String> themeIds(ThemeRegistry source, ThemePreferences preferences) {
        return switch (preferences.mode()) {
            case DARK -> List.of(BuiltinThemes.DARK);
            case LIGHT -> List.of(BuiltinThemes.LIGHT_ID);
            case FIXED -> source.find(preferences.fixedThemeId()).isPresent()
                    ? List.of(preferences.fixedThemeId()) : List.of(BuiltinThemes.DARK);
            case FOLLOW_ENVIRONMENT -> {
                var ids = new LinkedHashSet<String>();
                ids.add(BuiltinThemes.DARK);
                for (var id : selection.themeIds()) if (source.find(id).isPresent()) ids.add(id);
                yield List.copyOf(ids);
            }
        };
    }

    private ThemeSnapshot resolveTarget(ThemeRegistry source, List<String> ids) {
        return resolveTarget(source, ids, preferences.mode());
    }

    private ThemeSnapshot resolveTarget(ThemeRegistry source, List<String> ids, ThemePreferences.Mode mode) {
        var plan = resolver.plan(source, ids);
        if (mode == ThemePreferences.Mode.FOLLOW_ENVIRONMENT && ids.equals(List.of(BuiltinThemes.DARK))) {
            plan = plan.overlay(environmentDefaults);
        }
        var next = resolver.evaluate(plan, tokens.snapshot(), requested);
        if (next != requested) {
            resolutions++;
            evaluatedTokens += next.evaluatedTokens();
            next.diagnostics().forEach(this::remember);
        }
        return next;
    }

    private static boolean isEnvironmentDefault(ThemePreferences preferences, List<String> ids) {
        return preferences.mode() == ThemePreferences.Mode.FOLLOW_ENVIRONMENT && ids.equals(List.of(BuiltinThemes.DARK));
    }

    private static TransitionSpec transitionFor(ThemeSnapshot target) {
        return TransitionSpec.of(target.resolve(ThemeTokens.NORMAL).toMillis(), target.resolve(ThemeTokens.STANDARD));
    }

    private void applyTarget(ThemeSnapshot target, TransitionSpec spec) {
        boolean immediate = !preferences.animate() || spec.durationMillis() == 0 || spec.policy() == InterruptPolicy.SNAP;
        if (requested == target && (!immediate || motion.state() != TransitionState.RUNNING)) return;
        var effective = immediate ? TransitionSpec.immediate().withPolicy(InterruptPolicy.SNAP)
                : spec.atSpeed(preferences.animationSpeed());
        emit(ThemeEvent.Kind.WILL_CHANGE, target.themeIds(), null, Set.of(), null, null);
        motion.request(target, effective);
        requested = target;
        publishMotion();
    }

    /** Samples the current real time and flushes one batch of invalidations before widgets render. */
    public void frame() {
        execute(() -> {
            if (rules.pending()) updateSelection(rules.resolve(detector.snapshot(), Set.of()));
            if (motion.state() == TransitionState.RUNNING) {
                motion.update();
                publishMotion();
            }
            scopes.flush();
        });
    }

    public void pauseTransition() { execute(() -> { motion.pause(); publishMotion(); }); }
    public void resumeTransition() { execute(motion::resume); }
    public void cancelTransition() {
        execute(() -> {
            motion.cancel();
            publishMotion();
            requested = null;
        });
    }

    private ThemeSnapshot interpolate(ThemeSnapshot from, ThemeSnapshot to, double progress) {
        var values = new LinkedHashMap<ThemeToken<?>, Object>();
        to.values().forEach((token, end) -> {
            Object start = from.values().getOrDefault(token, token.defaultValue());
            values.put(token, token.type().interpolate(start, end, progress, progress));
        });
        var source = progress < 0.5 ? from : to;
        if (values.equals(source.values())) return source;
        return resolver.snapshot(source.plan(), values, source.dependencies(), 0);
    }

    private void transitionNotice(TransitionNotice notice) {
        notices.addLast(new Notice(notice, motion.value(), motion.target().themeIds()));
    }

    private void publishMotion() {
        ThemeSnapshot next = motion.value();
        var before = displayed;
        try {
            scopes.publish(motion.target(), next);
            displayed = next;
        } catch (ThemeResolutionException failure) {
            notices.clear();
            motion.restore(before);
            requested = null;
            resolutionFailed(failure);
        }
        while (!notices.isEmpty()) {
            var entry = notices.removeFirst();
            var kind = switch (entry.notice.kind()) {
                case STARTED -> ThemeEvent.Kind.TRANSITION_STARTED;
                case UPDATED -> ThemeEvent.Kind.TRANSITION_UPDATED;
                case INTERRUPTED -> ThemeEvent.Kind.TRANSITION_INTERRUPTED;
                case COMPLETED -> ThemeEvent.Kind.TRANSITION_COMPLETED;
                case CANCELLED -> ThemeEvent.Kind.TRANSITION_CANCELLED;
            };
            outbox.addLast(new ThemeEvent(kind, entry.frame, entry.ids, null, Set.of(), entry.notice, null));
            if (kind == ThemeEvent.Kind.TRANSITION_COMPLETED) {
                outbox.addLast(new ThemeEvent(ThemeEvent.Kind.CHANGED, entry.frame, entry.ids, null, Set.of(), null, null));
            }
        }
        var changed = displayed.changedFrom(before);
        if (!changed.isEmpty()) emit(ThemeEvent.Kind.TOKEN_CHANGED, displayed.themeIds(), null, changed, null, null);
    }

    /** Observes lifecycle events in registration order. */
    public Subscription observe(ThemeObserver observer) {
        requireOpen();
        return events.observe(ignored -> true, observer);
    }

    /** Observes the enclosing theme and ancestor scope changes until the supplied scope closes. */
    public Subscription observe(ThemeScope scope, ThemeObserver observer) {
        requireOpen();
        if (scope.manager != scopes || scope.isClosed()) throw new IllegalArgumentException("Inactive or foreign scope");
        var subscription = events.observe(event -> {
            if (event.scope() == null) return true;
            for (var node = scope; node != null; node = node.parent()) {
                if (node == event.scope()) return true;
            }
            return false;
        }, observer);
        return scope.manage(subscription);
    }

    private void scopeChanged(ThemeScope scope) {
        execute(() -> emit(ThemeEvent.Kind.SCOPE_CHANGED, displayed.themeIds(), scope, Set.of(), null, scope.name()));
    }

    /** Reports an asynchronous resource/extension failure without changing the displayed snapshot. */
    public void resolutionFailed(RuntimeException failure) {
        execute(() -> {
            report(failure);
            emit(ThemeEvent.Kind.RESOLUTION_FAILED, displayed.themeIds(), null, Set.of(), null, failure.getMessage());
        });
    }

    private void emit(ThemeEvent.Kind kind, List<String> ids, ThemeScope scope, Set<ThemeToken<?>> changed,
                      TransitionNotice notice, String detail) {
        outbox.addLast(new ThemeEvent(kind, displayed, ids, scope, changed, notice, detail));
    }

    /** Immutable diagnostic sample for developer tools. */
    public ThemeInspector inspect(ThemeScope scope) {
        requireOpen();
        if (scope.manager != scopes) throw new IllegalArgumentException("Foreign scope");
        return new ThemeInspector(scope.name(), scope.parent() == null ? null : scope.parent().name(),
                scope.snapshot(), scope.overrides(), rules.debugStates(), rules.diagnostics(), detector.snapshot(),
                motion.state(), motion.progress(), motion.queuedTargets(), scopes.lastInvalidatedOwners(),
                resolutions + scopes.resolutions(), evaluatedTokens + scopes.evaluatedTokens(), scopes.styleHits(),
                scopes.styleMisses(), List.copyOf(diagnostics));
    }

    private void execute(Runnable action) {
        requireOpen();
        if (processing) {
            if (commands.size() >= MAX_CALLBACK_OPERATIONS) throw new IllegalStateException("Theme callback queue is full");
            commands.addLast(action);
            return;
        }
        processing = true;
        try {
            action.run();
            drainEvents();
            for (int count = 0; !closed && !commands.isEmpty() && count < MAX_CALLBACK_OPERATIONS; count++) {
                try { commands.removeFirst().run(); }
                catch (RuntimeException failure) {
                    report(failure);
                    emit(ThemeEvent.Kind.RESOLUTION_FAILED, displayed.themeIds(), null, Set.of(), null, failure.getMessage());
                }
                drainEvents();
            }
            if (!closed && !commands.isEmpty()) report(new IllegalStateException("Theme event loop exceeded operation limit"));
        } catch (ThemeResolutionException failure) {
            report(failure);
            emit(ThemeEvent.Kind.RESOLUTION_FAILED, displayed.themeIds(), null, Set.of(), null, failure.getMessage());
            drainEvents();
            throw failure;
        } finally {
            processing = false;
            commands.clear();
            outbox.clear();
        }
    }

    private void drainEvents() {
        while (!outbox.isEmpty()) events.dispatch(outbox.removeFirst());
    }

    private void remember(String diagnostic) {
        if (Objects.equals(diagnostics.peekLast(), diagnostic)) return;
        if (diagnostics.size() >= MAX_DIAGNOSTICS) diagnostics.removeFirst();
        diagnostics.addLast(diagnostic);
    }

    private void report(RuntimeException failure) {
        remember(Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()));
        try { errors.accept(failure); }
        catch (RuntimeException ignored) { /* Diagnostic consumers cannot stop theme processing. */ }
    }

    public boolean isClosed() { return closed; }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Theme manager requires its owner thread");
    }

    private void requireOpen() {
        checkThread();
        if (closed) throw new IllegalStateException("Closed theme manager");
    }

    @Override
    public void close() {
        checkThread();
        if (closed) return;
        execute(() -> {
            motion.close();
            publishMotion();
            scopes.root().closeBranch();
            drainEvents();
            events.clear();
            closed = true;
            commands.clear();
        });
    }

    private record Notice(TransitionNotice notice, ThemeSnapshot frame, List<String> ids) {}
}
