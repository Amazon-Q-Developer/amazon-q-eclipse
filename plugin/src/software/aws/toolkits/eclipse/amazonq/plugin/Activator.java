// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.plugin;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import software.aws.toolkits.eclipse.amazonq.broker.EventBroker;
import software.aws.toolkits.eclipse.amazonq.configuration.DefaultPluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.inlineChat.InlineChatEditorListener;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.DefaultLoginService;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.LoginService;
import software.aws.toolkits.eclipse.amazonq.lsp.editor.ActiveEditorChangeListener;
import software.aws.toolkits.eclipse.amazonq.providers.browser.AmazonQBrowserProvider;
import software.aws.toolkits.eclipse.amazonq.providers.lsp.LspProvider;
import software.aws.toolkits.eclipse.amazonq.providers.lsp.LspProviderImpl;
import software.aws.toolkits.eclipse.amazonq.telemetry.service.DefaultTelemetryService;
import software.aws.toolkits.eclipse.amazonq.telemetry.service.TelemetryService;
import software.aws.toolkits.eclipse.amazonq.util.CodeReferenceLoggingService;
import software.aws.toolkits.eclipse.amazonq.util.DefaultCodeReferenceLoggingService;
import software.aws.toolkits.eclipse.amazonq.util.LoggingService;
import software.aws.toolkits.eclipse.amazonq.util.PluginLogger;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationPollingService;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;
import software.aws.toolkits.eclipse.amazonq.views.router.ViewRouter;
import software.aws.toolkits.eclipse.workspace.WorkspaceChangeListener;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "amazon-q-eclipse";
    private static Activator plugin;
    private static TelemetryService telemetryService;
    private static LoggingService defaultLogger;
    private static LspProvider lspProvider;
    private static LoginService loginService;
    private static CodeReferenceLoggingService codeReferenceLoggingService;
    private static PluginStore pluginStore;
    private static EventBroker eventBroker = new EventBroker();
    private static ViewRouter viewRouter = ViewRouter.builder().build();
    private final InlineChatEditorListener editorListener;
    private static WorkspaceChangeListener workspaceListener = WorkspaceChangeListener.getInstance();
    private static ActiveEditorChangeListener activeEditorListener = ActiveEditorChangeListener.getInstance();

    public Activator() {
        super();
        plugin = this;
        defaultLogger = PluginLogger.getInstance();
        telemetryService = DefaultTelemetryService.builder().build();
        lspProvider = LspProviderImpl.getInstance();
        pluginStore = DefaultPluginStore.getInstance();
        loginService = DefaultLoginService.builder()
                .withLspProvider(lspProvider)
                .withPluginStore(pluginStore)
                .initializeOnStartUp()
                .build();
        codeReferenceLoggingService = DefaultCodeReferenceLoggingService.getInstance();
        editorListener = InlineChatEditorListener.getInstance();
        editorListener.initialize();
        workspaceListener.start();
        activeEditorListener.initialize();
    }

    @Override
    public final void stop(final BundleContext context) throws Exception {
        NotificationPollingService.getInstance().stop();
        AmazonQBrowserProvider.getInstance().dispose();
        super.stop(context);
        plugin = null;
        workspaceListener.stop();
        activeEditorListener.stop();
        ThreadingUtils.shutdown();
    }

    public static Activator getDefault() {
        return plugin;
    }

    // TODO: replace with proper injection pattern
    public static TelemetryService getTelemetryService() {
        return telemetryService;
    }
    public static LoggingService getLogger() {
        return defaultLogger;
    }
    public static LspProvider getLspProvider() {
        return lspProvider;
    }
    public static LoginService getLoginService() {
        return loginService;
    }
    public static PluginStore getPluginStore() {
        return pluginStore;
    }
    public static CodeReferenceLoggingService getCodeReferenceLoggingService() {
        return codeReferenceLoggingService;
    }
    public static EventBroker getEventBroker() {
        return eventBroker;
    }

}
