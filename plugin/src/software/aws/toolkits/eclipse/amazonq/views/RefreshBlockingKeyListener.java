// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;

/**
 * Blocks F5 keypress to prevent browser refresh in the chat webview.
 * The webview uses setText() with no backing URL, so F5 would navigate
 * to about:blank, wiping the UI.
 */
final class RefreshBlockingKeyListener extends KeyAdapter {

    @Override
    public void keyPressed(final KeyEvent e) {
        if (shouldBlockKey(e.keyCode)) {
            e.doit = false;
        }
    }

    /**
     * Determines whether the given key code should be blocked.
     * Package-private for testability.
     */
    static boolean shouldBlockKey(final int keyCode) {
        return keyCode == SWT.F5;
    }
}
