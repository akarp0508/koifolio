package io.github.akarp0508.koifolio.instrument;

import java.util.Set;

/**
 * What the user knows about a company, whichever account holds it.
 *
 * @param risk 1 (very low) to 5 (very high), or null if not set
 * @param analysis Markdown
 */
public record InstrumentDetails(String name, String sector, Integer risk, String thesis, String analysis,
                                Set<String> tags) {

    public InstrumentDetails {
        name = clean(name);
        sector = clean(sector);
        thesis = thesis == null ? "" : thesis.strip();
        analysis = analysis == null ? "" : analysis;
        tags = Tags.clean(tags);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
