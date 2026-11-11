// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.providers.assets;

import software.aws.toolkits.eclipse.amazonq.configuration.DeprecationAcknowledgmentStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStoreKeys;

final class ChatClientConfiguration {

    private final boolean disclaimerAcknowledged;
    private final boolean pairProgrammingAcknowledged;
    private final boolean deprecationNoticeAcknowledged;

    private ChatClientConfiguration(final boolean disclaimerAcknowledged,
            final boolean pairProgrammingAcknowledged, final boolean deprecationNoticeAcknowledged) {
        this.disclaimerAcknowledged = disclaimerAcknowledged;
        this.pairProgrammingAcknowledged = pairProgrammingAcknowledged;
        this.deprecationNoticeAcknowledged = deprecationNoticeAcknowledged;
    }

    static ChatClientConfiguration load(final PluginStore pluginStore,
            final DeprecationAcknowledgmentStore deprecationAcknowledgmentStore) {
        return new ChatClientConfiguration(
                "true".equals(pluginStore.get(PluginStoreKeys.CHAT_DISCLAIMER_ACKNOWLEDGED)),
                "true".equals(pluginStore.get(PluginStoreKeys.PAIR_PROGRAMMING_ACKNOWLEDGED)),
                deprecationAcknowledgmentStore.isAcknowledged(
                        PluginStoreKeys.CHAT_DEPRECATION_NOTICE_ACKNOWLEDGED));
    }

    String toJavaScript() {
        return String.format("""
                disclaimerAcknowledged: %b,
                pairProgrammingAcknowledged: %b,
                deprecationNoticeAcknowledged: %b,
                """, disclaimerAcknowledged, pairProgrammingAcknowledged, deprecationNoticeAcknowledged);
    }
}
