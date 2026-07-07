// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single hosted notification (schema 2.x). Ported from the JetBrains notification model so payloads
 * stay compatible across IDEs. Unknown JSON keys are ignored (the shared mapper disables
 * FAIL_ON_UNKNOWN_PROPERTIES), so a missing optional block deserializes to {@code null}.
 */
public record NotificationData(
        String id,
        NotificationSchedule schedule,
        String severity,
        NotificationDisplayCondition condition,
        NotificationContent content,
        List<NotificationFollowupAction> actions) {

    /** How often the notification is shown. */
    public enum NotificationScheduleType {
        /** Shown once per IDE session (on the first poll). */
        STARTUP,
        /** Shown on every poll until dismissed. */
        EMERGENCY;

        /**
         * Maps the raw JSON value case-insensitively: only {@code "startup"} yields
         * {@link #STARTUP}; anything else (including typos and {@code null}) yields {@link #EMERGENCY}.
         */
        @JsonCreator
        public static NotificationScheduleType fromString(final String value) {
            return value != null && "startup".equals(value.toLowerCase(Locale.ROOT))
                    ? STARTUP
                    : EMERGENCY;
        }
    }

    /** Notification severity; drives the toast style. */
    public enum NotificationSeverity {
        INFO,
        WARNING,
        CRITICAL;

        /** Maps the exact-case JSON value; any unrecognized or {@code null} value yields {@link #INFO}. */
        public static NotificationSeverity fromString(final String value) {
            if ("Critical".equals(value)) {
                return CRITICAL;
            }
            if ("Warning".equals(value)) {
                return WARNING;
            }
            return INFO;
        }
    }

    /** Wrapper around the schedule type as it appears in JSON: {@code { "type": "Startup" }}. */
    public record NotificationSchedule(NotificationScheduleType type) { }

    /**
     * Display conditions. All present blocks must match (logical AND); a {@code null} block is skipped.
     * Note {@code extension} is a singular-named array, matching the JetBrains field the rules engine reads.
     */
    public record NotificationDisplayCondition(
            ComputeType compute,
            SystemType os,
            SystemType ide,
            List<ExtensionType> extension,
            List<AuthxType> authx) { }

    /** Compute-environment condition. */
    public record ComputeType(NotificationExpression type, NotificationExpression architecture) { }

    /** OS or IDE condition (type + version). */
    public record SystemType(NotificationExpression type, NotificationExpression version) { }

    /** Installed-extension condition, matched by id + optional version expression. */
    public record ExtensionType(String id, NotificationExpression version) { }

    /** Authentication/connection condition for a feature (for example {@code "q"}). */
    public record AuthxType(
            String feature,
            NotificationExpression type,
            NotificationExpression region,
            NotificationExpression connectionState,
            NotificationExpression ssoScopes) { }

    /** Localized notification content. Only the {@code en-US} locale is consumed. */
    public record NotificationContent(@JsonProperty("en-US") LocalizedContent enUs) { }

    /** Title/description for a single locale. */
    public record LocalizedContent(String title, String description) { }

    /** A follow-up action (button) on the notification. */
    public record NotificationFollowupAction(String type, NotificationFollowupActionContent content) { }

    /** Localized action content. */
    public record NotificationFollowupActionContent(@JsonProperty("en-US") LocalizedAction enUs) { }

    /** Title and optional URL for a single locale's action. */
    public record LocalizedAction(String title, String url) { }
}
