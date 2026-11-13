// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.toolkittelemetry.model.MetadataEntry;
import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum;
import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.telemetry.AwsTelemetryProvider.BrowserLoginParams;
import software.aws.toolkits.eclipse.amazonq.telemetry.service.TelemetryService;
import software.aws.toolkits.eclipse.amazonq.util.Constants;
import software.aws.toolkits.telemetry.TelemetryDefinitions.CredentialType;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;

public final class AwsTelemetryProviderTest {

    private static final String SESSION_DURATION_KEY = "sessionDuration";

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorStaticMockExtension = new ActivatorStaticMockExtension();

    @Test
    void emitLoginWithBrowserEventReportsSessionDurationWhenKnown() {
        AwsTelemetryProvider.emitLoginWithBrowserEvent(new BrowserLoginParams(Constants.AWS_BUILDER_ID_URL,
                CredentialType.BEARER_TOKEN, false, Result.SUCCEEDED, null, 7776000000L));

        MetricDatum metricDatum = captureMetricDatum();
        assertEquals("aws_loginWithBrowser", metricDatum.metricName());
        assertEquals(Optional.of("7776000000"), getMetadataValue(metricDatum, SESSION_DURATION_KEY));
        assertEquals(Optional.of(Constants.AWS_BUILDER_ID_URL), getMetadataValue(metricDatum, "credentialStartUrl"));
        assertEquals(Optional.of("bearerToken"), getMetadataValue(metricDatum, "credentialType"));
        assertEquals(Optional.of("Succeeded"), getMetadataValue(metricDatum, "result"));
        assertEquals(Optional.of("false"), getMetadataValue(metricDatum, "isReAuth"));
        assertFalse(metricDatum.passive());
    }

    @Test
    void emitLoginWithBrowserEventLeavesOutSessionDurationWhenUnknown() {
        AwsTelemetryProvider.emitLoginWithBrowserEvent(new BrowserLoginParams(Constants.AWS_BUILDER_ID_URL,
                CredentialType.BEARER_TOKEN, false, Result.SUCCEEDED, null, null));

        MetricDatum metricDatum = captureMetricDatum();
        assertTrue(getMetadataValue(metricDatum, SESSION_DURATION_KEY).isEmpty(),
                "session duration should not be reported when no previous login is known");
        assertEquals(Optional.of(Constants.AWS_BUILDER_ID_URL), getMetadataValue(metricDatum, "credentialStartUrl"));
    }

    @Test
    void emitLoginWithBrowserEventReportsReasonCodeOnFailure() {
        AwsTelemetryProvider.emitLoginWithBrowserEvent(new BrowserLoginParams(Constants.AWS_BUILDER_ID_URL,
                CredentialType.BEARER_TOKEN, true, Result.FAILED, "IllegalStateException", null));

        MetricDatum metricDatum = captureMetricDatum();
        assertEquals(Optional.of("Failed"), getMetadataValue(metricDatum, "result"));
        assertEquals(Optional.of("IllegalStateException"), getMetadataValue(metricDatum, "reason"));
        assertEquals(Optional.of("true"), getMetadataValue(metricDatum, "isReAuth"));
        assertTrue(getMetadataValue(metricDatum, SESSION_DURATION_KEY).isEmpty(),
                "session duration should not be reported for a failed login");
    }

    private MetricDatum captureMetricDatum() {
        TelemetryService telemetryService = activatorStaticMockExtension.getMock(TelemetryService.class);
        ArgumentCaptor<MetricDatum> captor = ArgumentCaptor.forClass(MetricDatum.class);
        verify(telemetryService).emitMetric(captor.capture());
        return captor.getValue();
    }

    private Optional<String> getMetadataValue(final MetricDatum metricDatum, final String key) {
        return metricDatum.metadata().stream()
                .filter(entry -> key.equals(entry.key()))
                .map(MetadataEntry::value)
                .findFirst();
    }

}
