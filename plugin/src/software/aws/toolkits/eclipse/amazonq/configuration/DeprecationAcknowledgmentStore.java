// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.configuration;

import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.LoggingService;

/**
 * Persists deprecation acknowledgements at the Eclipse installation level when possible.
 *
 * <p>Some Eclipse installations do not allow writes to the configuration scope. In those
 * environments, acknowledgements fall back to the existing workspace-scoped plugin store.
 */
public final class DeprecationAcknowledgmentStore {

    private static final String STORAGE_PROBE_KEY = "deprecationAcknowledgmentStorageProbe";

    private final IEclipsePreferences installationPreferences;
    private final PluginStore workspaceFallback;
    private final LoggingService logger;

    private boolean storageChecked;
    private boolean useWorkspaceFallback;

    public DeprecationAcknowledgmentStore(final IEclipsePreferences installationPreferences,
            final PluginStore workspaceFallback, final LoggingService logger) {
        this.installationPreferences = installationPreferences;
        this.workspaceFallback = workspaceFallback;
        this.logger = logger;
    }

    public static DeprecationAcknowledgmentStore getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public synchronized boolean isAcknowledged(final String key) {
        ensureStorageAvailable();
        if (useWorkspaceFallback) {
            return "true".equals(workspaceFallback.get(key));
        }
        try {
            return "true".equals(installationPreferences.get(key, null));
        } catch (RuntimeException e) {
            switchToWorkspaceFallback("reading", key, e);
            return "true".equals(workspaceFallback.get(key));
        }
    }

    public synchronized void acknowledge(final String key) {
        ensureStorageAvailable();
        if (useWorkspaceFallback) {
            workspaceFallback.put(key, "true");
            return;
        }
        try {
            installationPreferences.put(key, "true");
            installationPreferences.flush();
        } catch (BackingStoreException | RuntimeException e) {
            switchToWorkspaceFallback("saving", key, e);
            workspaceFallback.put(key, "true");
        }
    }

    private void ensureStorageAvailable() {
        if (storageChecked) {
            return;
        }
        storageChecked = true;
        try {
            installationPreferences.put(STORAGE_PROBE_KEY, "true");
            installationPreferences.flush();
            installationPreferences.remove(STORAGE_PROBE_KEY);
            installationPreferences.flush();
        } catch (BackingStoreException | RuntimeException e) {
            try {
                installationPreferences.remove(STORAGE_PROBE_KEY);
            } catch (RuntimeException ignored) {
                // The installation scope is already known to be unavailable.
            }
            switchToWorkspaceFallback("validating", STORAGE_PROBE_KEY, e);
        }
    }

    private void switchToWorkspaceFallback(final String operation, final String key, final Exception exception) {
        useWorkspaceFallback = true;
        if (logger != null) {
            logger.warn(String.format(
                    "Unable to use installation-level preferences while %s deprecation acknowledgement '%s'; using workspace preferences",
                    operation, key), exception);
        }
    }

    private static final class InstanceHolder {
        private static final DeprecationAcknowledgmentStore INSTANCE = new DeprecationAcknowledgmentStore(
                ConfigurationScope.INSTANCE.getNode(Activator.PLUGIN_ID),
                Activator.getPluginStore(),
                Activator.getLogger());

        private InstanceHolder() {
        }
    }
}
