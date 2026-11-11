// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.providers.assets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.junit.jupiter.api.Test;

import software.aws.toolkits.eclipse.amazonq.configuration.DeprecationAcknowledgmentStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStoreKeys;

public final class ChatClientConfigurationTest {

    @Test
    void forwardsAcknowledgedStateForLegacyAndDeprecationPrompts() {
        var pluginStore = new InMemoryPluginStore();
        pluginStore.put(PluginStoreKeys.CHAT_DISCLAIMER_ACKNOWLEDGED, "true");
        pluginStore.put(PluginStoreKeys.PAIR_PROGRAMMING_ACKNOWLEDGED, "true");
        var deprecationStore = new DeprecationAcknowledgmentStore(
                new EclipsePreferences(), pluginStore, null);
        deprecationStore.acknowledge(PluginStoreKeys.CHAT_DEPRECATION_NOTICE_ACKNOWLEDGED);

        String javaScript = ChatClientConfiguration.load(pluginStore, deprecationStore)
                .toJavaScript();

        assertTrue(javaScript.contains("disclaimerAcknowledged: true"));
        assertTrue(javaScript.contains("pairProgrammingAcknowledged: true"));
        assertTrue(javaScript.contains("deprecationNoticeAcknowledged: true"));
    }

    @Test
    void forwardsFalseForUnacknowledgedDeprecationPrompt() {
        var pluginStore = new InMemoryPluginStore();
        var deprecationStore = new DeprecationAcknowledgmentStore(
                new EclipsePreferences(), pluginStore, null);

        String javaScript = ChatClientConfiguration.load(pluginStore, deprecationStore)
                .toJavaScript();

        assertTrue(javaScript.contains("deprecationNoticeAcknowledged: false"));
    }

    private static final class InMemoryPluginStore implements PluginStore {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public void put(final String key, final String value) {
            values.put(key, value);
        }

        @Override
        public String get(final String key) {
            return values.get(key);
        }

        @Override
        public void remove(final String key) {
            values.remove(key);
        }

        @Override
        public void addChangeListener(final IPreferenceChangeListener prefChangeListener) {
        }

        @Override
        public <T> void putObject(final String key, final T value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getObject(final String key, final Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }
}
