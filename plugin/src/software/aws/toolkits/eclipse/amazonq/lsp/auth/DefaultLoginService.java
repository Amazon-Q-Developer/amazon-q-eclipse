// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.customization.CustomizationUtil;
import software.aws.toolkits.eclipse.amazonq.configuration.profiles.QDeveloperProfileUtil;
import software.aws.toolkits.eclipse.amazonq.exception.AmazonQPluginException;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthState;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.InvalidateSsoTokenParams;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginParams;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginType;
import software.aws.toolkits.eclipse.amazonq.lsp.model.UpdateCredentialsPayload;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.providers.lsp.LspProvider;
import software.aws.toolkits.eclipse.amazonq.telemetry.AwsTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.telemetry.AwsTelemetryProvider.BrowserLoginParams;
import software.aws.toolkits.eclipse.amazonq.util.AuthUtil;
import software.aws.toolkits.telemetry.TelemetryDefinitions.CredentialType;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;

/**
 * Core authentication service for the Amazon Q Eclipse plugin that manages
 * user authentication flows and token lifecycle management.
 *
 * Key responsibilities:
 * - Manages authentication workflows (login/logout/expire/re-authenticate)
 * - Coordinates browser-based authentication flows
 * - Maintains authentication session state
 *
 * Important: Maintaining synchronized credentials with the Amazon Q LSP server is critical for proper operation.
 * Communication to the external Amazon Q server is handled by the Amazon Q LSP server acting as a proxy. Outdated
 * credentials may cause inconsistent behavior and failed requests.
 *
 * @see AuthStateManager For authentication state management and persistent storage updates
 * @see AuthTokenService Handles operations related to SSO token
 * @see AuthCredentialsService Handles operations related to LSP server credentials
 */
public final class DefaultLoginService implements LoginService {
    private AuthStateManager authStateManager;
    private AuthTokenService authTokenService;
    private AuthCredentialsService authCredentialsService;
    private AuthPluginStore authPluginStore;

