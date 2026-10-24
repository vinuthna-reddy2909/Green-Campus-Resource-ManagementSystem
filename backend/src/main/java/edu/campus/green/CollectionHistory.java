package edu.campus.green;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_history", indexes = @Index(name = "idx_history_record_time", columnList = "record_id,occurred_at"))
public class CollectionHistory {
    public enum Action { COLLECTION, HANDOVER }
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "record_id")
    private WasteRecord record;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Action action;
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "performed_by")
    private AppUser performedBy;
    @Column(length = 160)
    private String recipient;
    @Column(length = 1000)
    private String notes;
    protected CollectionHistory() {}
    public CollectionHistory(WasteRecord record, Action action, BigDecimal quantity, AppUser performedBy, String recipient, String notes) {
        this.record = record; this.action = action; this.quantity = quantity; this.performedBy = performedBy; this.recipient = recipient; this.notes = notes;
    }
    public String getId() { return id; }
    public Action getAction() { return action; }
    public BigDecimal getQuantity() { return quantity; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getRecipient() { return recipient; }
    public String getNotes() { return notes; }
    public AppUser getPerformedBy() { return performedBy; }
}
