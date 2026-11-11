// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.providers.assets;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import software.aws.toolkits.eclipse.amazonq.broker.events.ChatWebViewAssetState;
import software.aws.toolkits.eclipse.amazonq.chat.ChatCommunicationManager;
import software.aws.toolkits.eclipse.amazonq.chat.ChatTheme;
import software.aws.toolkits.eclipse.amazonq.configuration.DeprecationAcknowledgmentStore;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.providers.lsp.LspManagerProvider;
import software.aws.toolkits.eclipse.amazonq.util.PluginPlatform;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;
import software.aws.toolkits.eclipse.amazonq.util.WebviewAssetServer;
import software.aws.toolkits.eclipse.amazonq.views.AmazonQChatViewActionHandler;
import software.aws.toolkits.eclipse.amazonq.views.LoginViewCommandParser;
import software.aws.toolkits.eclipse.amazonq.views.ViewActionHandler;
import software.aws.toolkits.eclipse.amazonq.views.ViewCommandParser;

public final class ChatWebViewAssetProvider extends WebViewAssetProvider {

    private WebviewAssetServer webviewAssetServer;
    private final ChatTheme chatTheme;
    private final ViewCommandParser commandParser;
    private final ViewActionHandler actionHandler;
    private final ChatCommunicationManager chatCommunicationManager;
    private Optional<String> content;

    public ChatWebViewAssetProvider() {
        chatTheme = new ChatTheme();
        commandParser = new LoginViewCommandParser();
        chatCommunicationManager = ChatCommunicationManager.getInstance();
        actionHandler = new AmazonQChatViewActionHandler(chatCommunicationManager);
        content = Optional.empty();
    }

    @Override
    public void initialize() {
        if (content.isEmpty()) {
            ThreadingUtils.executeAsyncTask(() -> {
                content = resolveContent();
                Activator.getEventBroker().post(ChatWebViewAssetState.class,
                        content.isPresent() ? ChatWebViewAssetState.RESOLVED : ChatWebViewAssetState.DEPENDENCY_MISSING);
            });
        }
    }

    @Override
    public void setContent(final Browser browser) {
        browser.setText(content.get());
    }

    @Override
    public void injectAssets(final Browser browser) {
        new BrowserFunction(browser, "ideCommand") {
            @Override
            public Object function(final Object[] arguments) {
                ThreadingUtils.executeAsyncTask(() -> {
                    handleMessageFromUI(browser, arguments);
                });
                return null;
            }
        };

        new BrowserFunction(browser, "isMacOs") {
            @Override
            public Object function(final Object[] arguments) {
                return Boolean.TRUE.equals(PluginUtils.getPlatform() == PluginPlatform.MAC);
            }
        };

        new BrowserFunction(browser, "copyToClipboard") {
            @Override
            public Object function(final Object[] arguments) {
                if (arguments.length > 0 && arguments[0] instanceof String) {
                    StringSelection stringSelection = new StringSelection((String) arguments[0]);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                }
                return null;
            }
        };
    }