    private DefaultLoginService(final Builder builder) {
        this.authStateManager = Objects.requireNonNull(builder.authStateManager, "authStateManager cannot be null");
        this.authTokenService = Objects.requireNonNull(builder.authTokenService, "authTokenService cannot be null");
        this.authCredentialsService = Objects.requireNonNull(builder.authCredentialsService, "authCredentialsService cannot be null");
        this.authPluginStore = new AuthPluginStore(Objects.requireNonNull(builder.pluginStore, "pluginStore cannot be null"));

        if (builder.initializeOnStartUp) {
            AuthState authState = authStateManager.getAuthState();
            if (!authState.isLoggedOut()) {
                boolean loginOnInvalidToken = false;
                reAuthenticate(loginOnInvalidToken).thenRun(() -> {
                    if (authStateManager.getAuthState().loginType().equals(LoginType.IAM_IDENTITY_CENTER)) {
                        QDeveloperProfileUtil.getInstance().initialize();
                    }
                });
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletableFuture<Void> login(final LoginType loginType, final LoginParams loginParams) {
        if (authStateManager.getAuthState().isLoggedIn()) {
            Activator.getLogger().warn("Attempted to log in while already in a logged in state");
            return CompletableFuture.completedFuture(null);
        }

        Activator.getLogger().info("Attempting to login...");

        return processLogin(loginType, loginParams, true, false)
                .exceptionally(throwable -> {
                    Activator.getLogger().error("Failed to log in", throwable);
                    logout();
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> logout() {
        AuthState authState = getAuthState();

        if (authState.isLoggedOut()) {
            Activator.getLogger().warn("Attempted to log out while already in a logged out state");
            return CompletableFuture.completedFuture(null);
        }

        if (authState.ssoTokenId() == null || authState.ssoTokenId().isBlank()) {
            authStateManager.toLoggedOut();
            Activator.getLogger().warn("Attempted to log out with no ssoTokenId saved in auth state");
            return CompletableFuture.completedFuture(null);
        }

        Activator.getLogger().info("Attempting to log out...");

        InvalidateSsoTokenParams params = new InvalidateSsoTokenParams(authState.ssoTokenId());
        QDeveloperProfileUtil.getInstance().clearSelectedProfile();

        return authTokenService.invalidateSsoToken(params)
                .thenRun(() -> {
                    authCredentialsService.deleteTokenCredentials();
                })
                .thenRun(() -> {
                    authStateManager.toLoggedOut();
                    Activator.getLogger().info("Successfully logged out");
                })
                .exceptionally(throwable -> {
                    Activator.getLogger().error("Failed to log out", throwable);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> expire() {
        Activator.getLogger().info("Attempting to expire credentials...");

        return authCredentialsService.updateTokenCredentials(new UpdateCredentialsPayload(null, null, false))
                .thenRun(() -> {
                    authStateManager.toExpired();
                    Activator.getLogger().info("Successfully expired credentials");
                })
                .exceptionally(throwable -> {
                    Activator.getLogger().error("Failed to expire credentials", throwable);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> reAuthenticate(final boolean loginOnInvalidToken) {
        AuthState authState = authStateManager.getAuthState();

        if (authState.isLoggedOut()) {
            Activator.getLogger().warn("Attempted to re-authenticate while user is in a logged out state");
            return CompletableFuture.completedFuture(null);
        }

        Activator.getLogger().info("Attempting to re-authenticate...");

        return processLogin(authState.loginType(), authState.loginParams(), loginOnInvalidToken, true)
                .exceptionally(throwable -> {
                    Activator.getLogger().error("Failed to re-authenticate", throwable);
                    logout();
                    return null;
                });
    }

    @Override
    public AuthState getAuthState() {
        return authStateManager.getAuthState();
    }

    CompletableFuture<Void> processLogin(final LoginType loginType, final LoginParams loginParams, final boolean loginOnInvalidToken,
            final boolean isReAuth) {
        AuthUtil.validateLoginParameters(loginType, loginParams);

        final AtomicReference<String> ssoTokenId = new AtomicReference<>(); // Saved for logout

        return authTokenService.getSsoToken(loginType, loginParams, loginOnInvalidToken)
                .thenApply(ssoToken -> {
                    ssoTokenId.set(ssoToken.ssoToken().id());
                    return ssoToken;
                })
                .thenAccept(ssoToken -> {
                    authCredentialsService.updateTokenCredentials(loginType == LoginType.IAM_IDENTITY_CENTER
                            ? ssoToken.getUpdateCredentialsPayloadHydratedWithStartUrl(
                                    loginParams.getLoginIdcParams().getUrl())
                            : ssoToken.updateCredentialsParams());
                })
                .thenRun(() -> {
                    authStateManager.toLoggedIn(loginType, loginParams, ssoTokenId.get());
                    if (loginOnInvalidToken) {
                        emitBrowserLoginMetric(loginType, loginParams, isReAuth, Result.SUCCEEDED, null);
                    }
                    Activator.getLogger().info("Successfully logged in");
                })
                /*
                 * Reports the outcome of the login itself. The steps that follow are not part of the login,
                 * so they are wired after this stage to keep them out of the metric.
                 *
                 * Only logins that were allowed to open the browser are reported. The re-authentication
                 * performed on start up passes loginOnInvalidToken=false, it refreshes the cached token
                 * silently and would otherwise report a browser login (and a session duration) on every
                 * start of the IDE.
                 */
                .whenComplete((unused, throwable) -> {
                    if (throwable != null && loginOnInvalidToken) {
                        emitBrowserLoginMetric(loginType, loginParams, isReAuth, Result.FAILED, getReasonCode(throwable));
                    }
                }).thenRun(() -> {
                    CustomizationUtil.triggerChangeConfigurationNotification();
                }).exceptionally(throwable -> {
                    throw new AmazonQPluginException("Failed to process log in", throwable);
                });
    }

    /**
     * Emits the browser login metric, reporting how long the previous authentication session for the
     * same start url lived for.
     *
     * The session duration is only known once a login has been recorded for that start url, so it is
     * left out of the first login and of the first login that follows a sign out, which clears the
     * recorded login. A successful login becomes the new reference point for the next one.
     *
     * @param loginType the type of connection being authenticated
     * @param loginParams the parameters of the connection being authenticated
     * @param isReAuth whether the login renews an existing connection
     * @param result whether the login succeeded
     * @param reason a short reason code when the login failed, null otherwise
     */
    private void emitBrowserLoginMetric(final LoginType loginType, final LoginParams loginParams, final boolean isReAuth,
            final Result result, final String reason) {
        String credentialStartUrl = AuthUtil.getIssuerUrl(loginType, loginParams);

        // The start url identifies the authentication session, a metric without it carries no signal.
        if (credentialStartUrl == null || credentialStartUrl.isBlank()) {
            return;
        }

        Long sessionDuration = null;
        if (result == Result.SUCCEEDED) {
            Instant loginInstant = Instant.now();
            sessionDuration = authPluginStore.getLoginTimestamp(credentialStartUrl)
                    .map(previousLogin -> Duration.between(previousLogin, loginInstant).toMillis())
                    .filter(duration -> duration >= 0) // guards against a recorded login dated in the future
                    .orElse(null);
            authPluginStore.setLoginTimestamp(credentialStartUrl, loginInstant);
        }

        AwsTelemetryProvider.emitLoginWithBrowserEvent(new BrowserLoginParams(credentialStartUrl,
                CredentialType.BEARER_TOKEN, isReAuth, result, reason, sessionDuration));
    }

    private static String getReasonCode(final Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        return cause.getClass().getSimpleName();
    }

    public static class Builder {
        private LspProvider lspProvider;
        private PluginStore pluginStore;
        private AuthStateManager authStateManager;
        private AuthCredentialsService authCredentialsService;
        private AuthTokenService authTokenService;
        private boolean initializeOnStartUp;

        public final Builder withLspProvider(final LspProvider lspProvider) {
            this.lspProvider = lspProvider;
            return this;
        }
        public final Builder withPluginStore(final PluginStore pluginStore) {
            this.pluginStore = pluginStore;
            return this;
        }
        public final Builder withAuthStateManager(final AuthStateManager authStateManager) {
            this.authStateManager = authStateManager;
            return this;
        }
        public final Builder withAuthCredentialsService(final AuthCredentialsService qCredentialService) {
            this.authCredentialsService = qCredentialService;
            return this;
        }
        public final Builder withAuthTokenService(final AuthTokenService authTokenService) {
            this.authTokenService = authTokenService;
            return this;
        }
        public final Builder initializeOnStartUp() {
            this.initializeOnStartUp = true;
            return this;
        }
        public final DefaultLoginService build() {
            if (lspProvider == null) {
                lspProvider = Activator.getLspProvider();
            }
            if (pluginStore == null) {
                pluginStore = Activator.getPluginStore();
            }
            if (authStateManager == null) {
                authStateManager = new DefaultAuthStateManager(pluginStore);
            }
            if (authCredentialsService == null) {
                authCredentialsService = DefaultAuthCredentialsService.builder()
                        .withLspProvider(lspProvider)
                        .build();
            }
            if (authTokenService == null) {
                authTokenService = DefaultAuthTokenService.builder()
                        .withLspProvider(lspProvider)
                        .build();
            }
            DefaultLoginService instance = new DefaultLoginService(this);
            return instance;
        }
    }
}
