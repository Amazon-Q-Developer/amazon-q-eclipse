// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.toolkittelemetry.model.MetadataEntry;
import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum;
import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.telemetry.service.TelemetryService;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;

public final class ToolkitTelemetryProviderTest {

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorStaticMockExtension = new ActivatorStaticMockExtension();

    @Test
    void emitDidLoadModuleEventMetricReportsLoginModuleLoaded() {
        ToolkitTelemetryProvider.emitDidLoadModuleEventMetric(ToolkitTelemetryProvider.LOGIN_MODULE, Result.SUCCEEDED, null);

        MetricDatum metricDatum = captureMetricDatum();
        assertEquals("toolkit_didLoadModule", metricDatum.metricName());
        assertEquals(Optional.of("login"), getMetadataValue(metricDatum, "module"));
        assertEquals(Optional.of("Succeeded"), getMetadataValue(metricDatum, "result"));
        assertTrue(metricDatum.passive());
    }

    @Test
    void emitDidLoadModuleEventMetricReportsReasonCodeOnFailure() {
        ToolkitTelemetryProvider.emitDidLoadModuleEventMetric(ToolkitTelemetryProvider.LOGIN_MODULE, Result.FAILED,
                "DependencyMissing");

        MetricDatum metricDatum = captureMetricDatum();
        assertEquals(Optional.of("login"), getMetadataValue(metricDatum, "module"));
        assertEquals(Optional.of("Failed"), getMetadataValue(metricDatum, "result"));
        assertEquals(Optional.of("DependencyMissing"), getMetadataValue(metricDatum, "reason"));
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
