package br.com.tiagotds.transfereasy.infrastructure.config;

import br.com.tiagotds.transfereasy.domain.model.AmountPolicy;
import br.com.tiagotds.transfereasy.domain.model.Money;
import java.math.BigDecimal;

public record MoneySettings(Money maxAmount) {

    static MoneySettings from(Config config) {
        Money max = config.get("money.max-amount", "a positive amount with at most 2 decimals", raw -> {
            var value = new BigDecimal(raw);
            return value.signum() > 0 ? Money.of(value) : null;
        });
        return new MoneySettings(max);
    }

    public AmountPolicy amountPolicy() {
        return new AmountPolicy(maxAmount);
    }
}
