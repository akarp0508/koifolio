package io.github.akarp0508.koifolio.tax;

import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.bond.BondService;
import io.github.akarp0508.koifolio.domain.Dividend;
import io.github.akarp0508.koifolio.domain.TaxEstimate;
import io.github.akarp0508.koifolio.position.PositionService;
import io.github.akarp0508.koifolio.position.PositionService.Position;
import io.github.akarp0508.koifolio.price.FxService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tax estimates in the base currency, for sales and for what's left to pay on dividends. */
@Service
class TaxService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AuthService authService;
    private final PositionService positions;
    private final AccountRepository accounts;
    private final BondService bonds;
    private final FxService fx;

    TaxService(AuthService authService, PositionService positions, AccountRepository accounts, BondService bonds, FxService fx) {
        this.bonds = bonds;
        this.fx = fx;
        this.authService = authService;
        this.positions = positions;
        this.accounts = accounts;
    }

    record OpenPosition(Account account, UUID instrumentId, String ticker, BigDecimal gain) {
    }

    /**
     * @param unsetAccounts accounts with something to tax but no rate, so left out of the estimate
     * @param open positions that would add to the tax if sold, for the current year only
     */
    record Estimate(String currency, int year, boolean currentYear, List<Integer> years,
                    List<TaxEstimate.Group<Account>> groups, List<TaxEstimate.DividendGroup<Account>> dividendGroups,
                    List<Account> unsetAccounts, List<OpenPosition> open) {
    }

    @Transactional(readOnly = true)
    Estimate estimate(UUID userId, Integer requestedYear) {
        String base = authService.currentUser(userId).getBaseCurrency();
        FxService.Rates rates = fx.rates();
        int thisYear = LocalDate.now(ZONE).getYear();
        int year = requestedYear == null ? thisYear : requestedYear;
        boolean currentYear = year == thisYear;

        Map<Integer, Map<UUID, BigDecimal>> realized = positions.realizedByYear(userId);
        Map<UUID, BigDecimal> realizedThisYear = realized.getOrDefault(year, Map.of());
        Map<Integer, Map<UUID, List<Dividend>>> dividends = positions.dividendsByYear(userId);
        Map<UUID, List<Dividend>> dividendsThisYear = dividends.getOrDefault(year, Map.of());

        Map<UUID, BigDecimal> unrealized = new HashMap<>();
        List<OpenPosition> open = new ArrayList<>();
        if (currentYear) {
            for (Position position : positions.list(userId)) {
                Optional<BigDecimal> gain = position.result().isClosed() ? Optional.empty() : position.gainInBase();
                gain.ifPresent(amount -> {
                    unrealized.merge(position.account().getId(), amount, (a, b) -> a.add(b, MathContext.DECIMAL128));
                    open.add(new OpenPosition(position.account(), position.instrument().id(), position.ticker(), amount));
                });
            }
        }

        if (currentYear) {
            for (BondService.Valued bond : bonds.list(userId)) {
                rates.convert(bond.interest(), BondService.CURRENCY, base).ifPresent(amount -> {
                    unrealized.merge(bond.account().getId(), amount, (a, b) -> a.add(b, MathContext.DECIMAL128));
                    open.add(new OpenPosition(bond.account(), null, bond.series(), amount));
                });
            }
        }

        List<TaxEstimate.AccountGain<Account>> gains = new ArrayList<>();
        List<TaxEstimate.AccountDividends<Account>> paid = new ArrayList<>();
        List<Account> unset = new ArrayList<>();
        for (Account account : accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            BigDecimal sold = realizedThisYear.get(account.getId());
            BigDecimal held = unrealized.get(account.getId());
            List<Dividend> received = dividendsThisYear.get(account.getId());
            boolean traded = sold != null || held != null;
            if (!traded && received == null) {
                continue;
            }
            if (account.getTaxRate() == null) {
                unset.add(account);
                continue;
            }
            if (traded) {
                gains.add(new TaxEstimate.AccountGain<>(account, account.getTaxRate(),
                        sold == null ? BigDecimal.ZERO : sold, held == null ? BigDecimal.ZERO : held));
            }
            if (received != null) {
                paid.add(new TaxEstimate.AccountDividends<>(account, account.getTaxRate(), received));
            }
        }

        TreeSet<Integer> years = new TreeSet<>(realized.keySet());
        years.addAll(dividends.keySet());
        years.add(thisYear);
        return new Estimate(base, year, currentYear, List.copyOf(years.descendingSet()), TaxEstimate.group(gains),
                TaxEstimate.groupDividends(paid), unset, open);
    }
}
