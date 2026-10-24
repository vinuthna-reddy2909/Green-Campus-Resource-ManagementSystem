package edu.campus.green;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts", indexes = @Index(name = "idx_alert_open", columnList = "resolved,created_at"))
public class Alert {
    public enum Type {
        COLLECTION_OVERDUE, COLLECTION_STATUS_STALE, SEGREGATION_CORRECTION,
        E_WASTE_HANDOVER_OVERDUE, MISSING_REQUIRED_INFO
    }
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "record_id")
    private WasteRecord record;
    @Column(nullable = false, length = 500)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private Type type = Type.COLLECTION_OVERDUE;
    @Column(nullable = false)
    private boolean resolved;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_by")
    private AppUser resolvedBy;
    protected Alert() {}
    public Alert(WasteRecord record, Type type, String message) { this.record = record; this.type = type; this.message = message; }
    public String getId() { return id; }
    public WasteRecord getRecord() { return record; }
    public String getMessage() { return message; }
    public Type getType() { return type; }
    public boolean isResolved() { return resolved; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void resolve(AppUser user) { resolved = true; resolvedAt = Instant.now(); resolvedBy = user; }
}
