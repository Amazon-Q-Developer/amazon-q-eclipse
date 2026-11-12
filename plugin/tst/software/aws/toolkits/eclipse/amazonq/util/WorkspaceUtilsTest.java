// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

public class WorkspaceUtilsTest {

    private MockedStatic<ResourcesPlugin> mockedResourcesPlugin;
    private MockedStatic<Activator> mockedActivator;
    private IWorkspaceRoot mockRoot;
    private LoggingService mockLogger;

    @BeforeEach
    public final void setUp() {
        mockedResourcesPlugin = mockStatic(ResourcesPlugin.class);
        mockedActivator = mockStatic(Activator.class);
        mockLogger = mock(LoggingService.class);
        mockedActivator.when(Activator::getLogger).thenReturn(mockLogger);

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
    public void testReturnsCanonicalWorkspaceRootPath(final @TempDir Path tempDir) throws IOException {
        File workspaceRoot = tempDir.toFile();
        givenWorkspaceLocation(workspaceRoot.getAbsolutePath(), workspaceRoot);

        // Compared against getCanonicalPath() rather than a literal so the assertion holds on
        // Windows and on macOS, where the temp directory is itself reached through a symlink.
        assertEquals(workspaceRoot.getCanonicalPath(), WorkspaceUtils.getWorkspaceRootPath());
    }

    @Test
    public void testResolvesRedundantSegmentsToTheSameIdentifier(final @TempDir Path tempDir) throws IOException {
        File workspaceRoot = tempDir.toFile();
        File subDirectory = new File(workspaceRoot, "projects");
        assertTrue(subDirectory.mkdir(), "failed to create fixture directory");
        File indirect = new File(subDirectory, "..");

        givenWorkspaceLocation(indirect.getPath(), indirect);

        // The whole point of the identifier is stability: two spellings of one directory must not
        // produce two different chat history files.
        assertEquals(workspaceRoot.getCanonicalPath(), WorkspaceUtils.getWorkspaceRootPath());
    }

    @Test
    public void testFallsBackToLiteralPathWhenCanonicalizationFails() throws IOException {
        File failingFile = mock(File.class);
        when(failingFile.getCanonicalPath()).thenThrow(new IOException("cannot resolve"));

        givenWorkspaceLocation("/home/user/workspace", failingFile);

        assertEquals("/home/user/workspace", WorkspaceUtils.getWorkspaceRootPath());
        verify(mockLogger).warn(
                eq("Failed to canonicalize workspace location, using the literal path"),
                any(IOException.class));
    }

    @Test
    public void testReturnsNullWhenLocationIsNull() {
        when(mockRoot.getLocation()).thenReturn(null);

        assertNull(WorkspaceUtils.getWorkspaceRootPath());
    }

    @Test
    public void testReturnsNullWhenLocationIsBlankWithoutTouchingTheFilesystem() {
        IPath mockPath = mock(IPath.class);
        when(mockPath.toOSString()).thenReturn("  ");
        when(mockRoot.getLocation()).thenReturn(mockPath);

        assertNull(WorkspaceUtils.getWorkspaceRootPath());
        // A blank path must short-circuit: new File("").getCanonicalPath() resolves to the process
        // working directory, which would be a wrong and unstable identifier.
        verify(mockPath, never()).toFile();
    }

    @Test
    public void testReturnsNullWhenWorkspaceUnavailable() {
        mockedResourcesPlugin.when(ResourcesPlugin::getWorkspace).thenThrow(new IllegalStateException("not running"));

        assertNull(WorkspaceUtils.getWorkspaceRootPath());
    }

    private void givenWorkspaceLocation(final String osPath, final File file) {
        IPath mockPath = mock(IPath.class);
        when(mockPath.toOSString()).thenReturn(osPath);
        when(mockPath.toFile()).thenReturn(file);
        when(mockRoot.getLocation()).thenReturn(mockPath);
    }
}
