// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

import static software.aws.toolkits.eclipse.amazonq.util.QConstants.Q_INLINE_HINT_TEXT_STYLE;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import software.aws.toolkits.eclipse.amazonq.editor.InMemoryInput;
import software.aws.toolkits.eclipse.amazonq.exception.AmazonQPluginException;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

public final class QEclipseEditorUtils {

    private QEclipseEditorUtils() {
        // Prevent instantiation
    }

    private static IWorkbenchPage getActivePage() {
        IWorkbenchWindow window = getActiveWindow();
        return window == null ? null : window.getActivePage();
    }

    private static IWorkbenchWindow getActiveWindow() {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    }

    public static ITextEditor getActiveTextEditor() {
        IWorkbenchPage activePage = getActivePage();
        return activePage == null ? null : asTextEditor(activePage.getActiveEditor());
    }

    private static ISelection getSelection(final ITextEditor textEditor) {
        return textEditor.getSelectionProvider().getSelection();
    }

    /**
     * Resolves the document backing the given editor, or {@code null} when it
     * cannot be resolved.
     *
     * <p>
     * {@link ITextEditor#getDocumentProvider()} is an optional part of the Eclipse
     * API: third party editors may return {@code null} from it. Callers must
     * therefore treat both the provider and the document it returns as nullable.
     * </p>
     */
    private static IDocument getDocument(final ITextEditor editor) {
        if (editor == null) {
            return null;
        }
        var documentProvider = editor.getDocumentProvider();
        if (documentProvider == null) {
            return null;
        }
        return documentProvider.getDocument(editor.getEditorInput());
    }

    private static ITextEditor asTextEditor(final IEditorPart editorPart) {
        if (editorPart instanceof ITextEditor) {
            return (ITextEditor) editorPart;
        } else {
            if (editorPart instanceof MultiPageEditorPart) {
                Object multiPageEditorPart;
                if (editorPart instanceof MultiPageEditorPart) {
                    multiPageEditorPart = ((MultiPageEditorPart) editorPart).getSelectedPage();
                    if (multiPageEditorPart instanceof ITextEditor) {
                        return (ITextEditor) multiPageEditorPart;
                    }
                }
            }
            return null;
        }
    }

    private static ITextViewer asTextViewer(final IEditorPart editorPart) {
        return editorPart != null ? editorPart.getAdapter(ITextViewer.class) : null;
    }

    public static ITextViewer getActiveTextViewer(final ITextEditor editor) {
        return asTextViewer(editor);
    }

    public static boolean shouldIndentVertically(final StyledText textWidget, final int zeroIndexedLine) {
        return zeroIndexedLine + 1 < textWidget.getLineCount();
    }

    public static Optional<String> getOpenFileUri() {
        try {
            return getOpenFilePath()
                    .map(filePath -> Paths.get(filePath).toUri().toString());
        } catch (Exception e) {
            Activator.getLogger().error("Unexpected error when determining open file path", e);
            return Optional.empty();
        }
    }

    public static Optional<String> getOpenFileUri(final IEditorInput editorInput) {
        try {
            if (editorInput instanceof InMemoryInput) {
                return Optional.empty();
            }
            var filePath = getOpenFilePath(editorInput);
            var fileUri = Paths.get(filePath).toUri().toString();
            return Optional.of(fileUri);
        } catch (Exception e) {
            Activator.getLogger().error("Unexpected error when determining open file path", e);
            return Optional.empty();
        }
    }

    private static Optional<String> getOpenFilePath() {
        var editor = getActiveTextEditor();
        if (editor == null) {
            return Optional.empty();
        }
        return Optional.of(getOpenFilePath(editor.getEditorInput()));
    }

    private static String getOpenFilePath(final IEditorInput editorInput) {
        if (editorInput instanceof FileStoreEditorInput fileStoreEditorInput) {
            return fileStoreEditorInput.getURI().getPath();
        } else if (editorInput instanceof IFileEditorInput fileEditorInput) {
            var file = fileEditorInput.getFile();
            IEditorPart editorPart = getActivePage().findEditor(editorInput);
            if (editorPart != null && AbapUtil.isAdtEditor(editorPart.getClass().getName())) {
                // Special handling for ABAP files: ADT plugins store files in a semantic cache location
                // rather than the workspace location, so we need to reference the cached file path
                return AbapUtil.getSemanticCachePath(file.getFullPath().toOSString());
            }
            // Fallback to semantic cache location if rawLocation is null, one of the use
            // cases is also file when users connect to a remote SAP project
            return (file.getRawLocation() != null)
                    ? file.getRawLocation().toOSString()
                    : AbapUtil.getSemanticCachePath(file.getFullPath().toOSString());
        } else {
            throw new AmazonQPluginException("Unexpected editor input type: " + editorInput.getClass().getName());
        }
    }

