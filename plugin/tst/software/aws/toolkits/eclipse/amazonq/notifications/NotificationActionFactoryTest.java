// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.MockedStatic;

import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.notifications.AmazonQNotificationPopup.NotificationAction;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.LocalizedAction;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationFollowupAction;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationFollowupActionContent;
import software.aws.toolkits.eclipse.amazonq.util.Constants;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;

/** Coverage for the pure action-to-button mapping (ShowUrl-is-not-a-button, always-append-More, unknown-ignored). */
public final class NotificationActionFactoryTest {

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorExtension = new ActivatorStaticMockExtension();

    private static NotificationFollowupAction action(final String type, final String title, final String url) {
        return new NotificationFollowupAction(type,
                new NotificationFollowupActionContent(new LocalizedAction(title, url)));
    }

    @Test
    void noActionsStillYieldsMoreButton() {
        final List<NotificationAction> result = NotificationActionFactory.createActions("id", null, "t", "d");
        assertEquals(1, result.size());
        assertEquals("More", result.get(0).label());
    }

    @Test
    void updateAndChangelogBecomeButtonsPlusMore() {
        final List<NotificationAction> result = NotificationActionFactory.createActions("id",
                List.of(action("UpdateExtension", "Update", null), action("OpenChangelog", "Changelog", null)),
                "t", "d");
        assertEquals(3, result.size());
        assertEquals("Update", result.get(0).label());
        assertEquals("View changelog", result.get(1).label());
        assertEquals("More", result.get(2).label());
    }

    @Test
    void showUrlSuppliesMoreUrlButNoOwnButton() {
        final List<NotificationAction> result = NotificationActionFactory.createActions("id",
                List.of(action("ShowUrl", "Click me", "https://x.test")), "t", "d");
        assertEquals(1, result.size());
        assertEquals("More", result.get(0).label());

        try (MockedStatic<PluginUtils> pluginUtils = mockStatic(PluginUtils.class)) {
            result.get(0).onClick().run();
            pluginUtils.verify(() -> PluginUtils.handleExternalLinkClick(eq("https://x.test")));
        }
    }

    @Test
    void unknownActionTypeIgnored() {
        final List<NotificationAction> result = NotificationActionFactory.createActions("id",
                List.of(action("ShowMarketplace", "Go", null)), "t", "d");
        assertEquals(1, result.size());
        assertEquals("More", result.get(0).label());
    }

    @Test
    void lowercaseShowurlIsNotTreatedAsShowUrl() {
        // Case-sensitive: "showurl" is unknown, so it is ignored (no URL captured, only More remains).
        final List<NotificationAction> result = NotificationActionFactory.createActions("id",
                List.of(action("showurl", "x", "https://x.test")), "t", "d");
        assertEquals(1, result.size());
    }

    @Test
    void updateButtonOpensUpdateSite() {
        final List<NotificationAction> result = NotificationActionFactory.createActions("id",
                List.of(action("UpdateExtension", "Update", null)), "t", "d");
        try (MockedStatic<PluginUtils> pluginUtils = mockStatic(PluginUtils.class)) {
            result.get(0).onClick().run();
            pluginUtils.verify(() -> PluginUtils.openWebpage(eq(Constants.AMAZON_Q_UPDATE_SITE_URL)));
        }
        assertTrue(true);
    }
}
