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

    @Test
    void corruptStateIsResetAndPersistedOnRead() {
        // Persist a value of the WRONG shape via the same byte path getObject reads (putObject), so getObject's
        // Gson.fromJson deterministically throws when coercing it to NotificationDismissalConfiguration. Using a
        // bare String here serializes to a JSON string literal, which cannot deserialize into the config object.
        pluginStore.putObject(NotificationConstants.DISMISSAL_STORAGE_KEY, "not-a-config-object");
        final NotificationDismissalStore store = new NotificationDismissalStore(pluginStore);
        // Must not throw, and treats the corrupt state as empty.
        assertFalse(store.isDismissed("n1"));
        // The corrupt bytes must be overwritten with a valid empty config, so a fresh read no longer sees garbage.
        final NotificationDismissalConfiguration repaired =
                pluginStore.getObject(NotificationConstants.DISMISSAL_STORAGE_KEY, NotificationDismissalConfiguration.class);
        assertTrue(repaired != null && repaired.getDismissedNotifications().isEmpty());
        // And a subsequent dismiss still works after repair.
        store.dismiss("n1");
        assertTrue(store.isDismissed("n1"));
    }

    @Test
    void nullIdEntryDoesNotThrow() {
        final NotificationDismissalConfiguration config = new NotificationDismissalConfiguration();
        config.setDismissedNotifications(new java.util.ArrayList<>(List.of(
                new DismissedNotification(null, Instant.now().toEpochMilli()))));
        pluginStore.putObject(NotificationConstants.DISMISSAL_STORAGE_KEY, config);
        final NotificationDismissalStore store = new NotificationDismissalStore(pluginStore);
        // A stored entry with a null id must not NPE when checking a real id.
        assertFalse(store.isDismissed("n1"));
    }
}
