// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.versioning.ArtifactVersion;

import software.aws.toolkits.eclipse.amazonq.lsp.manager.fetcher.ArtifactUtils;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.AuthxType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.ComputeType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.ExtensionType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationDisplayCondition;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.SystemType;

/**
 * Pure evaluator that decides whether a notification's display conditions match the current system/auth state.
 * Ported 1:1 from the JetBrains RulesEngine: a null condition matches everyone; a present condition is an AND of its
 * five optional blocks; version comparisons use semver only for {@code ide.version} and {@code extension.version}.
 */
public final class RulesEngine {

    private static final String CLEAN_SEMVER = "^\\d+(\\.\\d+)*$";

    private RulesEngine() {
        // prevent instantiation
    }

    /** {@code condition == null} shows the notification to everyone. */
    public static boolean displayNotification(final NotificationData notification, final SystemDetails sys) {
        final NotificationDisplayCondition condition = notification.condition();
        return condition == null || matchesAllRules(condition, sys);
    }

    static boolean matchesAllRules(final NotificationDisplayCondition c, final SystemDetails sys) {
        final boolean compute = c.compute() == null
                || matchesCompute(c.compute(), sys.computeType(), sys.computeArchitecture());
        final boolean os = c.os() == null || matchesOs(c.os(), sys.osType(), sys.osVersion());
        final boolean ide = c.ide() == null || matchesIde(c.ide(), sys.ideType(), sys.ideVersion());
        final boolean extension = matchesExtension(c.extension(), sys.pluginVersions());
        final boolean authx = matchesAuth(c.authx(), sys);
        return compute && os && ide && extension && authx;
    }

    private static boolean matchesCompute(final ComputeType nc, final String type, final String arch) {
        final boolean typeMatch = nc.type() == null || evaluateNotificationExpression(nc.type(), type);
        final boolean archMatch = nc.architecture() == null || evaluateNotificationExpression(nc.architecture(), arch);
        return typeMatch && archMatch;
    }

    private static boolean matchesOs(final SystemType no, final String os, final String osVersion) {
        final boolean typeMatch = no.type() == null || evaluateNotificationExpression(no.type(), os);
        final boolean versionMatch = no.version() == null || evaluateNotificationExpression(no.version(), osVersion);
        return typeMatch && versionMatch;
    }

    private static boolean matchesIde(final SystemType ni, final String ide, final String ideVersion) {
        final boolean typeMatch = ni.type() == null || evaluateNotificationExpression(ni.type(), ide);
        final boolean versionMatch = ni.version() == null || evaluateNotificationExpression(ni.version(), ideVersion, true);
        return typeMatch && versionMatch;
    }

    private static boolean matchesExtension(final List<ExtensionType> ne, final Map<String, String> installedVersions) {
        if (ne == null || ne.isEmpty()) {
            return true;
        }
        boolean anyInstalled = false;
        for (final ExtensionType ext : ne) {
            final String installed = installedVersions.get(ext.id());
            if (installed == null) {
                continue;
            }
            anyInstalled = true;
            // Development builds must never receive notifications.
            if (installed.toLowerCase(java.util.Locale.ROOT).contains("snapshot")) {
                return false;
            }
            if (ext.version() != null && !evaluateNotificationExpression(ext.version(), installed, true)) {
                return false;
            }
        }
        // Declared but none of the extensions are installed -> do not show.
        return anyInstalled;
    }

    private static boolean matchesAuth(final List<AuthxType> na, final SystemDetails sys) {
        if (na == null || na.isEmpty()) {
            return true;
        }
        for (final AuthxType feature : na) {
            if (!"q".equals(feature.feature())) {
                // Faithful to JetBrains: any non-"q" feature passes.
                continue;
            }
            final FeatureAuthDetails auth = sys.qAuth();
            if (auth == null) {
                return false;
            }
            final boolean typeMatch = feature.type() == null
                    || evaluateNotificationExpression(feature.type(), auth.connectionType());
            final boolean regionMatch = feature.region() == null
                    || evaluateNotificationExpression(feature.region(), auth.region());
            final boolean stateMatch = feature.connectionState() == null
                    || evaluateNotificationExpression(feature.connectionState(), auth.connectionState());
            if (!(typeMatch && regionMatch && stateMatch)) {
                return false;
            }
        }
        return true;
    }

    /** Evaluates an expression against an actual value, using string comparison for ordering operators. */
    public static boolean evaluateNotificationExpression(final NotificationExpression expr, final String value) {
        return evaluateNotificationExpression(expr, value, false);
    }

    /** Evaluates an expression; when {@code useSemver} is true, ordering operators compare versions semantically. */
    public static boolean evaluateNotificationExpression(final NotificationExpression expr, final String value,
            final boolean useSemver) {
        if (expr instanceof NotificationExpression.ComparisonCondition c) {
            return c.value().equals(value);
        } else if (expr instanceof NotificationExpression.NotEqualsCondition c) {
            return !c.value().equals(value);
        } else if (expr instanceof NotificationExpression.GreaterThanCondition c) {
            return compare(value, c.value(), useSemver) > 0;
        } else if (expr instanceof NotificationExpression.GreaterThanOrEqualsCondition c) {
            return compare(value, c.value(), useSemver) >= 0;
        } else if (expr instanceof NotificationExpression.LessThanCondition c) {
            return compare(value, c.value(), useSemver) < 0;
        } else if (expr instanceof NotificationExpression.LessThanOrEqualsCondition c) {
            return compare(value, c.value(), useSemver) <= 0;
        } else if (expr instanceof NotificationExpression.AnyOfCondition c) {
            return c.value().contains(value);
        } else if (expr instanceof NotificationExpression.NoneOfCondition c) {
            return !c.value().contains(value);
        } else if (expr instanceof NotificationExpression.NotCondition c) {
            return !evaluateNotificationExpression(c.expectedValue(), value, useSemver);
        } else if (expr instanceof NotificationExpression.OrCondition c) {
            return c.expectedValueList().stream().anyMatch(e -> evaluateNotificationExpression(e, value, useSemver));
        } else if (expr instanceof NotificationExpression.AndCondition c) {
            return c.expectedValueList().stream().allMatch(e -> evaluateNotificationExpression(e, value, useSemver));
        }
        return true;
    }

    private static int compare(final String actual, final String expected, final boolean useSemver) {
        return useSemver ? compareSemver(actual, expected) : actual.compareTo(expected);
    }

    private static int compareSemver(final String actual, final String expected) {
        // Match JetBrains: fall back to lexical comparison when either side is not clean numeric semver.
        if (!isCleanSemver(actual) || !isCleanSemver(expected)) {
            return actual.compareTo(expected);
        }
        final ArtifactVersion actualVersion = ArtifactUtils.parseVersion(actual);
        final ArtifactVersion expectedVersion = ArtifactUtils.parseVersion(expected);
        return actualVersion.compareTo(expectedVersion);
    }

    private static boolean isCleanSemver(final String v) {
        return v != null && v.matches(CLEAN_SEMVER);
    }
}
