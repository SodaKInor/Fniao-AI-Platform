package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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

    static String text(JsonNode node, String name, int max) {
        JsonNode value = node.get(name);
        require(value != null && value.isTextual() && !value.textValue().isEmpty() && value.textValue().length() <= max);
        return value.textValue();
    }

    static long integer(JsonNode node, String name, long min, long max) {
        JsonNode value = node.get(name);
        require(value != null && value.isIntegralNumber() && value.canConvertToLong());
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

    static boolean bool(JsonNode node, String name) {
        JsonNode value = node.get(name);
        require(value != null && value.isBoolean());
        return value.booleanValue();
    }

    static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid draft response");
    }
}
