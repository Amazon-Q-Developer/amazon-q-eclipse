// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.lsp.auth;

import java.time.Instant;
import java.util.Optional;

import software.aws.toolkits.eclipse.amazonq.configuration.PluginStore;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginIdcParams;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginParams;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginType;
import software.aws.toolkits.eclipse.amazonq.util.Constants;

public final class AuthPluginStore {

    private PluginStore pluginStore;

    public AuthPluginStore(final PluginStore pluginStore) {
        this.pluginStore = pluginStore;
    }

    public void setLoginType(final LoginType loginType) {
        pluginStore.put(Constants.LOGIN_TYPE_KEY, loginType.name());
    }

    public LoginType getLoginType() {
        String storedValue = pluginStore.get(Constants.LOGIN_TYPE_KEY);

        if (storedValue == null) {
            return LoginType.NONE;
        } else if (storedValue.equals(LoginType.BUILDER_ID.name())) {
            return LoginType.BUILDER_ID;
        } else if (storedValue.equals(LoginType.IAM_IDENTITY_CENTER.name())) {
            return LoginType.IAM_IDENTITY_CENTER;
        } else {
            return LoginType.NONE;
        }
    }

    public void setLoginIdcParams(final LoginParams loginParams) {
        pluginStore.putObject(Constants.LOGIN_IDC_PARAMS_KEY, loginParams.getLoginIdcParams());
    }

    public LoginParams getLoginIdcParams() {
        LoginIdcParams loginIdcParams = pluginStore.getObject(Constants.LOGIN_IDC_PARAMS_KEY, LoginIdcParams.class);
        LoginParams loginParams = new LoginParams();
        loginParams.setLoginIdcParams(loginIdcParams);
        return loginParams;
    }

    public void setSsoTokenId(final String ssoTokenId) {
        pluginStore.put(Constants.SSO_TOKEN_ID, ssoTokenId);
    }

    public String getSsoTokenId() {
        return pluginStore.get(Constants.SSO_TOKEN_ID);
    }

    /**
     * Persists the instant of the latest successful login alongside the start url it was performed
     * against. It is used to report how long the previous authentication session lived for.
     *
     * @param startUrl the start url the login was performed against
     * @param loginInstant the instant the login completed
     */
    public void setLoginTimestamp(final String startUrl, final Instant loginInstant) {
        if (startUrl == null || loginInstant == null) {
            return;
        }
        pluginStore.put(Constants.LOGIN_TIMESTAMP_START_URL_KEY, startUrl);
        pluginStore.put(Constants.LOGIN_TIMESTAMP_KEY, String.valueOf(loginInstant.toEpochMilli()));
    }

    /**
     * Retrieves the instant of the latest successful login for the given start url. An empty value is
     * returned when no login has been recorded yet, when the recorded login was performed against a
     * different start url, or when the recorded value cannot be parsed.
     *
     * @param startUrl the start url the login is being performed against
     * @return the instant of the previous successful login for that start url
     */
    public Optional<Instant> getLoginTimestamp(final String startUrl) {
        String storedStartUrl = pluginStore.get(Constants.LOGIN_TIMESTAMP_START_URL_KEY);
        String storedTimestamp = pluginStore.get(Constants.LOGIN_TIMESTAMP_KEY);

        if (startUrl == null || storedStartUrl == null || storedTimestamp == null || !storedStartUrl.equals(startUrl)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(storedTimestamp)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public void clear() {
        pluginStore.remove(Constants.LOGIN_TYPE_KEY);
        pluginStore.remove(Constants.LOGIN_IDC_PARAMS_KEY);
        pluginStore.remove(Constants.LOGIN_TIMESTAMP_START_URL_KEY);
        pluginStore.remove(Constants.LOGIN_TIMESTAMP_KEY);
        pluginStore.remove(Constants.SSO_TOKEN_ID);
    }
}
