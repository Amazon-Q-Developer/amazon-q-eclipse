// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;

/**
 * App-level singleton that polls the notifications endpoint every 10 minutes on the shared worker pool, self-rescheduling
 * after each poll. The poll body is total (fetch never throws; the work is wrapped so an escaped error cannot cancel the
 * loop) and the re-arm happens in a {@code finally}. {@link #stop()} must be called early in {@code Activator.stop()} to
 * cancel the pending future and prevent a re-arm during teardown.
 */
public final class NotificationPollingService {

    private static final NotificationPollingService INSTANCE = new NotificationPollingService();
    private static final long POLL_INTERVAL_MS = Duration.ofMinutes(10).toMillis();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean stopped;
    private volatile ScheduledFuture<?> scheduledPoll;
    private volatile NotificationsFetcher fetcher;
    private volatile ProcessNotifications processor;

    private NotificationPollingService() {
        // singleton
    }

    public static NotificationPollingService getInstance() {
        return INSTANCE;
    }

    /** Starts polling once per app lifetime; no-op if the kill-switch is off or polling already started. */
    public void start() {
        if (!NotificationPreferences.isNotificationsEnabled()) {
            return;
        }
        // Development/unreleased builds must not receive production notifications. Allow an explicit endpoint
        // override (preference or env var) so local/demo testing against a test endpoint still works.
        if (SystemDetailsCollector.isDevBuild() && !NotificationPreferences.hasEndpointOverride()) {
            Activator.getLogger().info("Notifications polling skipped: development build with no endpoint override");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        this.fetcher = new NotificationsFetcher(NotificationPreferences.resolveEndpoint());
        this.processor = new ProcessNotifications(new NotificationDismissalStore());
        // Schedule the first poll instead of running it inline so start() never blocks its caller (the shared
        // startup worker thread) on network I/O.
        scheduledPoll = schedulePoll(0L);
    }

    private void pollOnce() {
        if (stopped || !NotificationPreferences.isNotificationsEnabled()) {
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

    private void reschedule() {
        if (stopped || !NotificationPreferences.isNotificationsEnabled()) {
            return;
        }
        scheduledPoll = schedulePoll(POLL_INTERVAL_MS);
        // If stop() ran concurrently between the guard above and the assignment, cancel the future we just armed
        // so a poll cannot fire after shutdown.
        if (stopped && scheduledPoll != null) {
            scheduledPoll.cancel(false);
        }
    }

    private ScheduledFuture<?> schedulePoll(final long delayMs) {
        try {
            return (ScheduledFuture<?>) ThreadingUtils.scheduleAsyncTaskWithDelay(this::pollOnce, delayMs);
        } catch (RejectedExecutionException e) {
            Activator.getLogger().info("Notifications polling stopped (worker pool shutting down)");
            return null;
        }
    }

    /** Cancels the pending poll and prevents further rescheduling; safe to call during shutdown. */
    public void stop() {
        stopped = true;
        final ScheduledFuture<?> current = scheduledPoll;
        if (current != null) {
            current.cancel(false);
        }
    }
}
