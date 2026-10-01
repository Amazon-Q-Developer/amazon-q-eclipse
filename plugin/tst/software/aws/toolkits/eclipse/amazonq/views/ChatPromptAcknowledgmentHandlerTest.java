// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.aws.toolkits.eclipse.amazonq.configuration.DeprecationAcknowledgmentStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.PluginStoreKeys;

public final class ChatPromptAcknowledgmentHandlerTest {

    private InMemoryPluginStore pluginStore;
    private DeprecationAcknowledgmentStore deprecationStore;
    private ChatPromptAcknowledgmentHandler handler;

    @BeforeEach
    void setUp() {
        pluginStore = new InMemoryPluginStore();
        deprecationStore = new DeprecationAcknowledgmentStore(
                new EclipsePreferences(), pluginStore, null);
        handler = new ChatPromptAcknowledgmentHandler(pluginStore, deprecationStore);
    }

    @Test
    void persistsDeprecationNoticeAcknowledgement() {
        handler.acknowledge(ChatPromptAcknowledgmentHandler.DEPRECATION_NOTICE_MESSAGE_ID);

        assertTrue(deprecationStore.isAcknowledged(
                PluginStoreKeys.CHAT_DEPRECATION_NOTICE_ACKNOWLEDGED));
    }

    @Test
    void retainsPairProgrammingAcknowledgementHandling() {
        handler.acknowledge(ChatPromptAcknowledgmentHandler.PAIR_PROGRAMMING_MESSAGE_ID);

        assertEquals("true", pluginStore.get(PluginStoreKeys.PAIR_PROGRAMMING_ACKNOWLEDGED));
    }

    @Test
    void ignoresUnknownAndMissingMessageIds() {
        handler.acknowledge("unknown");
        handler.acknowledge(null);

        assertFalse(pluginStore.contains(PluginStoreKeys.PAIR_PROGRAMMING_ACKNOWLEDGED));
        assertFalse(deprecationStore.isAcknowledged(
                PluginStoreKeys.CHAT_DEPRECATION_NOTICE_ACKNOWLEDGED));
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

        boolean contains(final String key) {
            return values.containsKey(key);
        }
    }
}
