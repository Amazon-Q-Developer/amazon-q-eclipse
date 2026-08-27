// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;

import software.aws.toolkits.eclipse.amazonq.broker.api.EventObserver;
import software.aws.toolkits.eclipse.amazonq.broker.events.AmazonQViewType;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;


public final class AmazonQViewContainer extends ViewPart implements EventObserver<AmazonQViewType> {
    public static final String ID = "software.aws.toolkits.eclipse.amazonq.views.AmazonQViewContainer";
    private static final Map<AmazonQViewType, BaseAmazonQView> VIEWS;

    private Composite parentComposite;
    private volatile StackLayout layout;
    private volatile AmazonQViewType activeViewType;
    private volatile BaseAmazonQView currentView;
    private volatile AmazonQViewType queuedViewType;

    static {
        VIEWS = Map.of(AmazonQViewType.CHAT_ASSET_MISSING_VIEW, new ChatAssetMissingView(),
                AmazonQViewType.DEPENDENCY_MISSING_VIEW, new DependencyMissingView(),
                AmazonQViewType.RE_AUTHENTICATE_VIEW, new ReauthenticateView(),
                AmazonQViewType.LSP_STARTUP_FAILED_VIEW, new LspStartUpFailedView(),
                AmazonQViewType.CHAT_VIEW, new AmazonQChatWebview(),
                AmazonQViewType.TOOLKIT_LOGIN_VIEW, new ToolkitLoginWebview(),
                AmazonQViewType.Q_DEV_ACCESS_BLOCKED_VIEW, new QDevAccessBlockedView());
    }

    public AmazonQViewContainer() {
        activeViewType = AmazonQViewType.CHAT_VIEW;
        Activator.getEventBroker().subscribe(AmazonQViewType.class, this);
    }

    @Override
    public void createPartControl(final Composite parent) {
        layout = new StackLayout();
        parent.setLayout(layout);

        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        parent.setLayout(gridLayout);

        parentComposite = parent;

        if (queuedViewType != null) {
            AmazonQViewType viewType = queuedViewType;
            queuedViewType = null;
            onEvent(viewType);
        }
    }

    private void updateChildView() {
        Display.getDefault().asyncExec(() -> {
            BaseAmazonQView newView = VIEWS.get(activeViewType);

            if (currentView != null) {
                if (currentView instanceof AmazonQView) {
                    ((AmazonQView) currentView).disposeBrowser();
                }
                Control[] children = parentComposite.getChildren();
                for (Control child : children) {
                    if (child != null && !child.isDisposed()) {
                        child.dispose();
                    }
                }

                currentView.dispose();
            }

            newView.setViewSite(getViewSite());

            Composite newViewComposite = newView.setupView(parentComposite);
            GridData gridData = new GridData(SWT.CENTER, SWT.CENTER, true, true);
            newViewComposite.setLayoutData(gridData);

            layout.topControl = newViewComposite;
            parentComposite.layout(true, true);

            currentView = newView;
        });
    }

    @Override
    public void onEvent(final AmazonQViewType newViewType) {
        if (!VIEWS.containsKey(newViewType)) {
            return;
        }

        if (parentComposite == null || parentComposite.isDisposed()) {
            queuedViewType = newViewType;
            return;
        }

        activeViewType = newViewType;
        updateChildView();
    }

    @Override
    public void setFocus() {
        parentComposite.setFocus();
    }

    @Override
    public void dispose() {
        if (currentView != null) {
            currentView.dispose();
        }

        super.dispose();
    }

}
