// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.HttpClientFactory;
import software.aws.toolkits.eclipse.amazonq.util.ObjectMapperFactory;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;

/**
 * Fetches the hosted notifications payload with ETag conditional GET + on-disk caching, modeled on
 * {@code VersionManifestFetcher}. {@link #fetch()} is a TOTAL function: any failure (absent file, 403/404, empty body,
 * malformed JSON, network error) resolves to {@link Optional#empty()} and is only logged — it never throws and never
 * surfaces a user-facing popup, so a not-yet-deployed endpoint is a silent no-op.
 */
public final class NotificationsFetcher {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 1000L;
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();

    private final String endpointUrl;
    private final HttpClient httpClient;
    private final Path cachePath;

    public NotificationsFetcher(final String endpointUrl) {
        this(endpointUrl, null, null);
    }

    public NotificationsFetcher(final String endpointUrl, final HttpClient httpClient, final Path cachePath) {
        // Trim stray whitespace/newlines (a common copy-paste artifact when the endpoint is set via env var / preference).
        this.endpointUrl = endpointUrl == null ? null : endpointUrl.trim();
        this.httpClient = httpClient != null ? httpClient : HttpClientFactory.getInstance();
        this.cachePath = cachePath != null ? cachePath
                : PluginUtils.getPluginDir(NotificationConstants.NOTIFICATIONS_SUBDIRECTORY)
                        .resolve(NotificationConstants.NOTIFICATIONS_CACHE_FILENAME);
    }

    /** Never throws. Returns the parsed notifications, or empty when there is nothing to show. */
    public Optional<NotificationsList> fetch() {
        try {
            if (endpointUrl == null || endpointUrl.isBlank()) {
                return getResourceFromCache();
            }
            if (endpointUrl.regionMatches(true, 0, "file:", 0, 5)) {
                return readLocalFile(endpointUrl);
            }
            return fetchRemoteWithRetries();
        } catch (Exception e) {
            Activator.getLogger().warn("Unexpected error fetching notifications", e);
            return Optional.empty();
        }
    }

    private Optional<NotificationsList> fetchRemoteWithRetries() {
        final Optional<NotificationsList> cached = getResourceFromCache();
        final String cachedEtag = Activator.getPluginStore().get(endpointUrl);
        final String etagToRequest = cached.isPresent() && cachedEtag != null ? cachedEtag : null;

        Exception lastTransient = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                final HttpResponse<String> response = getResourceFromRemote(etagToRequest);
                final int status = response.statusCode();

                if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    if (cached.isPresent()) {
                        return cached;
                    }
                    // ETag stored but cache is gone/invalid: clear it so the next poll re-fetches fresh.
                    Activator.getLogger().warn("Notifications returned 304 but cache is missing; clearing ETag");
                    Activator.getPluginStore().remove(endpointUrl);
                    return Optional.empty();
                }
                if (status == HttpURLConnection.HTTP_OK) {
                    return validateAndCache(response.body());
                }
                // 403/404 (file not deployed yet) and any other non-2xx: not an error condition, show nothing.
                Activator.getLogger().info("No notifications available (HTTP " + status + ")");
                return Optional.empty();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return cached;
                }
                lastTransient = e;
                sleepBeforeRetry(attempt);
            }
        }
        Activator.getLogger().warn("Failed to fetch notifications after retries; using cache if present", lastTransient);
        return cached;
    }

    private HttpResponse<String> getResourceFromRemote(final String etag) throws IOException, InterruptedException {
        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        Optional.ofNullable(etag).ifPresent(tag -> requestBuilder.header("If-None-Match", tag));
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Optional<NotificationsList> readLocalFile(final String fileUrl) {
        try {
            // Prefer strict URI parsing; fall back to stripping the scheme for a plain path if the URI is not
            // strictly legal (e.g. an un-encoded path pasted as file:///...).
            Path path;
            try {
                path = Path.of(URI.create(fileUrl));
            } catch (IllegalArgumentException e) {
                path = Path.of(fileUrl.replaceFirst("(?i)^file://", ""));
            }
            return validate(Files.readString(path));
        } catch (Exception e) {
            Activator.getLogger().warn("Failed to read local notifications file: " + fileUrl, e);
            return Optional.empty();
        }
    }

    private Optional<NotificationsList> getResourceFromCache() {
        try {
            if (Files.exists(cachePath)) {
                final Optional<NotificationsList> parsed = validate(Files.readString(cachePath));
                if (parsed.isEmpty()) {
                    Files.deleteIfExists(cachePath);
                    Activator.getLogger().info("Deleted corrupt cached notifications file");
                }
                return parsed;
            }
        } catch (Exception e) {
            Activator.getLogger().warn("Error reading cached notifications", e);
        }
        return Optional.empty();
    }

    private Optional<NotificationsList> validate(final String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(OBJECT_MAPPER.readValue(content, NotificationsList.class));
        } catch (Exception e) {
            Activator.getLogger().warn("Failed to parse notifications payload", e);
            return Optional.empty();
        }
    }

    private Optional<NotificationsList> validateAndCache(final String body) {
        final Optional<NotificationsList> parsed = validate(body);
        if (parsed.isEmpty()) {
            // Do not cache a bad body; keep any prior valid cache untouched.
            return getResourceFromCache();
        }
        try {
            final Path tmp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.createDirectories(cachePath.getParent());
            Files.writeString(tmp, body);
            Files.move(tmp, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Activator.getLogger().warn("Failed to cache notifications file", e);
        }
        return parsed;
    }

    private void sleepBeforeRetry(final int attempt) {
        if (attempt >= MAX_RETRIES - 1) {
            return;
        }
        try {
            Thread.sleep(RETRY_BASE_DELAY_MS * (1L << attempt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
