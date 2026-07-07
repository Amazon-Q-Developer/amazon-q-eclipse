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
        processor = new ProcessNotifications(dismissalStore, (id, n, c, actions) -> shown.add(id));
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
}
