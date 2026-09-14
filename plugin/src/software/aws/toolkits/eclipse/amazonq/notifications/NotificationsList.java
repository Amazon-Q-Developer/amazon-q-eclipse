// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.List;

/**
 * Root of the hosted notifications file: {@code { "schema": { "version": "2.0" }, "notifications": [ ... ] }}.
 * The schema version is parsed but not validated (matching the JetBrains client), and {@code notifications}
 * may be {@code null} or empty when there is nothing to show.
 */
public record NotificationsList(Schema schema, List<NotificationData> notifications) {

    /** Schema descriptor; only the version string is carried. */
    public record Schema(String version) { }
}