    /**
     * Returns file paths for all currently open editor tabs, excluding in-memory
     * editors. Used to provide supplemental context for inline completions.
     */
    public static List<String> getOpenEditorFilePaths() {
        List<String> filePaths = new ArrayList<>();
        try {
            IWorkbenchPage page = getActivePage();
            if (page == null) {
                return filePaths;
            }
            for (IEditorReference editorRef : page.getEditorReferences()) {
                try {
                    IEditorInput input = editorRef.getEditorInput();
                    if (input instanceof InMemoryInput) {
                        continue;
                    }
                    String path = getOpenFilePath(input);
                    if (path != null && !path.isEmpty()) {
                        filePaths.add(path);
                    }
                } catch (Exception e) {
                    Activator.getLogger().warn("Skipping editor tab: unable to resolve file path", e);
                }
            }
        } catch (Exception e) {
            Activator.getLogger().error("Error collecting open editor file paths", e);
        }
        return filePaths;
    }

    public static Optional<Range> getSelectionRange(final ITextEditor editor) {
        if (editor == null) {
            return Optional.empty();
        }
        ISelection selection = getSelection(editor);
        var document = getDocument(editor);
        if (document == null) {
            return Optional.empty();
        }
        if (selection instanceof ITextSelection textSelection) {
            try {
                int startOffset = textSelection.getOffset();
                int startLine = document.getLineOfOffset(startOffset);
                int startColumn = startOffset - document.getLineOffset(startLine);

                int endOffset = startOffset + textSelection.getLength();
                int endLine = document.getLineOfOffset(endOffset);
                int endColumn = endOffset - document.getLineOffset(endLine);

                var start = new Position(startLine, startColumn);
                var end = new Position(endLine, endColumn);
                return Optional.of(new Range(start, end));
            } catch (org.eclipse.jface.text.BadLocationException e) {
                Activator.getLogger()
                        .error("Error occurred while attempting to determine selected text position in editor", e);
            }
        }
        return Optional.empty();
    }

    public static Optional<Range> getActiveSelectionRange() {
        var editor = getActiveTextEditor();
        return getSelectionRange(editor);
    }

