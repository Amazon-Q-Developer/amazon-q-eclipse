// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public final class RefreshBlockingLocationListenerTest {

    @Test
    void testBlocksNavigationToAboutBlank() {
        assertTrue(RefreshBlockingLocationListener.shouldBlockNavigation("about:blank"),
                "Navigation to about:blank should be blocked");
    }

    @Test
    void testBlocksNavigationToFileProtocol() {
        assertTrue(RefreshBlockingLocationListener.shouldBlockNavigation("file:///"),
                "Navigation to file:/// should be blocked (macOS WebKit reload)");
    }

    @Test
    void testBlocksNavigationToFileProtocolWithPath() {
        assertTrue(RefreshBlockingLocationListener.shouldBlockNavigation("file:///tmp/test.html"),
                "Navigation to file:// URLs should be blocked");
    }

    @Test
    void testAllowsNavigationToHttpsUrl() {
        assertFalse(RefreshBlockingLocationListener.shouldBlockNavigation("https://example.com"),
                "Navigation to HTTPS URLs should be allowed");
    }

    @Test
    void testAllowsNavigationWhenLocationIsNull() {
        assertFalse(RefreshBlockingLocationListener.shouldBlockNavigation(null),
                "Navigation should be allowed when location is null");
    }

    @Test
    void testAllowsNavigationToEmptyString() {
        assertFalse(RefreshBlockingLocationListener.shouldBlockNavigation(""),
                "Navigation should be allowed when location is empty");
    }
}
