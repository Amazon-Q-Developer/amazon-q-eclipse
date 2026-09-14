// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A notification display-condition expression, encoded in the hosted JSON as a single-key wrapper
 * object (for example <code>{ "==": "1.0" }</code> or <code>{ "and": [ ... ] }</code>). The operator
 * is the wrapper key; the value is a bare string, an array of strings, or nested expressions.
 * Ported from the JetBrains schema-2.x notification model so the rules engine can match 1:1.
 */
@JsonDeserialize(using = NotificationExpressionDeserializer.class)
public sealed interface NotificationExpression {

    /** Matches when the actual value equals the given value (<code>==</code>). */
    record ComparisonCondition(String value) implements NotificationExpression { }

    /** Matches when the actual value does not equal the given value (<code>!=</code>). */
    record NotEqualsCondition(String value) implements NotificationExpression { }

    /** Matches when the actual value is greater than the given value (<code>&gt;</code>). */
    record GreaterThanCondition(String value) implements NotificationExpression { }

    /** Matches when the actual value is greater than or equal to the given value (<code>&gt;=</code>). */
    record GreaterThanOrEqualsCondition(String value) implements NotificationExpression { }

    /** Matches when the actual value is less than the given value (<code>&lt;</code>). */
    record LessThanCondition(String value) implements NotificationExpression { }

    /** Matches when the actual value is less than or equal to the given value (<code>&lt;=</code>). */
    record LessThanOrEqualsCondition(String value) implements NotificationExpression { }

    /** Matches when the actual value is contained in the given list (<code>anyOf</code>). */
    record AnyOfCondition(List<String> value) implements NotificationExpression { }

    /** Matches when the actual value is not contained in the given list (<code>noneOf</code>). */
    record NoneOfCondition(List<String> value) implements NotificationExpression { }

    /** Matches when every nested expression matches (<code>and</code>). */
    record AndCondition(List<NotificationExpression> expectedValueList) implements NotificationExpression { }

    /** Matches when any nested expression matches (<code>or</code>). */
    record OrCondition(List<NotificationExpression> expectedValueList) implements NotificationExpression { }

    /** Matches when the nested expression does not match (<code>not</code>). */
    record NotCondition(NotificationExpression expectedValue) implements NotificationExpression { }
}
