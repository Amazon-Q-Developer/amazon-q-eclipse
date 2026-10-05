// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

public class WorkspaceUtilsTest {

    private MockedStatic<ResourcesPlugin> mockedResourcesPlugin;
    private MockedStatic<Activator> mockedActivator;
    private IWorkspaceRoot mockRoot;

    @BeforeEach
    public final void setUp() {
        mockedResourcesPlugin = mockStatic(ResourcesPlugin.class);
        mockedActivator = mockStatic(Activator.class);
        mockedActivator.when(Activator::getLogger).thenReturn(mock(LoggingService.class));

        IWorkspace mockWorkspace = mock(IWorkspace.class);
        mockRoot = mock(IWorkspaceRoot.class);
        when(mockWorkspace.getRoot()).thenReturn(mockRoot);
        mockedResourcesPlugin.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);
    }

    @AfterEach
    public final void tearDown() {
        mockedActivator.close();
        mockedResourcesPlugin.close();
    }

    @Test
    public void testGetWorkspaceFilePathReturnsWorkspaceRootLocation() {
        IPath mockPath = mock(IPath.class);
        when(mockPath.toOSString()).thenReturn("/home/user/workspace");
        when(mockRoot.getLocation()).thenReturn(mockPath);

        assertEquals("/home/user/workspace", WorkspaceUtils.getWorkspaceFilePath());
    }

    @Test
    public void testGetWorkspaceFilePathReturnsNullWhenLocationIsNull() {
        when(mockRoot.getLocation()).thenReturn(null);

        assertNull(WorkspaceUtils.getWorkspaceFilePath());
    }

    @Test
    public void testGetWorkspaceFilePathReturnsNullWhenLocationIsBlank() {
        IPath mockPath = mock(IPath.class);
        when(mockPath.toOSString()).thenReturn("  ");
        when(mockRoot.getLocation()).thenReturn(mockPath);

        assertNull(WorkspaceUtils.getWorkspaceFilePath());
    }

    @Test
    public void testGetWorkspaceFilePathReturnsNullWhenWorkspaceUnavailable() {
        mockedResourcesPlugin.when(ResourcesPlugin::getWorkspace).thenThrow(new IllegalStateException("not running"));

        assertNull(WorkspaceUtils.getWorkspaceFilePath());
    }
}
