// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.lsp4e.LanguageClientImpl;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment;
import software.aws.toolkits.eclipse.amazonq.chat.ChatAsyncResultManager;
import software.aws.toolkits.eclipse.amazonq.chat.ChatCommunicationManager;
import software.aws.toolkits.eclipse.amazonq.chat.models.ChatUIInboundCommand;
import software.aws.toolkits.eclipse.amazonq.chat.models.ChatUIInboundCommandName;
import software.aws.toolkits.eclipse.amazonq.chat.models.GetSerializedChatParams;
import software.aws.toolkits.eclipse.amazonq.chat.models.GetSerializedChatResult;
import software.aws.toolkits.eclipse.amazonq.chat.models.SerializedChatResult;
import software.aws.toolkits.eclipse.amazonq.chat.models.ShowSaveFileDialogParams;
import software.aws.toolkits.eclipse.amazonq.chat.models.ShowSaveFileDialogResult;
import software.aws.toolkits.eclipse.amazonq.editor.InMemoryInput;
import software.aws.toolkits.eclipse.amazonq.editor.MemoryStorage;
import software.aws.toolkits.eclipse.amazonq.inlineChat.TextDiff;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthState;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginType;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.SsoTokenChangedKind;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.SsoTokenChangedParams;
import software.aws.toolkits.eclipse.amazonq.lsp.model.ConnectionMetadata;
import software.aws.toolkits.eclipse.amazonq.lsp.model.OpenFileDiffParams;
import software.aws.toolkits.eclipse.amazonq.lsp.model.ShowNotificationParams;
import software.aws.toolkits.eclipse.amazonq.broker.events.QDevAccessBlockedState;
import software.aws.toolkits.eclipse.amazonq.lsp.model.OpenTabUiResponse;
import software.aws.toolkits.eclipse.amazonq.lsp.model.SsoProfileData;
import software.aws.toolkits.eclipse.amazonq.lsp.model.TelemetryEvent;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.QEclipseEditorUtils;
import software.aws.toolkits.eclipse.amazonq.preferences.AmazonQPreferencePage;
import software.aws.toolkits.eclipse.amazonq.telemetry.service.DefaultTelemetryService;
import software.aws.toolkits.eclipse.amazonq.util.AbapUtil;
import software.aws.toolkits.eclipse.amazonq.util.Constants;
import software.aws.toolkits.eclipse.amazonq.util.ObjectMapperFactory;
import software.aws.toolkits.eclipse.amazonq.util.ThemeDetector;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;
import software.aws.toolkits.eclipse.amazonq.util.WorkspaceUtils;
import software.aws.toolkits.eclipse.amazonq.views.model.Customization;
import software.aws.toolkits.eclipse.amazonq.views.model.UpdateRedirectUrlCommand;

@SuppressWarnings("restriction")
public class AmazonQLspClientImpl extends LanguageClientImpl implements AmazonQLspClient {

    private ThemeDetector themeDetector = new ThemeDetector();

    @Override
    public final CompletableFuture<ConnectionMetadata> getConnectionMetadata() {
        return CompletableFuture.supplyAsync(() -> {
            SsoProfileData sso = new SsoProfileData();
            String startUrl = Constants.AWS_BUILDER_ID_URL;
            AuthState authState = Activator.getLoginService().getAuthState();
            if (authState.issuerUrl() != null && !authState.issuerUrl().isBlank()) {
                startUrl = authState.issuerUrl();
            }
            sso.setStartUrl(startUrl);
            ConnectionMetadata metadata = new ConnectionMetadata();
            metadata.setSso(sso);
            return metadata;
        });
    }

