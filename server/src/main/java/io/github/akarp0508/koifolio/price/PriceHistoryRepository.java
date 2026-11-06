package io.github.akarp0508.koifolio.price;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PriceHistoryRepository extends JpaRepository<PriceHistory, String> {

    record Needed(String symbol, String isin, LocalDate firstTrade) {
    }

    /** Each symbol someone has traded, with the earliest trade across all users. */
    @Query("""
            select new io.github.akarp0508.koifolio.price.PriceHistoryRepository$Needed(
                i.quoteSymbol, i.quoteIsin, min(t.date))
            from Transaction t, io.github.akarp0508.koifolio.instrument.Instrument i
            where t.instrumentId = i.id and i.quoteSymbol is not null
                and t.type in (io.github.akarp0508.koifolio.transaction.TransactionType.BUY,
                               io.github.akarp0508.koifolio.transaction.TransactionType.SELL,
                               io.github.akarp0508.koifolio.transaction.TransactionType.SPINOFF)
            group by i.quoteSymbol, i.quoteIsin
            order by i.quoteSymbol""")
    List<Needed> findNeeded();

    @Query("""
            select min(t.date) from Transaction t
            where t.type in (io.github.akarp0508.koifolio.transaction.TransactionType.BUY,
                             io.github.akarp0508.koifolio.transaction.TransactionType.SELL,
                             io.github.akarp0508.koifolio.transaction.TransactionType.SPINOFF)""")
    LocalDate findEarliestTrade();
}
