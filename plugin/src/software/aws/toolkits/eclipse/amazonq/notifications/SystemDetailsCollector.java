// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthState;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.AuthStateType;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginType;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

/** Resolves the current system + auth state into an immutable {@link SystemDetails} snapshot for the rules engine. */
public final class SystemDetailsCollector {

    private static final String PLATFORM_BUNDLE_ID = "org.eclipse.platform";
    private static final String UNKNOWN = "Unknown";

    private SystemDetailsCollector() {
        // prevent instantiation
    }

    /** Snapshots {@code getAuthState()} once and resolves everything into an immutable {@link SystemDetails}. */
    public static SystemDetails collect() {
        final Bundle pluginBundle = FrameworkUtil.getBundle(SystemDetailsCollector.class);
        final String pluginId = pluginBundle != null ? pluginBundle.getSymbolicName() : UNKNOWN;
        // Use a clean major.minor.micro string (drop the OSGi qualifier, e.g. "2.7.4.202607161757") so the rules
        // engine compares extension.version with semver, not lexical ordering. See resolveIdeVersion for the same.
        final String pluginVersion = pluginBundle != null ? cleanVersion(pluginBundle.getVersion()) : UNKNOWN;

        return new SystemDetails(
                "Local",
                Platform.getOSArch(),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                "Eclipse",
                resolveIdeVersion(),
                Map.of(pluginId, pluginVersion),
                resolveQAuth());
    }

    /** Amazon Q Eclipse bundle symbolic name — the key notification payloads use for {@code extension.id}. */
    public static String pluginId() {
        final Bundle pluginBundle = FrameworkUtil.getBundle(SystemDetailsCollector.class);
        return pluginBundle != null ? pluginBundle.getSymbolicName() : UNKNOWN;
    }

    /**
     * Whether this is an unreleased/development build. Tycho replaces the {@code .qualifier} segment with a numeric
     * build timestamp at release time, so a bundle whose qualifier is still the literal {@code "qualifier"} (PDE/dev
     * launch) or contains {@code "snapshot"} is a development build that should not receive production notifications.
     */
    public static boolean isDevBuild() {
        final Bundle pluginBundle = FrameworkUtil.getBundle(SystemDetailsCollector.class);
        if (pluginBundle == null) {
            return true;
        }
        final String qualifier = pluginBundle.getVersion().getQualifier();
        if (qualifier == null || qualifier.isBlank()) {
            return false;
        }
        final String q = qualifier.toLowerCase(java.util.Locale.ROOT);
        return q.equals("qualifier") || q.contains("snapshot");
    }

    private static String resolveIdeVersion() {
        final Bundle platform = Platform.getBundle(PLATFORM_BUNDLE_ID);
        if (platform == null) {
            return UNKNOWN;
        }
        return cleanVersion(platform.getVersion());
    }

    /** Renders an OSGi {@link Version} as clean {@code major.minor.micro}, dropping the qualifier segment. */
    private static String cleanVersion(final Version v) {
        if (v == null) {
            return UNKNOWN;
        }
        return v.getMajor() + "." + v.getMinor() + "." + v.getMicro();
    }

    private static FeatureAuthDetails resolveQAuth() {
        final AuthState authState = Activator.getLoginService().getAuthState();
        if (authState == null) {
            return new FeatureAuthDetails(UNKNOWN, UNKNOWN, "NotConnected");
        }
        return new FeatureAuthDetails(
                mapConnectionType(authState.loginType()),
                mapRegion(authState),
                mapConnectionState(authState.authStateType()));
    }

    private static String mapConnectionType(final LoginType loginType) {
        if (loginType == null) {
            return UNKNOWN;
        }
        switch (loginType) {
            case BUILDER_ID:
                return "BuilderId";
            case IAM_IDENTITY_CENTER:
                return "Idc";
            default:
                return UNKNOWN;
        }
    }

    private static String mapConnectionState(final AuthStateType authStateType) {
        if (authStateType == null) {
            return "NotConnected";
        }
        switch (authStateType) {
            case LOGGED_IN:
                return "Connected";
            case EXPIRED:
                return "Expired";
            case LOGGED_OUT:
            default:
                return "NotConnected";
        }
    }

    private static String mapRegion(final AuthState authState) {
        if (authState.loginParams() != null && authState.loginParams().getLoginIdcParams() != null) {
            final String region = authState.loginParams().getLoginIdcParams().getRegion();
            if (region != null && !region.isBlank()) {
                return region;
            }
        }
        return UNKNOWN;
    }
}
