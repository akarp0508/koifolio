package io.github.akarp0508.koifolio.tax;

import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.TaxEstimate;
import io.github.akarp0508.koifolio.position.PositionService;
import io.github.akarp0508.koifolio.position.PositionService.Position;
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

/** Tax estimates in the base currency. Only sales count: dividends are normally taxed at source. */
@Service
class TaxService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final AuthService authService;
    private final PositionService positions;
    private final AccountRepository accounts;

    TaxService(AuthService authService, PositionService positions, AccountRepository accounts) {
        this.authService = authService;
        this.positions = positions;
        this.accounts = accounts;
    }

    record OpenPosition(Account account, String ticker, BigDecimal gain) {
    }

    /**
     * @param unsetAccounts accounts with something to tax but no rate, so left out of the estimate
     * @param open positions that would add to the tax if sold, for the current year only
     */
    record Estimate(String currency, int year, boolean currentYear, List<Integer> years,
                    List<TaxEstimate.Group<Account>> groups, List<Account> unsetAccounts, List<OpenPosition> open) {
    }

    @Transactional(readOnly = true)
    Estimate estimate(UUID userId, Integer requestedYear) {
        String base = authService.currentUser(userId).getBaseCurrency();
        int thisYear = LocalDate.now(ZONE).getYear();
        int year = requestedYear == null ? thisYear : requestedYear;
        boolean currentYear = year == thisYear;

        Map<Integer, Map<UUID, BigDecimal>> realized = positions.realizedByYear(userId);
        Map<UUID, BigDecimal> realizedThisYear = realized.getOrDefault(year, Map.of());

        Map<UUID, BigDecimal> unrealized = new HashMap<>();
        List<OpenPosition> open = new ArrayList<>();
        if (currentYear) {
            for (Position position : positions.list(userId)) {
                Optional<BigDecimal> gain = position.result().isClosed() ? Optional.empty() : position.gainInBase();
                gain.ifPresent(amount -> {
                    unrealized.merge(position.account().getId(), amount, (a, b) -> a.add(b, MathContext.DECIMAL128));
                    open.add(new OpenPosition(position.account(), position.ticker(), amount));
                });
            }
        }

        List<TaxEstimate.AccountGain<Account>> gains = new ArrayList<>();
        List<Account> unset = new ArrayList<>();
        for (Account account : accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            BigDecimal sold = realizedThisYear.get(account.getId());
            BigDecimal held = unrealized.get(account.getId());
            if (sold == null && held == null) {
                continue;
            }
            if (account.getTaxRate() == null) {
                unset.add(account);
            } else {
                gains.add(new TaxEstimate.AccountGain<>(account, account.getTaxRate(),
                        sold == null ? BigDecimal.ZERO : sold, held == null ? BigDecimal.ZERO : held));
            }
        }

        TreeSet<Integer> years = new TreeSet<>(realized.keySet());
        years.add(thisYear);
        return new Estimate(base, year, currentYear, List.copyOf(years.descendingSet()), TaxEstimate.group(gains), unset, open);
    }
}
