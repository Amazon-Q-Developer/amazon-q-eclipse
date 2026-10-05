// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.providers.assets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.telemetry.ToolkitTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;
import software.aws.toolkits.eclipse.amazonq.util.WebviewAssetServer;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;

public final class ToolkitLoginWebViewAssetProviderTest {

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorStaticMockExtension = new ActivatorStaticMockExtension();

    @Test
    void initializeEmitsDidLoadLoginModuleFailedWhenAssetsAreMissing() {
        try (MockedStatic<ThreadingUtils> mockedThreadingUtils = mockStatic(ThreadingUtils.class);
                MockedStatic<PluginUtils> mockedPluginUtils = mockStatic(PluginUtils.class);
                MockedStatic<ToolkitTelemetryProvider> mockedToolkitTelemetryProvider = mockStatic(ToolkitTelemetryProvider.class)) {
            mockedPluginUtils.when(() -> PluginUtils.getResource("webview/build/assets/js/getStart.js"))
                    .thenThrow(new IOException("resource is unavailable"));

            ToolkitLoginWebViewAssetProvider assetProvider = new ToolkitLoginWebViewAssetProvider();
            assetProvider.initialize();

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            mockedThreadingUtils.verify(() -> ThreadingUtils.executeAsyncTask(captor.capture()));
            captor.getValue().run();

            mockedToolkitTelemetryProvider.verify(() -> ToolkitTelemetryProvider.emitDidLoadModuleEventMetric(
                    ToolkitTelemetryProvider.LOGIN_MODULE, Result.FAILED, "DependencyMissing"), times(1));
        }
    }

    @Test
    void initializeEmitsDidLoadLoginModuleFailedWhenAssetsCannotBeServed() throws Exception {
        try (MockedStatic<ThreadingUtils> mockedThreadingUtils = mockStatic(ThreadingUtils.class);
                MockedStatic<PluginUtils> mockedPluginUtils = mockStatic(PluginUtils.class);
                MockedConstruction<WebviewAssetServer> mockedWebviewAssetServer = mockConstruction(WebviewAssetServer.class,
                        (mock, context) -> when(mock.resolve(any(String.class))).thenReturn(false));
                MockedStatic<ToolkitTelemetryProvider> mockedToolkitTelemetryProvider = mockStatic(ToolkitTelemetryProvider.class)) {
            mockedPluginUtils.when(() -> PluginUtils.getResource("webview/build/assets/js/getStart.js"))
                    .thenReturn(new URL("file:/assets/js/getStart.js"));

            ToolkitLoginWebViewAssetProvider assetProvider = new ToolkitLoginWebViewAssetProvider();
            assetProvider.initialize();

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            mockedThreadingUtils.verify(() -> ThreadingUtils.executeAsyncTask(captor.capture()));
            captor.getValue().run();

            mockedToolkitTelemetryProvider.verify(() -> ToolkitTelemetryProvider.emitDidLoadModuleEventMetric(
                    ToolkitTelemetryProvider.LOGIN_MODULE, Result.FAILED, "AssetLoadFailed"), times(1));
        }
    }

}
