// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.broker.events;

import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthState;

public record ViewRouterPluginState(AuthState authState, AmazonQLspState lspState, BrowserCompatibilityState browserCompatibilityState,
        ChatWebViewAssetState chatWebViewAssetState, ToolkitLoginWebViewAssetState toolkitLoginWebViewAssetState,
        QDeveloperProfileState qDeveloperProfileState, QDevAccessBlockedState qDevAccessBlockedState) {
}
