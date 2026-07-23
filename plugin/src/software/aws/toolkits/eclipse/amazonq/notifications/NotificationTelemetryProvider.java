// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.time.Instant;

import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Component;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;
import software.aws.toolkits.telemetry.ToolkitTelemetry;

/**
 * Emits notification telemetry ({@code toolkit_showNotification} / {@code toolkit_invokeAction}). Emission routes
 * through {@code DefaultTelemetryService.emitMetric}, which respects the telemetry opt-in independently of the
 * notifications feature. The metric {@code id} is the raw notification id (no {@code TARGETED_NOTIFICATION:} prefix).
 */
public final class NotificationTelemetryProvider {

    private NotificationTelemetryProvider() {
        // prevent instantiation
    }

    /** A notification was shown to the user. */
    public static void emitShowNotification(final String notificationId) {
        final MetricDatum datum = ToolkitTelemetry.ShowNotificationEvent()
                .id(notificationId)
                .component(Component.INFOBAR)
                .result(Result.SUCCEEDED)
                .passive(true)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(datum);
    }

    /** A poll cycle failed to retrieve notifications. */
    public static void emitPollFailure(final String reason) {
        final MetricDatum datum = ToolkitTelemetry.ShowNotificationEvent()
                .id("")
                .component(Component.FILESYSTEM)
                .result(Result.FAILED)
                .reason(reason)
                .passive(true)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(datum);
    }

    /** The user clicked an action button on a notification. */
    public static void emitInvokeAction(final String notificationId, final String actionType) {
        final MetricDatum datum = ToolkitTelemetry.InvokeActionEvent()
                .id(notificationId)
                .source(notificationId)
                .action(actionType)
                .component(Component.INFOBAR)
                .result(Result.SUCCEEDED)
                .passive(false)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(datum);
    }
}
