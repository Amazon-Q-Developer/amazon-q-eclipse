// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public final class DeprecationBannerSessionTest {

    @Test
    void permanentlyDismissedBannerIsNotPresented() {
        var session = new DeprecationBannerSession(() -> true, () -> {
        });

        assertFalse(session.claimPresentation());
        assertFalse(session.isPresentationActive());
    }

    @Test
    void onlyOneWindowCanClaimTheLogicalPresentation() {
        var session = new DeprecationBannerSession(() -> false, () -> {
        });

        assertTrue(session.claimPresentation());
        assertFalse(session.claimPresentation());
        assertTrue(session.isPresentationActive());
    }

    @Test
    void explicitDismissalIsPersistedOnlyOnce() {
        AtomicBoolean permanentlyDismissed = new AtomicBoolean();
        AtomicInteger persistenceCalls = new AtomicInteger();
        var session = new DeprecationBannerSession(
                permanentlyDismissed::get,
                () -> {
                    permanentlyDismissed.set(true);
                    persistenceCalls.incrementAndGet();
                });
        session.claimPresentation();

        assertTrue(session.dismissPermanently());
        assertFalse(session.dismissPermanently());
        assertFalse(session.isPresentationActive());
        assertTrue(permanentlyDismissed.get());
        assertEquals(1, persistenceCalls.get());
    }
}