    /*
     * Inserts the given text at cursor position and returns cursor position range
     * of the text
     */
    public static Optional<Range> insertAtCursor(final String text) {
        var editor = getActiveTextEditor();
        if (editor == null) {
            return Optional.empty();
        }

        var selection = editor.getSelectionProvider().getSelection();
        var document = getDocument(editor);
        if (document == null) {
            return Optional.empty();
        }
        if (selection instanceof ITextSelection textSelection) {
            try {
                int startOffset = textSelection.getOffset();
                int startLine = document.getLineOfOffset(startOffset);
                int startColumn = startOffset - document.getLineOffset(startLine);
                // correctly indent/format text
                var indentation = getIndentation(document, startOffset);
                var indentedText = applyIndentation(text, indentation);

                // insert text
                document.replace(startOffset, 0, indentedText);
                // compute end offset after text is inserted
                int endOffset = startOffset + indentedText.length();
                // Move the cursor to the end of the inserted text
                editor.selectAndReveal(endOffset, 0);

                int endLine = document.getLineOfOffset(endOffset);
                int endColumn = endOffset - document.getLineOffset(endLine);

                // return the text cursor position
                var start = new Position(startLine, startColumn);
                var end = new Position(endLine, endColumn);
                return Optional.of(new Range(start, end));
            } catch (org.eclipse.jface.text.BadLocationException e) {
                Activator.getLogger().error("Error occurred while inserting at cursor in editor", e);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> getSelectedText() {
        var editor = getActiveTextEditor();
        if (editor == null) {
            return Optional.empty();
        }
        ISelection selection = getSelection(editor);
        try {
            if (selection instanceof ITextSelection) {
                ITextSelection textSelection = (ITextSelection) selection;
                String selectedText = textSelection.getText();

                if (selectedText != null && !selectedText.isEmpty()) {
                    return Optional.of(selectedText);
                }
            }
        } catch (Exception e) {
            throw new AmazonQPluginException("Error occurred while retrieving selected text", e);
        }
        return Optional.empty();
    }

    private static String applyIndentation(final String text, final String indentation) {
        var lines = List.of(text.split("\n"));
        if (lines.isEmpty()) {
            return text;
        }
        // skip indenting first line
        StringBuilder indentedText = new StringBuilder(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            indentedText.append("\n")
                    .append(lines.get(i).isEmpty() ? "" : indentation) // Don't apply the gap to empty lines (eg: end of
                                                                       // string may end in a newline)
                    .append(lines.get(i));
        }

        return indentedText.toString();
    }

    private static String getIndentation(final IDocument document, final int offset) {
        try {
            int line = document.getLineOfOffset(offset);
            int lineOffset = document.getLineOffset(line);
            var content = document.get(lineOffset, offset - lineOffset);

            if (content.trim().isEmpty()) {
                // if current line is blank or contains only whitespace, return line as
                // indentation
                return content;
            }
            return content.substring(0, content.indexOf(content.trim()));
        } catch (BadLocationException e) {
            // swallow error and return 0 indent level
            return "";
        }
    }

    public static Font getInlineTextFont(final StyledText widget, final int inlineTextStyle) {
        FontData[] fontData = widget.getFont().getFontData();
        for (FontData fontDatum : fontData) {
            fontDatum.setStyle(Q_INLINE_HINT_TEXT_STYLE);
        }
        return new Font(widget.getDisplay(), fontData);
    }

    public static Font getInlineCloseBracketFontBold(final StyledText widget) {
        Font font = widget.getFont();
        FontData[] fontData = font.getFontData();
        for (FontData fontDatum : fontData) {
            fontDatum.setStyle(fontDatum.getStyle() | SWT.BOLD);
        }
        return new Font(widget.getDisplay(), fontData);
    }

    public static QInlineInputListener getInlineInputListener(final StyledText widget) {
        return new QInlineInputListener(widget);
    }

    public static int getOffsetInFullyExpandedDocument(final ITextViewer viewer, final int caretOffsetFromWidget) {
        int adjustedOffset = caretOffsetFromWidget;
        if (viewer instanceof ITextViewerExtension5) {
            ITextViewerExtension5 extension = (ITextViewerExtension5) viewer;
            adjustedOffset = extension.widgetOffset2ModelOffset(caretOffsetFromWidget);
        }

        return adjustedOffset > -1 ? adjustedOffset : caretOffsetFromWidget;
    }

    public static QInlineTerminationListener getInlineTerminationListener() {
        return new QInlineTerminationListener();
    }

    public static IExecutionListener getAutoTriggerExecutionListener(final Consumer<String> callback) {
        return new IExecutionListener() {
            @Override
            public void notHandled(final String commandId, final NotHandledException exception) {
                return;
            }

            @Override
            public void postExecuteFailure(final String commandId,
                    final org.eclipse.core.commands.ExecutionException exception) {
                return;
            }

            @Override
            public void postExecuteSuccess(final String commandId, final Object returnValue) {
                return;
            }

            @Override
            public void preExecute(final String commandId, final ExecutionEvent event) {
                callback.accept(commandId);
            }
        };
    }

    public static IQInlineTypeaheadProcessor getAutoCloseSettings(final ITextEditor editor) {
        IEditorInput input = editor.getEditorInput();
        String contentTypeName = null;
        if (input instanceof FileEditorInput) {
            IFile file = ((FileEditorInput) input).getFile();
            IContentType contentType = Platform.getContentTypeManager().findContentTypeFor(file.getName());
            if (contentType != null) {
                contentTypeName = contentType.getName();
            }
        }
        if (contentTypeName == null) {
            return new GenericTypeheadProcessor();
        }
        switch (contentTypeName) {
            // TODO: Add more supported file types here:
            case "Java Source File":
                IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui");
                boolean isBracesSetToAutoClose = preferences.getBoolean("closeBraces", true);
                boolean isBracketsSetToAutoClose = preferences.getBoolean("closeBrackets", true);
                boolean isStringSetToAutoClose = preferences.getBoolean("closeStrings", true);
                return new JavaTypeaheadProcessor(editor, isBracesSetToAutoClose, isBracketsSetToAutoClose,
                        isStringSetToAutoClose);
            case "JavaScript Source File":
                return new JavascriptTypeaheadProcessor();
            default:
                return new GenericTypeheadProcessor();
        }
    }

    public static void showToast(final String message, final Display display, final int ttlInMs) {
        Shell parentShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        Shell toast = new Shell(parentShell, SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
        toast.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        toast.setAlpha(230);
        toast.setLayout(new FillLayout());

        Label label = new Label(toast, SWT.NONE);
        label.setText(message);
        Point labelSize = label.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        toast.setSize(labelSize.x + 10, labelSize.y + 8);
        Point size = toast.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        Point parentSize = parentShell.getSize();
        Point parentLocation = parentShell.getLocation();
        int x = parentLocation.x + parentSize.x - size.x - 20;
        int y = parentLocation.y + parentSize.y - size.y - 40;
        toast.setLocation(x, y);

        toast.open();
        display.timerExec(ttlInMs, () -> {
            toast.close();
        });
    }
}
