package io.github.akarp0508.koifolio.instrument;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class Tags {

    private Tags() {
    }

    /** Lower case without blanks or repeats, so "Dividend " and "dividend" are one tag. */
    public static Set<String> clean(Collection<String> tags) {
        Set<String> cleaned = new TreeSet<>();
        if (tags != null) {
            for (String tag : tags) {
                String value = tag == null ? "" : tag.strip().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) {
                    cleaned.add(value);
                }
            }
        }
        return cleaned;
    }
}
