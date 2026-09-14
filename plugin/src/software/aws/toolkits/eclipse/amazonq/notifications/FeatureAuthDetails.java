// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

/** Snapshot of a feature's auth/connection state, used by the rules engine's {@code authx} matching. */
public record FeatureAuthDetails(String connectionType, String region, String connectionState) {
}
