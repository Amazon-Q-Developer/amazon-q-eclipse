// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

public final class Constants {

    private Constants() {
        // to avoid initiation
    }

    public static final String CUSTOMIZATION_STORAGE_INTERNAL_KEY = "aws.q.customization.eclipse";
    public static final String LSP_CUSTOMIZATION_CONFIGURATION_KEY = "customization";
    public static final String LSP_ENABLE_TELEMETRY_EVENTS_CONFIGURATION_KEY = "enableTelemetryEventsToDestination";
    public static final String LSP_OPT_OUT_TELEMETRY_CONFIGURATION_KEY = "optOutTelemetry";
    public static final String LSP_Q_CONFIGURATION_KEY = "aws.q";
    public static final String LSP_CW_CONFIGURATION_KEY = "aws.codeWhisperer";
    public static final String DO_NOT_SHOW_UPDATE_KEY = "doNotShowUpdate";
    public static final String PLUGIN_UPDATE_NOTIFICATION_TITLE = "Amazon Q Update Available";
    public static final String PLUGIN_UPDATE_NOTIFICATION_BODY = "Amazon Q plugin version %s is available."
            + " Please update to receive the latest features and bug fixes.";
    public static final String LSP_CW_OPT_OUT_KEY = "shareCodeWhispererContentWithAWS";
    public static final String LSP_CODE_REFERENCES_OPT_OUT_KEY = "includeSuggestionsWithCodeReferences";
    public static final String IDE_CUSTOMIZATION_NOTIFICATION_TITLE = "Amazon Q Customization";
    public static final String IDE_DEVELOPER_PROFILES_NOTIFICATION_TITLE = "Amazon Q Developer Profile";
    public static final String IDE_CUSTOMIZATION_NOTIFICATION_BODY_TEMPLATE = "Amazon Q inline suggestions are now coming from the %s";
    public static final String IDE_DEVELOPER_PROFILES_NOTIFICATION_BODY_TEMPLATE = "You're using the '%s' profile for Amazon Q.";
    public static final String MANIFEST_DEPRECATED_NOTIFICATION_KEY = "doNotShowDeprecatedManifest";
    public static final String MANIFEST_DEPRECATED_NOTIFICATION_TITLE = "Update Amazon Q Extension";
    public static final String MANIFEST_DEPRECATED_NOTIFICATION_BODY = "This version of the plugin"
            + " will no longer receive updates to Amazon Q Language authoring features";
    public static final String DEFAULT_Q_FOUNDATION_DISPLAY_NAME = "Amazon Q foundation (Default)";
    public static final String LOGIN_TYPE_KEY = "LOGIN_TYPE";
    public static final String LOGIN_IDC_PARAMS_KEY = "IDC_PARAMS";
    public static final String SSO_TOKEN_ID = "SSO_TOKEN_IN";
    public static final String AWS_BUILDER_ID_URL = "https://view.awsapps.com/start";
    public static final String IDC_PROFILE_NAME = "eclipse-q-profile";
    public static final String IDC_SESSION_NAME = "eclipse-q-session";
    public static final String IDC_PROFILE_KIND = "SsoTokenProfile";
    public static final String TELEMETRY_NOTIFICATION_TITLE = "AWS IDE plugins telemetry";
    public static final String TELEMETRY_NOTIFICATION_BODY = "Usage metrics are collected by default. This can be changed in the \"Amazon Q\" section"
            + " of the IDE preferences.";
    public static final String KIRO_SUNSET_NOTIFICATION_TITLE = "Amazon Q Developer end of support";
    public static final String KIRO_SUNSET_NOTIFICATION_BODY = "Amazon Q Developer IDE plugins will reach end of support on April 30, 2027."
            + " New accounts will no longer be available starting May 15, 2026";
    public static final String KIRO_SUNSET_LEARN_MORE_URL = "https://aws.amazon.com/blogs/devops/amazon-q-developer-end-of-support-announcement/";
    public static final String KIRO_SUNSET_NOTIFICATION_DISMISSED_KEY = "kiroSunsetNotificationDismissed";
    public static final String RE_AUTHENTICATE_FAILURE_MESSAGE = "An error occurred while attempting to re-authenticate. Please try again.";
    public static final String AUTHENTICATE_FAILURE_MESSAGE = "An error occurred while attempting to authenticate. Please try again.";
    public static final String IDE_SSL_HANDSHAKE_TITLE = "SSL Handshake Error";
    public static final String IDE_SSL_HANDSHAKE_BODY = "The plugin encountered an SSL handshake error. If using a proxy, check the plugin preferences.";
    public static final String INVALID_PROXY_CONFIGURATION_TITLE = "Proxy Configuration Error";
    public static final String INVALID_PROXY_CONFIGURATION_BODY = "The provided proxy URL is invalid. Please check your plugin configuration.";
    public static final String INLINE_CHAT_NOTIFICATION_TITLE = "Amazon Q Inline Chat";
    public static final String INLINE_CHAT_MULTIPLE_TRIGGER_BODY = "Reject or Accept the current suggestions before creating a new one";
    public static final String INLINE_CHAT_ERROR_NOTIFICATION_BODY = "Encountered an unexpected error when processing the request, please try again.";
    public static final String INLINE_CHAT_CODEREF_NOTIFICATION_BODY = "Suggestion had code references; removed per settings.";
    public static final String INLINE_CHAT_NO_SUGGESTIONS_BODY = "No suggestions from Q; please try a different instruction.";
    public static final String INLINE_CHAT_EXPIRED_AUTH_BODY = "Login status expired; please open Q plugin window to reauthenticate.";
    public static final String INLINE_CHAT_CONTEXT_ID = "org.eclipse.ui.inlineChatContext";
    public static final String INLINE_SUGGESTIONS_CONTEXT_ID = "org.eclipse.ui.suggestionsContext";
    public static final String AMAZON_Q_CHANGELOG_URL = "https://github.com/aws/amazon-q-eclipse/releases";
    public static final String AMAZON_Q_UPDATE_SITE_URL = "https://marketplace.eclipse.org/content/amazon-q";
}
