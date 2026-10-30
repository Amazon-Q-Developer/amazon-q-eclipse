// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import java.util.concurrent.Future;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import software.aws.toolkits.eclipse.amazonq.chat.ChatCommunicationManager;
import software.aws.toolkits.eclipse.amazonq.providers.assets.ChatWebViewAssetProvider;
import software.aws.toolkits.eclipse.amazonq.providers.assets.WebViewAssetProvider;
import software.aws.toolkits.eclipse.amazonq.views.actions.AmazonQViewCommonActions;

public class AmazonQChatWebview extends AmazonQView implements ChatUiRequestListener {

    public static final String ID = "software.aws.toolkits.eclipse.amazonq.views.AmazonQChatWebview";

    private AmazonQViewCommonActions amazonQCommonActions;
    private final ChatCommunicationManager chatCommunicationManager;
    private Browser browser;
    private WebViewAssetProvider webViewAssetProvider;
    private Future<?> refreshFuture;

    public AmazonQChatWebview() {
        super();
        chatCommunicationManager = ChatCommunicationManager.getInstance();
        webViewAssetProvider = new ChatWebViewAssetProvider();
        webViewAssetProvider.initialize();
    }

    @Override
    public final Composite setupView(final Composite parent) {
        setupParentBackground(parent);
        browser = getAndAttachBrowser(parent);
        // attempt to use existing browser with chat history if present, else create a
        // new one
        if (browser == null || browser.isDisposed()) {
            browser = setupBrowser(parent);
            // if setup of amazon q view fails due to missing webview dependency, switch to
            // that view and don't setup rest of the content
            if (browser == null) {
                return parent;
            }

            browser.setVisible(false);
            browser.addProgressListener(new ProgressAdapter() {
                @Override
                public void completed(final ProgressEvent event) {
                    Display.getDefault().asyncExec(() -> {
                        if (!browser.isDisposed()) {
                            browser.setVisible(true);
                            webViewAssetProvider.injectAssets(browser);
                            chatCommunicationManager.activate();
                        }
                    });
                }
            });

            webViewAssetProvider.setContent(browser);

            // Block F5/Refresh - the webview uses setText() with no backing URL,
            // so browser refresh navigates to about:blank or file:/// causing a blank screen.
            browser.addLocationListener(new RefreshBlockingLocationListener());
            browser.addKeyListener(new RefreshBlockingKeyListener());
        }

        super.setupView(parent);

        parent.addDisposeListener(e -> this.preserveBrowser());
        amazonQCommonActions = getAmazonQCommonActions();
        chatCommunicationManager.setChatUiRequestListener(this);
        addFocusListener(parent, browser);
        setupAmazonQCommonActions();

        return parent;
    }

    @Override
    public final void onSendToChatUi(final String message) {
        String script = "window.postMessage(" + message + ");";
        Display.getDefault().asyncExec(() -> {
            browser.execute(script);
        });
    }

    @Override
    public final void dispose() {
        chatCommunicationManager.removeListener(this);
        super.dispose();
    }

}
