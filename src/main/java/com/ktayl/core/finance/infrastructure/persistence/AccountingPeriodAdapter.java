package com.ktayl.core.finance.infrastructure.persistence;

import com.ktayl.core.finance.domain.AccountingPeriodPort;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter for the accounting-period registry (FINCLOSE-01). A period with no row is implicitly
 * {@code OPEN} (a period is open until explicitly closed). Status is stored lowercase ({@code open}/
 * {@code closed}) and exposed uppercase to the domain.
 */
@Component
class AccountingPeriodAdapter implements AccountingPeriodPort {

    private final AccountingPeriodJpaRepository repository;

    AccountingPeriodAdapter(AccountingPeriodJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public String statusOf(YearMonth period) {
        return repository.findById(period.toString())
                .map(e -> e.getStatus().toUpperCase(Locale.ROOT))
                .orElse("OPEN");
    }
}
