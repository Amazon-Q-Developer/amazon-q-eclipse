// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

/**
 * Persists dismissed-notification ids (with a 60-day retention) via {@link PluginStore}. All mutations funnel through
 * synchronized methods so a load-modify-write (which spans two PluginStore calls) cannot lose entries under concurrency.
 */
public final class NotificationDismissalStore {

    private static final Duration RETENTION = Duration.ofDays(60);

    private final PluginStore pluginStore;

    public NotificationDismissalStore() {
        this(Activator.getPluginStore());
    }

    public NotificationDismissalStore(final PluginStore pluginStore) {
        this.pluginStore = pluginStore;
    }

    public synchronized boolean isDismissed(final String id) {
        return loadAndClean().getDismissedNotifications().stream().anyMatch(d -> d.getId().equals(id));
    }

    public synchronized void dismiss(final String id) {
        final NotificationDismissalConfiguration config = loadAndClean();
        final List<DismissedNotification> dismissed = config.getDismissedNotifications();
        if (dismissed.stream().anyMatch(d -> d.getId().equals(id))) {
            return;
        }
        dismissed.add(new DismissedNotification(id, Instant.now().toEpochMilli()));
        pluginStore.putObject(NotificationConstants.DISMISSAL_STORAGE_KEY, config);
    }

    private NotificationDismissalConfiguration loadAndClean() {
        NotificationDismissalConfiguration config;
        try {
            config = pluginStore.getObject(NotificationConstants.DISMISSAL_STORAGE_KEY,
                    NotificationDismissalConfiguration.class);
        } catch (Exception e) {
            Activator.getLogger().warn("Corrupt notification dismissal state; resetting", e);
            config = null;
        }
        if (config == null || config.getDismissedNotifications() == null) {
            return new NotificationDismissalConfiguration();
        }
        final Instant cutoff = Instant.now().minus(RETENTION);
        final boolean removedAny = config.getDismissedNotifications()
                .removeIf(d -> Instant.ofEpochMilli(d.getDismissedAtEpochMs()).isBefore(cutoff));
        if (removedAny) {
            pluginStore.putObject(NotificationConstants.DISMISSAL_STORAGE_KEY, config);
        }
        return config;
    }
}
