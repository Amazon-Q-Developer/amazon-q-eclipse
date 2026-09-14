// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.preferences.AmazonQPreferencePage;

/** Reads the notifications kill-switch preference and resolves the endpoint (preference &gt; env &gt; prod default). */
public final class NotificationPreferences {

    private NotificationPreferences() {
        // prevent instantiation
    }

    /** Whether the notifications feature is enabled (kill-switch); defaults to {@code true}. */
    public static boolean isNotificationsEnabled() {
        return Activator.getDefault().getPreferenceStore().getBoolean(AmazonQPreferencePage.NOTIFICATIONS_OPT_IN);
    }

    /** Resolves the endpoint URL. Precedence: preference override -&gt; environment variable -&gt; production default. */
    public static String resolveEndpoint() {
        final String pref = Activator.getDefault().getPreferenceStore()
                .getString(AmazonQPreferencePage.NOTIFICATIONS_ENDPOINT_OVERRIDE);
        if (pref != null && !pref.isBlank()) {
            return pref;
        }
        final String env = System.getenv(NotificationConstants.NOTIFICATIONS_ENDPOINT_ENV);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return NotificationConstants.NOTIFICATIONS_ENDPOINT;
    }

    /**
     * Whether an explicit endpoint override (preference or environment variable) is set. Used to let a
     * development/PDE build opt in to polling a test endpoint, which is otherwise suppressed on dev builds.
     */
    public static boolean hasEndpointOverride() {
        final String pref = Activator.getDefault().getPreferenceStore()
                .getString(AmazonQPreferencePage.NOTIFICATIONS_ENDPOINT_OVERRIDE);
        if (pref != null && !pref.isBlank()) {
            return true;
        }
        final String env = System.getenv(NotificationConstants.NOTIFICATIONS_ENDPOINT_ENV);
        return env != null && !env.isBlank();
    }
}
