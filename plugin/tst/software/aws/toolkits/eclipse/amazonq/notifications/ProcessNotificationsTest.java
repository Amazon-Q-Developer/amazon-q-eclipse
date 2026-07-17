// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.LocalizedContent;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationContent;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationSchedule;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationScheduleType;

/**
 * Covers the filtering/dedup/dismissal decisions that are impractical to verify manually (they depend on
 * multiple 10-minute polls). Uses an injected display callback to capture which notifications would be shown.
 */
public final class ProcessNotificationsTest {

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorExtension = new ActivatorStaticMockExtension();

    private NotificationDismissalStore dismissalStore;
    private List<String> shown;
    private ProcessNotifications processor;

    @BeforeEach
    void setUp() {
        dismissalStore = mock(NotificationDismissalStore.class);
        shown = new ArrayList<>();
        // Display that reports a successful render (completion=true), mirroring a real rendered toast.
        processor = new ProcessNotifications(dismissalStore, (id, n, c, actions, completion) -> {
            shown.add(id);
            completion.accept(true);
        });
    }

    /** Builds a processor whose display reports render failure, to exercise the retry/no-commit path. */
    private ProcessNotifications processorWithFailingDisplay() {
        return new ProcessNotifications(dismissalStore, (id, n, c, actions, completion) -> {
            shown.add(id);
            completion.accept(false);
        });
    }

    private static NotificationData notif(final String id, final NotificationScheduleType type) {
        return new NotificationData(id, new NotificationSchedule(type), "Info", null,
                new NotificationContent(new LocalizedContent("Title", "Description")), null);
    }

    private static NotificationsList list(final NotificationData... notifications) {
        return new NotificationsList(new NotificationsList.Schema("2.0"), List.of(notifications));
    }

    @Test
    void startupShownOnlyOnFirstPoll() {
        final NotificationsList payload = list(notif("startup1", NotificationScheduleType.STARTUP));
        processor.process(payload);
        assertEquals(List.of("startup1"), shown);

        // Second poll: startup notification must NOT be shown again.
        shown.clear();
        processor.process(payload);
        assertTrue(shown.isEmpty());
    }

    @Test
    void emergencyNotReshownInSameSession() {
        final NotificationsList payload = list(notif("emerg1", NotificationScheduleType.EMERGENCY));
        processor.process(payload);
        assertEquals(List.of("emerg1"), shown);

        // Same undismissed emergency on the next poll is suppressed by the in-session guard.
        shown.clear();
        processor.process(payload);
        assertTrue(shown.isEmpty());
    }

    @Test
    void newEmergencyStillShownAfterAPriorOne() {
        processor.process(list(notif("emerg1", NotificationScheduleType.EMERGENCY)));
        shown.clear();
        processor.process(list(notif("emerg2", NotificationScheduleType.EMERGENCY)));
        assertEquals(List.of("emerg2"), shown);
    }

    @Test
    void dismissedNotificationNeverShown() {
        when(dismissalStore.isDismissed("emerg1")).thenReturn(true);
        processor.process(list(notif("emerg1", NotificationScheduleType.EMERGENCY)));
        assertTrue(shown.isEmpty());
    }

    @Test
    void ruleFilteredNotificationNotShown() {
        final NotificationData gated = new NotificationData("gated",
                new NotificationSchedule(NotificationScheduleType.EMERGENCY), "Info",
                new NotificationData.NotificationDisplayCondition(null,
                        new NotificationData.SystemType(new NotificationExpression.ComparisonCondition("NoSuchOS"), null),
                        null, null, null),
                new NotificationContent(new LocalizedContent("t", "d")), null);
        processor.process(list(gated));
        assertTrue(shown.isEmpty());
    }

    @Test
    void blankContentSkipped() {
        final NotificationData blank = new NotificationData("blank",
                new NotificationSchedule(NotificationScheduleType.EMERGENCY), "Info", null,
                new NotificationContent(new LocalizedContent("", "")), null);
        processor.process(list(blank));
        assertTrue(shown.isEmpty());
    }

    @Test
    void emptyListIsNoOp() {
        processor.process(new NotificationsList(new NotificationsList.Schema("2.0"), List.of()));
        assertTrue(shown.isEmpty());
    }

    @Test
    void startupFilteredOnFirstPollStillShowsOnceItQualifies() {
        // Poll 1: the STARTUP notification is dismissed, so it is filtered out.
        when(dismissalStore.isDismissed("startup1")).thenReturn(true);
        final NotificationsList payload = list(notif("startup1", NotificationScheduleType.STARTUP));
        processor.process(payload);
        assertTrue(shown.isEmpty());

        // Poll 2 (same session): it is no longer dismissed. The startup window must still be open, because it was
        // consumed by an actual display, not merely by the first poll running.
        shown.clear();
        when(dismissalStore.isDismissed("startup1")).thenReturn(false);
        processor.process(payload);
        assertEquals(List.of("startup1"), shown);
    }

    @Test
    void startupWindowConsumedOnlyAfterActualDisplay() {
        final NotificationsList payload = list(notif("startup1", NotificationScheduleType.STARTUP));
        processor.process(payload);
        assertEquals(List.of("startup1"), shown);

        // A DIFFERENT startup notification appearing later in the same session must NOT show: the window closed
        // once startup1 rendered.
        shown.clear();
        processor.process(list(notif("startup2", NotificationScheduleType.STARTUP)));
        assertTrue(shown.isEmpty());
    }

    @Test
    void renderFailureIsRetriedOnNextPoll() {
        final ProcessNotifications failing = processorWithFailingDisplay();
        final NotificationsList payload = list(notif("emerg1", NotificationScheduleType.EMERGENCY));
        failing.process(payload);
        assertEquals(List.of("emerg1"), shown);

        // Because rendering failed (completion=false), the id was un-marked, so the next poll retries it.
        shown.clear();
        failing.process(payload);
        assertEquals(List.of("emerg1"), shown);
    }

    @Test
    void nullElementInBatchIsSkipped() {
        final List<NotificationData> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(notif("emerg1", NotificationScheduleType.EMERGENCY));
        processor.process(new NotificationsList(new NotificationsList.Schema("2.0"), withNull));
        assertEquals(List.of("emerg1"), shown);
    }
}
