package io.github.akarp0508.koifolio.rule;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.FieldValidationException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.audit.Actor;
import io.github.akarp0508.koifolio.audit.AuditLog;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.RuleCheck;
import io.github.akarp0508.koifolio.domain.RuleType;
import io.github.akarp0508.koifolio.position.PositionService;
import io.github.akarp0508.koifolio.position.PositionService.Position;
import io.github.akarp0508.koifolio.price.FxService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleService {

    private final RuleRepository rules;
    private final AccountRepository accounts;
    private final AuthService authService;
    private final PositionService positions;
    private final FxService fx;
    private final AuditLog auditLog;

    RuleService(RuleRepository rules, AccountRepository accounts, AuthService authService, PositionService positions,
                FxService fx, AuditLog auditLog) {
        this.rules = rules;
        this.accounts = accounts;
        this.authService = authService;
        this.positions = positions;
        this.fx = fx;
        this.auditLog = auditLog;
    }

    /** @param unpriced open positions left out of the check, lacking a price or an FX rate */
    record Checked(Rule rule, RuleCheck check, int unpriced) {
    }

    public record Input(RuleType type, UUID accountId, BigDecimal limitPercent, Integer maxRisk) {
    }

    @Transactional(readOnly = true)
    public List<Input> exportAll(UUID userId) {
        return list(userId).stream()
                .map(rule -> new Input(rule.getType(), rule.getAccountId(), rule.getLimitPercent(), rule.getMaxRisk()))
                .toList();
    }

    @Transactional
    public void restore(UUID userId, Input input) {
        save(userId, new Rule(userId), input);
    }

    @Transactional(readOnly = true)
    List<Rule> list(UUID userId) {
        authService.currentUser(userId);
        return rules.findByUserIdOrderByCreatedAtAscIdAsc(userId);
    }

    /** The rule's values, for checking what would replace them. */
    @Transactional(readOnly = true)
    public Input get(UUID userId, UUID id) {
        return input(find(userId, id));
    }

    /** Empty once the rule is gone, which a change waiting for review has to cope with. */
    @Transactional(readOnly = true)
    public Optional<Input> lookUp(UUID userId, UUID id) {
        return rules.findByIdAndUserId(id, userId).map(RuleService::input);
    }

    @Transactional
    public Rule create(UUID userId, Input input, Actor actor) {
        authService.currentUser(userId);
        Rule rule = save(userId, new Rule(userId), input);
        log(userId, actor, rule, "create", "Added", null, input);
        return rule;
    }

    @Transactional
    public Rule update(UUID userId, UUID id, Input input, Actor actor) {
        authService.currentUser(userId);
        Rule rule = find(userId, id);
        Input before = input(rule);
        save(userId, rule, input);
        log(userId, actor, rule, "update", "Changed", before, input);
        return rule;
    }

    @Transactional
    public void delete(UUID userId, UUID id, Actor actor) {
        authService.currentUser(userId);
        Rule rule = find(userId, id);
        log(userId, actor, rule, "delete", "Removed", input(rule), null);
        rules.delete(rule);
    }

    private void log(UUID userId, Actor actor, Rule rule, String action, String verb, Input before, Input after) {
        String summary = "%s rule %s".formatted(verb, describe(after == null ? before : after));
        auditLog.record(userId, actor, new AuditLog.Change("rule", rule.getId(), action, summary, before, after));
    }

    public static String describe(Input input) {
        return "%s %s%%".formatted(input.type().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                input.limitPercent().stripTrailingZeros().toPlainString());
    }

    private static Input input(Rule rule) {
        return new Input(rule.getType(), rule.getAccountId(), rule.getLimitPercent(), rule.getMaxRisk());
    }

    private Rule save(UUID userId, Rule rule, Input input) {
        check(userId, input);
        rule.update(input.accountId(), input.type(), input.limitPercent(), input.maxRisk());
        return rules.save(rule);
    }

    public void check(UUID userId, Input input) {
        boolean lowRisk = input.type() == RuleType.MIN_LOW_RISK_WEIGHT;
        if (lowRisk && input.maxRisk() == null) {
            throw new FieldValidationException(Map.of("maxRisk", "must not be null for this type"));
        }
        if (!lowRisk && input.maxRisk() != null) {
            throw new FieldValidationException(Map.of("maxRisk", "only applies to low-risk rules"));
        }
        if (input.accountId() != null) {
            accounts.findByIdAndUserId(input.accountId(), userId).orElseThrow(RuleService::accountNotFound);
        }
    }

    /** One account uses its own currency, so it needs no rates. Everything together uses the base currency. */
    @Transactional(readOnly = true)
    List<Checked> check(UUID userId) {
        String base = authService.currentUser(userId).getBaseCurrency();
        FxService.Rates rates = fx.rates();
        List<Account> owned = accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        List<Position> open = positions.list(userId).stream().filter(position -> !position.result().isClosed()).toList();

        List<Checked> results = new ArrayList<>();
        for (Rule rule : list(userId)) {
            UUID accountId = rule.getAccountId();
            List<RuleCheck.Holding> holdings = new ArrayList<>();
            int unpriced = 0;
            for (Position position : open) {
                if (accountId != null && !position.account().getId().equals(accountId)) {
                    continue;
                }
                Optional<BigDecimal> value = accountId != null ? position.value()
                        : position.value().flatMap(position::convertToBase);
                if (value.isPresent()) {
                    holdings.add(new RuleCheck.Holding(position.ticker(), value.get(), Optional.ofNullable(position.risk())));
                } else {
                    unpriced++;
                }
            }

            BigDecimal cash = BigDecimal.ZERO;
            for (Account account : owned) {
                if (accountId == null) {
                    cash = cash.add(rates.convert(account.getBalance(), account.getCurrency(), base).orElse(BigDecimal.ZERO),
                            MathContext.DECIMAL128);
                } else if (account.getId().equals(accountId)) {
                    cash = account.getBalance();
                }
            }
            RuleCheck check = rule.getType() == RuleType.MAX_POSITION_WEIGHT
                    ? RuleCheck.maxPositionWeight(rule.getLimitPercent(), holdings, cash)
                    : RuleCheck.minLowRiskWeight(rule.getLimitPercent(), rule.getMaxRisk(), holdings, cash);
            results.add(new Checked(rule, check, unpriced));
        }
        return results;
    }

    private Rule find(UUID userId, UUID id) {
        return rules.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "rule_not_found", "Rule not found"));
    }

    private static ApiException accountNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "account_not_found", "Account not found");
    }
}
