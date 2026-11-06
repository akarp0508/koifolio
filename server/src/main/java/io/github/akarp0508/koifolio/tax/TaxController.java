package io.github.akarp0508.koifolio.tax;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.TaxEstimate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tax")
@Tag(name = "Tax")
class TaxController {

    private final TaxService tax;

    TaxController(TaxService tax) {
        this.tax = tax;
    }

    @GetMapping
    @Operation(summary = "An estimate of tax on sales for one year", description = """
            In the base currency. Accounts with the same rate add up, so losses offset gains between them. \
            Losses aren't carried between years, and dividends aren't included. Not tax advice.""")
    TaxResponse estimate(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer year) {
        return TaxResponse.of(tax.estimate(UUID.fromString(jwt.getSubject()), year));
    }

    /**
     * @param totalTax tax on this year's sales
     * @param totalIfSoldToday extra tax if everything open were sold now; left out for past years
     * @param unsetAccounts accounts with something to tax but no rate, so not in the estimate
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TaxResponse(String currency, int year, List<Integer> years, String totalTax, String totalIfSoldToday,
                       List<TaxGroup> groups, List<String> unsetAccounts, List<OpenGain> openPositions) {

        static TaxResponse of(TaxService.Estimate estimate) {
            String currency = estimate.currency();
            BigDecimal tax = estimate.groups().stream().map(TaxEstimate.Group::tax).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ifSold = estimate.groups().stream().map(TaxEstimate.Group::ifSoldToday).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new TaxResponse(currency, estimate.year(), estimate.years(), money(tax, currency),
                    estimate.currentYear() ? money(ifSold, currency) : null,
                    estimate.groups().stream().map(group -> TaxGroup.of(group, currency)).toList(),
                    estimate.unsetAccounts().stream().map(account -> account.getName()).toList(),
                    estimate.open().stream().map(open -> new OpenGain(open.account().getId(), open.account().getName(),
                            open.ticker(), money(open.gain(), currency))).toList());
        }
    }

    record TaxGroup(String ratePercent, String realized, String tax, String unrealized, String ifSoldToday,
                    List<AccountLine> accounts) {

        static TaxGroup of(TaxEstimate.Group<io.github.akarp0508.koifolio.account.Account> group, String currency) {
            return new TaxGroup(group.ratePercent().toPlainString(), money(group.realized(), currency),
                    money(group.tax(), currency), money(group.unrealized(), currency),
                    money(group.ifSoldToday(), currency),
                    group.accounts().stream().map(line -> new AccountLine(line.account().getId(), line.account().getName(),
                            money(line.realized(), currency))).toList());
        }
    }

    record AccountLine(UUID accountId, String name, String realized) {
    }

    record OpenGain(UUID accountId, String accountName, String ticker, String gain) {
    }

    private static String money(BigDecimal value, String currency) {
        return value.setScale(Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.HALF_UP).toPlainString();
    }
}
