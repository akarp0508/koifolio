package io.github.akarp0508.koifolio.rule;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.FieldValidationException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
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

    RuleService(RuleRepository rules, AccountRepository accounts, AuthService authService, PositionService positions,
                FxService fx) {
        this.rules = rules;
        this.accounts = accounts;
        this.authService = authService;
        this.positions = positions;
        this.fx = fx;
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
        create(userId, input);
    }

    @Transactional(readOnly = true)
    List<Rule> list(UUID userId) {
        authService.currentUser(userId);
        return rules.findByUserIdOrderByCreatedAtAscIdAsc(userId);
    }

    @Transactional
    Rule create(UUID userId, Input input) {
        authService.currentUser(userId);
        return save(userId, new Rule(userId), input);
    }

    @Transactional
    Rule update(UUID userId, UUID id, Input input) {
        authService.currentUser(userId);
        return save(userId, find(userId, id), input);
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        authService.currentUser(userId);
        rules.delete(find(userId, id));
    }

    private Rule save(UUID userId, Rule rule, Input input) {
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
        rule.update(input.accountId(), input.type(), input.limitPercent(), input.maxRisk());
        return rules.save(rule);
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
