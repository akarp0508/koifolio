package io.github.akarp0508.koifolio.dashboard;

import io.github.akarp0508.koifolio.domain.Breakdown;
import io.github.akarp0508.koifolio.domain.PlanCheck;
import io.github.akarp0508.koifolio.domain.PortfolioHistory;
import io.github.akarp0508.koifolio.domain.PositionResult;
import io.github.akarp0508.koifolio.domain.Valuation;
import io.github.akarp0508.koifolio.position.PositionService;
import io.github.akarp0508.koifolio.position.PositionService.Position;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.price.FxService;
import io.github.akarp0508.koifolio.price.PriceHistoryService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Totals in the user's base currency, at the latest rates, for one filtered view of their portfolio. */
@Service
class DashboardService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AuthService authService;
    private final PositionService positions;
    private final AccountRepository accounts;
    private final FxService fx;

    DashboardService(AuthService authService, PositionService positions, AccountRepository accounts, FxService fx) {
        this.authService = authService;
        this.positions = positions;
        this.accounts = accounts;
        this.fx = fx;
    }

    /** Tags of one kind match if any applies; the kinds and the account must all match. */
    record Filter(UUID account, Set<String> instrumentTags, Set<String> positionTags) {

        boolean hasTags() {
            return !instrumentTags.isEmpty() || !positionTags.isEmpty();
        }

        boolean matches(Position position) {
            return (account == null || position.account().getId().equals(account))
                    && matches(instrumentTags, position.instrument().details().tags())
                    && matches(positionTags, position.tags());
        }

        private static boolean matches(Set<String> wanted, Set<String> tags) {
            return wanted.isEmpty() || tags.stream().anyMatch(wanted::contains);
        }
    }

    record Attention(Position position, PlanCheck check) {
    }

    /**
     * @param value positions plus cash, without cash while tags filter the view
     * @param cash empty while tags filter the view, because cash has no tags
     * @param unrealizedGain value now against cost at each purchase's rate
     * @param currencyGain the part of it from exchange rates moving, for accounts in another currency
     * @param gainPercent gain against the cost of priced positions, empty without any
     * @param unpriced open positions left out of the numbers, lacking a price or an FX rate
     */
    record Dashboard(String currency, BigDecimal value, BigDecimal positionsValue, BigDecimal unrealizedGain,
                     BigDecimal currencyGain, Optional<BigDecimal> gainPercent, BigDecimal realizedGain, Optional<BigDecimal> cash,
                     List<Breakdown.Slice> bySector, List<Breakdown.Slice> byRisk, List<Attention> attention,
                     int unpriced) {
    }

    @Transactional(readOnly = true)
    Dashboard build(UUID userId, Filter filter) {
        String base = authService.currentUser(userId).getBaseCurrency();
        FxService.Rates rates = fx.rates();

        BigDecimal value = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal currencyGain = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        int unpriced = 0;
        List<Breakdown.Part> sectors = new ArrayList<>();
        List<Breakdown.Part> risks = new ArrayList<>();
        List<Attention> attention = new ArrayList<>();

        for (Position position : positions.list(userId)) {
            if (!filter.matches(position)) {
                continue;
            }
            String currency = position.account().getCurrency();
            realized = add(realized, position.inBase().map(PositionResult::realizedGain).orElse(BigDecimal.ZERO));
            position.planCheck().filter(check -> check.signal().isPresent())
                    .ifPresent(check -> attention.add(new Attention(position, check)));
            if (position.result().isClosed()) {
                continue;
            }

            Optional<Valuation> valuation = position.accountPrice().map(price -> Valuation.of(position.result(), price));
            Optional<BigDecimal> valueInBase = valuation.flatMap(v -> rates.convert(v.value(), currency, base));
            Optional<BigDecimal> costToday = rates.convert(position.result().cost(), currency, base);
            if (valueInBase.isEmpty() || costToday.isEmpty() || position.inBase().isEmpty()) {
                unpriced++;
                continue;
            }
            BigDecimal costInBase = position.inBase().get().cost();
            value = add(value, valueInBase.get());
            cost = add(cost, costInBase);
            currencyGain = add(currencyGain, costToday.get().subtract(costInBase, MathContext.DECIMAL128));
            sectors.add(new Breakdown.Part(sectorOf(position), valueInBase.get()));
            risks.add(new Breakdown.Part(riskGroup(position.risk()), valueInBase.get()));
        }

        Optional<BigDecimal> cash = filter.hasTags() ? Optional.empty() : Optional.of(cash(userId, filter, rates, base));
        BigDecimal gain = value.subtract(cost, MathContext.DECIMAL128);
        Optional<BigDecimal> percent = cost.signum() == 0 ? Optional.empty()
                : Optional.of(gain.multiply(HUNDRED, MathContext.DECIMAL128).divide(cost, MathContext.DECIMAL128));
        return new Dashboard(base, add(value, cash.orElse(BigDecimal.ZERO)), value, gain, currencyGain, percent, realized, cash,
                Breakdown.of(sectors), Breakdown.of(risks), attention, unpriced);
    }

    /** Totals over time, each day at that day's rate. */
    record History(String currency, List<PortfolioHistory.Point> points, List<PortfolioHistory.Change> changes) {
    }

    @Transactional(readOnly = true)
    History history(UUID userId, Filter filter) {
        String base = authService.currentUser(userId).getBaseCurrency();
        FxService.History rates = fx.history();
        List<Position> matching = positions.list(userId).stream().filter(filter::matches).toList();

        List<List<PortfolioHistory.Point>> series = positions.points(userId, matching, rates).stream()
                .map(position -> position.points().stream()
                        .map(point -> new PortfolioHistory.Point(point.date(), point.value(), point.cost(), point.realized()))
                        .toList())
                .toList();
        List<PortfolioHistory.Point> totals = PortfolioHistory.total(series);
        LocalDate today = LocalDate.now(PriceHistoryService.MARKET_ZONE);
        return new History(base, totals, PortfolioHistory.changes(totals, today));
    }

    private BigDecimal cash(UUID userId, Filter filter, FxService.Rates rates, String base) {
        BigDecimal total = BigDecimal.ZERO;
        for (Account account : accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            if (filter.account() == null || account.getId().equals(filter.account())) {
                total = add(total, rates.convert(account.getBalance(), account.getCurrency(), base).orElse(BigDecimal.ZERO));
            }
        }
        return total;
    }

    private static Optional<String> sectorOf(Position position) {
        return Optional.of(position.instrument().details().sector()).filter(sector -> !sector.isEmpty());
    }

    private static Optional<String> riskGroup(Integer risk) {
        if (risk == null) {
            return Optional.empty();
        }
        return Optional.of(risk <= 2 ? "1-2" : risk == 3 ? "3" : "4-5");
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b, MathContext.DECIMAL128);
    }
}
