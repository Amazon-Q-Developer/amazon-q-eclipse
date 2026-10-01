// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import software.aws.toolkits.eclipse.amazonq.lsp.model.ShowNotificationParams;
import software.aws.toolkits.eclipse.amazonq.lsp.model.ShowNotificationParams.NotificationContent;

public final class QDevAccessBlockedNotificationTest {

    private static final String BLOCKED_ID = "qDevPluginAccessBlocked";
    private static final String SHARED_TITLE = "Amazon Q Developer";

    private static String routed(final String id) {
        String envelope = "{\"serverName\":\"AmazonQ-For-Eclipse\",\"id\":\"" + id + "\"}";
        return Base64.getEncoder().encodeToString(envelope.getBytes(StandardCharsets.UTF_8));
    }

    private static ShowNotificationParams params(final String id, final String title) {
        return new ShowNotificationParams(id, "error", new NotificationContent(title, "blocked message"));
    }

    @Test
    void matchesTheRoutedEnvelopeTheRuntimeActuallySends() {
        assertTrue(QDevAccessBlockedNotification.isAccessBlocked(params(routed(BLOCKED_ID), SHARED_TITLE)));
    }

    @Test
    void matchesAPlainIdSoAnUnroutedServerStillWorks() {
        assertTrue(QDevAccessBlockedNotification.isAccessBlocked(params(BLOCKED_ID, SHARED_TITLE)));
    }

    /**
     * The whole point of matching on the id: an unrelated error that happens to share the title must
     * not sign the user out. Matching on title instead would fire here.
     */
    @Test
    void ignoresADifferentNotificationThatSharesTheTitle() {
        assertFalse(QDevAccessBlockedNotification.isAccessBlocked(params(routed("someOtherError"), SHARED_TITLE)));
    }

    @Test
    void ignoresANotificationWithNoId() {
        assertFalse(QDevAccessBlockedNotification.isAccessBlocked(params(null, SHARED_TITLE)));
        assertFalse(QDevAccessBlockedNotification.isAccessBlocked(params("", SHARED_TITLE)));
    }

    @Test
    void ignoresNullParams() {
        assertFalse(QDevAccessBlockedNotification.isAccessBlocked(null));
    }

    /**
     * Malformed input must be a non-match rather than an exception: this classifier sits on the
     * shared notification channel, so throwing would break every other notification.
     */
    @Test
    void treatsMalformedInputAsNotBlocked() {
        assertFalse(QDevAccessBlockedNotification.isAccessBlocked(params("!!!not-base64!!!", SHARED_TITLE)));
        String base64OfNonJson = Base64.getEncoder().encodeToString("not json".getBytes(StandardCharsets.UTF_8));
        assertFalse(QDevAccessBlockedNotification.isAccessBlocked(params(base64OfNonJson, SHARED_TITLE)));
        String envelopeWithoutId = Base64.getEncoder()
                .encodeToString("{\"serverName\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        assertFalse(QDevAccessBlockedNotification.isAccessBlocked(params(envelopeWithoutId, SHARED_TITLE)));
    }

    @Test
    void doesNotRequireContentToBePresent() {
        assertTrue(QDevAccessBlockedNotification
                .isAccessBlocked(new ShowNotificationParams(routed(BLOCKED_ID), "error", null)));
    }
}
