// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

/** Endpoint, cache, and storage-key constants for the hosted-file notifications feature. */
public final class NotificationConstants {

    /** Production hosted-file endpoint for Eclipse notifications (schema 2.x combined). */
    public static final String NOTIFICATIONS_ENDPOINT =
            "https://idetoolkits-hostedfiles.amazonaws.com/Notifications/Eclipse/combined/2.x.json";

    /** Subdirectory (under the plugin state dir) that holds the cached notifications file. */
    public static final String NOTIFICATIONS_SUBDIRECTORY = "notifications";

    /** Filename of the cached notifications payload. */
    public static final String NOTIFICATIONS_CACHE_FILENAME = "notifications.json";

    /** PluginStore key under which dismissed-notification state is persisted. */
    public static final String DISMISSAL_STORAGE_KEY = "qNotificationDismissals";

    /** Environment variable that overrides the endpoint (for local dev/testing). */
    public static final String NOTIFICATIONS_ENDPOINT_ENV = "AMAZONQ_NOTIFICATIONS_ENDPOINT";

    private NotificationConstants() {
        // prevent instantiation
    }
}
