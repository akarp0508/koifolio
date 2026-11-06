package io.github.akarp0508.koifolio.agent;

import io.github.akarp0508.koifolio.FieldValidationException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads tool arguments that stay text in the schema. Money stays text so it never passes through a
 * floating-point number on the way, and enums so their schema doesn't list the Java names.
 */
public final class ToolArguments {

    private ToolArguments() {
    }

    /** @return null if it wasn't given */
    public static BigDecimal decimal(String field, String value) {
        try {
            return value == null ? null : new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            throw new FieldValidationException(Map.of(field, "must be a number such as 12.50"));
        }
    }

    public static <E extends Enum<E>> E choice(Class<E> type, String field, String value) {
        return Arrays.stream(type.getEnumConstants())
                .filter(option -> option.name().equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElseThrow(() -> new FieldValidationException(Map.of(field, "must be one of " + Arrays.stream(type.getEnumConstants())
                        .map(option -> option.name().toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", ")))));
    }
}
