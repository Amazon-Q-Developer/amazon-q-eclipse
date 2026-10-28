// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp.auth;

import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.configuration.profiles.QDeveloperProfileUtil;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthState;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthStateType;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginParams;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginType;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.telemetry.AuthTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.util.AuthUtil;
import software.aws.toolkits.telemetry.TelemetryDefinitions.AuthStatus;

/**
 * Manages authentication state transitions and persistence in the Amazon Q plugin.
 *
 * This manager handles the following authentication state changes:
 * - Login
 * - Logout
 * - Session expiration
 * - Re-authentication
 *
 * State changes follow this workflow:
 * 1. Update internal authentication state variables
 * 2. Persist state changes to the plugin store
 * 3. Notify registered authentication state listeners
 *
 * The manager ensures consistency between in-memory state, persistent storage,
 * and all components observing authentication state changes. The manager should
 * only be accessed from the DefaultLoginService which is the central point of
 * authentication handlers.
 *
 * @See DefaultLoginService
 * @see AuthPluginStore
 */
public final class DefaultAuthStateManager implements AuthStateManager {
    private final AuthPluginStore authPluginStore;

    private AuthStateType authStateType;
    private LoginType loginType; // used in login's getSsoToken params
    private LoginParams loginParams; // used in login's getSsoToken params
    private String issuerUrl; // used in AmazonQLspClientImpl.getConnectionMetadata()
    private String ssoTokenId; // used in logout's invalidateSsoToken params
    private AuthState previousAuthState = null;
    private boolean isRestoringPersistedAuthState = false;
    private boolean hasEmittedStartupAuthState = false;

    public DefaultAuthStateManager(final PluginStore pluginStore) {
        this.authPluginStore = new AuthPluginStore(pluginStore);
        syncAuthStateWithPluginStore();
    }

    @Override
    public void toLoggedIn(final LoginType loginType, final LoginParams loginParams, final String ssoTokenId)
            throws IllegalArgumentException {
        if (loginType == null) {
            throw new IllegalArgumentException("loginType is a required parameter");
        }

        if (loginType.equals(LoginType.NONE)) {
            throw new IllegalArgumentException("LoginType.NONE is not a valid parameter");
        }

        if (loginParams == null) {
            throw new IllegalArgumentException("loginParams is a required parameter");
        }

        if (ssoTokenId == null) {
            throw new IllegalArgumentException("ssoTokenId is a required parameter");
        }

        updateState(AuthStateType.LOGGED_IN, loginType, loginParams, ssoTokenId);

    }

    @Override
    public void toLoggedOut() {
        updateState(AuthStateType.LOGGED_OUT, LoginType.NONE, null, null);
    }

    @Override
    public void toExpired() {
        if (loginType == null || loginType.equals(LoginType.NONE) || loginParams == null) {
            Activator.getLogger().error("Attempted to transition to an expired state but the required parameters for"
                    + " re-authentication are not available. Transitioning to a logged out state instead.");
            toLoggedOut();
            return;
        }
        updateState(AuthStateType.EXPIRED, loginType, loginParams, ssoTokenId);
    }

    @Override
    public AuthState getAuthState() {
        return new AuthState(authStateType, loginType, loginParams, issuerUrl, ssoTokenId);
    }

