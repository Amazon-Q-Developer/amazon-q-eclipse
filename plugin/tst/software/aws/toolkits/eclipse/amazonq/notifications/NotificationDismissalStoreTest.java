// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import software.aws.toolkits.eclipse.amazonq.configuration.DefaultPluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;

/** Round-trip + retention + concurrency-safety coverage for the dismissal store, using a real Gson-backed PluginStore. */
public final class NotificationDismissalStoreTest {

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorExtension = new ActivatorStaticMockExtension();

    private PluginStore pluginStore;

    @BeforeEach
    void setUp() {
        pluginStore = new DefaultPluginStore(new EclipsePreferences());
    }

    @Test
    void dismissThenIsDismissedRoundTrips() {
        final NotificationDismissalStore store = new NotificationDismissalStore(pluginStore);
        assertFalse(store.isDismissed("n1"));
        store.dismiss("n1");
        assertTrue(store.isDismissed("n1"));
        // A fresh store reading the same PluginStore sees the persisted dismissal.
        assertTrue(new NotificationDismissalStore(pluginStore).isDismissed("n1"));
    }

    @Test
    void dismissIsIdempotent() {
        final NotificationDismissalStore store = new NotificationDismissalStore(pluginStore);
        store.dismiss("n1");
        store.dismiss("n1");
        final NotificationDismissalConfiguration config =
                pluginStore.getObject(NotificationConstants.DISMISSAL_STORAGE_KEY, NotificationDismissalConfiguration.class);
        assertTrue(config.getDismissedNotifications().size() == 1);
    }

    @Test
    void expiredDismissalsAreCleanedOnRead() {
        final NotificationDismissalConfiguration config = new NotificationDismissalConfiguration();
        final long old = Instant.now().minus(Duration.ofDays(61)).toEpochMilli();
        config.setDismissedNotifications(new java.util.ArrayList<>(List.of(new DismissedNotification("stale", old))));
        pluginStore.putObject(NotificationConstants.DISMISSAL_STORAGE_KEY, config);

        final NotificationDismissalStore store = new NotificationDismissalStore(pluginStore);
        assertFalse(store.isDismissed("stale"));
    }

    @Test
    void recentDismissalsSurviveCleanup() {
        final NotificationDismissalConfiguration config = new NotificationDismissalConfiguration();
        final long recent = Instant.now().minus(Duration.ofDays(5)).toEpochMilli();
        config.setDismissedNotifications(new java.util.ArrayList<>(List.of(new DismissedNotification("fresh", recent))));
        pluginStore.putObject(NotificationConstants.DISMISSAL_STORAGE_KEY, config);

        assertTrue(new NotificationDismissalStore(pluginStore).isDismissed("fresh"));
    }

    @Test
    void noStateReturnsNotDismissed() {
        assertFalse(new NotificationDismissalStore(pluginStore).isDismissed("anything"));
    }
}
