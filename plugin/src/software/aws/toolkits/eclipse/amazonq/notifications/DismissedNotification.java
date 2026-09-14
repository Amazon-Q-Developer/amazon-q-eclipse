// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

/** A dismissed notification id plus the epoch-millis timestamp it was dismissed (for retention cleanup). Gson-friendly. */
public final class DismissedNotification {

    private String id;
    private long dismissedAtEpochMs;

    public DismissedNotification() {
        // no-arg constructor for Gson
    }

    public DismissedNotification(final String id, final long dismissedAtEpochMs) {
        this.id = id;
        this.dismissedAtEpochMs = dismissedAtEpochMs;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public long getDismissedAtEpochMs() {
        return dismissedAtEpochMs;
    }

    public void setDismissedAtEpochMs(final long dismissedAtEpochMs) {
        this.dismissedAtEpochMs = dismissedAtEpochMs;
    }
}
