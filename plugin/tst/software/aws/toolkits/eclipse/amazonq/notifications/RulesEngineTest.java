// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.AuthxType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.ComputeType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.ExtensionType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationDisplayCondition;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationSchedule;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationScheduleType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.SystemType;

/** Pure, deterministic coverage for the rules engine (top JaCoCo contributor; no mocks needed). */
public final class RulesEngineTest {

    private static final String PLUGIN_ID = "amazon-q-eclipse";

    private static SystemDetails sys(final String pluginVersion, final FeatureAuthDetails qAuth) {
        return new SystemDetails("Local", "aarch64", "Mac OS X", "14.5.0", "Eclipse", "4.30.0",
                Map.of(PLUGIN_ID, pluginVersion), qAuth);
    }

    private static SystemDetails defaultSys() {
        return sys("1.70.0", new FeatureAuthDetails("BuilderId", "us-east-1", "Connected"));
    }

    private static NotificationData notification(final NotificationDisplayCondition condition) {
        return new NotificationData("id", new NotificationSchedule(NotificationScheduleType.EMERGENCY), "Info",
                condition, null, null);
    }

    private static NotificationExpression eq(final String v) {
        return new NotificationExpression.ComparisonCondition(v);
    }

    @Test
    void nullConditionShowsToEveryone() {
        assertTrue(RulesEngine.displayNotification(notification(null), defaultSys()));
    }

    @Test
    void equalsAndNotEquals() {
        assertTrue(RulesEngine.evaluateNotificationExpression(eq("a"), "a"));
        assertFalse(RulesEngine.evaluateNotificationExpression(eq("a"), "b"));
        assertTrue(RulesEngine.evaluateNotificationExpression(new NotificationExpression.NotEqualsCondition("a"), "b"));
        assertFalse(RulesEngine.evaluateNotificationExpression(new NotificationExpression.NotEqualsCondition("a"), "a"));
    }

    @Test
    void semverOrderingForVersions() {
        // 3.101.0 is NEWER than 3.74.0 numerically (lexical would say "1" < "7").
        assertTrue(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.GreaterThanCondition("3.74.0"), "3.101.0", true));
        assertFalse(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.LessThanCondition("3.74.0"), "3.101.0", true));
        assertTrue(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.GreaterThanOrEqualsCondition("1.0"), "1.0", true));
        assertTrue(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.LessThanOrEqualsCondition("2.0"), "2.0", true));
    }

    @Test
    void malformedSemverFallsBackToLexical() {
        // "abc" is not clean semver -> lexical compare: "abc" > "1.0".
        assertTrue(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.GreaterThanCondition("1.0"), "abc", true));
    }

    @Test
    void anyOfNoneOfNotOrAnd() {
        assertTrue(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.AnyOfCondition(List.of("Darwin", "Linux")), "Darwin"));
        assertFalse(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.NoneOfCondition(List.of("Darwin")), "Darwin"));
        assertTrue(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.NotCondition(eq("x")), "y"));
        assertTrue(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.OrCondition(List.of(eq("a"), eq("b"))), "b"));
        assertFalse(RulesEngine.evaluateNotificationExpression(
                new NotificationExpression.AndCondition(List.of(eq("a"), eq("b"))), "a"));
    }

    @Test
    void computeOsIdeConditionsAreAnded() {
        final NotificationDisplayCondition cond = new NotificationDisplayCondition(
                new ComputeType(eq("Local"), null),
                new SystemType(new NotificationExpression.AnyOfCondition(List.of("Mac OS X")), null),
                new SystemType(eq("Eclipse"), new NotificationExpression.GreaterThanOrEqualsCondition("4.0.0")),
                null, null);
        assertTrue(RulesEngine.displayNotification(notification(cond), defaultSys()));

        final NotificationDisplayCondition mismatch = new NotificationDisplayCondition(
                new ComputeType(eq("Remote"), null), null, null, null, null);
        assertFalse(RulesEngine.displayNotification(notification(mismatch), defaultSys()));
    }

    @Test
    void extensionNoneInstalledDoesNotShow() {
        final NotificationDisplayCondition cond = new NotificationDisplayCondition(null, null, null,
                List.of(new ExtensionType("not.installed.ext", null)), null);
        assertFalse(RulesEngine.displayNotification(notification(cond), defaultSys()));
    }

    @Test
    void extensionInstalledWithMatchingVersionShows() {
        final NotificationDisplayCondition cond = new NotificationDisplayCondition(null, null, null,
                List.of(new ExtensionType(PLUGIN_ID, new NotificationExpression.LessThanCondition("2.0.0"))), null);
        assertTrue(RulesEngine.displayNotification(notification(cond), defaultSys()));
    }

    @Test
    void snapshotPluginVersionNeverShows() {
        final NotificationDisplayCondition cond = new NotificationDisplayCondition(null, null, null,
                List.of(new ExtensionType(PLUGIN_ID, null)), null);
        assertFalse(RulesEngine.displayNotification(notification(cond), sys("1.70.0-SNAPSHOT",
                new FeatureAuthDetails("BuilderId", "us-east-1", "Connected"))));
    }

    @Test
    void authxMatchingForQFeature() {
        final NotificationDisplayCondition cond = new NotificationDisplayCondition(null, null, null, null,
                List.of(new AuthxType("q", null, null, eq("Connected"), null)));
        assertTrue(RulesEngine.displayNotification(notification(cond), defaultSys()));

        final NotificationDisplayCondition wantExpired = new NotificationDisplayCondition(null, null, null, null,
                List.of(new AuthxType("q", null, null, eq("Expired"), null)));
        assertFalse(RulesEngine.displayNotification(notification(wantExpired), defaultSys()));
    }

    @Test
    void authxNonQFeaturePasses() {
        final NotificationDisplayCondition cond = new NotificationDisplayCondition(null, null, null, null,
                List.of(new AuthxType("codeCatalyst", eq("whatever"), null, null, null)));
        assertTrue(RulesEngine.displayNotification(notification(cond), defaultSys()));
    }

    @Test
    void loggedOutIsEvaluableAsNotConnected() {
        final SystemDetails loggedOut = sys("1.70.0", new FeatureAuthDetails("Unknown", "Unknown", "NotConnected"));
        final NotificationDisplayCondition cond = new NotificationDisplayCondition(null, null, null, null,
                List.of(new AuthxType("q", null, null, eq("NotConnected"), null)));
        assertTrue(RulesEngine.displayNotification(notification(cond), loggedOut));
    }
}
