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
import io.github.akarp0508.koifolio.instrument.InstrumentService.QuoteRef;
import io.github.akarp0508.koifolio.price.FxService;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Positions aren't stored: they're always worked out from the transactions. */
@Service
public class PositionService {

    private final AuthService authService;
    private final AccountRepository accounts;
    private final TransactionService transactions;
    private final InstrumentService instruments;
    private final PriceService prices;
    private final PriceHistoryService priceHistory;
    private final PositionPlanRepository plans;
    private final FxService fx;

    PositionService(AuthService authService, AccountRepository accounts, TransactionService transactions,
                    InstrumentService instruments, PriceService prices, PriceHistoryService priceHistory,
                    PositionPlanRepository plans, FxService fx) {
        this.authService = authService;
        this.accounts = accounts;
        this.transactions = transactions;
        this.instruments = instruments;
        this.prices = prices;
        this.priceHistory = priceHistory;
        this.plans = plans;
        this.fx = fx;
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
                      BigDecimal stop, Set<String> tags) {
        Position position = one(userId, accountId, ticker);
        UUID instrumentId = position.instrument().id();
        PositionPlan plan = plans.findById(new PositionPlan.Key(accountId, instrumentId))
                .orElseGet(() -> new PositionPlan(accountId, instrumentId, userId));
        plan.update(text, risk, target, stop, tags);
        return new Position(position.account(), position.instrument(), position.result(), position.price(),
                position.accountPrice(), Optional.of(plans.save(plan)));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "position_not_found", "Position not found");
    }

    /** @param accountPrice the price in the account's currency, empty if there's none or no FX rate */
    public record Position(Account account, InstrumentView instrument, PositionResult result, Optional<CurrentPrice> price,
                           Optional<BigDecimal> accountPrice, Optional<PositionPlan> plan) {

        public String ticker() {
            return instrument.ticker();
        }

        public Set<String> tags() {
            return plan.map(PositionPlan::getTags).orElse(Set.of());
        }

        /** The plan's risk if it has one, otherwise the company's. */
        public Integer risk() {
            return plan.map(PositionPlan::getRisk).orElse(null) != null
                    ? plan.get().getRisk() : instrument.details().risk();
        }

        public Optional<PlanCheck> planCheck() {
            return price.filter(current -> !result.isClosed()).flatMap(current -> plan.map(found -> PlanCheck.of(
                    current.price(), Optional.ofNullable(found.getTargetPrice()), Optional.ofNullable(found.getStopPrice()))));
        }
    }


    /** @param currencyMismatch prices are in another currency with no rate, so points are in that currency */
    record History(Account account, String ticker, List<PositionHistory.Point> points, String priceCurrency,
                   boolean covered, boolean currencyMismatch) {
    }

    /** Daily closes, then today's price if it's newer. A symbol the source doesn't cover gives no points. */
    @Transactional(readOnly = true)
    History history(UUID userId, UUID accountId, String ticker) {
        authService.currentUser(userId);
        String normalized = InstrumentService.normalizeTicker(ticker);
        Position position = list(userId).stream()
                .filter(found -> found.account().getId().equals(accountId) && found.ticker().equals(normalized))
                .findFirst()
                .orElseThrow(PositionService::notFound);

        UUID instrumentId = position.instrument().id();
        QuoteRef ref = instruments.quoteRefs(userId).get(instrumentId);
        if (ref == null) {
            return new History(position.account(), normalized, List.of(), null, false, false);
        }
        List<DatedTrade> trades = tradesOf(userId, accountId, instrumentId);
        List<DailyClose> closes = closes(ref, prices.currentPrices(userId).get(instrumentId));
        Optional<List<DailyClose>> converted = inAccountCurrency(closes, ref.currency(), position.account(), fx.history());
        return new History(position.account(), normalized,
                PositionHistory.calculate(trades, converted.orElse(closes), position.account().getCostMethod()),
                ref.currency(), true, converted.isEmpty());
    }

    /** Each position's daily points, in its account's currency. Positions with no prices are left out. */
    @Transactional(readOnly = true)
    public List<PointsOf> points(UUID userId, List<Position> positions, FxService.History rates) {
        Map<UUID, QuoteRef> refs = instruments.quoteRefs(userId);
        Map<UUID, CurrentPrice> current = prices.currentPrices(userId);
        List<AccountTrade> allTrades = transactions.tradesOldestFirst(userId);

        List<PointsOf> series = new ArrayList<>();
        for (Position position : positions) {
            UUID instrumentId = position.instrument().id();
            QuoteRef ref = refs.get(instrumentId);
            if (ref == null) {
                continue;
            }
            List<DatedTrade> trades = allTrades.stream()
                    .filter(trade -> trade.accountId().equals(position.account().getId())
                            && trade.instrumentId().equals(instrumentId))
                    .map(trade -> new DatedTrade(trade.date(), trade.trade()))
                    .toList();
            inAccountCurrency(closes(ref, current.get(instrumentId)), ref.currency(), position.account(), rates)
                    .map(closes -> PositionHistory.calculate(trades, closes, position.account().getCostMethod()))
                    .ifPresent(points -> series.add(new PointsOf(position, points)));
        }
        return series;
    }

    public record PointsOf(Position position, List<PositionHistory.Point> points) {
    }

    private List<DatedTrade> tradesOf(UUID userId, UUID accountId, UUID instrumentId) {
        return transactions.tradesOldestFirst(userId).stream()
                .filter(trade -> trade.accountId().equals(accountId) && trade.instrumentId().equals(instrumentId))
                .map(trade -> new DatedTrade(trade.date(), trade.trade()))
                .toList();
    }

    private List<DailyClose> closes(QuoteRef ref, CurrentPrice current) {
        List<DailyClose> closes = new ArrayList<>(priceHistory.closes(ref.symbol()));
        if (current != null) {
            LocalDate quoted = current.quotedAt().atZone(PriceHistoryService.MARKET_ZONE).toLocalDate();
            if (closes.isEmpty() || quoted.isAfter(closes.getLast().date())) {
                closes.add(new DailyClose(quoted, current.price()));
            }
        }
        return closes;
    }

    /** Each day at that day's rate. Empty if the currency has no rate at all. */
    private static Optional<List<DailyClose>> inAccountCurrency(List<DailyClose> closes, String priceCurrency,
                                                                Account account, FxService.History rates) {
        List<DailyClose> converted = new ArrayList<>();
        for (DailyClose close : closes) {
            Optional<BigDecimal> price = rates.convert(close.close(), priceCurrency, account.getCurrency(), close.date());
            if (price.isEmpty()) {
                return Optional.empty();
            }
            converted.add(new DailyClose(close.date(), price.get()));
        }
        return Optional.of(converted);
    }

    @Transactional(readOnly = true)
    public List<Position> list(UUID userId) {
        authService.currentUser(userId);
        Map<UUID, Account> accountsById = accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, InstrumentView> views = instruments.views(userId);
        Map<UUID, CurrentPrice> currentPrices = prices.currentPrices(userId);
        FxService.Rates rates = fx.rates();
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
                    Optional<CurrentPrice> price = Optional.ofNullable(currentPrices.get(instrumentId));
                    return new Position(account, views.get(instrumentId),
                            PositionCalculator.calculate(entry.getValue(), account.getCostMethod()), price,
                            price.flatMap(current -> rates.convert(current.price(), current.currency(), account.getCurrency())),
                            Optional.ofNullable(plansByKey.get(new PositionPlan.Key(account.getId(), instrumentId))));
                })
                .sorted(Comparator.comparing(Position::ticker).thenComparing(position -> position.account().getName()))
                .toList();
    }
}
