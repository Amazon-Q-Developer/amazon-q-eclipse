// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete wrapper persisted via {@code PluginStore.putObject}/{@code getObject}. A concrete class (rather than a raw
 * generic collection) is required because {@code getObject(key, Class)} deserializes with Gson reflecting into the
 * declared field type, which correctly recovers the {@link DismissedNotification} element type.
 */
public final class NotificationDismissalConfiguration {

    private List<DismissedNotification> dismissedNotifications = new ArrayList<>();

    public NotificationDismissalConfiguration() {
        // no-arg constructor for Gson
    }

    public List<DismissedNotification> getDismissedNotifications() {
        return dismissedNotifications;
    }

    public void setDismissedNotifications(final List<DismissedNotification> dismissedNotifications) {
        this.dismissedNotifications = dismissedNotifications;
    }
}
