// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationPollingService.PollScheduler;

/**
 * Deterministic lifecycle coverage for {@link NotificationPollingService} using the package-private injectable
 * constructor: a manual scheduler (records scheduled tasks without a real thread pool) plus toggleable
 * enabled/dev-build/override suppliers.
 */
public final class NotificationPollingServiceTest {

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorExtension = new ActivatorStaticMockExtension();

    /** Records scheduled tasks and lets the test fire them manually; returns a mock cancellable future. */
    private static final class ManualScheduler implements PollScheduler {
        private final List<Runnable> tasks = new ArrayList<>();
        private final List<Long> delays = new ArrayList<>();
        private final List<ScheduledFuture<?>> futures = new ArrayList<>();
        private int scheduleCount;

        @Override
        public ScheduledFuture<?> schedule(final Runnable task, final long delayMs) {
            scheduleCount++;
            tasks.add(task);
            delays.add(delayMs);
            final ScheduledFuture<?> future = mock(ScheduledFuture.class);
            futures.add(future);
            return future;
        }

        void fireLast() {
            tasks.get(tasks.size() - 1).run();
        }
    }

    /**
     * Scheduler that runs the task SYNCHRONOUSLY inside schedule() — reproducing a real ScheduledExecutorService
     * executing a delay-0 first poll before start() returns. This is the race that silently no-op'd the first poll
     * when running was set after scheduling.
     */
    private static final class InlineScheduler implements PollScheduler {
        private int scheduleCount;

        @Override
        public ScheduledFuture<?> schedule(final Runnable task, final long delayMs) {
            scheduleCount++;
            if (delayMs == 0L) {
                task.run();
            }
            return mock(ScheduledFuture.class);
        }
    }

    private AtomicBoolean enabled;
    private AtomicBoolean devBuild;
    private AtomicBoolean override;
    private NotificationsFetcher fetcher;
    private ProcessNotifications processor;
    private ManualScheduler scheduler;
    private AtomicInteger fetcherBuilds;

    @BeforeEach
    void setUp() {
        enabled = new AtomicBoolean(true);
        devBuild = new AtomicBoolean(false);
        override = new AtomicBoolean(false);
        fetcher = mock(NotificationsFetcher.class);
        when(fetcher.fetch()).thenReturn(Optional.empty());
        processor = mock(ProcessNotifications.class);
        scheduler = new ManualScheduler();
        fetcherBuilds = new AtomicInteger();
    }

    private NotificationPollingService service() {
        return new NotificationPollingService(enabled::get, devBuild::get, override::get,
            () -> {
                fetcherBuilds.incrementAndGet();
                return fetcher;
            },
            () -> processor, scheduler);
    }

    @Test
    void startSchedulesFirstPollWithZeroDelayAndDoesNotBlock() {
        service().start();
        assertEquals(1, scheduler.scheduleCount);
        assertEquals(0L, scheduler.delays.get(0));
    }

    @Test
    void startIsIdempotent() {
        final NotificationPollingService s = service();
        s.start();
        s.start();
        assertEquals(1, scheduler.scheduleCount, "second start() must be a no-op");
        assertEquals(1, fetcherBuilds.get());
    }

    @Test
    void disabledKillSwitchDoesNotPoll() {
        enabled.set(false);
        service().start();
        assertEquals(0, scheduler.scheduleCount);
    }

    @Test
    void devBuildWithoutOverrideDoesNotPoll() {
        devBuild.set(true);
        service().start();
        assertEquals(0, scheduler.scheduleCount);
    }

    @Test
    void devBuildWithOverrideDoesPoll() {
        devBuild.set(true);
        override.set(true);
        service().start();
        assertEquals(1, scheduler.scheduleCount);
    }

    @Test
    void pollReschedulesAtInterval() {
        service().start();
        scheduler.fireLast(); // run the first poll
        assertEquals(2, scheduler.scheduleCount, "poll must self-reschedule");
        assertTrue(scheduler.delays.get(1) > 0, "reschedule uses the poll interval, not 0");
    }

    @Test
    void shutdownCancelsPendingAndPreventsReschedule() {
        final NotificationPollingService s = service();
        s.start();
        s.shutdown();
        // Firing a poll after shutdown must not re-arm.
        final int countAtShutdown = scheduler.scheduleCount;
        scheduler.fireLast();
        assertEquals(countAtShutdown, scheduler.scheduleCount, "no reschedule after shutdown");
    }

    @Test
    void killSwitchToggleOffThenOnResumesPolling() {
        final NotificationPollingService s = service();
        s.start();
        assertEquals(1, scheduler.scheduleCount);

        // Turn off -> pause.
        enabled.set(false);
        s.onEnabledPreferenceChanged();
        // Firing a stale scheduled task while disabled must not reschedule.
        scheduler.fireLast();
        assertEquals(1, scheduler.scheduleCount);

        // Turn back on -> resumes without an IDE restart.
        enabled.set(true);
        s.onEnabledPreferenceChanged();
        assertEquals(2, scheduler.scheduleCount, "re-enabling must restart polling");
    }

    @Test
    void startAfterShutdownStaysDown() {
        final NotificationPollingService s = service();
        s.shutdown();
        s.start();
        assertEquals(0, scheduler.scheduleCount);
    }

    @Test
    void firstPollExecutingInlineAtScheduleTimeStillRuns() {
        // Regression: the first poll is scheduled with delay 0; if the scheduler runs it synchronously (as a real
        // pool thread can, before start() returns), pollOnce must see running==true and actually fetch — not
        // silently no-op. Verify the poll reached the fetcher.
        when(fetcher.fetch()).thenReturn(Optional.empty());
        final InlineScheduler inline = new InlineScheduler();
        final NotificationPollingService s = new NotificationPollingService(enabled::get, devBuild::get,
                override::get, () -> fetcher, () -> processor, inline);
        s.start();
        org.mockito.Mockito.verify(fetcher).fetch();
    }

    @Test
    void schedulerRejectionLeavesServiceRestartable() {
        // A scheduler that rejects (returns null, as the real one does on RejectedExecutionException) must not
        // latch the service into a started-but-never-scheduled state: a later start() can retry.
        final AtomicBoolean reject = new AtomicBoolean(true);
        final PollScheduler rejecting = (task, delayMs) -> {
            if (reject.get()) {
                return null;
            }
            return scheduler.schedule(task, delayMs);
        };
        final NotificationPollingService s = new NotificationPollingService(enabled::get, devBuild::get,
                override::get, () -> fetcher, () -> processor, rejecting);
        s.start();
        assertEquals(0, scheduler.scheduleCount, "rejected schedule armed nothing");

        // Pool recovers; a subsequent start() succeeds because running was never latched true.
        reject.set(false);
        s.start();
        assertEquals(1, scheduler.scheduleCount, "start() retries after an earlier rejection");
    }
}
