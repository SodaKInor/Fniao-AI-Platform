package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.math.BigDecimal;

/** Strict, protocol-specific field checks, not a general JSON mapping API. */
final class DraftFields {
    private DraftFields() { }

    static void object(JsonNode node, String... names) {
        require(node != null && node.isObject() && node.size() == names.length);
        Set<String> expected = new HashSet<>(Arrays.asList(names));
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) require(expected.remove(fields.next()));
        require(expected.isEmpty());
    }

    static void object(JsonNode node, String[] required, String... optional) {
        require(node != null && node.isObject());
        Set<String> allowed = new HashSet<>(Arrays.asList(required));
        allowed.addAll(Arrays.asList(optional));
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) require(allowed.contains(fields.next()));
        for (String name : required) require(node.has(name));
    }

    static String text(JsonNode node, String name, int max) {
        JsonNode value = node.get(name);
        require(value != null && value.isTextual() && !value.textValue().isEmpty() && value.textValue().length() <= max);
        return value.textValue();
    }

    static String nullableText(JsonNode node, String name, int max) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) return null;
        require(value.isTextual() && !value.textValue().isEmpty() && value.textValue().length() <= max);
        return value.textValue();
    }

    static long integer(JsonNode node, String name, long min, long max) {
        JsonNode value = node.get(name);
        require(value != null && value.isIntegralNumber() && value.canConvertToLong());
        long number = value.longValue();
        require(number >= min && number <= max);
        return number;
    }

    static Long nullableInteger(JsonNode node, String name, long min, long max) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) return null;
        require(value.isIntegralNumber() && value.canConvertToLong());
        long number = value.longValue();
        require(number >= min && number <= max);
        return number;
    }

    static double unit(JsonNode node, String name) {
        JsonNode value = node.get(name);
        require(value != null && value.isNumber());
        double number = value.doubleValue();
        require(Double.isFinite(number) && number >= 0 && number <= 1);
        return number;
    }

    static BigDecimal nullableUnit(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) return null;
        require(value.isNumber());
        BigDecimal number = value.decimalValue();
        require(number.compareTo(BigDecimal.ZERO) >= 0 && number.compareTo(BigDecimal.ONE) <= 0);
        return number;
    }

    static boolean bool(JsonNode node, String name) {
        JsonNode value = node.get(name);
        require(value != null && value.isBoolean());
        return value.booleanValue();
    }

    static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid draft response");
    }
}
