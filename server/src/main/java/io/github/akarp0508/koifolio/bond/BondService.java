package io.github.akarp0508.koifolio.bond;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.FieldValidationException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.audit.Actor;
import io.github.akarp0508.koifolio.audit.AuditLog;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.BondValue;
import io.github.akarp0508.koifolio.price.PriceHistoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Polish retail bonds, always in PLN. Their value comes from their terms, so it needs no price source. */
@Service
public class BondService {

    public static final String CURRENCY = "PLN";

    private static final BigDecimal FEE_FROM_SEPTEMBER_2024 = new BigDecimal("1.00");
    private static final BigDecimal FEE_BEFORE_SEPTEMBER_2024 = new BigDecimal("0.70");
    private static final LocalDate FEE_CHANGE = LocalDate.of(2024, 9, 1);
    private static final DateTimeFormatter SERIES_MONTH = DateTimeFormatter.ofPattern("MMyy");

    private final BondLotRepository lots;
    private final AccountRepository accounts;
    private final AuthService authService;
    private final AuditLog auditLog;

    BondService(BondLotRepository lots, AccountRepository accounts, AuthService authService, AuditLog auditLog) {
        this.lots = lots;
        this.accounts = accounts;
        this.authService = authService;
        this.auditLog = auditLog;
    }

    /**
     * @param rates in percent, one per year so far. A TOS needs only one
     * @param earlyFee per bond. Only a TOS can leave it out, since its fee is known
     * @param affectsBalance the purchase came out of the account's cash here
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Input(String series, UUID accountId, int quantity, LocalDate purchaseDate, List<BigDecimal> rates,
                        BigDecimal earlyFee, boolean affectsBalance) {
    }

    /**
     * @param value at today's accrued interest, as the official tables give it
     * @param ifRedeemedToday after the early redemption fee, which doesn't apply from maturity
     * @param rateAssumed a year has no rate yet, so the last known one stands in
     */
    public record Valued(UUID id, Account account, String series, String kind, int quantity, LocalDate purchaseDate,
                         LocalDate maturity, List<BigDecimal> rates, BigDecimal earlyFee, BigDecimal cost, BigDecimal value,
                         BigDecimal ifRedeemedToday, boolean matured, boolean rateAssumed) {

        public BigDecimal interest() {
            return value.subtract(cost);
        }
    }

    public record Point(LocalDate date, BigDecimal value, BigDecimal cost) {
    }

    private static LocalDate today() {
        return LocalDate.now(PriceHistoryService.MARKET_ZONE);
    }

    @Transactional(readOnly = true)
    public List<Valued> list(UUID userId) {
        authService.currentUser(userId);
        Map<UUID, Account> byId = accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        LocalDate today = today();
        return lots.findByUserIdOrderByPurchaseDateAscIdAsc(userId).stream()
                .map(lot -> valued(lot, byId.get(lot.getAccountId()), today)).toList();
    }

    private static Valued valued(BondLot lot, Account account, LocalDate today) {
        BondKind kind = BondKind.ofSeries(lot.getSeries()).orElseThrow();
        LocalDate maturity = lot.getPurchaseDate().plusYears(kind.termYears);
        boolean matured = !today.isBefore(maturity);
        BigDecimal quantity = BigDecimal.valueOf(lot.getQuantity());
        BigDecimal perBond = BondValue.perBond(lot.getPurchaseDate(), kind.termYears, lot.getRates(), today);
        BigDecimal redeemed = matured ? perBond : BondValue.afterEarlyFee(perBond, lot.getEarlyFee());
        int yearsHeld = (int) Math.min(java.time.temporal.ChronoUnit.YEARS.between(lot.getPurchaseDate(), today), kind.termYears - 1L);
        return new Valued(lot.getId(), account, lot.getSeries(), kind.name(), lot.getQuantity(), lot.getPurchaseDate(), maturity,
                lot.getRates(), lot.getEarlyFee(), BondValue.NOMINAL.multiply(quantity), perBond.multiply(quantity),
                redeemed.multiply(quantity), matured, !kind.fixedRate && lot.getRates().size() <= yearsHeld);
    }

