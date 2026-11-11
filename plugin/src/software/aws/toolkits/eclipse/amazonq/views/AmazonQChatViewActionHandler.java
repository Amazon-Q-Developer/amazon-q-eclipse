// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.fasterxml.jackson.databind.JsonNode;

import software.aws.toolkits.eclipse.amazonq.chat.ChatAsyncResultManager;
import software.aws.toolkits.eclipse.amazonq.chat.ChatCommunicationManager;
import software.aws.toolkits.eclipse.amazonq.chat.ChatMessage;
import software.aws.toolkits.eclipse.amazonq.chat.models.CursorState;
import software.aws.toolkits.eclipse.amazonq.configuration.DeprecationAcknowledgmentStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStoreKeys;
import software.aws.toolkits.eclipse.amazonq.exception.AmazonQPluginException;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthFollowUpType;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.preferences.AmazonQPreferencePage;
import software.aws.toolkits.eclipse.amazonq.util.Constants;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;
import software.aws.toolkits.eclipse.amazonq.util.QEclipseEditorUtils;
import software.aws.toolkits.eclipse.amazonq.views.model.Command;
import software.aws.toolkits.eclipse.amazonq.views.model.ParsedCommand;

public class AmazonQChatViewActionHandler implements ViewActionHandler {
    private ChatCommunicationManager chatCommunicationManager;
    private final ChatPromptAcknowledgmentHandler promptAcknowledgmentHandler;

    public AmazonQChatViewActionHandler(final ChatCommunicationManager chatCommunicationManager) {
        this(chatCommunicationManager, new ChatPromptAcknowledgmentHandler(
                Activator.getPluginStore(), DeprecationAcknowledgmentStore.getInstance()));
    }

    AmazonQChatViewActionHandler(final ChatCommunicationManager chatCommunicationManager,
            final ChatPromptAcknowledgmentHandler promptAcknowledgmentHandler) {
        this.chatCommunicationManager = chatCommunicationManager;
        this.promptAcknowledgmentHandler = promptAcknowledgmentHandler;
    }

