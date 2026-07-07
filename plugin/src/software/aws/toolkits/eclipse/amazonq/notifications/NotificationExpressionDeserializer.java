// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Deserializes a {@link NotificationExpression} from its single-key operator-wrapper object form,
 * for example <code>{ "&gt;=": "1.0" }</code>, <code>{ "anyOf": ["a", "b"] }</code>, or
 * <code>{ "and": [ { "&gt;=": "1.0" }, { "&lt;": "2.0" } ] }</code>. The <code>and</code>,
 * <code>or</code>, and <code>not</code> operators nest recursively.
 */
public final class NotificationExpressionDeserializer extends JsonDeserializer<NotificationExpression> {

    @Override
    public NotificationExpression deserialize(final JsonParser parser, final DeserializationContext ctxt) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || !node.isObject() || node.size() != 1) {
            throw new JsonMappingException(parser, "Notification expression must be a single-key operator object");
        }

        Map.Entry<String, JsonNode> entry = node.fields().next();
        String operator = entry.getKey();
        JsonNode value = entry.getValue();

        switch (operator) {
            case "==":
                return new NotificationExpression.ComparisonCondition(value.asText());
            case "!=":
                return new NotificationExpression.NotEqualsCondition(value.asText());
            case ">":
                return new NotificationExpression.GreaterThanCondition(value.asText());
            case ">=":
                return new NotificationExpression.GreaterThanOrEqualsCondition(value.asText());
            case "<":
                return new NotificationExpression.LessThanCondition(value.asText());
            case "<=":
                return new NotificationExpression.LessThanOrEqualsCondition(value.asText());
            case "anyOf":
                return new NotificationExpression.AnyOfCondition(toStringList(parser, value, operator));
            case "noneOf":
                return new NotificationExpression.NoneOfCondition(toStringList(parser, value, operator));
            case "and":
                return new NotificationExpression.AndCondition(toExpressionList(parser, value, operator));
            case "or":
                return new NotificationExpression.OrCondition(toExpressionList(parser, value, operator));
            case "not":
                return new NotificationExpression.NotCondition(toExpression(parser, value));
            default:
                throw new JsonMappingException(parser, "Unknown notification expression operator: " + operator);
        }
    }

    private List<String> toStringList(final JsonParser parser, final JsonNode value, final String operator) throws JsonMappingException {
        if (!value.isArray()) {
            throw new JsonMappingException(parser, operator + " must contain an array of values");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : value) {
            values.add(element.asText());
        }
        return values;
    }

    private List<NotificationExpression> toExpressionList(final JsonParser parser, final JsonNode value, final String operator)
            throws IOException {
        if (!value.isArray()) {
            throw new JsonMappingException(parser, operator + " must contain an array of expressions");
        }
        List<NotificationExpression> expressions = new ArrayList<>();
        Iterator<JsonNode> elements = value.elements();
        while (elements.hasNext()) {
            expressions.add(toExpression(parser, elements.next()));
        }
        return expressions;
    }

    private NotificationExpression toExpression(final JsonParser parser, final JsonNode value) throws IOException {
        return parser.getCodec().treeToValue(value, NotificationExpression.class);
    }
}
