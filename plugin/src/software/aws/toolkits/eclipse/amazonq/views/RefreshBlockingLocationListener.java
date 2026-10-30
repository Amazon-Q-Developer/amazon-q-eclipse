// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;

/**
 * Blocks browser navigation to prevent the chat webview from going blank on
 * refresh. The webview uses setText() with no backing URL, so any refresh or
 * navigation attempt will leave the rendered content. On different platforms,
 * refresh may navigate to about:blank (Windows/Linux) or file:/// (macOS WebKit).
 * This listener blocks all navigation since the webview should never navigate away.
 */
final class RefreshBlockingLocationListener extends LocationAdapter {

    @Override
    public void changing(final LocationEvent event) {
        if (shouldBlockNavigation(event.location)) {
            event.doit = false;
        }
    }

    /**
     * Determines whether navigation to the given location should be blocked.
     * Blocks about:blank and file:// URLs which are triggered by refresh on
     * different platforms. The webview content is set via setText() and should
     * never navigate to any URL.
     * Package-private for testability.
     */
    static boolean shouldBlockNavigation(final String location) {
        if (location == null || location.isEmpty()) {
            return false;
        }
        return "about:blank".equals(location) || location.startsWith("file:");
    }
}
