package io.github.akarp0508.koifolio.position;

import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.PositionCalculator;
import io.github.akarp0508.koifolio.domain.PositionResult;
import io.github.akarp0508.koifolio.domain.Trade;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import io.github.akarp0508.koifolio.transaction.AccountTrade;
import io.github.akarp0508.koifolio.transaction.TransactionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Positions aren't stored: they're always worked out from the transactions. */
@Service
class PositionService {

    private final AuthService authService;
    private final AccountRepository accounts;
    private final TransactionService transactions;
    private final InstrumentService instruments;

    PositionService(AuthService authService, AccountRepository accounts, TransactionService transactions,
                    InstrumentService instruments) {
        this.authService = authService;
        this.accounts = accounts;
        this.transactions = transactions;
        this.instruments = instruments;
    }

    record Position(Account account, String ticker, PositionResult result) {
    }

    @Transactional(readOnly = true)
    List<Position> list(UUID userId) {
        authService.currentUser(userId);
        Map<UUID, Account> accountsById = accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, String> tickers = instruments.tickers(userId);

        record Holding(UUID accountId, UUID instrumentId) {
        }
        Map<Holding, List<Trade>> trades = new LinkedHashMap<>();
        for (AccountTrade trade : transactions.tradesOldestFirst(userId)) {
            trades.computeIfAbsent(new Holding(trade.accountId(), trade.instrumentId()), key -> new ArrayList<>())
                    .add(trade.trade());
        }

        return trades.entrySet().stream()
                .map(entry -> {
                    Account account = accountsById.get(entry.getKey().accountId());
                    return new Position(account, tickers.get(entry.getKey().instrumentId()),
                            PositionCalculator.calculate(entry.getValue(), account.getCostMethod()));
                })
                .sorted(Comparator.comparing(Position::ticker).thenComparing(position -> position.account().getName()))
                .toList();
    }
}
