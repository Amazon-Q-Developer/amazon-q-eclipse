// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Parameters of the {@code aws/window/showNotification} notification, the language server's generic
 * channel for surfacing a message to the user.
 *
 * <p>The {@code id} does not arrive as the server declared it. The runtime's router rewrites it into
 * base64 of {@code {"serverName":...,"id":...}} so that a follow-up action can be routed back to the
 * server that raised it. Callers must therefore decode the envelope and compare the inner id rather
 * than this field, and must never key behaviour off {@code content.title} -- titles are shared
 * between unrelated notifications, so matching on one would fire this handler for the wrong message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShowNotificationParams(String id, String type, NotificationContent content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationContent(String title, String text) {
    }
}