    private Optional<String> resolveContent() {
        var chatAsset = resolveJsPath();
        if (!chatAsset.isPresent()) {
            return Optional.empty();
        }

        String chatJsPath = chatAsset.get();
        String themeVariables = chatTheme.getThemeVariables();
        String webkitWorkarounds = getWebkitProgressWorkaround();
        String eclipseWebkitScript = getEclipseWebkitScript();

        return Optional.of(String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <meta
                        http-equiv="Content-Security-Policy"
                        content="default-src 'none'; script-src %s 'unsafe-inline'; style-src %s 'unsafe-inline';
                        img-src 'self' data:; object-src 'none'; base-uri 'none'; connect-src swt:;"
                    >
                    <title>Amazon Q Chat</title>
                    <style>
                        %s
                        body {
                            background-color: var(--mynah-color-bg);
                            color: var(--mynah-color-text-default);
                            height: 100vh;
                            width: 100%%;
                            overflow: hidden;
                            margin: 0;
                            padding: 0;
                        }
                        .mynah-ui-icon-down-open {
                            -webkit-mask-size: 180%% !important;
                            scale: 80%% !important;
                        }
                        [class*="mynah-ui-icon-"] {
                            transform: translateZ(0);
                        }
                        %s
                    </style>
                </head>
                <body>
                    %s
                    %s
                </body>
                </html>
                """, chatJsPath, chatJsPath, themeVariables, webkitWorkarounds, eclipseWebkitScript, generateJS(chatJsPath)));
    }

    /**
     * Replaces the progress icon with a simplified static spinner on Mac to reduce Webkit rendering issues.
     */
    private String getWebkitProgressWorkaround() {
        PluginPlatform platform = PluginUtils.getPlatform();
        if (platform != PluginPlatform.WINDOWS) {
            return """
                    .mynah-ui-icon-progress {
                        -webkit-mask: none !important;
                        mask: none !important;
                        background: none !important;
                        border: 2px solid currentColor !important;
                        border-top-color: transparent !important;
                        border-radius: 50% !important;
                    }

                    .mynah-ui-icon-progress-subtract {
                        mask: none !important;
                        background: none !important;
                        border: 2px solid currentColor !important;
                        border-top-color: transparent !important;
                        border-radius: 50% !important;
                    }
                    """;
        }
        return "";
    }

    /**
     * Injects a script to the UI JS to pass in a flag when SWT Webkit is used.
     * The backing code has logic to skip styling that adds transparency layers
     * that can potentially cause rendering issues with Webkit on Mac
     * @return
     */
    private String getEclipseWebkitScript() {
        PluginPlatform platform = PluginUtils.getPlatform();
        if (platform != PluginPlatform.WINDOWS) {
            return "<script>document.body.classList.add('eclipse-swt-webkit');</script>";
        }
        return "";
    }

    private String generateJS(final String jsEntrypoint) {
        var clientConfiguration = ChatClientConfiguration.load(
                Activator.getPluginStore(), DeprecationAcknowledgmentStore.getInstance());
        return String.format("""
                <script type="text/javascript" charset="UTF-8" src="%s" defer></script>
                <script type="text/javascript">
                    %s
                    const init = () => {
                        waitForFunction('ideCommand')
                            .then(() => {
                                function refreshUi() {
                                    document.querySelectorAll('[class*="mynah-chat-wrapper"]').forEach(wrapper => {
                                        wrapper.style.overflow = 'visible';
                                    });
                                }

                                const mynahUI = amazonQChat.createChat({
                                    postMessage: (message) => {
                                        ideCommand(JSON.stringify(message));
                                    }
                                },
                                {
                                    %s
                                    agenticMode: true,
                                    modelSelectionEnabled: true,
                                    stringOverrides: { pinContextHint: '' },
                                });
                                window.mynah = mynahUI
                            })
                            .catch(error => console.error('Error initializing chat:', error));
                    }
                    window.addEventListener('load', init);
                    %s
                </script>
                """, jsEntrypoint, getWaitFunction(), clientConfiguration.toJavaScript(), getInputFunctions());
    }

    @SuppressWarnings("MethodLength")
    private String getInputFunctions() {
        return """
                window.addEventListener('load', () => {
                    const isMacOs = () => navigator.platform.toUpperCase().indexOf('MAC') >= 0;

                    const cursorPositions = new WeakMap();
                    const undoStacks = new WeakMap();
                    const redoStacks = new WeakMap();

                    const getCursorPosition = (element) => {
                        const selection = window.getSelection();
                        if (selection.rangeCount > 0) {
                            const range = selection.getRangeAt(0);
                            const preCaretRange = range.cloneRange();
                            preCaretRange.selectNodeContents(element);
                            preCaretRange.setEnd(range.endContainer, range.endOffset);
                            return preCaretRange.toString().length;
                        }
                        return cursorPositions.get(element) || 0;
                    };

                    const selectAllContent = (element) => {
                        const range = document.createRange();
                        range.selectNodeContents(element);
                        const selection = window.getSelection();
                        selection.removeAllRanges();
                        selection.addRange(range);
                        cursorPositions.set(element, element.innerText.length);
                    };

                    const updateCursorPosition = (element, newPosition) => {
                        const position = Math.max(0, Math.min(newPosition, element.innerText.length));
                        cursorPositions.set(element, position);
                    };

                    const addInputListener = (element) => {
                        cursorPositions.set(element, 0);
                        undoStacks.set(element, []);
                        redoStacks.set(element, []);
                        let isUndoRedoAction = false;

                        const saveState = () => {
                            if (!isUndoRedoAction) {
                                const currentState = {
                                    text: element.innerText,
                                    cursorPosition: getCursorPosition(element)
                                };
                                const undoStack = undoStacks.get(element);
                                undoStack.push(currentState);
                                redoStacks.set(element, []);
                                if (undoStack.length > 100) {
                                    undoStack.shift();
                                }
                            }
                        };

                        const undo = () => {
                            const undoStack = undoStacks.get(element);
                            const redoStack = redoStacks.get(element);
                            if (undoStack.length > 1) {
                                isUndoRedoAction = true;
                                redoStack.push(undoStack.pop());
                                const previousState = undoStack[undoStack.length - 1];
                                element.innerText = previousState.text;
                                updateCursorPosition(element, previousState.cursorPosition);
                                isUndoRedoAction = false;
                            }
                        };

                        const redo = () => {
                            const redoStack = redoStacks.get(element);
                            if (redoStack.length > 0) {
                                isUndoRedoAction = true;
                                const redoState = redoStack.pop();
                                element.innerText = redoState.text;
                                updateCursorPosition(element, redoState.cursorPosition);
                                undoStacks.get(element).push(redoState);
                                isUndoRedoAction = false;
                            }
                        };

                        const updateCursorAfterInput = () => {
                            setTimeout(() => {
                                const newPosition = getCursorPosition(element);
                                updateCursorPosition(element, newPosition);
                                saveState();
                            }, 0);
                        };

                        saveState();

                        element.addEventListener('input', updateCursorAfterInput);
                        element.addEventListener('paste', updateCursorAfterInput);

                        element.addEventListener('keydown', (event) => {
                            const cmdOrCtrl = isMacOs() ? event.metaKey : event.ctrlKey;

                            if (cmdOrCtrl && event.key === 'a') {
                                selectAllContent(element);
                                event.preventDefault();
                                event.stopPropagation();
                                return;
                            }

                            if (cmdOrCtrl && event.key === 'z') {
                                if (event.shiftKey) {
                                    redo();
                                } else {
                                    undo();
                                }
                                event.preventDefault();
                                event.stopPropagation();
                                return;
                            }

                            const currentText = element.innerText.trim();
                            const hasText = currentText.length > 0;
                            const textLength = currentText.length;
                            const cursorPosition = getCursorPosition(element);

                            switch (event.key) {
                                case 'ArrowLeft':
                                    if (!hasText || cursorPosition === 0) {
                                        event.preventDefault();
                                        event.stopPropagation();
                                    } else {
                                        updateCursorPosition(element, cursorPosition - 1);
                                    }
                                    break;

                                case 'ArrowRight':
                                    if (!hasText || cursorPosition === textLength) {
                                        event.preventDefault();
                                        event.stopPropagation();
                                    } else {
                                        updateCursorPosition(element, cursorPosition + 1);
                                    }
                                    break;

                                case 'ArrowUp':
                                    updateCursorPosition(element, 0);
                                    break;

                                case 'ArrowDown':
                                    updateCursorPosition(element, textLength);
                                    break;
                            }
                        }, true);

                        element.addEventListener('focus', () => {
                            const newPosition = getCursorPosition(element);
                            updateCursorPosition(element, newPosition);
                        });
                    };

                    document.querySelectorAll('div.mynah-chat-prompt-input').forEach(addInputListener);

                    const observer = new MutationObserver((mutations) => {
                        mutations.forEach((mutation) => {
                            mutation.addedNodes.forEach((node) => {
                                if (node.nodeType === 1) {
                                    if (node.matches('div.mynah-chat-prompt-input')) {
                                        addInputListener(node);
                                    }
                                    node.querySelectorAll('div.mynah-chat-prompt-input').forEach(addInputListener);
                                }
                            });
                        });
                    });

                    observer.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                });
                """;
    }

    private void handleMessageFromUI(final Browser browser, final Object[] arguments) {
        try {
            commandParser.parseCommand(arguments)
                    .ifPresent(parsedCommand -> actionHandler.handleCommand(parsedCommand, browser));
        } catch (Exception e) {
            Activator.getLogger().error("Error processing message from Amazon Q chat", e);
        }
    }


    private Optional<String> resolveJsPath() {
        var chatUiDirectory = getChatUiDirectory();

        if (!isValid(chatUiDirectory)) {
            Activator.getLogger().error(
                    "Error loading Chat UI. If override used, please verify the override env variables else restart Eclipse");
            return Optional.empty();
        }

        String jsFile = Paths.get(chatUiDirectory.get()).resolve("amazonq-ui.js").toString();
        var jsParent = Path.of(jsFile).getParent();
        var jsDirectoryPath = Path.of(jsParent.toUri()).normalize().toString();

        if (webviewAssetServer == null) {
            webviewAssetServer = new WebviewAssetServer();
        }

        var result = webviewAssetServer.resolve(jsDirectoryPath);
        if (!result) {
            Activator.getLogger().error(String.format(
                    "Error loading Chat UI. Unable to find the `amazonq-ui.js` file in the directory: %s. Please verify and restart",
                    chatUiDirectory.get()));
            return Optional.empty();
        }

        String chatJsPath = webviewAssetServer.getUri() + "amazonq-ui.js";

        return Optional.ofNullable(chatJsPath);
    }

    private Optional<String> getChatUiDirectory() {
        try {
            return Optional.of(LspManagerProvider.getInstance().getLspInstallation().getClientDirectory());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean isValid(final Optional<String> chatUiDirectory) {
        return chatUiDirectory.isPresent() && Files.exists(Paths.get(chatUiDirectory.get()));
    }

    @Override
    public void dispose() {
        if (webviewAssetServer != null) {
            webviewAssetServer.stop();
        }
        webviewAssetServer = null;
    }

}