    /*
     * Handles the command message received from the webview
     */
    @Override
    public final void handleCommand(final ParsedCommand parsedCommand, final Browser browser) {
        Command command = parsedCommand.getCommand();
        ChatMessage message = new ChatMessage(parsedCommand.getParams());

        switch (command) {
            case CHAT_SEND_PROMPT:
            case CHAT_PROMPT_OPTION_CHANGE:
            case CHAT_QUICK_ACTION:
            case FILE_CLICK:
            case CHAT_READY:
            case CHAT_TAB_ADD:
            case CHAT_TAB_REMOVE:
            case CHAT_TAB_CHANGE:
            case CHAT_END_CHAT:
            case CHAT_FEEDBACK:
            case CHAT_FOLLOW_UP_CLICK:
            case LIST_CONVERSATIONS:
            case CONVERSATION_CLICK:
            case CREATE_PROMPT:
            case STOP_CHAT_RESPONSE:
            case BUTTON_CLICK:
            case TAB_BAR_ACTION:
            case LIST_MCP_SERVERS:
            case MCP_SERVER_CLICK:
            case LIST_RULES:
            case RULE_CLICK:
            case PINNED_CONTEXT_ADD:
            case PINNED_CONTEXT_REMOVE:
            case LIST_AVAILABLE_MODELS:
            case FILTER_CONTEXT_COMMANDS:
                chatCommunicationManager.sendMessageToChatServer(command, message);
                break;
            case CHAT_INFO_LINK_CLICK:
            case CHAT_LINK_CLICK:
            case CHAT_SOURCE_LINK_CLICK:
                validateAndHandleLink(message.getValueAsString("link"));
                chatCommunicationManager.sendMessageToChatServer(command, message);
                break;
            case CHAT_INSERT_TO_CURSOR_POSITION:
                var cursorState = insertAtCursor(message);
                // add information about editor state and send telemetry event
                // only include files that are accessible via lsp which have absolute paths
                // When this fails, we will still send the request for
                // amazonq_interactWithMessage telemetry
                getOpenFileUri().ifPresent(filePathUri -> {
                    message.addValueForKey("textDocument", new TextDocumentIdentifier(filePathUri));
                    cursorState.ifPresent(state -> message.addValueForKey("cursorState", Arrays.asList(state)));
                });
                chatCommunicationManager.sendMessageToChatServer(command, message);
                break;
            case TELEMETRY_EVENT:
                // telemetry notification for insert to cursor is modified and forwarded to
                // server in the InsertToCursorPosition handler
                if (isInsertToCursorEvent(message)) {
                    break;
                }
                chatCommunicationManager.sendMessageToChatServer(command, message);
                break;
            case CHAT_COPY_TO_CLIPBOARD:
                handleCopyToClipboard(message.getValueAsString("code"));
                break;
            case AUTH_FOLLOW_UP_CLICKED:
                handleAuthFollowUpClicked(message);
                break;
            case DISCLAIMER_ACKNOWLEDGED:
                Activator.getPluginStore().put(PluginStoreKeys.CHAT_DISCLAIMER_ACKNOWLEDGED, "true");
                break;
            case PROMPT_OPTION_ACKNOWLEDGED:
                if (!(message.getData() instanceof Map)) {
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, String> options = (Map<String, String>) message.getData();
                String messageId = options.get("messageId");
                promptAcknowledgmentHandler.acknowledge(messageId);
                break;
            case GET_SERIALIZED_CHAT:
                ChatAsyncResultManager.getInstance().setResult(parsedCommand.getRequestId(), message.getData());
                Activator.getLogger().info("Got serialized chat response for request ID: " + parsedCommand.getRequestId());
                break;
            case CHAT_OPEN_TAB:
                ChatAsyncResultManager.getInstance().setResult(parsedCommand.getRequestId(), message.getData());
                Activator.getLogger().info("Got open tab response for request ID: " + parsedCommand.getRequestId());
                break;
            case OPEN_SETTINGS:
                AmazonQPreferencePage.openPreferencePane();
                break;
            default:
                throw new AmazonQPluginException("Unexpected command received from Amazon Q Chat: " + command.toString());
        }
    }

    /*
     *   Inserts the text present in parameters at caret position in editor
     *   and returns cursor state range from the start caret to end caret, which includes the entire inserted text range
     */
    private Optional<CursorState> insertAtCursor(final ChatMessage message) {
        AtomicReference<Optional<Range>> range = new AtomicReference<Optional<Range>>();
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                range.set(QEclipseEditorUtils.insertAtCursor(message.getValueAsString("code")));
            }
        });
        return range.get().map(CursorState::new);
    }

    private void validateAndHandleLink(final String link) {
        if (link == null || link.isEmpty()) {
            throw new IllegalArgumentException("Link parameter cannot be null or empty");
        }
        PluginUtils.handleExternalLinkClick(link);
    }

    private boolean isInsertToCursorEvent(final ChatMessage message) {
        return Optional.ofNullable(message.getValueForKey("name"))
                .map(JsonNode::asText)
                .map("insertToCursorPosition"::equals)
                .orElse(false);
    }

    private Optional<String> getOpenFileUri() {
        AtomicReference<Optional<String>> fileUri = new AtomicReference<Optional<String>>();
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                fileUri.set(QEclipseEditorUtils.getOpenFileUri());
            }
        });
        return fileUri.get();
    }

    private void handleCopyToClipboard(final String selection) {
        Display display = PlatformUI.getWorkbench().getDisplay();

        display.asyncExec(() -> {
            Clipboard clipboard = new Clipboard(display);
            try {
                TextTransfer textTransfer = TextTransfer.getInstance();
                clipboard.setContents(new Object[]{selection}, new Transfer[]{textTransfer});
            } catch (Exception e) {
                throw new AmazonQPluginException("Failed to copy to clipboard", e);
            } finally {
                clipboard.dispose();
            }
        });
    }

    private void handleAuthFollowUpClicked(final ChatMessage message) {
        String incomingType = message.getValueAsString("authFollowupType");
        String fullAuth =  AuthFollowUpType.FULL_AUTH.getValue();
        String reAuth = AuthFollowUpType.RE_AUTH.getValue();
        String missingScopes = AuthFollowUpType.MISSING_SCOPES.getValue();
        String useSupportedAuth = AuthFollowUpType.USE_SUPPORTED_AUTH.getValue();

        try {
            if (incomingType.equals(reAuth) || incomingType.equals(missingScopes)) {
                boolean loginOnInvalidToken = true;
                Activator.getLoginService().reAuthenticate(loginOnInvalidToken).get();
                return;
            }
        } catch (Exception ex) {
            PluginUtils.showErrorDialog("Amazon Q", Constants.RE_AUTHENTICATE_FAILURE_MESSAGE);
            throw new AmazonQPluginException("Failed to re-authenticate when auth follow up clicked", ex);
        }

        try {
            if (incomingType.equals(fullAuth) || incomingType.equals(useSupportedAuth)) {
                Activator.getLoginService().logout();
                return;
            }
        } catch (Exception ex) {
            PluginUtils.showErrorDialog("Amazon Q", Constants.AUTHENTICATE_FAILURE_MESSAGE);
            throw new AmazonQPluginException("Failed to authenticate when auth follow up clicked", ex);
        }

        PluginUtils.showErrorDialog("Amazon Q", Constants.AUTHENTICATE_FAILURE_MESSAGE);
        throw new AmazonQPluginException("Error occured while attempting to handle auth follow up: Unknown AuthFollowUpType " + incomingType);
    }
}
