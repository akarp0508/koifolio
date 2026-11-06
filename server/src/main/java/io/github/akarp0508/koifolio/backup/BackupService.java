package io.github.akarp0508.koifolio.backup;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.FieldValidationException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.bond.BondService;
import io.github.akarp0508.koifolio.auth.User;
import io.github.akarp0508.koifolio.backup.BackupFormat.AccountEntry;
import io.github.akarp0508.koifolio.backup.BackupFormat.Backup;
import io.github.akarp0508.koifolio.backup.BackupFormat.BondEntry;
import io.github.akarp0508.koifolio.backup.BackupFormat.CompanyEntry;
import io.github.akarp0508.koifolio.backup.BackupFormat.InvalidBackupException;
import io.github.akarp0508.koifolio.backup.BackupFormat.Manifest;
import io.github.akarp0508.koifolio.backup.BackupFormat.PlanEntry;
import io.github.akarp0508.koifolio.backup.BackupFormat.RuleEntry;
import io.github.akarp0508.koifolio.backup.BackupFormat.TransactionEntry;
import io.github.akarp0508.koifolio.domain.CostMethod;
import io.github.akarp0508.koifolio.instrument.InstrumentDetails;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import io.github.akarp0508.koifolio.position.PositionService;
import io.github.akarp0508.koifolio.price.FxService;
import io.github.akarp0508.koifolio.rule.RuleService;
import io.github.akarp0508.koifolio.transaction.TransactionService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final AuthService authService;
    private final AccountRepository accounts;
    private final TransactionService transactions;
    private final InstrumentService instruments;
    private final PositionService positions;
    private final RuleService rules;
    private final BondService bonds;
    private final FxService fx;
    private final JdbcClient jdbc;
    private final EntityManager entityManager;

    BackupService(AuthService authService, AccountRepository accounts, TransactionService transactions,
                  InstrumentService instruments, PositionService positions, RuleService rules, BondService bonds, FxService fx,
                  JdbcClient jdbc, EntityManager entityManager) {
        this.authService = authService;
        this.accounts = accounts;
        this.transactions = transactions;
        this.instruments = instruments;
        this.positions = positions;
        this.rules = rules;
        this.bonds = bonds;
        this.fx = fx;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    record Restored(int accounts, int transactions, int companies, int plans, int rules) {
    }

    @Transactional(readOnly = true)
    byte[] export(UUID userId) {
        User user = authService.currentUser(userId);
        Map<UUID, String> keys = new LinkedHashMap<>();
        List<AccountEntry> accountEntries = new ArrayList<>();
        for (Account account : accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            String key = uniqueKey(account.getName(), keys);
            keys.put(account.getId(), key);
            accountEntries.add(new AccountEntry(key, account.getName(), account.getDescription(), account.getCurrency(),
                    plain(account.getBalance()), account.getBalanceSetOn(), account.getCostMethod(), plain(account.getTaxRate()),
                    account.getCreatedAt()));
        }

        Map<String, List<TransactionEntry>> lines = new LinkedHashMap<>();
        for (TransactionService.Exported t : transactions.exportAll(userId)) {
            lines.computeIfAbsent(keys.get(t.accountId()), key -> new ArrayList<>()).add(new TransactionEntry(t.date(), t.type(),
                    t.ticker(), plain(t.quantity()), plain(t.price()), plain(t.fee()), plain(t.amount()), plain(t.withheldTax()),
                    plain(t.splitFrom()), plain(t.splitTo()), t.parentTicker(), plain(t.costShare()), t.note(), t.affectsBalance(),
                    t.createdAt(), t.createdBy(),
                    t.unvalidated() ? Boolean.TRUE : null));
        }
        List<CompanyEntry> companies = instruments.exportAll(userId).stream().map(i -> new CompanyEntry(i.ticker(),
                i.details().name(), i.details().sector(), i.details().risk(), i.details().thesis(), i.details().tags(), i.listed(),
                i.details().analysis())).toList();
        List<PlanEntry> plans = positions.exportPlans(userId).stream().map(plan -> new PlanEntry(keys.get(plan.accountId()),
                plan.ticker(), plan.plan(), plan.risk(), plain(plan.targetPrice()), plain(plan.stopPrice()), plan.tags())).toList();
        List<RuleEntry> ruleEntries = rules.exportAll(userId).stream().map(rule -> new RuleEntry(rule.type(),
                rule.accountId() == null ? null : keys.get(rule.accountId()), plain(rule.limitPercent()), rule.maxRisk())).toList();

        List<BondEntry> bondEntries = bonds.exportAll(userId).stream().map(bond -> new BondEntry(keys.get(bond.accountId()), bond.series(),
                bond.quantity(), bond.purchaseDate(), bond.rates(), plain(bond.earlyFee()), bond.affectsBalance())).toList();

        Manifest manifest = new Manifest(BackupFormat.VERSION, Instant.now(), user.getBaseCurrency(), user.getCostFxRule());
        return BackupFormat.write(new Backup(manifest, accountEntries, lines, companies, plans, ruleEntries, bondEntries));
    }

    /** Replaces everything in one transaction, so any problem leaves the data as it was. */
    @Transactional
    Restored restore(UUID userId, Backup backup) {
        authService.currentUser(userId);
        requireKnown(backup);
        jdbc.sql("DELETE FROM accounts WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM rules WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM pending_changes WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM instruments WHERE user_id = ?").param(userId).update();
        entityManager.clear();

        try {
            Map<String, UUID> ids = new HashMap<>();
            for (AccountEntry entry : backup.accounts()) {
                Account account = Account.restored(userId, entry.name(), entry.description() == null ? "" : entry.description(),
                        entry.currency(), entry.balance(), entry.balanceSetOn() == null ? LocalDate.now() : entry.balanceSetOn(),
                        entry.costMethod() == null ? CostMethod.AVERAGE : entry.costMethod(), entry.taxRate(),
                        entry.createdAt() == null ? Instant.now() : entry.createdAt());
                ids.put(entry.key(), accounts.save(account).getId());
            }

            int restoredTransactions = 0;
            Instant start = Instant.now();
            for (Map.Entry<String, List<TransactionEntry>> file : backup.transactions().entrySet()) {
                String name = "transactions/" + file.getKey() + ".jsonl";
                List<TransactionEntry> lines = file.getValue();
                for (int i = 0; i < lines.size(); i++) {
                    TransactionEntry t = lines.get(i);
                    // Without a time, a hand-written line keeps its place in the file
                    Instant createdAt = t.createdAt() == null ? start.plusMillis(i) : t.createdAt();
                    String place = "%s line %d".formatted(name, i + 1);
                    restoreLine(place, new TransactionService.Exported(ids.get(file.getKey()), t.type(), t.date(), t.ticker(),
                            t.quantity(), t.price(), t.fee(), t.amount(), t.withheldTax(), t.splitFrom(), t.splitTo(), t.parentTicker(),
                            t.costShare(), t.note(), t.affectsBalance(), createdAt, t.createdBy(), Boolean.TRUE.equals(t.unvalidated())), userId);
                    restoredTransactions++;
                }
            }

            for (PlanEntry plan : backup.plans()) {
                positions.restorePlan(userId, new PositionService.ExportedPlan(ids.get(plan.account()), plan.ticker(), plan.plan(),
                        plan.risk(), plan.targetPrice(), plan.stopPrice(), plan.tags() == null ? Set.of() : plan.tags()));
            }
            for (CompanyEntry company : backup.companies()) {
                instruments.restore(userId, new InstrumentService.Exported(company.ticker(), new InstrumentDetails(company.name(),
                        company.sector(), company.risk(), company.thesis(), company.analysis(),
                        company.tags() == null ? Set.of() : company.tags()), company.listed()));
            }
            for (RuleEntry rule : backup.rules()) {
                rules.restore(userId, new RuleService.Input(rule.type(), rule.account() == null ? null : ids.get(rule.account()),
                        rule.limitPercent(), rule.maxRisk()));
            }
            for (BondEntry bond : backup.bonds()) {
                try {
                    bonds.restore(userId, new BondService.Exported(ids.get(bond.account()), bond.series(), bond.quantity(), bond.purchaseDate(),
                            bond.rates(), bond.earlyFee(), bond.affectsBalance()));
                } catch (FieldValidationException e) {
                    throw invalid("bonds.yaml " + bond.series() + ": " + e.getMessage());
                }
            }
            authService.restoreSettings(userId, backup.manifest().baseCurrency(), backup.manifest().costFxRule());
            entityManager.flush();
            return new Restored(backup.accounts().size(), restoredTransactions, backup.companies().size(),
                    backup.plans().size(), backup.rules().size());
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            log.warn("Restore refused by the database", e);
            throw invalid("A value is out of range or a name repeats, so nothing was changed");
        }
    }

    private void restoreLine(String place, TransactionService.Exported line, UUID userId) {
        if (line.type() == null || line.date() == null) {
            throw invalid(place + ": type and date are needed");
        }
        try {
            transactions.restore(userId, line);
        } catch (FieldValidationException e) {
            throw invalid(place + ": " + e.getMessage());
        }
    }

    /** Catches broken links before anything is deleted. */
    private void requireKnown(Backup backup) {
        Manifest manifest = backup.manifest();
        if (manifest.baseCurrency() == null || manifest.costFxRule() == null) {
            throw invalid("koifolio.yaml: baseCurrency and costFxRule are needed");
        }
        if (!fx.rates().currencies().contains(manifest.baseCurrency())) {
            throw invalid("koifolio.yaml: no rate for " + manifest.baseCurrency());
        }
        Set<String> keys = new HashSet<>();
        for (AccountEntry account : backup.accounts()) {
            if (account.key() == null || account.name() == null || account.currency() == null || account.balance() == null) {
                throw invalid("accounts.yaml: each account needs key, name, currency and balance");
            }
            if (!keys.add(account.key())) {
                throw invalid("accounts.yaml: the key " + account.key() + " repeats");
            }
        }
        backup.transactions().keySet().stream().filter(key -> !keys.contains(key)).findFirst().ifPresent(key -> {
            throw invalid("transactions/" + key + ".jsonl: no account has this key");
        });
        backup.plans().stream().filter(plan -> !keys.contains(plan.account())).findFirst().ifPresent(plan -> {
            throw invalid("plans.yaml: no account has the key " + plan.account());
        });
        backup.bonds().stream().filter(bond -> !keys.contains(bond.account())).findFirst().ifPresent(bond -> {
            throw invalid("bonds.yaml: no account has the key " + bond.account());
        });
        backup.rules().stream().filter(rule -> rule.account() != null && !keys.contains(rule.account())).findFirst()
                .ifPresent(rule -> {
                    throw invalid("rules.yaml: no account has the key " + rule.account());
                });
    }

    static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_import", message);
    }

    /** A readable file name, kept apart from accounts with the same name. */
    private static String uniqueKey(String name, Map<UUID, String> taken) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String base = slug.isEmpty() ? "account" : slug;
        String key = base;
        for (int i = 2; taken.containsValue(key); i++) {
            key = base + "-" + i;
        }
        return key;
    }

    private static BigDecimal plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
