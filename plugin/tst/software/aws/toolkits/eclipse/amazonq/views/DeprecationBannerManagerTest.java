// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPerspectiveListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public final class DeprecationBannerManagerTest {

    @Test
    void perspectiveChangesReuseOneLogicalPresentation() throws Exception {
        IWorkbench workbench = mock(IWorkbench.class);
        IWorkbenchWindow window = mock(IWorkbenchWindow.class);
        IWorkbenchPage firstPage = mock(IWorkbenchPage.class);
        IWorkbenchPage secondPage = mock(IWorkbenchPage.class);
        when(window.getActivePage()).thenReturn(firstPage);
        when(workbench.getActiveWorkbenchWindow()).thenReturn(window);
        when(workbench.getWorkbenchWindows()).thenReturn(new IWorkbenchWindow[] {window});

        DeprecationBannerSession session = newSession(new AtomicInteger());
        DeprecationBannerManager manager = new DeprecationBannerManager(session, workbench);
        manager.showOncePerSession();

        ArgumentCaptor<IPerspectiveListener> perspectiveListener = ArgumentCaptor.forClass(
                IPerspectiveListener.class);
        verify(window).addPerspectiveListener(perspectiveListener.capture());

        perspectiveListener.getValue().perspectiveActivated(secondPage, null);

        verify(firstPage).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        verify(secondPage).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        assertFalse(session.claimPresentation());
    }

    @Test
    void waitsForAWorkbenchWindowWithoutReclaimingTheSession() throws Exception {
        IWorkbench workbench = mock(IWorkbench.class);
        IWorkbenchWindow window = mock(IWorkbenchWindow.class);
        IWorkbenchPage page = mock(IWorkbenchPage.class);
        when(workbench.getWorkbenchWindows()).thenReturn(new IWorkbenchWindow[0]);
        when(window.getActivePage()).thenReturn(page);

        DeprecationBannerSession session = newSession(new AtomicInteger());
        DeprecationBannerManager manager = new DeprecationBannerManager(session, workbench);
        manager.showOncePerSession();

        ArgumentCaptor<IWindowListener> windowListener = ArgumentCaptor.forClass(
                IWindowListener.class);
        verify(workbench).addWindowListener(windowListener.capture());
        assertTrue(manager.isPresentationActive());
        assertFalse(session.claimPresentation());

        windowListener.getValue().windowOpened(window);

        verify(page).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        verify(window).addPerspectiveListener(any(IPerspectiveListener.class));
    }

    @Test
    void waitsForTheExistingWorkbenchWindowPageToBecomeReady() throws Exception {
        IWorkbench workbench = mock(IWorkbench.class);
        IWorkbenchWindow window = mock(IWorkbenchWindow.class);
        IWorkbenchPage page = mock(IWorkbenchPage.class);
        when(workbench.getActiveWorkbenchWindow()).thenReturn(window);
        when(workbench.getWorkbenchWindows()).thenReturn(new IWorkbenchWindow[] {window});
        when(window.getActivePage()).thenReturn(null);
        when(page.getWorkbenchWindow()).thenReturn(window);

        DeprecationBannerSession session = newSession(new AtomicInteger());
        DeprecationBannerManager manager = new DeprecationBannerManager(session, workbench);
        manager.showOncePerSession();

        ArgumentCaptor<IPageListener> pageListener = ArgumentCaptor.forClass(
                IPageListener.class);
        verify(window).addPageListener(pageListener.capture());
        assertTrue(manager.isPresentationActive());

        when(window.getActivePage()).thenReturn(page);
        pageListener.getValue().pageOpened(page);

        verify(page).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        verify(window).addPerspectiveListener(any(IPerspectiveListener.class));
    }

    @Test
    void retriesWhenInitialWindowAndPageEventsAreMissed() throws Exception {
        IWorkbench workbench = mock(IWorkbench.class);
        IWorkbenchWindow window = mock(IWorkbenchWindow.class);
        IWorkbenchPage page = mock(IWorkbenchPage.class);
        Display display = mock(Display.class);
        when(workbench.getWorkbenchWindows()).thenReturn(new IWorkbenchWindow[0]);
        when(workbench.getDisplay()).thenReturn(display);

        DeprecationBannerManager manager = new DeprecationBannerManager(
                newSession(new AtomicInteger()), workbench);
        manager.showOncePerSession();

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(display).timerExec(eq(250), retry.capture());

        when(workbench.getActiveWorkbenchWindow()).thenReturn(window);
        when(workbench.getWorkbenchWindows()).thenReturn(new IWorkbenchWindow[] {window});
        when(window.getActivePage()).thenReturn(page);
        retry.getValue().run();

        verify(page).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        verify(window).addPerspectiveListener(any(IPerspectiveListener.class));
    }

    @Test
    void closingPresentationWindowTransfersToAnotherWindow() throws Exception {
        IWorkbench workbench = mock(IWorkbench.class);
        IWorkbenchWindow firstWindow = mock(IWorkbenchWindow.class);
        IWorkbenchWindow secondWindow = mock(IWorkbenchWindow.class);
        IWorkbenchPage firstPage = mock(IWorkbenchPage.class);
        IWorkbenchPage secondPage = mock(IWorkbenchPage.class);
        when(firstWindow.getActivePage()).thenReturn(firstPage);
        when(secondWindow.getActivePage()).thenReturn(secondPage);
        when(workbench.getActiveWorkbenchWindow()).thenReturn(firstWindow);
        when(workbench.getWorkbenchWindows()).thenReturn(
                new IWorkbenchWindow[] {firstWindow, secondWindow});

        DeprecationBannerManager manager = new DeprecationBannerManager(
                newSession(new AtomicInteger()), workbench);
        manager.showOncePerSession();

        ArgumentCaptor<IWindowListener> windowListener = ArgumentCaptor.forClass(
                IWindowListener.class);
        ArgumentCaptor<IPerspectiveListener> perspectiveListener = ArgumentCaptor.forClass(
                IPerspectiveListener.class);
        verify(workbench).addWindowListener(windowListener.capture());
        verify(firstWindow).addPerspectiveListener(perspectiveListener.capture());

        windowListener.getValue().windowClosed(firstWindow);

        verify(firstWindow).removePerspectiveListener(perspectiveListener.getValue());
        verify(secondPage).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        verify(secondWindow).addPerspectiveListener(perspectiveListener.getValue());
    }

    @Test
    void otherWindowEventsDoNotCreateDuplicatePresentation() throws Exception {
        IWorkbench workbench = mock(IWorkbench.class);
        IWorkbenchWindow firstWindow = mock(IWorkbenchWindow.class);
        IWorkbenchWindow secondWindow = mock(IWorkbenchWindow.class);
        IWorkbenchPage firstPage = mock(IWorkbenchPage.class);
        IWorkbenchPage secondPage = mock(IWorkbenchPage.class);
        when(firstWindow.getActivePage()).thenReturn(firstPage);
        when(secondWindow.getActivePage()).thenReturn(secondPage);
        when(workbench.getActiveWorkbenchWindow()).thenReturn(firstWindow);
        when(workbench.getWorkbenchWindows()).thenReturn(
                new IWorkbenchWindow[] {firstWindow, secondWindow});

        DeprecationBannerManager manager = new DeprecationBannerManager(
                newSession(new AtomicInteger()), workbench);
        manager.showOncePerSession();

        ArgumentCaptor<IWindowListener> windowListener = ArgumentCaptor.forClass(
                IWindowListener.class);
        verify(workbench).addWindowListener(windowListener.capture());
        windowListener.getValue().windowOpened(secondWindow);
        windowListener.getValue().windowActivated(secondWindow);

        verify(firstPage, times(1)).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        verify(secondPage, never()).showView(
                DeprecationBannerView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
    }

    @Test
    void permanentDismissalPersistsAndCleansUpPresentationState() throws Exception {
        IWorkbench workbench = mock(IWorkbench.class);
        IWorkbenchWindow window = mock(IWorkbenchWindow.class);
        IWorkbenchPage page = mock(IWorkbenchPage.class);
        IViewPart view = mock(IViewPart.class);
        Display display = mock(Display.class);
        when(window.getActivePage()).thenReturn(page);
        when(window.getPages()).thenReturn(new IWorkbenchPage[] {page});
        when(page.findView(DeprecationBannerView.ID)).thenReturn(view);
        when(workbench.getActiveWorkbenchWindow()).thenReturn(window);
        when(workbench.getWorkbenchWindows()).thenReturn(new IWorkbenchWindow[] {window});
        when(workbench.getDisplay()).thenReturn(display);
        when(display.getThread()).thenReturn(Thread.currentThread());

        AtomicBoolean dismissed = new AtomicBoolean();
        AtomicInteger persistenceCalls = new AtomicInteger();
        DeprecationBannerSession session = new DeprecationBannerSession(
                dismissed::get,
                () -> {
                    dismissed.set(true);
                    persistenceCalls.incrementAndGet();
                });
        DeprecationBannerManager manager = new DeprecationBannerManager(session, workbench);
        manager.showOncePerSession();

        ArgumentCaptor<IWindowListener> windowListener = ArgumentCaptor.forClass(
                IWindowListener.class);
        ArgumentCaptor<IPerspectiveListener> perspectiveListener = ArgumentCaptor.forClass(
                IPerspectiveListener.class);
        verify(workbench).addWindowListener(windowListener.capture());
        verify(window).addPerspectiveListener(perspectiveListener.capture());

        manager.dismissPermanently();
        manager.dismissPermanently();

        verify(page).hideView(view);
        verify(window).removePerspectiveListener(perspectiveListener.getValue());
        verify(workbench).removeWindowListener(windowListener.getValue());
        assertFalse(manager.isPresentationActive());
        assertTrue(dismissed.get());
        assertEquals(1, persistenceCalls.get());
    }

    private DeprecationBannerSession newSession(final AtomicInteger persistenceCalls) {
        return new DeprecationBannerSession(
                () -> false,
                persistenceCalls::incrementAndGet);
    }
}
