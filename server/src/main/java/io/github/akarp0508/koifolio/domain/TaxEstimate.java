package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An estimate of tax on sales, for one calendar year. Accounts with the same rate add up, so a loss
 * in one offsets a gain in another, as on a Polish PIT-38. Losses aren't carried between years.
 * Dividends are counted apart, since losses on sales don't offset them.
 */
public final class TaxEstimate {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private TaxEstimate() {
    }

    /** @param unrealized what the account's open positions would gain if sold now */
    public record AccountGain<K>(K account, BigDecimal ratePercent, BigDecimal realized, BigDecimal unrealized) {
    }

    /**
     * @param tax on this year's realized gains
     * @param ifSoldToday the extra tax if everything open were sold now, after this year's losses
     */
    public record Group<K>(BigDecimal ratePercent, BigDecimal realized, BigDecimal tax, BigDecimal unrealized,
                           BigDecimal ifSoldToday, List<AccountGain<K>> accounts) {
    }

    public record AccountDividends<K>(K account, BigDecimal ratePercent, List<Dividend> dividends) {

        public BigDecimal gross() {
            return Dividend.total(dividends, Dividend::gross);
        }

        public BigDecimal withheld() {
            return Dividend.total(dividends, Dividend::withheld);
        }

        public BigDecimal taxLeft() {
            return Dividend.total(dividends, dividend -> dividend.taxLeft(ratePercent));
        }
    }

    public record DividendGroup<K>(BigDecimal ratePercent, BigDecimal gross, BigDecimal withheld, BigDecimal taxLeft,
                                   List<AccountDividends<K>> accounts) {
    }

    public static <K> List<DividendGroup<K>> groupDividends(List<AccountDividends<K>> accounts) {
        Map<BigDecimal, List<AccountDividends<K>>> byRate = new LinkedHashMap<>();
        for (AccountDividends<K> account : accounts) {
            byRate.computeIfAbsent(account.ratePercent().stripTrailingZeros(), rate -> new ArrayList<>()).add(account);
        }

        List<DividendGroup<K>> groups = new ArrayList<>();
        byRate.forEach((rate, members) -> groups.add(new DividendGroup<>(rate,
                sum(members.stream().map(AccountDividends::gross).toList()),
                sum(members.stream().map(AccountDividends::withheld).toList()),
                sum(members.stream().map(AccountDividends::taxLeft).toList()), members)));
        groups.sort(Comparator.comparing(DividendGroup<K>::ratePercent, Comparator.reverseOrder()));
        return groups;
    }

    public static <K> List<Group<K>> group(List<AccountGain<K>> accounts) {
        Map<BigDecimal, List<AccountGain<K>>> byRate = new LinkedHashMap<>();
        for (AccountGain<K> account : accounts) {
            byRate.computeIfAbsent(account.ratePercent().stripTrailingZeros(), rate -> new ArrayList<>()).add(account);
        }

        List<Group<K>> groups = new ArrayList<>();
        byRate.forEach((rate, members) -> {
            BigDecimal realized = sum(members.stream().map(AccountGain::realized).toList());
            BigDecimal unrealized = sum(members.stream().map(AccountGain::unrealized).toList());
            BigDecimal tax = taxOn(realized, rate);
            BigDecimal withEverythingSold = taxOn(realized.add(unrealized, PositionCalculator.PRECISION), rate);
            groups.add(new Group<>(rate, realized, tax, unrealized,
                    withEverythingSold.subtract(tax, PositionCalculator.PRECISION), members));
        });
        groups.sort(Comparator.comparing(Group<K>::ratePercent, Comparator.reverseOrder()));
        return groups;
    }

    /** Realized gain per calendar year of one holding's trades, oldest first. */
    public static Map<Integer, BigDecimal> gainsByYear(List<DatedTrade> trades, CostMethod method) {
        Map<Integer, BigDecimal> gains = new HashMap<>();
        List<Trade> applied = new ArrayList<>();
        BigDecimal realizedSoFar = BigDecimal.ZERO;
        for (DatedTrade dated : trades) {
            applied.add(dated.trade());
            if (dated.trade().side() == Trade.Side.SELL) {
                BigDecimal realized = PositionCalculator.calculate(applied, method).realizedGain();
                gains.merge(dated.date().getYear(), realized.subtract(realizedSoFar, PositionCalculator.PRECISION),
                        (a, b) -> a.add(b, PositionCalculator.PRECISION));
                realizedSoFar = realized;
            }
        }
        return gains;
    }

    private static BigDecimal taxOn(BigDecimal gain, BigDecimal ratePercent) {
        if (gain.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return gain.multiply(ratePercent, PositionCalculator.PRECISION).divide(HUNDRED, PositionCalculator.PRECISION);
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, PositionCalculator.PRECISION));
    }
}
