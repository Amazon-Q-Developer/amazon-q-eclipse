// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.providers.assets;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import software.aws.toolkits.eclipse.amazonq.broker.events.ToolkitLoginWebViewAssetState;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.telemetry.ToolkitTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.telemetry.UiTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;
import software.aws.toolkits.eclipse.amazonq.util.ThemeDetector;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;
import software.aws.toolkits.eclipse.amazonq.util.WebviewAssetServer;
import software.aws.toolkits.eclipse.amazonq.views.LoginViewActionHandler;
import software.aws.toolkits.eclipse.amazonq.views.LoginViewCommandParser;
import software.aws.toolkits.eclipse.amazonq.views.ViewActionHandler;
import software.aws.toolkits.eclipse.amazonq.views.ViewCommandParser;
import software.aws.toolkits.eclipse.amazonq.views.ViewConstants;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;

public final class ToolkitLoginWebViewAssetProvider extends WebViewAssetProvider {

    private static final String DEPENDENCY_MISSING_REASON = "DependencyMissing";
    private static final String ASSET_LOAD_FAILED_REASON = "AssetLoadFailed";

    private WebviewAssetServer webviewAssetServer;
    private static final ThemeDetector THEME_DETECTOR = new ThemeDetector();
    private final ViewCommandParser commandParser;
    private final ViewActionHandler actionHandler;

    private Optional<String> content;

    public ToolkitLoginWebViewAssetProvider() {
        commandParser = new LoginViewCommandParser();
        actionHandler = new LoginViewActionHandler();
        content = Optional.empty();
    }

    @Override
    public void initialize() {
        if (content.isEmpty()) {
            ThreadingUtils.executeAsyncTask(() -> {
                content = resolveContent();
                if (content.isEmpty()) {
                    ToolkitTelemetryProvider.emitDidLoadModuleEventMetric(ToolkitTelemetryProvider.LOGIN_MODULE,
                            Result.FAILED, DEPENDENCY_MISSING_REASON);
                }
                Activator.getEventBroker().post(ToolkitLoginWebViewAssetState.class,
                        content.isPresent() ? ToolkitLoginWebViewAssetState.RESOLVED
                                : ToolkitLoginWebViewAssetState.DEPENDENCY_MISSING);
            });
        }
    }

    @Override
    public void setContent(final Browser browser) {
        browser.setText(content.get());
    }

    @Override
    public void injectAssets(final Browser browser) {
        new BrowserFunction(browser, ViewConstants.COMMAND_FUNCTION_NAME) {
            @Override
            public Object function(final Object[] arguments) {
                commandParser.parseCommand(arguments)
                        .ifPresent(command -> actionHandler.handleCommand(command, browser));
                return null;
            }
        };
        new BrowserFunction(browser, "telemetryEvent") {
            @Override
            public Object function(final Object[] arguments) {
                String clickEvent = (String) arguments[0];
                UiTelemetryProvider.emitClickEventMetric("auth_" + clickEvent);
                return null;
            }
        };
    }

    private Optional<String> resolveContent() {
        try {
            URL jsFile = PluginUtils.getResource("webview/build/assets/js/getStart.js");
            String decodedPath = URLDecoder.decode(jsFile.getPath(), StandardCharsets.UTF_8);

            // Remove leading slash for Windows paths
            decodedPath = decodedPath.replaceFirst("^/([A-Za-z]:)", "$1");

            Path jsParent = Paths.get(decodedPath).getParent();
            String jsDirectoryPath = jsParent.normalize().toString();

            webviewAssetServer = new WebviewAssetServer();
            var result = webviewAssetServer.resolve(jsDirectoryPath);
            if (!result) {
                ToolkitTelemetryProvider.emitDidLoadModuleEventMetric(ToolkitTelemetryProvider.LOGIN_MODULE,
                        Result.FAILED, ASSET_LOAD_FAILED_REASON);
                return Optional.of("Failed to load JS");
            }
            var loginJsPath = webviewAssetServer.getUri() + "getStart.js";
            boolean isDarkTheme = THEME_DETECTOR.isDarkTheme();
            return Optional.of(String.format(
                    """
                            <!DOCTYPE html>
                            <html>
                                <head>
                                    <meta
                                        http-equiv="Content-Security-Policy"
                                        content="default-src 'none'; script-src %s 'unsafe-inline'; style-src %s 'unsafe-inline';
                                        img-src 'self' data:; object-src 'none'; base-uri 'none'; connect-src swt:;"
                                    >
                                    <title>AWS Q</title>
                                </head>
                                <body class="jb-light">
                                    <div id="app"></div>
                                    <script type="text/javascript" src="%s" defer></script>
                                    <script type="text/javascript">
                                        %s
                                        const init = () => {
                                            changeTheme(%b);
                                            Promise.all([
                                                waitForFunction('ideCommand'),
                                                waitForFunction('telemetryEvent')
                                            ])
                                                .then(([ideCommand, telemetryEvent]) => {
                                                    const ideApi = {
                                                        postMessage(message) {
                                                            ideCommand(JSON.stringify(message));
                                                        }
                                                    };
                                                    window.ideApi = ideApi;

                                                    const telemetryApi = {
                                                        postClickEvent(event) {
                                                            telemetryEvent(event);
                                                        }
                                                    };
                                                    window.telemetryApi = telemetryApi;

                                                    ideCommand(JSON.stringify({"command":"onLoad"}));
                                                })
                                                .catch(error => console.error('Error in initialization:', error));
                                        };
                                        window.addEventListener('load', init);
                                    </script>
                                </body>
                            </html>
                            """,
                    loginJsPath, loginJsPath, loginJsPath, getWaitFunction(), isDarkTheme));
        } catch (IOException e) {
            ToolkitTelemetryProvider.emitDidLoadModuleEventMetric(ToolkitTelemetryProvider.LOGIN_MODULE,
                    Result.FAILED, DEPENDENCY_MISSING_REASON);
            return Optional.of("Failed to load JS");
        }
    }

    @Override
    public void dispose() {
        if (webviewAssetServer != null) {
            webviewAssetServer.stop();
        }
        webviewAssetServer = null;
    }

}
