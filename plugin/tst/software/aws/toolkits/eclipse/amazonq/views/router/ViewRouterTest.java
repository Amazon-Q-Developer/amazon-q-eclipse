// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views.router;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.InOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import software.aws.toolkits.eclipse.amazonq.broker.EventBroker;
import software.aws.toolkits.eclipse.amazonq.broker.events.AmazonQLspState;
import software.aws.toolkits.eclipse.amazonq.broker.events.AmazonQViewType;
import software.aws.toolkits.eclipse.amazonq.broker.events.BrowserCompatibilityState;
import software.aws.toolkits.eclipse.amazonq.broker.events.ChatWebViewAssetState;
import software.aws.toolkits.eclipse.amazonq.broker.events.QDevAccessBlockedState;
import software.aws.toolkits.eclipse.amazonq.broker.events.QDeveloperProfileState;
import software.aws.toolkits.eclipse.amazonq.broker.events.ToolkitLoginWebViewAssetState;
import software.aws.toolkits.eclipse.amazonq.extensions.implementation.ActivatorStaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthState;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthStateType;

public final class ViewRouterTest {

    private PublishSubject<Object> publishSubject;

    private Observable<AuthState> authStateObservable;
    private Observable<AmazonQLspState> lspStateObservable;
    private Observable<BrowserCompatibilityState> browserCompatibilityStateObservable;
    private Observable<ChatWebViewAssetState> chatWebViewAssetStateObservable;
    private Observable<ToolkitLoginWebViewAssetState> toolkitLoginWebViewAssetStateObservable;
    private Observable<QDeveloperProfileState> qDeveloperProfileStateObservable;
    private Observable<QDevAccessBlockedState> qDevAccessBlockedStateObservable;

    private ViewRouter viewRouter;
    private EventBroker eventBrokerMock;

    @RegisterExtension
    private static ActivatorStaticMockExtension activatorStaticMockExtension = new ActivatorStaticMockExtension();

    @BeforeEach
    void setupBeforeEach() {
        // ensure event handlers run on same thread
        RxJavaPlugins.setComputationSchedulerHandler(scheduler -> Schedulers.trampoline());

        publishSubject = PublishSubject.create();

        authStateObservable = publishSubject.ofType(AuthState.class);
        lspStateObservable = publishSubject.ofType(AmazonQLspState.class);
        browserCompatibilityStateObservable = publishSubject.ofType(BrowserCompatibilityState.class);
        chatWebViewAssetStateObservable = publishSubject.ofType(ChatWebViewAssetState.class);
        toolkitLoginWebViewAssetStateObservable = publishSubject.ofType(ToolkitLoginWebViewAssetState.class);
        qDeveloperProfileStateObservable = publishSubject.ofType(QDeveloperProfileState.class);
        qDevAccessBlockedStateObservable = publishSubject.ofType(QDevAccessBlockedState.class);

        eventBrokerMock = activatorStaticMockExtension.getMock(EventBroker.class);

        viewRouter = ViewRouter.builder().withAuthStateObservable(authStateObservable)
                .withLspStateObservable(lspStateObservable)
                .withBrowserCompatibilityStateObservable(browserCompatibilityStateObservable)
                .withChatWebViewAssetStateObservable(chatWebViewAssetStateObservable)
                .withToolkitLoginWebViewAssetStateObservable(toolkitLoginWebViewAssetStateObservable)
                .withQDeveloperProfileStateObservable(qDeveloperProfileStateObservable)
                .withQDevAccessBlockedStateObservable(qDevAccessBlockedStateObservable).build();
    }

    @AfterEach
    public void resetAfterEach() {
        RxJavaPlugins.reset();
    }

    @ParameterizedTest
    @MethodSource("provideStateSource")
    void testActiveViewResolutionBasedOnPluginState(final AmazonQLspState lspState, final AuthState authState,
            final BrowserCompatibilityState browserCompatibilityState,
            final ChatWebViewAssetState chatWebViewAssetState,
            final ToolkitLoginWebViewAssetState toolkitLoginWebViewAssetState,
            final AmazonQViewType expectedActiveViewType) {
        publishSubject.onNext(authState);
        publishSubject.onNext(lspState);
        publishSubject.onNext(browserCompatibilityState);
        publishSubject.onNext(chatWebViewAssetState);
        publishSubject.onNext(toolkitLoginWebViewAssetState);
        publishSubject.onNext(QDeveloperProfileState.AVAILABLE);
        publishSubject.onNext(QDevAccessBlockedState.NOT_BLOCKED); // does not affect view selection

        verify(eventBrokerMock).post(AmazonQViewType.class, expectedActiveViewType);
    }

    /**
     * Reacting to the refusal signs the user out, so BLOCKED and logged-out are always true
     * together. The router must resolve BLOCKED first, otherwise the user lands on the ordinary
     * login view and the explanation is lost.
     */
    @Test
    void testAccessBlockedTakesPriorityOverLoggedOut() {
        publishSubject.onNext(getAuthStateObject(AuthStateType.LOGGED_OUT));
        publishSubject.onNext(AmazonQLspState.ACTIVE);
        publishSubject.onNext(BrowserCompatibilityState.COMPATIBLE);
        publishSubject.onNext(ChatWebViewAssetState.RESOLVED);
        publishSubject.onNext(ToolkitLoginWebViewAssetState.RESOLVED);
        publishSubject.onNext(QDeveloperProfileState.AVAILABLE);
        publishSubject.onNext(QDevAccessBlockedState.BLOCKED);

        verify(eventBrokerMock).post(AmazonQViewType.class, AmazonQViewType.Q_DEV_ACCESS_BLOCKED_VIEW);
    }

