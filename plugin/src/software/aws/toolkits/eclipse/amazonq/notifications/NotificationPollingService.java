// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;

/**
 * App-level singleton that polls the notifications endpoint every 10 minutes on the shared worker pool,
 * self-rescheduling after each poll. The poll body is total (fetch never throws) and the re-arm happens in a
 * {@code finally}.
 *
 * <p>Lifecycle: {@link #start()} is called once at startup; {@link #shutdown()} must be called early in
 * {@code Activator.stop()} to permanently cancel polling during teardown. {@link #onEnabledPreferenceChanged()} lets
 * the notifications kill-switch pause/resume polling within a session without an IDE restart.
 *
 * <p>The scheduler and collaborators are injectable via a package-private constructor so unit tests can drive
 * start/stop/reschedule deterministically without SWT, the network, or a real thread pool.
 */
public final class NotificationPollingService {

    private static final NotificationPollingService INSTANCE = new NotificationPollingService();
    private static final long POLL_INTERVAL_MS = Duration.ofMinutes(10).toMillis();

    /** Abstracts the scheduler so tests can inject a deterministic one; returns a cancellable handle or null. */
    interface PollScheduler {
        ScheduledFuture<?> schedule(Runnable task, long delayMs);
    }

    private final Supplier<Boolean> enabledSupplier;
    private final Supplier<Boolean> devBuildSupplier;
    private final Supplier<Boolean> endpointOverrideSupplier;
    private final Supplier<NotificationsFetcher> fetcherSupplier;
    private final Supplier<ProcessNotifications> processorSupplier;
    private final PollScheduler scheduler;

    private volatile boolean shutdown;
    private volatile boolean running;
    private volatile ScheduledFuture<?> scheduledPoll;
    private volatile NotificationsFetcher fetcher;
    private volatile ProcessNotifications processor;

    private NotificationPollingService() {
        this(
            NotificationPreferences::isNotificationsEnabled,
            SystemDetailsCollector::isDevBuild,
            NotificationPreferences::hasEndpointOverride,
            () -> new NotificationsFetcher(NotificationPreferences.resolveEndpoint()),
            () -> new ProcessNotifications(new NotificationDismissalStore()),
            defaultScheduler());
    }

    // Package-private for tests.
    NotificationPollingService(final Supplier<Boolean> enabledSupplier, final Supplier<Boolean> devBuildSupplier,
            final Supplier<Boolean> endpointOverrideSupplier, final Supplier<NotificationsFetcher> fetcherSupplier,
            final Supplier<ProcessNotifications> processorSupplier, final PollScheduler scheduler) {
        this.enabledSupplier = enabledSupplier;
        this.devBuildSupplier = devBuildSupplier;
        this.endpointOverrideSupplier = endpointOverrideSupplier;
        this.fetcherSupplier = fetcherSupplier;
        this.processorSupplier = processorSupplier;
        this.scheduler = scheduler;
    }

    private static PollScheduler defaultScheduler() {
        final BiFunction<Runnable, Long, ScheduledFuture<?>> sched =
                (task, delay) -> (ScheduledFuture<?>) ThreadingUtils.scheduleAsyncTaskWithDelay(task, delay);
        return (task, delayMs) -> {
            try {
                return sched.apply(task, delayMs);
            } catch (RejectedExecutionException e) {
                Activator.getLogger().info("Notifications polling stopped (worker pool shutting down)");
                return null;
            }
        };
    }

    public static NotificationPollingService getInstance() {
        return INSTANCE;
    }

    /** Starts polling once per app lifetime; no-op if disabled, a dev build without override, or already running. */
    public synchronized void start() {
        if (shutdown || running) {
            return;
        }
        if (!enabledSupplier.get()) {
            return;
        }
        // Development/unreleased builds must not receive production notifications. Allow an explicit endpoint
        // override (preference or env var) so local/demo testing against a test endpoint still works.
        if (devBuildSupplier.get() && !endpointOverrideSupplier.get()) {
            Activator.getLogger().info("Notifications polling skipped: development build with no endpoint override");
            return;
        }
        this.fetcher = fetcherSupplier.get();
        this.processor = processorSupplier.get();
        // Schedule the first poll instead of running it inline so start() never blocks its caller (the shared
        // startup worker thread) on network I/O.
        final ScheduledFuture<?> scheduled = scheduler.schedule(this::pollOnce, 0L);
        if (scheduled == null) {
            // The worker pool rejected the task (e.g. shutting down). Leave running=false so a later re-enable
            // can retry rather than latching into a started-but-never-scheduled state.
            return;
        }
        running = true;
        scheduledPoll = scheduled;
    }

    void pollOnce() {
        if (shutdown || !running || !enabledSupplier.get()) {
            return;
        }
        try {
            fetcher.fetch().ifPresent(processor::process);
        } catch (Throwable t) {
            Activator.getLogger().warn("Notifications poll failed", t);
            NotificationTelemetryProvider.emitPollFailure("Failed to poll for notifications");
        } finally {
            reschedule();
        }
    }

    private synchronized void reschedule() {
        if (shutdown || !running || !enabledSupplier.get()) {
            return;
        }
        // reschedule() and shutdown() are both synchronized on this monitor, so shutdown cannot interleave here;
        // the entry guard above plus shutdown()'s cancelPending() are sufficient to stop post-teardown polls.
        scheduledPoll = scheduler.schedule(this::pollOnce, POLL_INTERVAL_MS);
    }

    /**
     * Reacts to a change in the notifications kill-switch preference: starts polling if it was turned on, or pauses
     * (cancels the pending poll) if it was turned off. Unlike {@link #shutdown()}, this is reversible in-session.
     */
    public synchronized void onEnabledPreferenceChanged() {
        if (shutdown) {
            return;
        }
        if (enabledSupplier.get()) {
            start();
        } else {
            pause();
        }
    }

    private synchronized void pause() {
        running = false;
        cancelPending();
    }

    /** Permanently cancels polling for teardown; not resumable. */
    public synchronized void shutdown() {
        shutdown = true;
        running = false;
        cancelPending();
    }

    private void cancelPending() {
        final ScheduledFuture<?> current = scheduledPoll;
        if (current != null) {
            current.cancel(false);
            scheduledPoll = null;
        }
    }
}
