// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

public final class WorkspaceUtils {

    private WorkspaceUtils() { }

    /**
     * Returns a stable, filesystem-based identifier for the current Eclipse workspace:
     * the canonical absolute path of the workspace root (the directory that holds
     * {@code .metadata}).
     *
     * <p>The language server keys its chat history database on this value when it is provided
     * ({@code awsClientCapabilities.q.workspaceFilePath}). Without it the server falls back to
     * hashing the open project folders, which changes whenever a project is opened, closed,
     * imported or deleted, so a different history file is loaded on the next restart and
     * previously open chat tabs are not restored.
     *
     * <p>The path is canonicalized so that a symlinked {@code -data} directory, a redundant path
     * segment, or a different drive-letter case all resolve to the same identifier. If
     * canonicalization fails the literal path is returned, which is still stable for the common
     * case.
     *
     * @return the workspace root path, or {@code null} if it cannot be determined
     */
    public static String getWorkspaceRootPath() {
        try {
            IPath location = ResourcesPlugin.getWorkspace().getRoot().getLocation();
            if (location == null) {
                return null;
            }
            String osPath = location.toOSString();
            if (osPath.isBlank()) {
                return null;
            }
            try {
                return location.toFile().getCanonicalPath();
            } catch (IOException e) {
                Activator.getLogger().warn("Failed to canonicalize workspace location, using the literal path", e);
                return osPath;
            }
        } catch (Exception e) {
            Activator.getLogger().warn("Failed to determine workspace location", e);
            return null;
        }
    }

    public static void refreshAllProjects() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            try {
                project.refreshLocal(IResource.DEPTH_INFINITE, null);
            } catch (CoreException e) {
                Activator.getLogger().warn("Failed to refresh project(s): " + e.getMessage());
            }
        }
    }

    public static void refreshAdtViews() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return;
            }

            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                return;
            }

            IViewPart adtView = page.findView("com.sap.adt.tools.core.ui.views.objectnavigator");
            if (adtView != null) {
                // Force refresh of ADT view which triggers server sync
                adtView.getSite().getPage().activate(adtView);

                // Send refresh command to ADT view
                var handlerService = adtView.getSite().getService(IHandlerService.class);
                if (handlerService != null) {
                    handlerService.executeCommand("org.eclipse.ui.file.refresh", null);
                }
            }
        } catch (Exception e) {
            Activator.getLogger().error("Failed to refresh ADT views", e);
        }
    }

}