    /**
     * Clearing the state is what returns the user to sign-in: they are already signed out, so the
     * router should fall through to the login view.
     */
    @Test
    void testClearingAccessBlockedReturnsToLoginView() {
        publishSubject.onNext(getAuthStateObject(AuthStateType.LOGGED_OUT));
        publishSubject.onNext(AmazonQLspState.ACTIVE);
        publishSubject.onNext(BrowserCompatibilityState.COMPATIBLE);
        publishSubject.onNext(ChatWebViewAssetState.RESOLVED);
        publishSubject.onNext(ToolkitLoginWebViewAssetState.RESOLVED);
        publishSubject.onNext(QDeveloperProfileState.AVAILABLE);
        publishSubject.onNext(QDevAccessBlockedState.BLOCKED);
        publishSubject.onNext(QDevAccessBlockedState.NOT_BLOCKED);

        /*
         * The router seeds NOT_BLOCKED via startWithItem, so the login view is posted once before
         * BLOCKED arrives and once after it is cleared. Assert the sequence rather than a count.
         */
        InOrder inOrder = inOrder(eventBrokerMock);
        inOrder.verify(eventBrokerMock).post(AmazonQViewType.class, AmazonQViewType.Q_DEV_ACCESS_BLOCKED_VIEW);
        inOrder.verify(eventBrokerMock).post(AmazonQViewType.class, AmazonQViewType.TOOLKIT_LOGIN_VIEW);
    }

    @Test
    void testDuplicateViewIdPublishedWhenDeveloperProfileSelected() {
        publishSubject.onNext(getAuthStateObject(AuthStateType.LOGGED_IN));
        publishSubject.onNext(AmazonQLspState.ACTIVE);
        publishSubject.onNext(BrowserCompatibilityState.COMPATIBLE);
        publishSubject.onNext(ChatWebViewAssetState.RESOLVED);
        publishSubject.onNext(ToolkitLoginWebViewAssetState.RESOLVED);
        publishSubject.onNext(QDeveloperProfileState.AVAILABLE);
        publishSubject.onNext(QDevAccessBlockedState.NOT_BLOCKED);

        publishSubject.onNext(getAuthStateObject(AuthStateType.LOGGED_IN));
        publishSubject.onNext(AmazonQLspState.ACTIVE);
        publishSubject.onNext(BrowserCompatibilityState.COMPATIBLE);
        publishSubject.onNext(ChatWebViewAssetState.RESOLVED);
        publishSubject.onNext(ToolkitLoginWebViewAssetState.RESOLVED);
        publishSubject.onNext(QDeveloperProfileState.SELECTED);

        verify(eventBrokerMock, times(2)).post(AmazonQViewType.class, AmazonQViewType.CHAT_VIEW);
    }

    private static Stream<Arguments> provideStateSource() {
        return Stream.of(
                Arguments.of(AmazonQLspState.ACTIVE, getAuthStateObject(AuthStateType.LOGGED_IN),
                        BrowserCompatibilityState.DEPENDENCY_MISSING, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.DEPENDENCY_MISSING_VIEW),
                Arguments.of(AmazonQLspState.ACTIVE, getAuthStateObject(AuthStateType.LOGGED_IN),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.DEPENDENCY_MISSING,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.CHAT_ASSET_MISSING_VIEW),
                Arguments.of(AmazonQLspState.ACTIVE, getAuthStateObject(AuthStateType.LOGGED_IN),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.DEPENDENCY_MISSING, AmazonQViewType.CHAT_ASSET_MISSING_VIEW),
                Arguments.of(AmazonQLspState.FAILED, getAuthStateObject(AuthStateType.LOGGED_IN),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.LSP_STARTUP_FAILED_VIEW),
                Arguments.of(AmazonQLspState.FAILED, getAuthStateObject(AuthStateType.LOGGED_OUT),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.LSP_STARTUP_FAILED_VIEW),
                Arguments.of(AmazonQLspState.FAILED, getAuthStateObject(AuthStateType.EXPIRED),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.LSP_STARTUP_FAILED_VIEW),
                Arguments.of(AmazonQLspState.PENDING, getAuthStateObject(AuthStateType.LOGGED_OUT),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.TOOLKIT_LOGIN_VIEW),
                Arguments.of(AmazonQLspState.ACTIVE, getAuthStateObject(AuthStateType.LOGGED_OUT),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.TOOLKIT_LOGIN_VIEW),
                Arguments.of(AmazonQLspState.PENDING, getAuthStateObject(AuthStateType.EXPIRED),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.RE_AUTHENTICATE_VIEW),
                Arguments.of(AmazonQLspState.ACTIVE, getAuthStateObject(AuthStateType.EXPIRED),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.RE_AUTHENTICATE_VIEW),
                Arguments.of(AmazonQLspState.ACTIVE, getAuthStateObject(AuthStateType.LOGGED_IN),
                        BrowserCompatibilityState.COMPATIBLE, ChatWebViewAssetState.RESOLVED,
                        ToolkitLoginWebViewAssetState.RESOLVED, AmazonQViewType.CHAT_VIEW));
    }

    private static AuthState getAuthStateObject(final AuthStateType authStateType) {
        return new AuthState(authStateType, null, null, null, null);
    }

}
