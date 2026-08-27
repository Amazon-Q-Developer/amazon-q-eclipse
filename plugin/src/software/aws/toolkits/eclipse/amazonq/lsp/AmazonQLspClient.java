// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;

import software.aws.toolkits.eclipse.amazonq.chat.models.GetSerializedChatParams;
import software.aws.toolkits.eclipse.amazonq.chat.models.SerializedChatResult;
import software.aws.toolkits.eclipse.amazonq.chat.models.ShowSaveFileDialogParams;
import software.aws.toolkits.eclipse.amazonq.chat.models.ShowSaveFileDialogResult;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.SsoTokenChangedParams;
import software.aws.toolkits.eclipse.amazonq.lsp.model.ConnectionMetadata;
import software.aws.toolkits.eclipse.amazonq.lsp.model.OpenFileDiffParams;
import software.aws.toolkits.eclipse.amazonq.lsp.model.ShowNotificationParams;

public interface AmazonQLspClient extends LanguageClient {

    @JsonRequest("aws/credentials/getConnectionMetadata")
    CompletableFuture<ConnectionMetadata> getConnectionMetadata();

    @JsonNotification("aws/identity/ssoTokenChanged")
    void ssoTokenChanged(SsoTokenChangedParams params);

    @JsonNotification("aws/chat/sendContextCommands")
    void sendContextCommands(Object params);

    @JsonRequest("aws/chat/openTab")
    CompletableFuture<Object> openTab(Object params);

    @JsonRequest("aws/showSaveFileDialog")
    CompletableFuture<ShowSaveFileDialogResult> showSaveFileDialog(ShowSaveFileDialogParams params);

    @JsonRequest("aws/chat/getSerializedChat")
    CompletableFuture<SerializedChatResult> getSerializedChat(GetSerializedChatParams params);

    @JsonNotification("aws/openFileDiff")
    void openFileDiff(OpenFileDiffParams params);

    @JsonNotification("aws/chat/sendChatUpdate")
    void sendChatUpdate(Object params);

    @JsonNotification("aws/chat/chatOptionsUpdate")
    void chatOptionsUpdate(Object params);

    @JsonNotification("aws/didCopyFile")
    void didCopyFile(Object params);

    @JsonNotification("aws/didWriteFile")
    void didWriteFile(Object params);

    @JsonNotification("aws/didAppendFile")
    void didAppendFile(Object params);

    @JsonNotification("aws/didRemoveFileOrDirectory")
    void didRemoveFileOrDirectory(Object params);

    @JsonNotification("aws/didCreateDirectory")
    void didCreateDirectory(Object params);

    @JsonNotification("aws/window/showNotification")
    void showNotification(ShowNotificationParams params);

    @JsonNotification("aws/chat/sendPinnedContext")
    void sendPinnedContext(Object params);
}
