// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;

/** Covers the ETag fetch + graceful-degradation matrix with an injected mock HttpClient. */
public final class NotificationsFetcherTest {

    private static final String URL = "https://example.com/Notifications/Eclipse/combined/2.x.json";
    private static final String VALID_JSON =
            "{ \"schema\": { \"version\": \"2.0\" }, \"notifications\": [] }";

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorExtension = new ActivatorStaticMockExtension();

    private HttpClient httpClient;
    private PluginStore pluginStore;

    @TempDir
    private Path cacheDir;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        pluginStore = activatorExtension.getMock(PluginStore.class);
    }

    private NotificationsFetcher fetcher() {
        return new NotificationsFetcher(URL, httpClient, cacheDir.resolve("notifications.json"));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(final int status, final String body) {
        final HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        when(resp.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
        return resp;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> responseWithEtag(final int status, final String body, final String etag) {
        final HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        when(resp.headers()).thenReturn(
                java.net.http.HttpHeaders.of(java.util.Map.of("ETag", java.util.List.of(etag)), (a, b) -> true));
        return resp;
    }

    @Test
    @SuppressWarnings("unchecked")
    void ok200ParsesAndCaches() throws Exception {
        doReturn(response(200, VALID_JSON)).when(httpClient).send(any(HttpRequest.class), any());
        final Optional<NotificationsList> result = fetcher().fetch();
        assertTrue(result.isPresent());
        assertTrue(Files.exists(cacheDir.resolve("notifications.json")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void notFound404IsSilentNoOp() throws Exception {
        doReturn(response(404, "")).when(httpClient).send(any(HttpRequest.class), any());
        assertTrue(fetcher().fetch().isEmpty());
        verify(activatorExtension.getMock(software.aws.toolkits.eclipse.amazonq.util.LoggingService.class), never())
                .error(any(String.class), any(Throwable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformed200FallsBackToEmptyWhenNoCache() throws Exception {
        doReturn(response(200, "{ not json")).when(httpClient).send(any(HttpRequest.class), any());
        assertTrue(fetcher().fetch().isEmpty());
        // A bad body must not be cached.
        assertFalse(Files.exists(cacheDir.resolve("notifications.json")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void notModified304WithMissingCacheClearsEtag() throws Exception {
        when(pluginStore.get(URL)).thenReturn("\"etag-1\"");
        doReturn(response(304, "")).when(httpClient).send(any(HttpRequest.class), any());
        assertTrue(fetcher().fetch().isEmpty());
        verify(pluginStore).remove(URL);
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutReturnsEmptyWithoutThrowing() throws Exception {
        doThrow(new java.io.IOException("timeout")).when(httpClient).send(any(HttpRequest.class), any());
        assertDoesNotThrow(() -> assertTrue(fetcher().fetch().isEmpty()));
    }

    @Test
    void fileSchemeReadsLocalFileWithoutHttp() throws Exception {
        final Path local = cacheDir.resolve("local.json");
        Files.writeString(local, VALID_JSON);
        final NotificationsFetcher fileFetcher =
                new NotificationsFetcher(local.toUri().toString(), httpClient, cacheDir.resolve("notifications.json"));
        assertTrue(fileFetcher.fetch().isPresent());
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void blankEndpointReadsCacheOnly() throws Exception {
        final NotificationsFetcher blank = new NotificationsFetcher("", httpClient, cacheDir.resolve("notifications.json"));
        assertEquals(Optional.empty(), blank.fetch());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ok200WithEtagStoresEtagForConditionalGet() throws Exception {
        doReturn(responseWithEtag(200, VALID_JSON, "\"etag-42\"")).when(httpClient).send(any(HttpRequest.class), any());
        assertTrue(fetcher().fetch().isPresent());
        // The ETag must be persisted (keyed by URL) so the next poll can send If-None-Match.
        verify(pluginStore).put(URL, "\"etag-42\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void subsequentPollSendsIfNoneMatchWhenCachedAndEtagPresent() throws Exception {
        // Seed a cached file and a stored ETag, then confirm the next request carries If-None-Match.
        Files.writeString(cacheDir.resolve("notifications.json"), VALID_JSON);
        when(pluginStore.get(URL)).thenReturn("\"etag-42\"");
        final org.mockito.ArgumentCaptor<HttpRequest> captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        doReturn(response(304, "")).when(httpClient).send(captor.capture(), any());
        fetcher().fetch();
        assertEquals(Optional.of("\"etag-42\""), captor.getValue().headers().firstValue("If-None-Match"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void notModified304WithCachePresentReturnsCachedWithoutReDownload() throws Exception {
        Files.writeString(cacheDir.resolve("notifications.json"), VALID_JSON);
        when(pluginStore.get(URL)).thenReturn("\"etag-42\"");
        doReturn(response(304, "")).when(httpClient).send(any(HttpRequest.class), any());
        assertTrue(fetcher().fetch().isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void serverError500IsSilentNoOp() throws Exception {
        doReturn(response(500, "")).when(httpClient).send(any(HttpRequest.class), any());
        assertTrue(fetcher().fetch().isEmpty());
        verify(activatorExtension.getMock(software.aws.toolkits.eclipse.amazonq.util.LoggingService.class), never())
                .error(any(String.class), any(Throwable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void networkFailureFallsBackToCacheWhenPresent() throws Exception {
        Files.writeString(cacheDir.resolve("notifications.json"), VALID_JSON);
        doThrow(new java.io.IOException("offline")).when(httpClient).send(any(HttpRequest.class), any());
        // With a valid cache, a persistent network failure serves the cached payload rather than empty.
        assertTrue(fetcher().fetch().isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void oversizedPayloadIsIgnored() throws Exception {
        // A payload beyond the size cap must not be parsed or cached.
        final StringBuilder huge = new StringBuilder("{ \"schema\": { \"version\": \"2.0\" }, \"notifications\": [");
        while (huge.length() < 1_100_000) {
            huge.append("{\"id\":\"x\",\"schedule\":{\"type\":\"Emergency\"},\"severity\":\"Info\"},");
        }
        huge.append("] }");
        doReturn(response(200, huge.toString())).when(httpClient).send(any(HttpRequest.class), any());
        assertTrue(fetcher().fetch().isEmpty());
        assertFalse(Files.exists(cacheDir.resolve("notifications.json")));
    }
}
