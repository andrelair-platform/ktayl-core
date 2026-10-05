/**
 * Billing module — premium invoicing, installment schedules, Stripe (test, SEPA DD) payments, and
 * double-entry posting to the ERPNext GL. The first business module of ktayl-core.
 *
 * <p>A CLOSED Spring Modulith module: other modules may use only its published API / events, never its
 * internals; it owns the {@code billing} Postgres schema and may not read another module's tables.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Billing")
package com.ktayl.core.billing;
