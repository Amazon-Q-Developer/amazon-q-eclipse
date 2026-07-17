// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.AuthxType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.ExtensionType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationScheduleType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationSeverity;
import software.aws.toolkits.eclipse.amazonq.util.ObjectMapperFactory;

/**
 * Parse-only coverage for the notification data model and the condition-DSL deserializer. This is the
 * Phase-1 de-risking test: it proves the JetBrains schema-2.x payload (including the polymorphic operator
 * DSL) round-trips through Jackson in Java.
 */
public final class NotificationParsingTest {

    private final ObjectMapper mapper = ObjectMapperFactory.getInstance();

    // The canonical JetBrains example payload (exercises every operator + nesting). Note the file uses the
    // "extensions" (plural) key, which is a latent bug in that fixture: our model reads the singular
    // "extension", so this block deserializes to null here (asserted below), matching the JetBrains client.
    private static final String EXAMPLE_JSON = """
        {
            "schema": { "version": "2.0" },
            "notifications": [
                {
                    "id": "example_id_12344",
                    "schedule": { "type": "StartUp" },
                    "severity": "Critical",
                    "condition": {
                        "compute": {
                            "type": { "or": [ { "==": "ec2" }, { "==": "desktop" } ] },
                            "architecture": { "!=": "x64" }
                        },
                        "os": {
                            "type": { "anyOf": [ "Darwin", "Linux" ] },
                            "version": { "<=": "23.0.1.0" }
                        },
                        "ide": {
                            "type": { "noneOf": [ "PyCharm", "IDEA" ] },
                            "version": { "and": [ { ">=": "1.0" }, { "<": "2.0" } ] }
                        },
                        "extension": [
                            { "id": "aws.toolkit", "version": { "!=": "1.3334" } },
                            { "id": "amazon.q", "version": { "!=": "3.37.0" } }
                        ],
                        "authx": [ {
                            "feature": "q",
                            "type": { "anyOf": [ "IamIdentityCenter", "AwsBuilderId" ] },
                            "region": { "==": "us-east-1" },
                            "connectionState": { "!=": "Connected" },
                            "ssoScopes": { "noneOf": [ "codewhisperer:scope1", "sso:account:access" ] }
                        } ]
                    },
                    "content": {
                        "en-US": { "title": "Look at this!", "description": "Some bug is there" }
                    },
                    "actions": [
                        { "type": "ShowMarketplace", "content": { "en-US": { "title": "Go to market" } } },
                        { "type": "ShowUrl", "content": { "en-US": { "title": "Click me!", "url": "http://nowhere" } } }
                    ]
                }
            ]
        }
        """;

    @Test
    void parsesFullExamplePayload() throws Exception {
        NotificationsList list = mapper.readValue(EXAMPLE_JSON, NotificationsList.class);

        assertEquals("2.0", list.schema().version());
        assertEquals(1, list.notifications().size());

        NotificationData n = list.notifications().get(0);
        assertEquals("example_id_12344", n.id());
        assertEquals(NotificationScheduleType.STARTUP, n.schedule().type());
        assertEquals(NotificationSeverity.CRITICAL, NotificationSeverity.fromString(n.severity()));
        assertEquals("Look at this!", n.content().enUs().title());
        assertEquals("Some bug is there", n.content().enUs().description());
    }

    @Test
    void parsesEveryDslOperator() throws Exception {
        NotificationsList list = mapper.readValue(EXAMPLE_JSON, NotificationsList.class);
        NotificationData.NotificationDisplayCondition cond = list.notifications().get(0).condition();

        // or / == (compute.type), != (compute.architecture)
        assertInstanceOf(NotificationExpression.OrCondition.class, cond.compute().type());
        NotificationExpression.OrCondition or = (NotificationExpression.OrCondition) cond.compute().type();
        assertEquals(2, or.expectedValueList().size());
        assertInstanceOf(NotificationExpression.ComparisonCondition.class, or.expectedValueList().get(0));
        assertEquals("ec2", ((NotificationExpression.ComparisonCondition) or.expectedValueList().get(0)).value());
        assertInstanceOf(NotificationExpression.NotEqualsCondition.class, cond.compute().architecture());

        // anyOf (os.type), <= (os.version)
        NotificationExpression.AnyOfCondition anyOf = (NotificationExpression.AnyOfCondition) cond.os().type();
        assertEquals(List.of("Darwin", "Linux"), anyOf.value());
        assertInstanceOf(NotificationExpression.LessThanOrEqualsCondition.class, cond.os().version());

        // noneOf (ide.type), and[>=, <] (ide.version)
        assertInstanceOf(NotificationExpression.NoneOfCondition.class, cond.ide().type());
        NotificationExpression.AndCondition and = (NotificationExpression.AndCondition) cond.ide().version();
        assertEquals(2, and.expectedValueList().size());
        assertInstanceOf(NotificationExpression.GreaterThanOrEqualsCondition.class, and.expectedValueList().get(0));
        assertInstanceOf(NotificationExpression.LessThanCondition.class, and.expectedValueList().get(1));
    }

