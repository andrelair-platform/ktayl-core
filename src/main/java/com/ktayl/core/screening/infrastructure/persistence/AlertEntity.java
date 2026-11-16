package com.ktayl.core.screening.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA persistence shape for an alert (one per hit) — the {@code screening.alert} table. */
@Entity
@Table(name = "alert", schema = "screening")
public class AlertEntity {

    @Id
    private UUID id;

    @Column(name = "screening_id", nullable = false)
    private UUID screeningId;

    @Column(nullable = false)
    private String bucket;

    @Column(nullable = false)
    private int score;

    @Column(name = "matched_entry")
    private String matchedEntry;

    protected AlertEntity() {} // JPA

    public AlertEntity(UUID id, UUID screeningId, String bucket, int score, String matchedEntry) {
        this.id = id;
        this.screeningId = screeningId;
        this.bucket = bucket;
        this.score = score;
        this.matchedEntry = matchedEntry;
    }

    public UUID getId() { return id; }
    public UUID getScreeningId() { return screeningId; }
    public String getBucket() { return bucket; }
    public int getScore() { return score; }
    public String getMatchedEntry() { return matchedEntry; }
}
