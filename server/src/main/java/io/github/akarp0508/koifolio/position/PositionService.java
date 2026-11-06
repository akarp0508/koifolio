package io.github.akarp0508.koifolio.position;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.DailyClose;
import io.github.akarp0508.koifolio.domain.DatedTrade;
import io.github.akarp0508.koifolio.domain.PlanCheck;
import io.github.akarp0508.koifolio.domain.PositionCalculator;
import io.github.akarp0508.koifolio.domain.PositionHistory;
import io.github.akarp0508.koifolio.domain.PositionResult;
import io.github.akarp0508.koifolio.domain.Trade;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import io.github.akarp0508.koifolio.instrument.InstrumentService.InstrumentView;
import io.github.akarp0508.koifolio.price.PriceHistoryService;
import io.github.akarp0508.koifolio.price.PriceService;
import io.github.akarp0508.koifolio.price.PriceService.CurrentPrice;
import io.github.akarp0508.koifolio.transaction.AccountTrade;
import io.github.akarp0508.koifolio.transaction.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Positions aren't stored: they're always worked out from the transactions. */
@Service
class PositionService {

    private final AuthService authService;
    private final AccountRepository accounts;
    private final TransactionService transactions;
    private final InstrumentService instruments;
    private final PriceService prices;
    private final PriceHistoryService priceHistory;
    private final PositionPlanRepository plans;

    PositionService(AuthService authService, AccountRepository accounts, TransactionService transactions,
                    InstrumentService instruments, PriceService prices, PriceHistoryService priceHistory,
                    PositionPlanRepository plans) {
        this.authService = authService;
        this.accounts = accounts;
        this.transactions = transactions;
        this.instruments = instruments;
        this.prices = prices;
        this.priceHistory = priceHistory;
        this.plans = plans;
    }

    /** A position's numbers, the company and the plan. Closed positions work too. */
    @Transactional(readOnly = true)
    Position one(UUID userId, UUID accountId, String ticker) {
        String normalized = InstrumentService.normalizeTicker(ticker);
        return list(userId).stream()
                .filter(position -> position.account().getId().equals(accountId) && position.ticker().equals(normalized))
                .findFirst()
                .orElseThrow(PositionService::notFound);
    }

    @Transactional
    Position savePlan(UUID userId, UUID accountId, String ticker, String text, Integer risk, BigDecimal target,
                      BigDecimal stop) {
        Position position = one(userId, accountId, ticker);
        UUID instrumentId = position.instrument().id();
        PositionPlan plan = plans.findById(new PositionPlan.Key(accountId, instrumentId))
                .orElseGet(() -> new PositionPlan(accountId, instrumentId, userId));
        plan.update(text, risk, target, stop);
        return new Position(position.account(), position.instrument(), position.result(), position.price(),
                Optional.of(plans.save(plan)));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "position_not_found", "Position not found");
    }

    record Position(Account account, InstrumentView instrument, PositionResult result, Optional<CurrentPrice> price,
                    Optional<PositionPlan> plan) {

        String ticker() {
            return instrument.ticker();
        }

        /** The plan's risk if it has one, otherwise the company's. */
        Integer risk() {
            return plan.map(PositionPlan::getRisk).orElse(null) != null
                    ? plan.get().getRisk() : instrument.details().risk();
        }

        Optional<PlanCheck> planCheck() {
            return price.filter(current -> !result.isClosed()).flatMap(current -> plan.map(found -> PlanCheck.of(
                    current.price(), Optional.ofNullable(found.getTargetPrice()), Optional.ofNullable(found.getStopPrice()))));
        }
    }


    record History(Account account, String ticker, List<PositionHistory.Point> points, String priceCurrency,
                   boolean covered) {

        boolean currencyMismatch() {
            return priceCurrency != null && !priceCurrency.equals(account.getCurrency());
        }
    }

    /** Daily closes, then today's price if it's newer. A symbol the source doesn't cover gives no points. */
    @Transactional(readOnly = true)
    History history(UUID userId, UUID accountId, String ticker) {
        authService.currentUser(userId);
        String normalized = InstrumentService.normalizeTicker(ticker);
        Map<UUID, String> tickers = instruments.tickers(userId);
        List<AccountTrade> trades = transactions.tradesOldestFirst(userId).stream()
                .filter(trade -> trade.accountId().equals(accountId))
                .filter(trade -> normalized.equals(tickers.get(trade.instrumentId())))
                .toList();
        Account account = accounts.findByIdAndUserId(accountId, userId).filter(found -> !trades.isEmpty())
                .orElseThrow(PositionService::notFound);

        UUID instrumentId = trades.getFirst().instrumentId();
        InstrumentService.QuoteRef ref = instruments.quoteRefs(userId).get(instrumentId);
        if (ref == null) {
            return new History(account, normalized, List.of(), null, false);
        }

        List<DailyClose> closes = new ArrayList<>(priceHistory.closes(ref.symbol()));
        CurrentPrice current = prices.currentPrices(userId).get(instrumentId);
        if (current != null) {
            LocalDate quoted = current.quotedAt().atZone(PriceHistoryService.MARKET_ZONE).toLocalDate();
            if (closes.isEmpty() || quoted.isAfter(closes.getLast().date())) {
                closes.add(new DailyClose(quoted, current.price()));
            }
        }
        List<DatedTrade> dated = trades.stream().map(trade -> new DatedTrade(trade.date(), trade.trade())).toList();
        return new History(account, normalized, PositionHistory.calculate(dated, closes, account.getCostMethod()),
                ref.currency(), true);
    }

    @Transactional(readOnly = true)
    List<Position> list(UUID userId) {
        authService.currentUser(userId);
        Map<UUID, Account> accountsById = accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, InstrumentView> views = instruments.views(userId);
        Map<UUID, CurrentPrice> currentPrices = prices.currentPrices(userId);
        Map<PositionPlan.Key, PositionPlan> plansByKey = plans.findByUserId(userId).stream().collect(Collectors.toMap(
                plan -> new PositionPlan.Key(plan.getAccountId(), plan.getInstrumentId()), Function.identity()));

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
                    UUID instrumentId = entry.getKey().instrumentId();
                    return new Position(account, views.get(instrumentId),
                            PositionCalculator.calculate(entry.getValue(), account.getCostMethod()),
                            Optional.ofNullable(currentPrices.get(instrumentId)),
                            Optional.ofNullable(plansByKey.get(new PositionPlan.Key(account.getId(), instrumentId))));
                })
                .sorted(Comparator.comparing(Position::ticker).thenComparing(position -> position.account().getName()))
                .toList();
    }
}
