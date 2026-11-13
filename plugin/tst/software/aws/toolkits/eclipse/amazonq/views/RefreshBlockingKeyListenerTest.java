// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.junit.jupiter.api.Test;

public final class RefreshBlockingKeyListenerTest {

    @Test
    void testBlocksF5KeyPress() {
        assertTrue(RefreshBlockingKeyListener.shouldBlockKey(SWT.F5),
                "F5 keypress should be blocked");
    }

    @Test
    void testAllowsF4KeyPress() {
        assertFalse(RefreshBlockingKeyListener.shouldBlockKey(SWT.F4),
                "F4 keypress should not be blocked");
    }

    @Test
    void testAllowsF6KeyPress() {
        assertFalse(RefreshBlockingKeyListener.shouldBlockKey(SWT.F6),
                "F6 keypress should not be blocked");
    }

    @Test
    void testAllowsEnterKey() {
        assertFalse(RefreshBlockingKeyListener.shouldBlockKey(SWT.CR),
                "Enter key should not be blocked");
    }

    @Test
    void testAllowsRegularCharacterKey() {
        assertFalse(RefreshBlockingKeyListener.shouldBlockKey('a'),
                "Regular character keys should not be blocked");
    }

    @Test
    void testAllowsEscapeKey() {
        assertFalse(RefreshBlockingKeyListener.shouldBlockKey(SWT.ESC),
                "Escape key should not be blocked");
    }
}