    /** What's held per account today, in PLN. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> valueByAccount(UUID userId) {
        Map<UUID, BigDecimal> values = new HashMap<>();
        list(userId).forEach(bond -> values.merge(bond.account().getId(), bond.value(), BigDecimal::add));
        return values;
    }

    /** One bond purchase's value on every day it's been held, in PLN. */
    public List<Point> points(Valued bond, LocalDate today) {
        BigDecimal quantity = BigDecimal.valueOf(bond.quantity());
        int term = BondKind.valueOf(bond.kind()).termYears;
        List<Point> points = new ArrayList<>();
        for (LocalDate day = bond.purchaseDate(); !day.isAfter(today); day = day.plusDays(1)) {
            points.add(new Point(day, BondValue.perBond(bond.purchaseDate(), term, bond.rates(), day).multiply(quantity), bond.cost()));
        }
        return points;
    }

    /** Refuses what {@link #create} would, so a proposal fails early instead of at approval. */
    public void check(UUID userId, Input input) {
        if (input.series() == null || !input.series().matches("[A-Z]{3}\\d{4}")) {
            throw new FieldValidationException(Map.of("series", "must be a series like TOS0828"));
        }
        BondKind kind = BondKind.ofSeries(input.series()).orElseThrow(() ->
                new FieldValidationException(Map.of("series", "only TOS, ROS, EDO and ROD bonds are supported for now")));
        if (input.purchaseDate() == null) {
            throw new FieldValidationException(Map.of("purchaseDate", "must not be null"));
        }
        YearMonth maturity = YearMonth.from(input.purchaseDate().plusYears(kind.termYears));
        if (!input.series().endsWith(maturity.format(SERIES_MONTH))) {
            throw new FieldValidationException(Map.of("series", "a %s bought on %s matures in %s, so it would be %s%s".formatted(
                    kind, input.purchaseDate(), maturity, kind, maturity.format(SERIES_MONTH))));
        }
        if (input.quantity() <= 0) {
            throw new FieldValidationException(Map.of("quantity", "must be at least 1"));
        }
        if (input.rates() == null || input.rates().isEmpty() || input.rates().size() > kind.termYears
                || input.rates().stream().anyMatch(rate -> rate == null || rate.signum() <= 0 || rate.compareTo(new BigDecimal("100")) > 0)) {
            throw new FieldValidationException(Map.of("rates", "must have 1 to %d rates in percent, each above 0".formatted(kind.termYears)));
        }
        if (input.earlyFee() != null && input.earlyFee().signum() < 0) {
            throw new FieldValidationException(Map.of("earlyFee", "must not be negative"));
        }
        if (input.earlyFee() == null && kind != BondKind.TOS) {
            throw new FieldValidationException(Map.of("earlyFee", "is needed for a %s, from its issue letter".formatted(kind)));
        }
        Account account = accounts.findByIdAndUserId(input.accountId(), userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "account_not_found", "Account not found"));
        if (!CURRENCY.equals(account.getCurrency())) {
            throw new FieldValidationException(Map.of("accountId", "must be a PLN account, since these bonds are in PLN"));
        }
    }

    private static BigDecimal fee(Input input) {
        if (input.earlyFee() != null) {
            return input.earlyFee();
        }
        return input.purchaseDate().isBefore(FEE_CHANGE) ? FEE_BEFORE_SEPTEMBER_2024 : FEE_FROM_SEPTEMBER_2024;
    }

    /** Takes the cost from the account's cash if the purchase affects it, and asks first if that goes below zero. */
    @Transactional
    public Valued create(UUID userId, Input input, Actor actor) {
        authService.currentUser(userId);
        check(userId, input);
        BondLot lot = new BondLot(userId, input, fee(input));
        Account account = accounts.findForUpdate(input.accountId(), userId).orElseThrow();
        Valued bond = valued(lot, account, today());
        if (input.affectsBalance()) {
            if (account.getBalance().compareTo(bond.cost()) < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "negative_balance", "%s has %s %s, and these bonds cost %s".formatted(
                        account.getName(), account.getBalance().toPlainString(), CURRENCY, bond.cost().toPlainString()));
            }
            account.adjustBalance(bond.cost().negate());
        }
        lots.save(lot);
        log(userId, actor, bond, "create", "Added", null, snapshot(lot));
        return bond;
    }

    /** New rates are set yearly for bonds that aren't fixed. */
    @Transactional
    void setRates(UUID userId, UUID id, List<BigDecimal> rates) {
        authService.currentUser(userId);
        BondLot lot = find(userId, id);
        BondKind kind = BondKind.ofSeries(lot.getSeries()).orElseThrow();
        if (rates == null || rates.isEmpty() || rates.size() > kind.termYears
                || rates.stream().anyMatch(rate -> rate == null || rate.signum() <= 0 || rate.compareTo(new BigDecimal("100")) > 0)) {
            throw new FieldValidationException(Map.of("rates", "must have 1 to %d rates in percent, each above 0".formatted(kind.termYears)));
        }
        Map<String, Object> before = snapshot(lot);
        lot.setRates(rates);
        log(userId, Actor.USER, valued(lot, account(userId, lot), today()), "update", "Changed", before, snapshot(lot));
    }

    /** Puts the cost back if the purchase affected the balance. For a mistake or a bond that's been redeemed. */
    @Transactional
    public void delete(UUID userId, UUID id, Actor actor) {
        authService.currentUser(userId);
        BondLot lot = find(userId, id);
        Account account = accounts.findForUpdate(lot.getAccountId(), userId).orElseThrow();
        Valued bond = valued(lot, account, today());
        if (lot.isAffectsBalance()) {
            account.adjustBalance(bond.cost());
        }
        log(userId, actor, bond, "delete", "Removed", snapshot(lot), null);
        lots.delete(lot);
    }

    private Account account(UUID userId, BondLot lot) {
        return accounts.findByIdAndUserId(lot.getAccountId(), userId).orElseThrow();
    }

    private BondLot find(UUID userId, UUID id) {
        return lots.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "bond_not_found", "Bond not found"));
    }

    private void log(UUID userId, Actor actor, Valued bond, String action, String verb, Map<String, Object> before,
                     Map<String, Object> after) {
        auditLog.record(userId, actor, new AuditLog.Change("bond", bond.id(), action,
                "%s %d %s bonds".formatted(verb, bond.quantity(), bond.series()), before, after));
    }

    private static Map<String, Object> snapshot(BondLot lot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("accountId", lot.getAccountId());
        values.put("series", lot.getSeries());
        values.put("quantity", lot.getQuantity());
        values.put("purchaseDate", lot.getPurchaseDate());
        values.put("rates", lot.getRates().stream().map(BigDecimal::stripTrailingZeros).toList());
        values.put("earlyFee", lot.getEarlyFee().stripTrailingZeros());
        values.put("affectsBalance", lot.isAffectsBalance());
        return values;
    }

    public record Exported(UUID accountId, String series, int quantity, LocalDate purchaseDate, List<BigDecimal> rates,
                           BigDecimal earlyFee, boolean affectsBalance) {
    }

    @Transactional(readOnly = true)
    public List<Exported> exportAll(UUID userId) {
        return lots.findByUserIdOrderByPurchaseDateAscIdAsc(userId).stream()
                .map(lot -> new Exported(lot.getAccountId(), lot.getSeries(), lot.getQuantity(), lot.getPurchaseDate(), lot.getRates(),
                        lot.getEarlyFee(), lot.isAffectsBalance()))
                .toList();
    }

    /** Leaves the balance alone, because the export has that as it stood. */
    @Transactional
    public void restore(UUID userId, Exported exported) {
        Input input = new Input(exported.series(), exported.accountId(), exported.quantity(), exported.purchaseDate(), exported.rates(),
                exported.earlyFee(), exported.affectsBalance());
        check(userId, input);
        lots.save(new BondLot(userId, input, fee(input)));
    }

}
