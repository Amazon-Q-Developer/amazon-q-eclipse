// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.ISharedImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationSeverity;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.preferences.AmazonQPreferencePage;

/** Coverage for the icon mapping and the preference/endpoint resolver. */
public final class NotificationMiscTest {

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorExtension = new ActivatorStaticMockExtension();

    @Test
    void iconKeyMapsSeverity() {
        assertEquals(ISharedImages.IMG_OBJS_ERROR_TSK, AmazonQNotificationPopup.iconKey(NotificationSeverity.CRITICAL));
        assertEquals(ISharedImages.IMG_OBJS_WARN_TSK, AmazonQNotificationPopup.iconKey(NotificationSeverity.WARNING));
        assertEquals(ISharedImages.IMG_OBJS_INFO_TSK, AmazonQNotificationPopup.iconKey(NotificationSeverity.INFO));
    }

    @Test
    void notificationsEnabledReadsPreference() {
        final IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        when(store.getBoolean(eq(AmazonQPreferencePage.NOTIFICATIONS_OPT_IN))).thenReturn(true);
        assertTrue(NotificationPreferences.isNotificationsEnabled());

        when(store.getBoolean(eq(AmazonQPreferencePage.NOTIFICATIONS_OPT_IN))).thenReturn(false);
        assertFalse(NotificationPreferences.isNotificationsEnabled());
    }

    @Test
    void resolveEndpointPrefersOverrideThenDefault() {
        final IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        when(store.getString(eq(AmazonQPreferencePage.NOTIFICATIONS_ENDPOINT_OVERRIDE))).thenReturn("https://override.test/x.json");
        assertEquals("https://override.test/x.json", NotificationPreferences.resolveEndpoint());

        when(store.getString(eq(AmazonQPreferencePage.NOTIFICATIONS_ENDPOINT_OVERRIDE))).thenReturn("");
        // With no override and (in a test JVM) no env var, falls through to the prod default.
        assertEquals(NotificationConstants.NOTIFICATIONS_ENDPOINT, NotificationPreferences.resolveEndpoint());
    }
}
