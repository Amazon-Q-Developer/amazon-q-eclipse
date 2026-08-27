// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.aws.toolkits.eclipse.amazonq.lsp.model.ShowNotificationParams;
import software.aws.toolkits.eclipse.amazonq.util.ObjectMapperFactory;

/**
 * Recognises the notification the language server raises when Amazon Q Developer refuses an identity
 * at sign-in.
 *
 * <p>Only the language server ever observes the refusal. The service gates on the User-Agent of the
 * shared language server, so the plugin's own SDK calls are allowed unconditionally -- there is no
 * client-side signal to classify. The server reports it over the existing notification channel and
 * this class decides whether a given notification is that report.
 *
 * <p>Identification is by id, never by title. The runtime's router rewrites the declared id into
 * base64 of {@code {"serverName":...,"id":...}}, so the raw id never reaches us and a title match
 * would sign out a working user the first time an unrelated error reused the same title.
 */
public final class QDevAccessBlockedNotification {

    /** Id declared by the server for this notification, found inside the routed envelope. */
    private static final String BLOCKED_NOTIFICATION_ID = "qDevPluginAccessBlocked";

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();

    private QDevAccessBlockedNotification() {
        // utility class
    }

    /**
     * @return true when the given notification reports that this identity is blocked from Amazon Q
     *         Developer. Never throws: an unrecognised or malformed notification is simply not a
     *         match, because failing to classify one must not break the notification channel for
     *         every other message that uses it.
     */
    public static boolean isAccessBlocked(final ShowNotificationParams params) {
        if (params == null) {
            return false;
        }
        return BLOCKED_NOTIFICATION_ID.equals(resolveId(params.id()));
    }

    /**
     * Resolves the id the server declared. Accepts the routed form, base64 of
     * {@code {"serverName":...,"id":...}}, and falls back to the raw value so that a server or
     * runtime that does not wrap the id still matches.
     */
    private static String resolveId(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(id), StandardCharsets.UTF_8);
            JsonNode node = OBJECT_MAPPER.readTree(decoded);
            JsonNode inner = node.get("id");
            if (inner != null && inner.isTextual()) {
                return inner.asText();
            }
        } catch (Exception e) {
            // Not a routed envelope. Fall through and treat the value as a plain id.
        }
        return id;
    }
}