    @Override
    public final CompletableFuture<List<Object>> configuration(final ConfigurationParams configurationParams) {
        if (configurationParams.getItems().size() == 0) {
            return CompletableFuture.completedFuture(null);
        }
        List<Object> output = new ArrayList<>();
        configurationParams.getItems().forEach(item -> {
            if (item.getSection().equals(Constants.LSP_Q_CONFIGURATION_KEY)) {
                Customization storedCustomization = Activator.getPluginStore().getObject(
                        Constants.CUSTOMIZATION_STORAGE_INTERNAL_KEY,
                        Customization.class);
                Map<String, Object> qConfig = new HashMap<>();
                qConfig.put(Constants.LSP_CUSTOMIZATION_CONFIGURATION_KEY, Objects.nonNull(storedCustomization) ? storedCustomization.getArn() : null);
                qConfig.put(Constants.LSP_ENABLE_TELEMETRY_EVENTS_CONFIGURATION_KEY, false);
                qConfig.put(Constants.LSP_OPT_OUT_TELEMETRY_CONFIGURATION_KEY, !DefaultTelemetryService.telemetryEnabled());
                output.add(qConfig);
                Activator.getLspProvider().activate(AmazonQLspServer.class);
            } else if (item.getSection().equals(Constants.LSP_CW_CONFIGURATION_KEY)) {
                Map<String, Boolean> cwConfig = new HashMap<>();
                boolean shareContentSetting = Activator.getDefault().getPreferenceStore().getBoolean(AmazonQPreferencePage.Q_DATA_SHARING);
                boolean referencesEnabled = Activator.getDefault().getPreferenceStore().getBoolean(AmazonQPreferencePage.CODE_REFERENCE_OPT_IN)
                        && Activator.getLoginService().getAuthState().loginType().equals(LoginType.BUILDER_ID);
                cwConfig.put(Constants.LSP_CW_OPT_OUT_KEY, shareContentSetting);
                cwConfig.put(Constants.LSP_CODE_REFERENCES_OPT_OUT_KEY, referencesEnabled);
                output.add(cwConfig);
            }
        });
        return CompletableFuture.completedFuture(output);
    }

    /*
     * Handles the progress notifications received from the LSP server.
     * - Process partial results for Chat messages if provided token is maintained by ChatCommunicationManager
     * - Other notifications are ignored at this time.
     */
    @Override
    public final void notifyProgress(final ProgressParams params) {
        var chatCommunicationManager = ChatCommunicationManager.getInstance();

        ThreadingUtils.executeAsyncTask(() -> {
            try {
                chatCommunicationManager.handlePartialResultProgressNotification(params);
            } catch (Exception e) {
                Activator.getLogger().error("Error processing partial result progress notification", e);
            }
        });
    }

    @Override
    public final void telemetryEvent(final Object event) {
        TelemetryEvent telemetryEvent = ObjectMapperFactory.getInstance().convertValue(event, TelemetryEvent.class);
        ThreadingUtils.executeAsyncTask(() -> {
            switch (telemetryEvent.name()) {
            // intercept the feedback telemetry event and re-route to our feedback backend
            case "amazonq_sendFeedback":
                sendFeedback(telemetryEvent);
                break;
            default:
                Activator.getTelemetryService().emitMetric(telemetryEvent);
            }
        });
    }

    private void sendFeedback(final TelemetryEvent telemetryEvent) {
        var data = telemetryEvent.data();

        if (data.containsKey("sentiment")) {
            var sentiment = data.get("sentiment").equals(Sentiment.POSITIVE) ? Sentiment.POSITIVE : Sentiment.NEGATIVE;

            var comment = Optional.ofNullable(data.get("comment")).filter(String.class::isInstance)
                    .map(String.class::cast).orElse("");
            try {
                Activator.getTelemetryService().emitFeedback(comment, sentiment);
            } catch (Exception e) {
                Activator.getLogger().error("Error occurred when submitting feedback", e);
            }
        }
    }

