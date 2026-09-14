// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import software.aws.toolkits.eclipse.amazonq.notifications.AmazonQNotificationPopup.NotificationAction;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationFollowupAction;
import software.aws.toolkits.eclipse.amazonq.util.Constants;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;

/**
 * Builds the rendered action buttons for a notification from its hosted {@code actions[]}, ports the JetBrains mapping:
 * {@code ShowUrl} (case-sensitive) only supplies the URL for the always-present "More" button; {@code UpdateExtension}
 * and {@code OpenChangelog} open pages; unknown types are ignored.
 */
public final class NotificationActionFactory {

    private static final String SHOW_URL = "ShowUrl";
    private static final String UPDATE_EXTENSION = "UpdateExtension";
    private static final String OPEN_CHANGELOG = "OpenChangelog";

    private NotificationActionFactory() {
        // prevent instantiation
    }

    public static List<NotificationAction> createActions(final String notificationId,
            final List<NotificationFollowupAction> followupActions, final String title, final String description) {
        final List<NotificationAction> result = new ArrayList<>();
        String moreUrl = null;

        if (followupActions != null) {
            for (final NotificationFollowupAction action : followupActions) {
                final String type = action.type();
                if (SHOW_URL.equals(type)) {
                    if (action.content() != null && action.content().enUs() != null) {
                        moreUrl = action.content().enUs().url();
                    }
                } else if (UPDATE_EXTENSION.equals(type)) {
                    result.add(new NotificationAction("Update", () -> {
                        NotificationTelemetryProvider.emitInvokeAction(notificationId, UPDATE_EXTENSION);
                        PluginUtils.openWebpage(Constants.AMAZON_Q_UPDATE_SITE_URL);
                    }));
                } else if (OPEN_CHANGELOG.equals(type)) {
                    result.add(new NotificationAction("View changelog", () -> {
                        NotificationTelemetryProvider.emitInvokeAction(notificationId, OPEN_CHANGELOG);
                        PluginUtils.openWebpage(Constants.AMAZON_Q_CHANGELOG_URL);
                    }));
                }
            }
        }

        final String capturedUrl = moreUrl;
        result.add(new NotificationAction("More", () -> {
            NotificationTelemetryProvider.emitInvokeAction(notificationId, "More");
            showMoreDialog(title, description, capturedUrl);
        }));
        return result;
    }

    private static void showMoreDialog(final String title, final String description, final String url) {
        if (url != null && !url.isBlank()) {
            PluginUtils.handleExternalLinkClick(url);
        } else {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), title, description);
        }
    }
}
