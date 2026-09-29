// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Owns the single logical editor-banner presentation for the current IDE process.
 */
final class DeprecationBannerSession {

    private final BooleanSupplier permanentlyDismissed;
    private final Runnable persistDismissal;
    private final AtomicBoolean presentationClaimed = new AtomicBoolean();
    private final AtomicBoolean dismissedThisSession = new AtomicBoolean();

    DeprecationBannerSession(final BooleanSupplier permanentlyDismissed,
            final Runnable persistDismissal) {
        this.permanentlyDismissed = permanentlyDismissed;
        this.persistDismissal = persistDismissal;
    }

    boolean claimPresentation() {
        return !permanentlyDismissed.getAsBoolean()
                && !dismissedThisSession.get()
                && presentationClaimed.compareAndSet(false, true);
    }

    boolean isPresentationActive() {
        return presentationClaimed.get() && !dismissedThisSession.get();
    }

    boolean dismissPermanently() {
        if (!dismissedThisSession.compareAndSet(false, true)) {
            return false;
        }
        persistDismissal.run();
        return true;
    }
}