    private void updateState(final AuthStateType authStatusType, final LoginType loginType, final LoginParams loginParams, final String ssoTokenId) {
        this.authStateType = authStatusType;
        this.loginType = loginType;
        this.loginParams = loginParams;
        this.issuerUrl = AuthUtil.getIssuerUrl(loginType, loginParams);
        this.ssoTokenId = ssoTokenId;

        /**
         * Manages authentication state persistence across Eclipse sessions using the plugin store.
         * When Eclipse restarts, the stored authentication state is used to reinitialize
         * the current session's state.
         *
         * @see #syncAuthStateWithPluginStore() Called during DefaultAuthStateManager initialization
         *                                      to restore persisted state
         */
        if (loginType.equals(LoginType.NONE)) {
            authPluginStore.clear();
        } else {
            authPluginStore.setLoginType(loginType);
            authPluginStore.setLoginIdcParams(loginParams);
            authPluginStore.setSsoTokenId(ssoTokenId);
        }

        /**
         * Notifies authentication state listeners to maintain plugin-wide state synchronization.
         * This notification is critical for ensuring all plugin components reflect the current
         * authentication state.
         */
        AuthState newAuthState = getAuthState();
        if (previousAuthState == null || newAuthState.authStateType() != previousAuthState.authStateType()) {
            if (loginType == LoginType.IAM_IDENTITY_CENTER && newAuthState.isLoggedIn()) {
                QDeveloperProfileUtil.getInstance().getProfileSelectionTaskFuture().thenRun(() -> {
                    Activator.getEventBroker().post(AuthState.class, newAuthState);
                });
            } else {
                Activator.getEventBroker().post(AuthState.class, newAuthState);
            }
        }
        previousAuthState = newAuthState;

        emitStartupAuthStateMetric(newAuthState);
    }

    /**
     * Reports the authentication state observed at startup, once per plugin session.
     *
     * The state restored from the plugin store is optimistic: a stored connection is assumed to still be
     * valid until the re-authentication performed on start up resolves it. The optimistic state is
     * therefore skipped and the metric is reported for the state that follows it, which is the outcome
     * of that re-authentication. A restored logged out state needs no re-authentication and is
     * definitive right away.
     *
     * @param authState the state the plugin transitioned to
     * @see #syncAuthStateWithPluginStore()
     * @see DefaultLoginService
     */
    private void emitStartupAuthStateMetric(final AuthState authState) {
        if (hasEmittedStartupAuthState || isRestoringPersistedAuthState) {
            return;
        }
        hasEmittedStartupAuthState = true;

        AuthTelemetryProvider.emitUserStateOnStartupMetric(toAuthStatus(authState.authStateType()), authState.issuerUrl());
    }

    private static AuthStatus toAuthStatus(final AuthStateType authStateType) {
        switch (authStateType) {
        case LOGGED_IN:
            return AuthStatus.CONNECTED;
        case EXPIRED:
            return AuthStatus.EXPIRED;
        case LOGGED_OUT:
            return AuthStatus.NOT_CONNECTED;
        default:
            return AuthStatus.UNKNOWN;
        }
    }

    private void syncAuthStateWithPluginStore() {
        LoginType loginType = authPluginStore.getLoginType();
        LoginParams loginParams = authPluginStore.getLoginIdcParams();
        String ssoTokenId = authPluginStore.getSsoTokenId();

        if (loginType.equals(LoginType.NONE)) {
            try {
                toLoggedOut();
            } catch (Exception ex) {
                Activator.getLogger().error("Failed to transition to a logged out state after syncing auth state with the persistent store", ex);
            }
            return;
        }

        /**
         * Initializes to a logged-in state to optimize the user experience. Authentication failures
         * (such as expired tokens) are handled gracefully through the chat interface:
         *
         * Authentication Flow:
         * 1. Failed chat prompt requests trigger an "Authenticate" or "Re-authenticate" response
         * 2. "Authenticate" button opens to the Amazon Q Login view
         * 3. "Re-authenticate" button for sso token renewal in an external browser
         *
         * Rather than displaying a loading view or re-authenticate view that will be temporary displayed,
         * it was decided that this experience of displaying the Chat view would be the least disruptive.
         *
         * @see DefaultLoginService constructor that handles the re-authentication on LoginService start up
         */
        boolean restoreFailed = false;
        try {
            isRestoringPersistedAuthState = true;
            toLoggedIn(loginType, loginParams, ssoTokenId);
        } catch (Exception ex) {
            Activator.getLogger().error("Failed to transition to a logged in state after syncing auth state with the persistent store", ex);
            restoreFailed = true;
        } finally {
            isRestoringPersistedAuthState = false;
        }

        if (restoreFailed) {
            toLoggedOut();
        }
    }
}
