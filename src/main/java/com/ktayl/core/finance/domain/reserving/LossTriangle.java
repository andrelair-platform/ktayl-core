package com.ktayl.core.finance.domain.reserving;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A <b>cumulative</b> loss-development triangle (FIN-03) — the input to actuarial reserving. Rows are
 * <i>accident periods</i> (when the loss occurred, e.g. {@code 2024-Q1}, ordered oldest→newest); columns are
 * <i>development periods</i> (lag since the accident period, {@code 0,1,2,…}); each cell is the
 * <b>cumulative</b> amount (paid, or incurred = paid + case reserves) for that accident period as at that
 * development lag, in <b>eurocents</b> (minor units — never a float; actuarial arithmetic here is integer/
 * {@code double} ratios, the money stays {@code long}).
 *
 * <p>A real triangle is <b>upper-left</b>: the newest accident period has only development lag 0 observed, so
 * each successive (older) row has one more observed cell. This class enforces exactly that staircase shape —
 * row {@code i} (newest = 0) must have {@code i+1} cells, cumulative (non-decreasing), and lag-aligned — so a
 * malformed triangle can never reach the chain-ladder/BF calculators ({@link TriangleShapeException}).
 *
 * <p>Pure domain: no Spring, no persistence, no IO. The persistence adapter builds one of these from the
 * stored {@code loss_triangle} cells; the calculators ({@link ChainLadder}, {@link BornhuetterFerguson})
 * consume it.
 */
public final class LossTriangle {

    /** One accident period's cumulative development row, oldest-lag first. {@code cells[d]} = cumulative at lag d. */
    public record Row(String accidentPeriod, long[] cells) {}

    private final List<Row> rows; // index 0 = NEWEST accident period (1 cell), last = OLDEST (fully developed)

    private LossTriangle(List<Row> rows) {
        this.rows = rows;
    }

    /**
     * Build + validate a triangle from cells keyed by (accidentPeriod → (developmentLag → cumulativeMinor)).
     * Accident periods are sorted by their natural (lexical, ISO) order; the newest is expected to carry the
     * fewest lags. Enforces the staircase: the i-th-newest row must observe exactly lags {@code 0..i}.
     *
     * @throws TriangleShapeException if empty, ragged (a gap in lags), not the upper-left staircase, or a row
     *                                is not non-decreasing (cumulative must never fall).
     */
    public static LossTriangle of(Map<String, ? extends Map<Integer, Long>> cells) {
        if (cells == null || cells.isEmpty()) {
            throw new TriangleShapeException("triangle is empty");
        }
        // oldest → newest by accident period
        SortedMap<String, ? extends Map<Integer, Long>> byPeriod = new TreeMap<>(cells);
        List<String> periods = new ArrayList<>(byPeriod.keySet());
        int n = periods.size();
        List<Row> built = new ArrayList<>(n);
        // newest first: index 0 is the newest period, which must have the fewest observed lags (1)
        for (int i = 0; i < n; i++) {
            String period = periods.get(n - 1 - i); // i-th newest
            Map<Integer, Long> lags = byPeriod.get(period);
            int expected = i + 1; // newest row → 1 cell, each older row one more
            if (lags.size() != expected) {
                throw new TriangleShapeException("accident period " + period + " has " + lags.size()
                        + " development cells, expected " + expected + " for the upper-left staircase");
            }
            long[] cumulative = new long[expected];
            long prev = Long.MIN_VALUE;
            for (int d = 0; d < expected; d++) {
                Long v = lags.get(d);
                if (v == null) {
                    throw new TriangleShapeException("accident period " + period + " is missing development lag " + d);
                }
                if (v < prev) {
                    throw new TriangleShapeException("accident period " + period
                            + " cumulative falls at lag " + d + " (" + prev + " → " + v + "); must be non-decreasing");
                }
                cumulative[d] = v;
                prev = v;
            }
            built.add(new Row(period, cumulative));
        }
        return new LossTriangle(built);
    }

    /** Number of accident periods (rows). */
    public int accidentPeriodCount() {
        return rows.size();
    }

    /** The maximum development lag observed (the oldest row's last lag) = rows − 1. */
    public int maxDevelopmentLag() {
        return rows.size() - 1;
    }

    /** Rows oldest accident period → newest (so the first is fully developed). */
    public List<Row> rowsOldestFirst() {
        List<Row> out = new ArrayList<>(rows);
        java.util.Collections.reverse(out); // internal list is newest-first
        return out;
    }

    /** The latest observed cumulative for an accident period (its diagonal cell) = the last cell of the row. */
    public long latestCumulativeMinor(String accidentPeriod) {
        return rows.stream().filter(r -> r.accidentPeriod().equals(accidentPeriod)).findFirst()
                .map(r -> r.cells()[r.cells().length - 1])
                .orElseThrow(() -> new IllegalArgumentException("no such accident period: " + accidentPeriod));
    }
}
