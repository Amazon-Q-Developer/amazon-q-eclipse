// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AutoTriggerPartListenerTest {

    private static IAutoTriggerDocListener docListenerMock = mock(IAutoTriggerDocListener.class);
    private static ITextEditor editorMock = mock(ITextEditor.class, RETURNS_DEEP_STUBS);
    private static IDocument docMock = mock(IDocument.class);
    private static MockedStatic<Display> displayMockStatic;
    private static MockedStatic<QEclipseEditorUtils> editorUtilsMockStatic;

    private static BlockingQueue<String> channel = new ArrayBlockingQueue<>(10);

    @BeforeAll
    public static void setUp() throws Exception {
        when(editorMock.getDocumentProvider().getDocument(any(IEditorInput.class))).thenReturn(docMock);

        editorUtilsMockStatic = mockStatic(QEclipseEditorUtils.class);
        editorUtilsMockStatic.when(QEclipseEditorUtils::getActiveTextEditor).thenReturn(editorMock);
    }

    @AfterAll
    public static void tearDown() {
        editorUtilsMockStatic.close();
        displayMockStatic.close();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testStartRoutine() throws InterruptedException {
        // Test case: a nested call should have been made on start up where active
        // editor is null
        mockInitialTimerExec(null);
        AutoTriggerPartListener partListener = new AutoTriggerPartListener(docListenerMock);
        partListener.onStart();
        String message;
        message = channel.poll(1, TimeUnit.SECONDS);
        assert (message != null);
        assert (message.equals("done"));
        message = null;

        // Test case: when active editor is not null, the following should be true:
        // - Active document on the part listener should not be null
        // - Active document on the part listener should have doc listener added
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                channel.put("document listener added");
                return null;
            }
        }).when(docMock).addDocumentListener(docListenerMock);
        displayMockStatic.close();
        mockInitialTimerExec(editorMock);
        partListener.onStart();
        message = channel.poll(1, TimeUnit.SECONDS);
        assert (message != null);
        assert (message.equals("document listener added"));

        // Test case: the onStart of the doc listener needs to have been called
        verify(docListenerMock, atLeast(1)).onStart();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testPartActivateRoutine() {
        // Test case: If part is not an ITextEditor, do nothing
        AutoTriggerPartListener partListener = new AutoTriggerPartListener(docListenerMock);
        IWorkbenchPartReference partRefMock = mock(IWorkbenchPartReference.class);
        IWorkbenchPart partMockNotEditor = mock(IWorkbenchPart.class);
        when(partRefMock.getPart(any(Boolean.class))).thenReturn(partMockNotEditor);
        int addDocListenerInvocNumber = mockingDetails(docMock).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("addDocumentListener")).collect(Collectors.toList())
                .size();
        partListener.partActivated(partRefMock);
        verify(docMock, times(addDocListenerInvocNumber)).addDocumentListener(docListenerMock);

        // Test case: Otherwise, update active document and attach document listener
        when(partRefMock.getPart(any(Boolean.class))).thenReturn(editorMock);
        partListener.partActivated(partRefMock);
        verify(docMock, times(addDocListenerInvocNumber + 1)).addDocumentListener(docListenerMock);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testPartDeactivateRoutine() {
        // Test case: If part is not an ITextEditor, do nothing
        AutoTriggerPartListener partListener = new AutoTriggerPartListener(docListenerMock);
        IWorkbenchPartReference partRefMock = mock(IWorkbenchPartReference.class);
        IWorkbenchPart partMockNotEditor = mock(IWorkbenchPart.class);
        when(partRefMock.getPart(any(Boolean.class))).thenReturn(partMockNotEditor);
        int removeDocListenerInvocNumber = mockingDetails(docMock).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("removeDocumentListener")).collect(Collectors.toList())
                .size();
        partListener.partDeactivated(partRefMock);
        verify(docMock, times(removeDocListenerInvocNumber)).removeDocumentListener(docListenerMock);

        // Test case: Otherwise, update active document and attach document listener
        when(partRefMock.getPart(any(Boolean.class))).thenReturn(editorMock);
        partListener.partActivated(partRefMock);
        partListener.partDeactivated(partRefMock);
        verify(docMock, times(removeDocListenerInvocNumber + 1)).removeDocumentListener(docListenerMock);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testShutdownRoutine() {
        // Test case: doc listener's shutdown should have been called
        AutoTriggerPartListener partListener = new AutoTriggerPartListener(docListenerMock);
        IWorkbenchPartReference partRefMock = mock(IWorkbenchPartReference.class);
        when(partRefMock.getPart(any(Boolean.class))).thenReturn(editorMock);
        int removeDocListenerInvocNumber = mockingDetails(docMock).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("removeDocumentListener")).collect(Collectors.toList())
                .size();
        partListener.partActivated(partRefMock);
        partListener.onShutdown();
        verify(docListenerMock, atLeast(1)).onShutdown();
        verify(docMock, times(removeDocListenerInvocNumber + 1)).removeDocumentListener(docListenerMock);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testPartActivateWithNullDocumentProvider() {
        // Test case: an ITextEditor that returns null from getDocumentProvider() must
        // be ignored instead of throwing out of the part listener callback
        AutoTriggerPartListener partListener = new AutoTriggerPartListener(docListenerMock);
        ITextEditor editorWithoutProvider = mock(ITextEditor.class);
        when(editorWithoutProvider.getDocumentProvider()).thenReturn(null);
        IWorkbenchPartReference partRefMock = mock(IWorkbenchPartReference.class);
        when(partRefMock.getPart(any(Boolean.class))).thenReturn(editorWithoutProvider);

        int addDocListenerInvocNumber = countDocInvocations("addDocumentListener");
        int removeDocListenerInvocNumber = countDocInvocations("removeDocumentListener");

        assertDoesNotThrow(() -> partListener.partActivated(partRefMock));
        verify(docMock, times(addDocListenerInvocNumber)).addDocumentListener(docListenerMock);

        // Test case: no active document was recorded, so deactivation is also a no-op
        assertDoesNotThrow(() -> partListener.partDeactivated(partRefMock));
        verify(docMock, times(removeDocListenerInvocNumber)).removeDocumentListener(docListenerMock);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testPartActivateWithNullDocument() {
        // Test case: a provider that returns a null document must be ignored rather
        // than dereferenced when attaching the document listener
        AutoTriggerPartListener partListener = new AutoTriggerPartListener(docListenerMock);
        IDocumentProvider providerWithoutDocument = mock(IDocumentProvider.class);
        when(providerWithoutDocument.getDocument(any())).thenReturn(null);
        ITextEditor editorWithoutDocument = mock(ITextEditor.class);
        when(editorWithoutDocument.getDocumentProvider()).thenReturn(providerWithoutDocument);
        IWorkbenchPartReference partRefMock = mock(IWorkbenchPartReference.class);
        when(partRefMock.getPart(any(Boolean.class))).thenReturn(editorWithoutDocument);

        int addDocListenerInvocNumber = countDocInvocations("addDocumentListener");

        assertDoesNotThrow(() -> partListener.partActivated(partRefMock));
        verify(docMock, times(addDocListenerInvocNumber)).addDocumentListener(docListenerMock);
    }

    private static int countDocInvocations(final String methodName) {
        return (int) mockingDetails(docMock).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals(methodName)).count();
    }

    static void mockEditor() {
        when(editorMock.getDocumentProvider().getDocument(any(IEditorInput.class))).thenReturn(docMock);
    }

    static void mockInitialTimerExec(final ITextEditor editor) {
        displayMockStatic = mockStatic(Display.class);
        Display displayMock = mock(Display.class);
        displayMockStatic.when(Display::getDefault).thenReturn(displayMock);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                Runnable runnable = invocation.getArgument(1);
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Runnable wrapper = () -> {
                    mockEditor();
                    mockEditorUtils(editor);
                    mockNestedTimerExecCalls();
                    runnable.run();
                };
                executor.submit(wrapper);
                return null;
            }

        }).when(displayMock).timerExec(any(Integer.class), any(Runnable.class));
    }

    static void mockEditorUtils(final ITextEditor editor) {
        MockedStatic<QEclipseEditorUtils> editorUtilsMockStatic = mockStatic(QEclipseEditorUtils.class);
        editorUtilsMockStatic.when(QEclipseEditorUtils::getActiveTextEditor).thenReturn(editor);
    }

    static void mockNestedTimerExecCalls() {
        MockedStatic<Display> displayMockStatic = mockStatic(Display.class);
        Display displayMock = mock(Display.class);
        displayMockStatic.when(Display::getDefault).thenReturn(displayMock);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                channel.put("done");
                return null;
            }
        }).when(displayMock).timerExec(any(Integer.class), any(Runnable.class));
    }

}

// Made for testing only
interface IAutoTriggerDocListener extends IDocumentListener, IAutoTriggerListener {
}
