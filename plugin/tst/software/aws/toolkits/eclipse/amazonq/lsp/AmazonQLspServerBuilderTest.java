// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import software.aws.toolkits.eclipse.amazonq.telemetry.metadata.ClientMetadata;
import software.aws.toolkits.eclipse.amazonq.telemetry.metadata.PluginClientMetadata;
import software.aws.toolkits.eclipse.amazonq.util.WorkspaceUtils;

/**
 * The server keys its chat history database on
 * {@code aws.awsClientCapabilities.q.workspaceFilePath}. The key is a plain string in a nested
 * map, so a typo or a dropped entry would compile, pass every other test, and silently restore
 * the wrong chat tabs. These tests pin the wire shape.
 */
public class AmazonQLspServerBuilderTest {

    private static final String WORKSPACE_ROOT = "/home/user/eclipse-workspace";

    private MockedStatic<WorkspaceUtils> mockedWorkspaceUtils;
    private MockedStatic<PluginClientMetadata> mockedClientMetadata;

    @BeforeEach
    public final void setUp() {
        mockedWorkspaceUtils = mockStatic(WorkspaceUtils.class);

        ClientMetadata metadata = mock(ClientMetadata.class);
        when(metadata.getPluginVersion()).thenReturn("1.0.0");
        when(metadata.getClientId()).thenReturn("client-id");
        when(metadata.getIdeVersion()).thenReturn("2026-09");
        when(metadata.getIdeName()).thenReturn("Eclipse");
        mockedClientMetadata = mockStatic(PluginClientMetadata.class);
        mockedClientMetadata.when(PluginClientMetadata::getInstance).thenReturn(metadata);
    }

    @AfterEach
    public final void tearDown() {
        mockedClientMetadata.close();
        mockedWorkspaceUtils.close();
    }

    @Test
    public void testSendsWorkspaceFilePathWhenWorkspaceRootIsKnown() {
        mockedWorkspaceUtils.when(WorkspaceUtils::getWorkspaceRootPath).thenReturn(WORKSPACE_ROOT);

        Map<String, Object> qCapabilities = qCapabilitiesFromInitialize();

        assertTrue(qCapabilities.containsKey("workspaceFilePath"));
        assertEquals(WORKSPACE_ROOT, qCapabilities.get("workspaceFilePath"));
    }

    @Test
    public void testOmitsWorkspaceFilePathWhenWorkspaceRootIsUnknown() {
        mockedWorkspaceUtils.when(WorkspaceUtils::getWorkspaceRootPath).thenReturn(null);

        Map<String, Object> qCapabilities = qCapabilitiesFromInitialize();

        // Absent rather than null: the server treats any truthy value as an identifier, so a null
        // entry must not be sent.
        assertFalse(qCapabilities.containsKey("workspaceFilePath"));
    }

    @Test
    public void testKeepsOtherQCapabilitiesWhenWorkspaceRootIsUnknown() {
        mockedWorkspaceUtils.when(WorkspaceUtils::getWorkspaceRootPath).thenReturn(null);

        Map<String, Object> qCapabilities = qCapabilitiesFromInitialize();

        assertEquals(true, qCapabilities.get("mcp"));
        assertEquals(true, qCapabilities.get("pinnedContextEnabled"));
        assertEquals(true, qCapabilities.get("modelSelection"));
        assertEquals(true, qCapabilities.get("developerProfiles"));
        assertEquals(true, qCapabilities.get("customizationsWithMetadata"));
    }

    /**
     * Drives an {@code initialize} request through the builder's message consumer and returns the
     * {@code aws.awsClientCapabilities.q} map the builder attached to it.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> qCapabilitiesFromInitialize() {
        InitializeParams initParams = new InitializeParams();
        RequestMessage message = new RequestMessage();
        message.setMethod("initialize");
        message.setParams(initParams);

        MessageConsumer consumer = new AmazonQLspServerBuilder().wrapMessageConsumer(m -> { });
        consumer.consume(message);

        Map<String, Object> initOptions = (Map<String, Object>) initParams.getInitializationOptions();
        Map<String, Object> aws = (Map<String, Object>) initOptions.get("aws");
        Map<String, Object> clientCapabilities = (Map<String, Object>) aws.get("awsClientCapabilities");
        return (Map<String, Object>) clientCapabilities.get("q");
    }
}
