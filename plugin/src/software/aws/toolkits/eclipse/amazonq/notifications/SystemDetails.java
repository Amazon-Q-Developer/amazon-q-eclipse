// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.Map;

/** Immutable snapshot of the current system + auth state that a notification's conditions are evaluated against. */
public record SystemDetails(
        String computeType,
        String computeArchitecture,
        String osType,
        String osVersion,
        String ideType,
        String ideVersion,
        Map<String, String> pluginVersions,
        FeatureAuthDetails qAuth) {
}
