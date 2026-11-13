// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.telemetry;

import java.time.Instant;

import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.telemetry.AuthTelemetry;
import software.aws.toolkits.telemetry.TelemetryDefinitions.AuthStatus;

public final class AuthTelemetryProvider {

    private static final String STARTUP_SOURCE = "startup";

    private AuthTelemetryProvider() {
        //prevent instantiation
    }

    /**
     * Reports the authentication state observed when the plugin starts.
     *
     * @param authStatus the authentication state the plugin started in
     * @param credentialStartUrl the start url of the restored connection, null when there is none
     */
    public static void emitUserStateOnStartupMetric(final AuthStatus authStatus, final String credentialStartUrl) {
        MetricDatum metricDatum = AuthTelemetry.UserStateEvent()
                .authStatus(authStatus)
                .credentialStartUrl(credentialStartUrl)
                .source(STARTUP_SOURCE)
                .passive(true)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(metricDatum);
    }

}
