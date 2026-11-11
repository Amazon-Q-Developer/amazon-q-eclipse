// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStoreKeys;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

/**
 * Presents a single editor-area deprecation banner without activating or focusing it.
 */
public final class DeprecationBannerManager {

    private static final int ATTACH_RETRY_DELAY_MILLIS = 250;
    private static final int MAX_ATTACH_RETRIES = 40;

    private final DeprecationBannerSession session;
    private final IWorkbench workbench;
    private final Runnable attachRetry;
    private final IPageListener pageListener;
    private final IPerspectiveListener perspectiveListener;
    private final IWindowListener windowListener;
    private IWorkbenchWindow presentationWindow;
    private int remainingAttachRetries;
    private boolean attachRetryScheduled;
    private boolean pageListenersRegistered;
    private boolean windowListenerRegistered;

    DeprecationBannerManager(final DeprecationBannerSession session, final IWorkbench workbench) {
        this.session = session;
        this.workbench = workbench;
        this.attachRetry = () -> {
            attachRetryScheduled = false;
            if (!session.isPresentationActive() || presentationWindow != null) {
                return;
            }
            if (!attachToAvailableWindow(null)) {
                scheduleAttachRetry();
            }
        };
        this.pageListener = new IPageListener() {
            @Override
            public void pageActivated(final IWorkbenchPage page) {
                attachIfUnassigned(page.getWorkbenchWindow());
            }

            @Override
            public void pageClosed(final IWorkbenchPage page) {
                // A replacement page is handled when it is opened or activated.
            }

            @Override
            public void pageOpened(final IWorkbenchPage page) {
                attachIfUnassigned(page.getWorkbenchWindow());
            }
        };
        this.perspectiveListener = new IPerspectiveListener() {
            @Override
            public void perspectiveActivated(final IWorkbenchPage page,
                    final IPerspectiveDescriptor perspective) {
                showInPage(page);
            }

            @Override
            public void perspectiveChanged(final IWorkbenchPage page,
                    final IPerspectiveDescriptor perspective, final String changeId) {
                // The banner only needs to react when a different perspective becomes active.
            }
        };
        this.windowListener = new IWindowListener() {
            @Override
            public void windowActivated(final IWorkbenchWindow window) {
                attachIfUnassigned(window);
            }

            @Override
            public void windowDeactivated(final IWorkbenchWindow window) {
                // The banner remains attached to its current workbench window.
            }

            @Override
            public void windowClosed(final IWorkbenchWindow window) {
                window.removePageListener(pageListener);
                transferFromClosedWindow(window);
            }

            @Override
            public void windowOpened(final IWorkbenchWindow window) {
                window.addPageListener(pageListener);
                attachIfUnassigned(window);
            }
        };
    }

    public static DeprecationBannerManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public void showOncePerSession() {
        if (!session.claimPresentation()) {
            return;
        }

        registerWindowListener();
        registerPageListeners();
        remainingAttachRetries = MAX_ATTACH_RETRIES;
        if (!attachToAvailableWindow(null)) {
            scheduleAttachRetry();
        }
    }

    public boolean isPresentationActive() {
        return session.isPresentationActive();
    }

    public void dismissPermanently() {
        if (!session.dismissPermanently()) {
            return;
        }
        hideAllViews();
        cancelAttachRetry();
        detachPerspectiveListener();
        unregisterPageListeners();
        unregisterWindowListener();
    }

    public void shutdown() {
        cancelAttachRetry();
        detachPerspectiveListener();
        unregisterPageListeners();
        unregisterWindowListener();
    }

