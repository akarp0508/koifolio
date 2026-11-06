package io.github.akarp0508.koifolio.split;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.domain.PositionCalculator;
import io.github.akarp0508.koifolio.domain.Trade;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import io.github.akarp0508.koifolio.instrument.InstrumentService.QuoteRef;
import io.github.akarp0508.koifolio.price.SplitSuggestion;
import io.github.akarp0508.koifolio.price.SplitSuggestionRepository;
import io.github.akarp0508.koifolio.transaction.AccountTrade;
import io.github.akarp0508.koifolio.transaction.TransactionService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Offers detected splits to the users they affect. Nothing changes until a user adds one. */
@Service
public class SplitSuggestionService {

    /** A split entered this close to the suggested date is taken to be the same one, since that date is approximate. */
    private static final int SAME_SPLIT_DAYS = 14;

    private final SplitSuggestionRepository suggestions;
    private final InstrumentService instruments;
    private final TransactionService transactions;
    private final AccountRepository accounts;

    SplitSuggestionService(SplitSuggestionRepository suggestions, InstrumentService instruments,
                           TransactionService transactions, AccountRepository accounts) {
        this.suggestions = suggestions;
        this.instruments = instruments;
        this.transactions = transactions;
        this.accounts = accounts;
    }

    public record Suggestion(SplitSuggestion split, String ticker, List<Account> accounts) {
    }

    /** Splits the user hasn't entered or dismissed, with the accounts that held the stock at the time. */
    @Transactional(readOnly = true)
    public List<Suggestion> forUser(UUID userId) {
        Map<UUID, QuoteRef> refs = instruments.quoteRefs(userId);
        if (refs.isEmpty()) {
            return List.of();
        }
        Map<String, UUID> instrumentBySymbol = refs.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getValue().symbol(), Map.Entry::getKey, (a, b) -> a));
        Set<UUID> dismissed = Set.copyOf(suggestions.findDismissed(userId));
        Map<UUID, String> tickers = instruments.tickers(userId);
        List<AccountTrade> trades = transactions.tradesOldestFirst(userId);

        List<Suggestion> found = new ArrayList<>();
        for (SplitSuggestion split : suggestions.findBySymbolIn(instrumentBySymbol.keySet())) {
            if (dismissed.contains(split.getId())) {
                continue;
            }
            UUID instrumentId = instrumentBySymbol.get(split.getSymbol());
            List<Account> affected = accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                    .filter(account -> needsSplit(account, instrumentId, split, trades))
                    .toList();
            if (!affected.isEmpty()) {
                found.add(new Suggestion(split, tickers.get(instrumentId), affected));
            }
        }
        found.sort(Comparator.comparing(suggestion -> suggestion.split().getDate()));
        return found;
    }

    /** Adds the split to every affected account. */
    @Transactional
    public void apply(UUID userId, UUID id) {
        Suggestion suggestion = find(userId, id);
        SplitSuggestion split = suggestion.split();
        for (Account account : suggestion.accounts()) {
            transactions.addSplit(userId, account.getId(), suggestion.ticker(), split.getDate(),
                    BigDecimal.valueOf(split.getSplitFrom()), BigDecimal.valueOf(split.getSplitTo()));
        }
    }

    @Transactional
    public void dismiss(UUID userId, UUID id) {
        find(userId, id);
        suggestions.dismiss(id, userId);
    }

    private Suggestion find(UUID userId, UUID id) {
        return forUser(userId).stream()
                .filter(suggestion -> suggestion.split().getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "suggestion_not_found", "Suggestion not found"));
    }

    private static boolean needsSplit(Account account, UUID instrumentId, SplitSuggestion split, List<AccountTrade> trades) {
        List<AccountTrade> own = trades.stream()
                .filter(trade -> trade.accountId().equals(account.getId()) && trade.instrumentId().equals(instrumentId))
                .toList();
        boolean alreadyEntered = own.stream().anyMatch(trade -> trade.trade().side() == Trade.Side.SPLIT
                && !trade.date().isBefore(split.getDate().minusDays(SAME_SPLIT_DAYS))
                && !trade.date().isAfter(split.getDate().plusDays(SAME_SPLIT_DAYS)));
        if (alreadyEntered) {
            return false;
        }
        List<Trade> before = own.stream().filter(trade -> trade.date().isBefore(split.getDate()))
                .map(AccountTrade::trade).toList();
        return PositionCalculator.calculate(before, account.getCostMethod()).quantity().signum() > 0;
    }
}
