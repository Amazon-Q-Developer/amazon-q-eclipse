// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.telemetry;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.telemetry.AuthTelemetry;
import software.aws.toolkits.telemetry.TelemetryDefinitions;

public final class AuthAddConnectionTelemetryProvider {

    private static AuthTelemetry.AddConnectionEventBuilder addConnectionBuilder;
    private static CompletableFuture<Void> timeoutFuture;
    private static long timeoutDuration = 2;
    private static TimeUnit timeoutUnit = TimeUnit.MINUTES;
    private static final AtomicBoolean IS_AUTHENTICATION_SESSION_ACTIVE = new AtomicBoolean(false);
    private static int attemptsCount;

    public enum InputField {

        AUTH_ENABLED_FEATURES("AuthEnabledFeatures"), AUTH_SCOPES("AuthScopes"),
        CREDENTIAL_SOURCE_ID("CredentialSourceId"), CREDENTIAL_START_URL("CredentialStartUrl"), FEATURE_ID("FeatureId"),
        INVALID_INPUT_FIELDS("InvalidInputFields"), PASSIVE("Passive"), SOURCE("Source"),
        SSO_REGISTRATION_CLIENT_ID("SsoRegistrationClientId"), RESULT("Result"), IS_AGGREGATED("IsAggregated"),
        IS_REAUTH("IsReAuth"), VALUE("Value");

        private final String fieldId;

        InputField(final String fieldId) {
            this.fieldId = fieldId;
        }

        public String getFieldId() {
            return fieldId;
        }

        public static InputField from(final String fieldId) {
            for (InputField field : values()) {
                if (field.getFieldId().equals(fieldId)) {
                    return field;
                }
            }
            throw new IllegalArgumentException("Unknown field ID: " + fieldId);
        }

    }

    private AuthAddConnectionTelemetryProvider() {
        // prevent initialization
    }

    public static void cancel() {
        updateField(InputField.RESULT, "Cancelled");
        IS_AUTHENTICATION_SESSION_ACTIVE.set(false);
    }

    public static void resetFields() {
        addConnectionBuilder = AuthTelemetry.AddConnectionEvent();
        attemptsCount = 0;
        IS_AUTHENTICATION_SESSION_ACTIVE.set(true);
    }

    public static void setTimeout(final long duration, final TimeUnit unit) {
        timeoutDuration = duration;
        timeoutUnit = unit;
    }

    private static void resetTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }

        timeoutFuture = new CompletableFuture<Void>().orTimeout(timeoutDuration, timeoutUnit)
                .whenComplete((result, exception) -> {
                    if (exception instanceof TimeoutException) {
                        if (IS_AUTHENTICATION_SESSION_ACTIVE.get()) {
                            updateField(InputField.RESULT, "Incomplete");
                        } else {
                            updateField(InputField.RESULT, "Cancelled");
                        }

                        emitAddConnectionEvent(completed(false));
                    }
                });
    }

    private static void updateSessionTimeout() {
        if (IS_AUTHENTICATION_SESSION_ACTIVE.get()) {
            resetTimeout(); // Reset the timeout on each update
        } else {
            resetFields(); // Start a new session if updating after completion
        }
    }

    public static void updateField(final InputField inputField, final String value) {
        updateSessionTimeout();

        switch (inputField) {
        case AUTH_ENABLED_FEATURES:
            addConnectionBuilder.authEnabledFeatures(value);
            break;
        case AUTH_SCOPES:
            addConnectionBuilder.authScopes(value);
            break;
        case CREDENTIAL_SOURCE_ID:
            addConnectionBuilder.credentialSourceId(TelemetryDefinitions.CredentialSourceId.from(value));
            break;
        case CREDENTIAL_START_URL:
            addConnectionBuilder.credentialStartUrl(value);
            break;
        case FEATURE_ID:
            addConnectionBuilder.featureId(TelemetryDefinitions.FeatureId.from(value));
            break;
        case INVALID_INPUT_FIELDS:
            addConnectionBuilder.invalidInputFields(value);
            break;
        case PASSIVE:
            addConnectionBuilder.passive(Boolean.parseBoolean(value));
            break;
        case SOURCE:
            addConnectionBuilder.source(value);
            break;
        case SSO_REGISTRATION_CLIENT_ID:
            addConnectionBuilder.ssoRegistrationClientId(value);
            break;
        case RESULT:
            addConnectionBuilder.result(TelemetryDefinitions.Result.from(value));
            break;
        case IS_AGGREGATED:
            addConnectionBuilder.isAggregated(Boolean.parseBoolean(value));
            break;
        case IS_REAUTH:
            addConnectionBuilder.isReAuth(Boolean.parseBoolean(value));
            break;
        case VALUE:
            addConnectionBuilder.value(Double.parseDouble(value));
            break;
        default:
            throw new IllegalArgumentException("The input field does not exist");
        }
    }

    public static MetricDatum completed(final Boolean success) {
        if (!timeoutFuture.isDone()) {
            timeoutFuture.complete(null);
            addConnectionBuilder.attempts(++attemptsCount);
        }

        IS_AUTHENTICATION_SESSION_ACTIVE.set(!success);
        updateSessionTimeout();

        return addConnectionBuilder.createTime(Instant.now()).build();
    }

    public static void emitAddConnectionEvent(final MetricDatum metricDatum) {
        Activator.getTelemetryService().emitMetric(metricDatum);
    }

}