    private boolean showInPage(final IWorkbenchPage page) {
        if (page == null || !session.isPresentationActive()) {
            return false;
        }
        try {
            page.showView(DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
            return true;
        } catch (PartInitException e) {
            Activator.getLogger().warn("Unable to show the Amazon Q end-of-support banner", e);
            return false;
        }
    }

    private boolean attachToAvailableWindow(final IWorkbenchWindow excludedWindow) {
        IWorkbenchWindow activeWindow = workbench.getActiveWorkbenchWindow();
        if (isEligible(activeWindow, excludedWindow) && attachToWindow(activeWindow)) {
            return true;
        }

        for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
            if (window != activeWindow
                    && isEligible(window, excludedWindow)
                    && attachToWindow(window)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEligible(final IWorkbenchWindow window,
            final IWorkbenchWindow excludedWindow) {
        return window != null && window != excludedWindow && window.getActivePage() != null;
    }

    private boolean attachToWindow(final IWorkbenchWindow window) {
        if (!session.isPresentationActive() || presentationWindow != null) {
            return false;
        }

        IWorkbenchPage page = window.getActivePage();
        if (!showInPage(page)) {
            return false;
        }

        presentationWindow = window;
        presentationWindow.addPerspectiveListener(perspectiveListener);
        cancelAttachRetry();
        return true;
    }

    private void attachIfUnassigned(final IWorkbenchWindow window) {
        if (presentationWindow == null && isEligible(window, null)) {
            attachToWindow(window);
        }
    }

    private void transferFromClosedWindow(final IWorkbenchWindow closedWindow) {
        if (closedWindow != presentationWindow) {
            return;
        }

        detachPerspectiveListener();
        if (!attachToAvailableWindow(closedWindow)) {
            remainingAttachRetries = MAX_ATTACH_RETRIES;
            scheduleAttachRetry();
        }
    }

    private void hideAllViews() {
        Display display = workbench.getDisplay();
        Runnable hideViews = () -> {
            for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
                for (IWorkbenchPage page : window.getPages()) {
                    IViewPart view = page.findView(DeprecationBannerView.ID);
                    if (view != null) {
                        page.hideView(view);
                    }
                }
            }
        };

        if (display == null || display.isDisposed()) {
            return;
        }
        if (display.getThread() == Thread.currentThread()) {
            hideViews.run();
        } else {
            display.asyncExec(hideViews);
        }
    }

    private void detachPerspectiveListener() {
        if (presentationWindow != null) {
            presentationWindow.removePerspectiveListener(perspectiveListener);
        }
        presentationWindow = null;
    }

    private void scheduleAttachRetry() {
        if (attachRetryScheduled || remainingAttachRetries <= 0
                || !session.isPresentationActive()) {
            return;
        }

        Display display = workbench.getDisplay();
        if (display == null || display.isDisposed()) {
            return;
        }

        remainingAttachRetries--;
        attachRetryScheduled = true;
        display.timerExec(ATTACH_RETRY_DELAY_MILLIS, attachRetry);
    }

    private void cancelAttachRetry() {
        remainingAttachRetries = 0;
        if (!attachRetryScheduled) {
            return;
        }

        attachRetryScheduled = false;
        Display display = workbench.getDisplay();
        if (display != null && !display.isDisposed()) {
            display.timerExec(-1, attachRetry);
        }
    }

    private void registerWindowListener() {
        if (!windowListenerRegistered) {
            workbench.addWindowListener(windowListener);
            windowListenerRegistered = true;
        }
    }

    private void registerPageListeners() {
        if (!pageListenersRegistered) {
            for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
                window.addPageListener(pageListener);
            }
            pageListenersRegistered = true;
        }
    }

    private void unregisterPageListeners() {
        if (pageListenersRegistered) {
            for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
                window.removePageListener(pageListener);
            }
            pageListenersRegistered = false;
        }
    }

    private void unregisterWindowListener() {
        if (windowListenerRegistered) {
            workbench.removeWindowListener(windowListener);
            windowListenerRegistered = false;
        }
    }

    private static DeprecationBannerManager createDefault() {
        PluginStore store = Activator.getPluginStore();
        return new DeprecationBannerManager(new DeprecationBannerSession(
                () -> store.get(PluginStoreKeys.EDITOR_DEPRECATION_BANNER_DISMISSED) != null,
                () -> store.put(PluginStoreKeys.EDITOR_DEPRECATION_BANNER_DISMISSED, "true")),
                PlatformUI.getWorkbench());
    }

    private static final class InstanceHolder {
        private static final DeprecationBannerManager INSTANCE = createDefault();
    }
}
