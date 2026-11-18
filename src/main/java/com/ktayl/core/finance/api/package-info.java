/**
 * The finance module's PUBLISHED surface — the only package other modules may depend on. A Spring Modulith
 * {@link org.springframework.modulith.NamedInterface named interface}: cross-module callers use
 * {@link com.ktayl.core.finance.api.FinanceCloseOperations} + its DTOs
 * ({@link com.ktayl.core.finance.api.PeriodReconciliation},
 * {@link com.ktayl.core.finance.api.PremiumRegister}), never the internal domain/application/infrastructure
 * types. Money is minor units (eurocents) throughout.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ktayl.core.finance.api;