    @Override
    public final CompletableFuture<ShowDocumentResult> showDocument(final ShowDocumentParams params) {
        String uri = params.getUri();
        Activator.getLogger().info("Opening URI: " + uri);

        return CompletableFuture.supplyAsync(() -> {
            final boolean[] success = new boolean[1];
            if (params.getExternal() != null && params.getExternal()) {
                var command = new UpdateRedirectUrlCommand(uri);
                Activator.getEventBroker().post(UpdateRedirectUrlCommand.class, command);
                Display.getDefault().syncExec(() -> {
                    try {
                        PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(uri));
                        success[0] = true;
                    } catch (Exception e) {
                        Activator.getLogger().error("Error in UI thread while opening external URI: " + uri, e);
                        success[0] = false;
                    }
                });
                return new ShowDocumentResult(success[0]);
            } else {
                Display.getDefault().syncExec(() -> {
                    try {
                        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                        IFileStore fileStore = EFS.getLocalFileSystem().getStore(new URI(uri));
                        IDE.openEditorOnFileStore(page, fileStore);
                        success[0] = true;
                    } catch (Exception e) {
                        Activator.getLogger().error("Error in UI thread while opening URI: " + uri, e);
                        success[0] = false;
                    }
                });
                return new ShowDocumentResult(success[0]);
            }
        });
    }

    @Override
    public final void ssoTokenChanged(final SsoTokenChangedParams params) {
        SsoTokenChangedKind kind = SsoTokenChangedKind.fromValue(params.kind());
        Activator.getLogger().info("Processing " + kind + "ssoTokenChanged notification...");

        try {
            switch (kind) {
                case EXPIRED:
                    Activator.getLoginService().expire();
                    return;
                case REFRESHED:
                    boolean loginOnInvalidToken = false;
                    Activator.getLoginService().reAuthenticate(loginOnInvalidToken);
                    return;
                default:
                    Activator.getLogger().error("Error processing ssoTokenChanged notification: Unhandled kind " + kind);
            }
        } catch (IllegalArgumentException ex) {
            Activator.getLogger().error("Error processing " + kind + " ssoTokenChanged notification", ex);
        }
    }

    /**
     * Handles the language server's generic notification channel. Today the only notification this
     * plugin acts on is the report that Amazon Q Developer has refused this identity at sign-in.
     *
     * <p>Reacting means signing the user out and routing to an explanation. Sign-out happens here
     * rather than in the view because the session is already useless: every Q request from this
     * identity is refused, so leaving the user signed in would show a working-looking IDE that
     * silently does nothing.
     *
     * <p>Never throws. This runs on the LSP message thread and is shared by every future
     * notification, so a failure to classify one must not take the channel down.
     */
    @Override
    public final void showNotification(final ShowNotificationParams params) {
        try {
            if (!QDevAccessBlockedNotification.isAccessBlocked(params)) {
                return;
            }

            String message = params.content() == null ? null : params.content().text();
            Activator.getLogger().info("Amazon Q Developer access is blocked for this identity: " + message);

            // Publish before signing out. Sign-out makes the router re-evaluate, and the blocked
            // state has to be in place by then or the router resolves the ordinary login view.
            Activator.getEventBroker().post(QDevAccessBlockedState.class, QDevAccessBlockedState.BLOCKED);
            Activator.getLoginService().logout();
        } catch (Exception e) {
            Activator.getLogger().error("Failed to handle showNotification", e);
        }
    }

    @Override
    public final void sendContextCommands(final Object params) {
        var command = ChatUIInboundCommand.createCommand("aws/chat/sendContextCommands", params);
        Activator.getEventBroker().post(ChatUIInboundCommand.class, command);
    }

    @Override
    public final CompletableFuture<Object> openTab(final Object params) {
        return CompletableFuture.supplyAsync(() -> {
            String requestId = UUID.randomUUID().toString();
            var command = ChatUIInboundCommand.createCommand("aws/chat/openTab", params, requestId);
            Activator.getEventBroker().post(ChatUIInboundCommand.class, command);
            ChatAsyncResultManager manager = ChatAsyncResultManager.getInstance();
            manager.createRequestId(requestId);
            OpenTabUiResponse response;
            try {
                Object res = ChatAsyncResultManager.getInstance().getResult(requestId);
                response = ObjectMapperFactory.getInstance().convertValue(res, OpenTabUiResponse.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to retrieve new tab response from chat UI", e);
            } finally {
                manager.removeRequestId(requestId);
            }
            if (response.result() == null) {
                Activator.getLogger().warn("Got null tab response from UI");
                return null;
            }
            return response.result();
        });
    }

    @Override
    public final CompletableFuture<ShowSaveFileDialogResult> showSaveFileDialog(final ShowSaveFileDialogParams params) {
        CompletableFuture<ShowSaveFileDialogResult> future = new CompletableFuture<>();
        Display.getDefault().syncExec(() -> {
            String name = "export-chat.md";
            String path = "";
            try {
                URI uri = new URI(params.defaultUri());
                File file = new File(uri);
                path = file.getParent();
                name = file.getName();
            } catch (URISyntaxException e) {
                Activator.getLogger().warn("Unable to parse file path details from showSaveFileDialog params: " + e.getMessage());
            }

            Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            FileDialog dialog = new FileDialog(shell, SWT.SAVE);
            dialog.setFilterExtensions(new String[] {"*.md", "*.html"});
            dialog.setFilterPath(path);
            dialog.setFileName(name);
            dialog.setFilterNames(new String[] {"Markdown Files (.md)", "HTML Files (.html)"});
            dialog.setOverwrite(true);

            String filePath = dialog.open();
            if (filePath != null) {
                future.complete(new ShowSaveFileDialogResult(filePath));
            } else {
                future.completeExceptionally(new IllegalStateException("User did not provide file path for export"));
            }
        });
        return future;
    }

    @Override
    public final CompletableFuture<SerializedChatResult> getSerializedChat(final GetSerializedChatParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String requestId = UUID.randomUUID().toString();
            var command = ChatUIInboundCommand.createCommand("aws/chat/getSerializedChat", params, requestId);
            ChatAsyncResultManager manager = ChatAsyncResultManager.getInstance();
            manager.createRequestId(requestId);
            Activator.getEventBroker().post(ChatUIInboundCommand.class, command);
            SerializedChatResult result;
            try {
                Object res = ChatAsyncResultManager.getInstance().getResult(requestId);
                GetSerializedChatResult serializedChatResult = ObjectMapperFactory.getInstance().convertValue(res, GetSerializedChatResult.class);
                result = serializedChatResult.result();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to retrieve serialized chat from chat UI", e);
            } finally {
                manager.removeRequestId(requestId);
            }
            return result;
        });
    }

    @Override
    public final void openFileDiff(final OpenFileDiffParams params) {
        String annotationAdded = themeDetector.isDarkTheme() ? "diffAnnotation.added.dark" : "diffAnnotation.added";
        String annotationDeleted = themeDetector.isDarkTheme() ? "diffAnnotation.deleted.dark"
                : "diffAnnotation.deleted";

        Display.getDefault().asyncExec(() -> {
            try {
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                IStorageEditorInput input = new InMemoryInput(
                        new MemoryStorage(new Path(params.originalFileUri()).lastSegment(), ""));

                IEditorDescriptor defaultEditor = PlatformUI.getWorkbench().getEditorRegistry()
                        .getDefaultEditor(".java");

                IEditorPart editor = page.openEditor(input,
                        defaultEditor != null ? defaultEditor.getId() : "org.eclipse.ui.DefaultTextEditor", true,
                        IWorkbenchPage.MATCH_INPUT);
                // Annotation model provides highlighting for the diff additions/deletions
                IAnnotationModel annotationModel = ((ITextEditor) editor).getDocumentProvider()
                        .getAnnotationModel(editor.getEditorInput());
                var document = ((ITextEditor) editor).getDocumentProvider().getDocument(editor.getEditorInput());

                // Split original and new code into lines for diff comparison
                String[] originalLines = (params.originalFileContent() != null
                        && !params.originalFileContent().isEmpty())
                                ? params.originalFileContent().lines().toArray(String[]::new)
                                : new String[0];
                String[] newLines = (params.fileContent() != null && !params.fileContent().isEmpty())
                        ? params.fileContent().lines().toArray(String[]::new)
                        : new String[0];
                // Diff generation --> returns Patch object which contains deltas for each line
                Patch<String> patch = DiffUtils.diff(Arrays.asList(originalLines), Arrays.asList(newLines));

                StringBuilder resultText = new StringBuilder();
                List<TextDiff> currentDiffs = new ArrayList<>();
                int currentPos = 0;
                int currentLine = 0;

                for (AbstractDelta<String> delta : patch.getDeltas()) {
                    // Continuously copy unchanged lines until we hit a diff
                    while (currentLine < delta.getSource().getPosition()) {
                        resultText.append(originalLines[currentLine]).append("\n");
                        currentPos += originalLines[currentLine].length() + 1;
                        currentLine++;
                    }

                    List<String> originalChangedLines = delta.getSource().getLines();
                    List<String> newChangedLines = delta.getTarget().getLines();

                    // Handle deleted lines and mark position
                    for (String line : originalChangedLines) {
                        resultText.append(line).append("\n");
                        currentDiffs.add(new TextDiff(currentPos, line.length(), true));
                        currentPos += line.length() + 1;
                    }

                    // Handle added lines and mark position
                    for (String line : newChangedLines) {
                        resultText.append(line).append("\n");
                        currentDiffs.add(new TextDiff(currentPos, line.length(), false));
                        currentPos += line.length() + 1;
                    }

                    currentLine = delta.getSource().getPosition() + delta.getSource().size();
                }
                // Loop through remaining unchanged lines
                while (currentLine < originalLines.length) {
                    resultText.append(originalLines[currentLine]).append("\n");
                    currentPos += originalLines[currentLine].length() + 1;
                    currentLine++;
                }

                final String finalText = resultText.toString();
                document.replace(0, document.getLength(), finalText);

                // Add all annotations after text modifications are complete
                for (TextDiff diff : currentDiffs) {
                    Position position = new Position(diff.offset(), diff.length());
                    String annotationType = diff.isDeletion() ? annotationDeleted : annotationAdded;
                    String annotationText = diff.isDeletion() ? "Deleted Code" : "Added Code";
                    annotationModel.addAnnotation(new Annotation(annotationType, false, annotationText), position);
                }
                makeEditorReadOnly(editor);
            } catch (CoreException | BadLocationException e) {
                Activator.getLogger().info("Failed to open file/diff: " + e);
            }
        });
    }

    private void makeEditorReadOnly(final IEditorPart editor) {
        ITextViewer viewer = editor.getAdapter(ITextViewer.class);
        if (viewer != null) {
            VerifyKeyListener verifyKeyListener = event -> event.doit = false;
            ((ITextViewerExtension) viewer).prependVerifyKeyListener(verifyKeyListener);
        }

        // stop text‑modifying commands
        ActionFactory[] ids = {ActionFactory.UNDO, ActionFactory.REDO, ActionFactory.CUT, ActionFactory.PASTE,
                ActionFactory.DELETE};
        for (ActionFactory id : ids) {
            IAction a = ((ITextEditor) editor).getAction(id.getId());
            if (a != null) {
                a.setEnabled(false);
            }
        }

        IWorkbenchPartSite site = editor.getSite();
        if (site == null) {
            return;
        }

        IWorkbenchWindow window = site.getWorkbenchWindow();
        if (window == null) {
            return;
        }

        Runnable cleanupEditor = () -> {
            Display.getDefault().asyncExec(() -> {
                try {
                    if (editor != null && !editor.isDirty()) {
                        IWorkbenchPage currentPage = editor.getSite().getPage();
                        if (currentPage != null) {
                            // Remove annotations
                            if (editor instanceof ITextEditor) {
                                ITextEditor textEditor = (ITextEditor) editor;
                                IDocumentProvider provider = textEditor.getDocumentProvider();
                                if (provider != null) {
                                    IAnnotationModel annotationModel = provider
                                            .getAnnotationModel(editor.getEditorInput());
                                    if (annotationModel != null) {
                                        Iterator<?> annotationIterator = annotationModel.getAnnotationIterator();
                                        while (annotationIterator.hasNext()) {
                                            annotationModel.removeAnnotation((Annotation) annotationIterator.next());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Activator.getLogger().error("Error during editor cleanup", e);
                }
            });
        };

        IPageListener pageListener = new IPageListener() {
            @Override
            public void pageOpened(final IWorkbenchPage page) {
            }

            @Override
            public void pageClosed(final IWorkbenchPage page) {
                cleanupEditor.run();
                window.removePageListener(this);
            }

            @Override
            public void pageActivated(final IWorkbenchPage page) {
            }
        };

        window.addPageListener(pageListener);

        editor.doSave(new NullProgressMonitor());
    }

    @Override
    public final void sendChatUpdate(final Object params) {
        var conversationClickCommand = new ChatUIInboundCommand("aws/chat/sendChatUpdate", null, params,
                false, null);
        Activator.getEventBroker().post(ChatUIInboundCommand.class, conversationClickCommand);
    }

    @Override
    public final void chatOptionsUpdate(final Object params) {
        var chatOptionsUpdateCommand = new ChatUIInboundCommand("aws/chat/chatOptionsUpdate", null, params,
                false, null);
        Activator.getEventBroker().post(ChatUIInboundCommand.class, chatOptionsUpdateCommand);
    }

    @Override
    public final void didCopyFile(final Object params) {
        refreshProjects();
    }

    @Override
    public final void didWriteFile(final Object params) {
        var path = extractFilePathFromParams(params);
        if (AbapUtil.isAbapFile(path)) {
            AbapUtil.updateAdtServer(path);
        }
        refreshProjects();
    }

    @Override
    public final void didAppendFile(final Object params) {
        var path = extractFilePathFromParams(params);
        if (AbapUtil.isAbapFile(path)) {
            AbapUtil.updateAdtServer(path);
        }
        refreshProjects();
    }

    @Override
    public final void didRemoveFileOrDirectory(final Object params) {
        refreshProjects();
    }

    @Override
    public final void didCreateDirectory(final Object params) {
        refreshProjects();
    }

    private void refreshProjects() {
        WorkspaceUtils.refreshAllProjects();
        WorkspaceUtils.refreshAdtViews();
    }

    private boolean isUriInWorkspace(final String uri) {
        try {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IPath workspacePath = workspace.getRoot().getLocation();

            URI fileUri = new URI(uri);
            File file = new File(fileUri);
            String filePath = file.getCanonicalPath();

            return filePath.startsWith(workspacePath.toFile().getCanonicalPath());
        } catch (Exception e) {
            Activator.getLogger().error("Error validating URI location: " + uri, e);
            return false;
        }
    }

    private String extractFilePathFromParams(final Object params) {
        if (params instanceof Map) {
            var map = (Map<?, ?>) params;
            Object path = map.get("path");
            return path != null ? path.toString() : null;
        }
        return null;
    }

    @Override
    public final void sendPinnedContext(final Object params) {
        Object updatedParams = params;
        Optional<String> fileUri = getActiveFileUri();
        if (fileUri.isPresent()) {
            Map<String, Object> textDocument = Map.of("uri", fileUri.get());
            if (params instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paramsMap = new HashMap<>((Map<String, Object>) params);
                paramsMap.put("textDocument", textDocument);
                updatedParams = paramsMap;
            } else {
                updatedParams = Map.of("params", params, "textDocument", textDocument);
            }
        }

        var sendPinnedContextCommand = ChatUIInboundCommand.createCommand(ChatUIInboundCommandName.SendPinnedContext.getValue(), updatedParams);
        Activator.getEventBroker().post(ChatUIInboundCommand.class, sendPinnedContextCommand);
    }

    private Optional<String> getActiveFileUri() {
        AtomicReference<Optional<String>> fileUri = new AtomicReference<>();
        Display.getDefault().syncExec(() -> {
            try {
                fileUri.set(getActiveEditorRelativePath());
            } catch (Exception e) {
                Activator.getLogger().error("Error getting active file URI", e);
                fileUri.set(Optional.empty());
            }
        });
        return fileUri.get();
    }

    private Optional<String> getActiveEditorRelativePath() {
        var activeEditor = QEclipseEditorUtils.getActiveTextEditor();
        if (activeEditor == null) {
            return Optional.empty();
        }
        return QEclipseEditorUtils.getOpenFileUri(activeEditor.getEditorInput())
                .map(this::getRelativePath);
    }

    private String getRelativePath(final String absoluteUri) {
        try {
            if (StringUtils.isBlank(absoluteUri)) {
                return absoluteUri;
            }

            var uri = new URI(absoluteUri);
            var activeFilePath = new File(uri).getCanonicalPath();

            // Get workspace root path
            var workspace = ResourcesPlugin.getWorkspace();
            var workspacePath = workspace.getRoot().getLocation();
            if (workspacePath == null) {
                return activeFilePath;
            }

            var workspaceRoot = workspacePath.toFile().getCanonicalPath();
            if (StringUtils.isBlank(workspaceRoot)) {
                return activeFilePath;
            }

            if (StringUtils.startsWithIgnoreCase(activeFilePath, workspaceRoot)) {
                var workspaceRootPath = Paths.get(workspaceRoot);
                var activeFilePathObj = Paths.get(activeFilePath);
                var relativePath = workspaceRootPath.relativize(activeFilePathObj).normalize();
                return relativePath.toString().replace('\\', '/');
            }

            // Not in workspace, return absolute path
            return activeFilePath;
        } catch (Exception e) {
            Activator.getLogger().error("Error occurred when attempting to determine relative path for: " + absoluteUri, e);
            return absoluteUri;
        }
    }
}