    @Test
    void parsesSingularExtensionArray() throws Exception {
        // The example fixture uses the plural "extension" key here (we corrected it in EXAMPLE_JSON), and our
        // model reads the singular field name — so the array is populated, not silently dropped.
        NotificationsList list = mapper.readValue(EXAMPLE_JSON, NotificationsList.class);
        List<ExtensionType> extensions = list.notifications().get(0).condition().extension();

        assertEquals(2, extensions.size());
        assertEquals("aws.toolkit", extensions.get(0).id());
        assertEquals("amazon.q", extensions.get(1).id());
        assertInstanceOf(NotificationExpression.NotEqualsCondition.class, extensions.get(0).version());
    }

    @Test
    void parsesAuthxAndActions() throws Exception {
        NotificationsList list = mapper.readValue(EXAMPLE_JSON, NotificationsList.class);
        NotificationData n = list.notifications().get(0);

        AuthxType authx = n.condition().authx().get(0);
        assertEquals("q", authx.feature());
        assertInstanceOf(NotificationExpression.AnyOfCondition.class, authx.type());
        assertInstanceOf(NotificationExpression.NoneOfCondition.class, authx.ssoScopes());

        // Both actions parse (ShowMarketplace is unknown to the handler but must still deserialize cleanly).
        assertEquals(2, n.actions().size());
        assertEquals("ShowMarketplace", n.actions().get(0).type());
        assertEquals("ShowUrl", n.actions().get(1).type());
        assertEquals("http://nowhere", n.actions().get(1).content().enUs().url());
    }

    @Test
    void scheduleTypeMapsCaseInsensitivelyAndDefaultsToEmergency() {
        assertEquals(NotificationScheduleType.STARTUP, NotificationScheduleType.fromString("startup"));
        assertEquals(NotificationScheduleType.STARTUP, NotificationScheduleType.fromString("StartUp"));
        assertEquals(NotificationScheduleType.EMERGENCY, NotificationScheduleType.fromString("Emergency"));
        assertEquals(NotificationScheduleType.EMERGENCY, NotificationScheduleType.fromString("typo"));
        assertEquals(NotificationScheduleType.EMERGENCY, NotificationScheduleType.fromString(null));
    }

    @Test
    void severityMapsCaseInsensitivelyAndDefaultsToInfo() {
        assertEquals(NotificationSeverity.CRITICAL, NotificationSeverity.fromString("Critical"));
        assertEquals(NotificationSeverity.WARNING, NotificationSeverity.fromString("Warning"));
        assertEquals(NotificationSeverity.INFO, NotificationSeverity.fromString("Info"));
        // Case-insensitive: a mis-cased "critical" must NOT silently downgrade to INFO.
        assertEquals(NotificationSeverity.CRITICAL, NotificationSeverity.fromString("critical"));
        assertEquals(NotificationSeverity.CRITICAL, NotificationSeverity.fromString("CRITICAL"));
        assertEquals(NotificationSeverity.WARNING, NotificationSeverity.fromString("warning"));
        assertEquals(NotificationSeverity.INFO, NotificationSeverity.fromString("info"));
        // Unrecognized and null still default to INFO.
        assertEquals(NotificationSeverity.INFO, NotificationSeverity.fromString("bogus"));
        assertEquals(NotificationSeverity.INFO, NotificationSeverity.fromString(null));
    }

    @Test
    void parsesEmptyNotificationsList() throws Exception {
        NotificationsList list = mapper.readValue(
                "{ \"schema\": { \"version\": \"2.0\" }, \"notifications\": [] }", NotificationsList.class);
        assertTrue(list.notifications().isEmpty());
    }

    @Test
    void ignoresUnknownTopLevelKeysAndAllowsNullCondition() throws Exception {
        NotificationsList list = mapper.readValue("""
            {
                "schema": { "version": "2.0" },
                "futureField": "ignored",
                "notifications": [
                    { "id": "n1", "schedule": { "type": "Emergency" }, "severity": "Info",
                      "content": { "en-US": { "title": "t", "description": "d" } } }
                ]
            }
            """, NotificationsList.class);

        NotificationData n = list.notifications().get(0);
        assertNull(n.condition());
        assertNull(n.actions());
        assertEquals(NotificationScheduleType.EMERGENCY, n.schedule().type());
    }

    @Test
    void rejectsMalformedExpression() {
        // An operator object with two keys is not a valid single-operator expression.
        String badJson = """
            {
                "schema": { "version": "2.0" },
                "notifications": [
                    { "id": "n1", "schedule": { "type": "Emergency" }, "severity": "Info",
                      "condition": { "os": { "type": { "==": "a", "!=": "b" } } },
                      "content": { "en-US": { "title": "t", "description": "d" } } }
                ]
            }
            """;
        assertThrows(Exception.class, () -> mapper.readValue(badJson, NotificationsList.class));
    }
}
