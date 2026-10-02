// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.telemetry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.toolkittelemetry.model.MetadataEntry;
import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.telemetry.AwsTelemetry;
import software.aws.toolkits.telemetry.TelemetryDefinitions.CredentialType;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;

public final class AwsTelemetryProvider {

    private static final String SESSION_DURATION_KEY = "sessionDuration";
    private static final String AUTH_VIEW_SOURCE = "authView";
    private static final String RE_AUTH_SOURCE = "reAuth";

    private AwsTelemetryProvider() {
        //prevent instantiation
    }

    public static void emitModifySettingEvent(final String settingId, final String settingState) {
        MetricDatum metricDatum = AwsTelemetry.ModifySettingEvent()
                .settingId(settingId)
                .settingState(settingState)
                .passive(false)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(metricDatum);
    }

    public static void emitLoginWithBrowserEvent(final BrowserLoginParams params) {
        MetricDatum metricDatum = AwsTelemetry.LoginWithBrowserEvent()
                .credentialStartUrl(params.credentialStartUrl())
                .credentialType(params.credentialType())
                .isReAuth(params.isReAuth())
                .result(params.result())
                .reason(params.reason())
                .source(params.isReAuth() ? RE_AUTH_SOURCE : AUTH_VIEW_SOURCE)
                .passive(false)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(withSessionDuration(metricDatum, params.sessionDuration()));
    }

    /**
     * Rewrites the session duration metadata entry of the given metric.
     *
     * The generated builder types session duration as a primitive int, which serializes to zero when
     * it is not set and cannot hold durations longer than roughly twenty five days in milliseconds.
     * Authentication sessions are expected to live for months, and a first login has no previous
     * session at all, so the entry is written with the exact millisecond value when a previous session
     * is known and removed when it is not.
     *
     * @param metricDatum the metric built by the generated builder
     * @param sessionDuration milliseconds since the previous successful login, or null when unknown
     * @return the metric carrying an accurate session duration, or none at all
     */
    private static MetricDatum withSessionDuration(final MetricDatum metricDatum, final Long sessionDuration) {
        List<MetadataEntry> metadata = new ArrayList<>();
        for (MetadataEntry entry : metricDatum.metadata()) {
            if (!SESSION_DURATION_KEY.equals(entry.key())) {
                metadata.add(entry);
            }
        }
        if (sessionDuration != null) {
            metadata.add(MetadataEntry.builder()
                    .key(SESSION_DURATION_KEY)
                    .value(String.valueOf(sessionDuration))
                    .build());
        }
        return metricDatum.toBuilder().metadata(metadata).build();
    }

    /**
     * Parameters of the browser login metric.
     *
     * @param credentialStartUrl the start url the login was performed against
     * @param credentialType the type of credentials the login produces
     * @param isReAuth whether the login renewed an existing connection
     * @param result whether the login succeeded
     * @param reason a short reason code when the login failed, null otherwise
     * @param sessionDuration milliseconds since the previous successful login, null when unknown
     */
    public record BrowserLoginParams(String credentialStartUrl, CredentialType credentialType, boolean isReAuth,
            Result result, String reason, Long sessionDuration) { };

}
