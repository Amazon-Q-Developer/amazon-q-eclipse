// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.junit.jupiter.api.Test;
import org.osgi.service.prefs.BackingStoreException;

public final class DeprecationAcknowledgmentStoreTest {

    private static final String TEST_KEY = "testAcknowledgement";

    @Test
    void storesAcknowledgementInInstallationPreferences() {
        var installationPreferences = new EclipsePreferences();
        var workspaceFallback = new InMemoryPluginStore();
        var store = new DeprecationAcknowledgmentStore(
                installationPreferences, workspaceFallback, null);

        store.acknowledge(TEST_KEY);

        assertTrue(store.isAcknowledged(TEST_KEY));
        assertEquals("true", installationPreferences.get(TEST_KEY, null));
        assertFalse(workspaceFallback.contains(TEST_KEY));
    }

    @Test
    void readsUnacknowledgedInstallationPreference() {
        var store = new DeprecationAcknowledgmentStore(
                new EclipsePreferences(), new InMemoryPluginStore(), null);

        assertFalse(store.isAcknowledged(TEST_KEY));
    }

    @Test
    void fallsBackToWorkspaceWhenInstallationPreferencesAreUnavailable() {
        var workspaceFallback = new InMemoryPluginStore();
        workspaceFallback.put(TEST_KEY, "true");
        var store = new DeprecationAcknowledgmentStore(
                new UnwritablePreferences(), workspaceFallback, null);

        assertTrue(store.isAcknowledged(TEST_KEY));

        store.acknowledge("anotherKey");
        assertEquals("true", workspaceFallback.get("anotherKey"));
    }

    private static final class UnwritablePreferences extends EclipsePreferences {
        @Override
        public void flush() throws BackingStoreException {
            throw new BackingStoreException("configuration scope is read-only");
        }
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
