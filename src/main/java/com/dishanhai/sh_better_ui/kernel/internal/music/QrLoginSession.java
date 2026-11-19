// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Finite polling; completion is committed under the player lock before it is published as CONFIRMED. */
final class QrLoginSession implements OnlineMusicLogin {
    private final ScheduledExecutorService worker;
    private final boolean ownsWorker;
    private final OnlineMusicSource provider;
    private final BiConsumer<QrLoginSession, String> commit;
    private final long intervalMs, deadline;
    private volatile Snapshot snapshot = new Snapshot(State.CREATING, null);
    private OnlineMusicSource.QrChallenge challenge;
    private ScheduledFuture<?> task;
    private boolean terminal;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    CompletableFuture<Void> completion() { return completion; }
    static final class Expired extends RuntimeException {}

    QrLoginSession(OnlineMusicSource provider, BiConsumer<QrLoginSession, String> commit) {
        this(provider, commit, 2000, 180_000);
    }
    QrLoginSession(OnlineMusicSource provider, BiConsumer<QrLoginSession, String> commit, ScheduledExecutorService worker) {
        this(provider, commit, 2000, 180_000, worker, false);
    }
    QrLoginSession(OnlineMusicSource provider, BiConsumer<QrLoginSession, String> commit, long intervalMs, long timeoutMs) {
        this(provider, commit, intervalMs, timeoutMs, Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "shb-music-login"); thread.setDaemon(true); return thread;
        }), true);
    }
    private QrLoginSession(OnlineMusicSource provider, BiConsumer<QrLoginSession, String> commit, long intervalMs, long timeoutMs,
                           ScheduledExecutorService worker, boolean ownsWorker) {
        this.worker = worker; this.ownsWorker = ownsWorker;
        this.provider = provider; this.commit = commit; this.intervalMs = intervalMs;
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    }
    synchronized void start() { if (!terminal) task = worker.schedule(this::create, 0, TimeUnit.MILLISECONDS); }
    private void create() {
        try {
            var created = provider.qrLogin();
            synchronized (this) {
                if (terminal) { created.close(); return; }
                challenge = created;
                if (System.nanoTime() >= deadline) { finish(State.EXPIRED); return; }
                snapshot = new Snapshot(State.WAITING, created.image());
                task = worker.schedule(this::poll, intervalMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) { finish(State.FAILED); }
    }
    private void poll() {
        try {
            OnlineMusicSource.QrChallenge current;
            synchronized (this) {
                if (terminal) return;
                if (System.nanoTime() >= deadline) { finish(State.EXPIRED); return; }
                current = challenge;
            }
            var result = current.poll();
            if (result.state() == State.CONFIRMED) {
                if (result.cookie() == null || result.cookie().isBlank()) { finish(State.FAILED); return; }
                commit.accept(this, result.cookie());
                return;
            }
            synchronized (this) {
                if (terminal) return;
                if (System.nanoTime() >= deadline) { finish(State.EXPIRED); return; }
                if (result.state() != State.WAITING && result.state() != State.SCANNED) {
                    finish(result.state() == State.EXPIRED ? State.EXPIRED : State.FAILED); return;
                }
                snapshot = new Snapshot(result.state(), snapshot.image());
                task = worker.schedule(this::poll, intervalMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) { finish(State.FAILED); }
    }
    /** Called only under the owning player lock; cancellation cannot race the credential replacement. */
    synchronized void confirm(Runnable apply) {
        if (terminal) return;
        if (System.nanoTime() >= deadline) { finish(State.EXPIRED); return; }
        try { apply.run(); finish(State.CONFIRMED); }
        catch (RuntimeException ignored) { finish(State.FAILED); }
    }
    private synchronized void finish(State state) {
        if (terminal) return;
        terminal = true;
        if (challenge != null) {
            try { challenge.close(); } catch (RuntimeException ignored) { }
            challenge = null;
        }
        if (task != null) task.cancel(true);
        if (ownsWorker) worker.shutdownNow();
        snapshot = new Snapshot(state, null);
        switch (state) {
            case CONFIRMED -> completion.complete(null);
            case CANCELLED -> completion.cancel(false);
            case EXPIRED -> completion.completeExceptionally(new Expired());
            default -> completion.completeExceptionally(new IllegalStateException("QR login did not complete"));
        }
    }
    @Override public Snapshot snapshot() { return snapshot; }
    @Override public void close() { finish(State.CANCELLED); }
}
