// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.exception.AmazonQPluginException;
import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthState;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthStateType;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginIdcParams;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginParams;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginType;
import software.aws.toolkits.eclipse.amazonq.telemetry.AuthTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.util.Constants;
import software.aws.toolkits.telemetry.TelemetryDefinitions.AuthStatus;

class DefaultAuthStateManagerTest {

    @Mock
    private PluginStore pluginStore;

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorStaticMockExtension = new ActivatorStaticMockExtension();

    private DefaultAuthStateManager authStateManager;
    private LoginParams loginParams;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        assertNotNull(pluginStore, "PluginStore mock should not be null");

        authStateManager = new DefaultAuthStateManager(pluginStore);
        loginParams = new LoginParams();
        LoginIdcParams idcParams = new LoginIdcParams();
        idcParams.setUrl("https://example.com");
        loginParams.setLoginIdcParams(idcParams);
    }

    @AfterEach
    void tearDown() throws Exception {
        clearInvocations(pluginStore);
        closeable.close();
    }

    @Test
    void toLoggedInWithValidParametersUpdatesStateCorrectly() {
        String ssoTokenId = "ssoTokenId";
        setAuthStateFields(AuthStateType.LOGGED_OUT, LoginType.NONE, null, null, null);

        clearInvocations(pluginStore);

        authStateManager.toLoggedIn(LoginType.BUILDER_ID, loginParams, ssoTokenId);

        // Verify auth state
        AuthState state = authStateManager.getAuthState();
        assertEquals(AuthStateType.LOGGED_IN, state.authStateType());
        assertEquals(LoginType.BUILDER_ID, state.loginType());
        assertEquals(loginParams, state.loginParams());
        assertEquals(ssoTokenId, state.ssoTokenId());
        assertEquals(Constants.AWS_BUILDER_ID_URL, state.issuerUrl());

        // Verify plugin store
        verify(pluginStore).put(Constants.LOGIN_TYPE_KEY, LoginType.BUILDER_ID.name());
        verify(pluginStore).putObject(Constants.LOGIN_IDC_PARAMS_KEY, loginParams.getLoginIdcParams());
        verify(pluginStore).put(Constants.SSO_TOKEN_ID, ssoTokenId);
    }

    @Test
    void toLoggedInWithNullLoginTypeThrowsException() {
        String ssoTokenId = "testToken";

        assertThrows(IllegalArgumentException.class,
            () -> authStateManager.toLoggedIn(null, loginParams, ssoTokenId));
    }

    @Test
    void toLoggedInWithNoneLoginTypeThrowsException() {
        String ssoTokenId = "testToken";

        assertThrows(IllegalArgumentException.class,
            () -> authStateManager.toLoggedIn(LoginType.NONE, loginParams, ssoTokenId));
    }

    @Test
    void toLoggedInWithNullLoginParamsThrowsException() {
        String ssoTokenId = "testToken";
        loginParams = null;

        assertThrows(IllegalArgumentException.class,
            () -> authStateManager.toLoggedIn(LoginType.BUILDER_ID, loginParams, ssoTokenId));
    }

    @Test
    void toLoggedInWithNullSsoTokenIdThrowsException() {
        String ssoTokenId = null;

        assertThrows(IllegalArgumentException.class,
            () -> authStateManager.toLoggedIn(LoginType.BUILDER_ID, loginParams, ssoTokenId));
    }

    @Test
    void toLoggedOutClearsStateCorrectly() {
        String ssoTokenId = "ssoTokenId";
        String issuerUrl = "testUrl";
        setAuthStateFields(AuthStateType.LOGGED_IN, LoginType.BUILDER_ID, loginParams, ssoTokenId, issuerUrl);

        clearInvocations(pluginStore);

        authStateManager.toLoggedOut();

        // Verify auth state
        AuthState state = authStateManager.getAuthState();
        assertEquals(AuthStateType.LOGGED_OUT, state.authStateType());
        assertEquals(LoginType.NONE, state.loginType());
        assertNull(state.loginParams());
        assertNull(state.ssoTokenId());
        assertNull(state.issuerUrl());

        // Verify plugin store
        verify(pluginStore).remove(Constants.LOGIN_TYPE_KEY);
        verify(pluginStore).remove(Constants.LOGIN_IDC_PARAMS_KEY);
        verify(pluginStore).remove(Constants.SSO_TOKEN_ID);
    }

    @Test
    void toExpiredUpdatesStateCorrectly() {
        String ssoTokenId = "ssoTokenId";
        String issuerUrl = "testUrl";
        setAuthStateFields(AuthStateType.LOGGED_IN, LoginType.BUILDER_ID, loginParams, ssoTokenId, issuerUrl);

        clearInvocations(pluginStore);

        authStateManager.toExpired();

        // Verify auth state
        AuthState state = authStateManager.getAuthState();
        assertEquals(AuthStateType.EXPIRED, state.authStateType());
        assertEquals(LoginType.BUILDER_ID, state.loginType());
        assertEquals(loginParams, state.loginParams());
        assertEquals(ssoTokenId, state.ssoTokenId());
        assertEquals(Constants.AWS_BUILDER_ID_URL, state.issuerUrl());

        // Verify plugin store
        verify(pluginStore).put(Constants.LOGIN_TYPE_KEY, LoginType.BUILDER_ID.name());
        verify(pluginStore).putObject(Constants.LOGIN_IDC_PARAMS_KEY, loginParams.getLoginIdcParams());
        verify(pluginStore).put(Constants.SSO_TOKEN_ID, ssoTokenId);
    }

    @Test
    void toExpiredWithNoLoginTypeSwitchesToLoggedOut() {
        String ssoTokenId = "ssoTokenId";
        String issuerUrl = "testUrl";
        setAuthStateFields(AuthStateType.LOGGED_IN, LoginType.NONE, loginParams, ssoTokenId, issuerUrl);

        clearInvocations(pluginStore);

        authStateManager.toExpired();

        AuthState state = authStateManager.getAuthState();
        assertEquals(AuthStateType.LOGGED_OUT, state.authStateType());
        assertEquals(LoginType.NONE, state.loginType());
        assertNull(state.loginParams());
        assertNull(state.ssoTokenId());
        assertNull(state.issuerUrl());

        // Verify plugin store
        verify(pluginStore).remove(Constants.LOGIN_TYPE_KEY);
        verify(pluginStore).remove(Constants.LOGIN_IDC_PARAMS_KEY);
        verify(pluginStore).remove(Constants.SSO_TOKEN_ID);
    }

    @Test
    void syncAuthStateWithPluginStoreWithStoredCredentialsRestoresState() {
        String ssoTokenId = "ssoTokenId";
        when(pluginStore.get(Constants.LOGIN_TYPE_KEY)).thenReturn(LoginType.BUILDER_ID.name());
        when(pluginStore.getObject(Constants.LOGIN_IDC_PARAMS_KEY, LoginIdcParams.class)).thenReturn(loginParams.getLoginIdcParams());
        when(pluginStore.get(Constants.SSO_TOKEN_ID)).thenReturn(ssoTokenId);

        DefaultAuthStateManager newManager = new DefaultAuthStateManager(pluginStore);

        // Verify auth state
        AuthState state = newManager.getAuthState();
        assertEquals(AuthStateType.LOGGED_IN, state.authStateType());
        assertEquals(LoginType.BUILDER_ID, state.loginType());
        assertEquals(loginParams.getLoginIdcParams().getUrl(), state.loginParams().getLoginIdcParams().getUrl());
        assertEquals(ssoTokenId, state.ssoTokenId());
        assertEquals(Constants.AWS_BUILDER_ID_URL, state.issuerUrl());
    }

    @Test
    void syncAuthStateWithPluginStoreWithNoStoredCredentialsSetsLoggedOut() {
        when(pluginStore.get(Constants.LOGIN_TYPE_KEY)).thenReturn(LoginType.NONE.name());

        DefaultAuthStateManager newManager = new DefaultAuthStateManager(pluginStore);

        // Verify auth state
        AuthState state = newManager.getAuthState();
        assertEquals(AuthStateType.LOGGED_OUT, state.authStateType());
        assertEquals(LoginType.NONE, state.loginType());
        assertNull(state.loginParams());
        assertNull(state.ssoTokenId());
        assertNull(state.issuerUrl());
    }

    @Test
    void syncAuthStateWithPluginStoreWithNoStoredCredentialsEmitsNotConnectedStartupUserState() {
        when(pluginStore.get(Constants.LOGIN_TYPE_KEY)).thenReturn(LoginType.NONE.name());

        try (MockedStatic<AuthTelemetryProvider> mockedAuthTelemetryProvider = mockStatic(AuthTelemetryProvider.class)) {
            new DefaultAuthStateManager(pluginStore);

            mockedAuthTelemetryProvider
                    .verify(() -> AuthTelemetryProvider.emitUserStateOnStartupMetric(AuthStatus.NOT_CONNECTED, null));
        }
    }

    @Test
    void syncAuthStateWithPluginStoreWithStoredCredentialsDefersStartupUserStateUntilReAuthenticationResolves() {
        when(pluginStore.get(Constants.LOGIN_TYPE_KEY)).thenReturn(LoginType.BUILDER_ID.name());
        when(pluginStore.getObject(Constants.LOGIN_IDC_PARAMS_KEY, LoginIdcParams.class))
                .thenReturn(loginParams.getLoginIdcParams());
        when(pluginStore.get(Constants.SSO_TOKEN_ID)).thenReturn("ssoTokenId");

        try (MockedStatic<AuthTelemetryProvider> mockedAuthTelemetryProvider = mockStatic(AuthTelemetryProvider.class)) {
            DefaultAuthStateManager newManager = new DefaultAuthStateManager(pluginStore);

            mockedAuthTelemetryProvider.verifyNoInteractions();

            newManager.toLoggedIn(LoginType.BUILDER_ID, loginParams, "ssoTokenId");

            mockedAuthTelemetryProvider.verify(() -> AuthTelemetryProvider
                    .emitUserStateOnStartupMetric(AuthStatus.CONNECTED, Constants.AWS_BUILDER_ID_URL));
        }
    }

    @Test
    void startupUserStateReportsExpiredWhenRestoredSessionExpires() {
        when(pluginStore.get(Constants.LOGIN_TYPE_KEY)).thenReturn(LoginType.BUILDER_ID.name());
        when(pluginStore.getObject(Constants.LOGIN_IDC_PARAMS_KEY, LoginIdcParams.class))
                .thenReturn(loginParams.getLoginIdcParams());
        when(pluginStore.get(Constants.SSO_TOKEN_ID)).thenReturn("ssoTokenId");

        try (MockedStatic<AuthTelemetryProvider> mockedAuthTelemetryProvider = mockStatic(AuthTelemetryProvider.class)) {
            DefaultAuthStateManager newManager = new DefaultAuthStateManager(pluginStore);

            newManager.toExpired();

            mockedAuthTelemetryProvider.verify(() -> AuthTelemetryProvider
                    .emitUserStateOnStartupMetric(AuthStatus.EXPIRED, Constants.AWS_BUILDER_ID_URL));
        }
    }

    @Test
    void startupUserStateIsEmittedOnlyOncePerSession() {
        when(pluginStore.get(Constants.LOGIN_TYPE_KEY)).thenReturn(LoginType.NONE.name());

        try (MockedStatic<AuthTelemetryProvider> mockedAuthTelemetryProvider = mockStatic(AuthTelemetryProvider.class)) {
            DefaultAuthStateManager newManager = new DefaultAuthStateManager(pluginStore);

            newManager.toLoggedIn(LoginType.BUILDER_ID, loginParams, "ssoTokenId");
            newManager.toExpired();

            mockedAuthTelemetryProvider.verify(
                    () -> AuthTelemetryProvider.emitUserStateOnStartupMetric(any(AuthStatus.class), any()), times(1));
        }
    }

    @Test
    void setAuthStateFieldsSuccess() {
        String ssoTokenId = "ssoTokenId";
        String issuerUrl = "testIssuerURl";
        setAuthStateFields(AuthStateType.LOGGED_IN, LoginType.BUILDER_ID, loginParams, ssoTokenId, issuerUrl);

        AuthState state = authStateManager.getAuthState();
        assertEquals(AuthStateType.LOGGED_IN, state.authStateType());
        assertEquals(LoginType.BUILDER_ID, state.loginType());
        assertEquals(loginParams, state.loginParams());
        assertEquals(ssoTokenId, state.ssoTokenId());
        assertEquals(issuerUrl, state.issuerUrl());
    }

    void setAuthStateFields(final AuthStateType authStateType, final LoginType loginType,
        final LoginParams loginParams, final String ssoTokenId, final String issuerUrl)  {
        try {
            Field authStateTypeField = DefaultAuthStateManager.class.getDeclaredField("authStateType");
            Field loginTypeField = DefaultAuthStateManager.class.getDeclaredField("loginType");
            Field loginParamsField = DefaultAuthStateManager.class.getDeclaredField("loginParams");
            Field ssoTokenIdField = DefaultAuthStateManager.class.getDeclaredField("ssoTokenId");
            Field issuerUrlField = DefaultAuthStateManager.class.getDeclaredField("issuerUrl");

            authStateTypeField.setAccessible(true);
            loginTypeField.setAccessible(true);
            loginParamsField.setAccessible(true);
            ssoTokenIdField.setAccessible(true);
            issuerUrlField.setAccessible(true);

            authStateTypeField.set(authStateManager, authStateType);
            loginTypeField.set(authStateManager, loginType);
            loginParamsField.set(authStateManager, loginParams);
            ssoTokenIdField.set(authStateManager, ssoTokenId);
            issuerUrlField.set(authStateManager, issuerUrl);
        } catch (Exception ex) {
            throw new AmazonQPluginException("Failed to set DefaultAuthStateManager fields");
        }
    }
}
