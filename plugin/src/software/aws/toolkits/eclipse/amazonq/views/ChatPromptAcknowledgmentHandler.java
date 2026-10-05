// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import software.aws.toolkits.eclipse.amazonq.configuration.DeprecationAcknowledgmentStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStoreKeys;

final class ChatPromptAcknowledgmentHandler {

    static final String PAIR_PROGRAMMING_MESSAGE_ID = "programmerModeCardId";
    static final String DEPRECATION_NOTICE_MESSAGE_ID = "client-deprecation-notice";

    private final PluginStore pluginStore;
    private final DeprecationAcknowledgmentStore deprecationAcknowledgmentStore;

    ChatPromptAcknowledgmentHandler(final PluginStore pluginStore,
            final DeprecationAcknowledgmentStore deprecationAcknowledgmentStore) {
        this.pluginStore = pluginStore;
        this.deprecationAcknowledgmentStore = deprecationAcknowledgmentStore;
    }

    void acknowledge(final String messageId) {
        if (PAIR_PROGRAMMING_MESSAGE_ID.equals(messageId)) {
            pluginStore.put(PluginStoreKeys.PAIR_PROGRAMMING_ACKNOWLEDGED, "true");
        } else if (DEPRECATION_NOTICE_MESSAGE_ID.equals(messageId)) {
            deprecationAcknowledgmentStore.acknowledge(
                    PluginStoreKeys.CHAT_DEPRECATION_NOTICE_ACKNOWLEDGED);
        }
    }
}
