// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.preferences;

import java.util.Collections;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4j.DidChangeConfigurationParams;

import software.aws.toolkits.eclipse.amazonq.notifications.NotificationPollingService;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;

public class AmazonQPreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public final void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(AmazonQPreferencePage.CODE_REFERENCE_OPT_IN, true);
        store.setDefault(AmazonQPreferencePage.TELEMETRY_OPT_IN, true);
        store.setDefault(AmazonQPreferencePage.Q_DATA_SHARING, true);
        store.setDefault(AmazonQPreferencePage.HTTPS_PROXY, "");
        store.setDefault(AmazonQPreferencePage.CA_CERT, "");
        store.setDefault(AmazonQPreferencePage.NOTIFICATIONS_OPT_IN, true);
        store.setDefault(AmazonQPreferencePage.NOTIFICATIONS_ENDPOINT_OVERRIDE, "");
        store.addPropertyChangeListener(event -> {
            // React to the notifications kill-switch so it can pause/resume polling within a session (no restart).
            if (AmazonQPreferencePage.NOTIFICATIONS_OPT_IN.equals(event.getProperty())) {
                NotificationPollingService.getInstance().onEnabledPreferenceChanged();
            }
            ThreadingUtils.executeAsyncTask(() -> {
                Activator.getLspProvider().getAmazonQServer()
                    .thenAccept(server -> server.getWorkspaceService().didChangeConfiguration(
                            new DidChangeConfigurationParams(Collections.EMPTY_MAP)));
            });
        });
    }

}
