package com.ktayl.core.finance.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Load (or replace) a loss-development triangle for a portfolio (FIN-03). A triangle is identified by
 * {@code (portfolio, asOf, basis)}; re-loading the same key replaces its cells. Cells are the cumulative
 * paid/incurred amount (eurocents) at each {@code (accidentPeriod, developmentLag)}; the actuarial staircase
 * shape is validated when an estimate is run.
 *
 * @param basis {@code PAID} or {@code INCURRED}
 */
public record TriangleLoadRequest(String portfolio, LocalDate asOf, String basis, String currency,
        List<Cell> cells) {

    public record Cell(String accidentPeriod, int developmentLag, long cumulativeMinor) {}
}
