package edu.campus.green;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_time", columnList = "occurred_at"))
public class AuditLog {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "user_id")
    private AppUser user;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entity_id", length = 36)
    private String entityId;
    @Column(length = 1000)
    private String details;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
    protected AuditLog() {}
    public AuditLog(AppUser user, String action, String entityType, String entityId, String details) {
        this.user = user; this.action = action; this.entityType = entityType; this.entityId = entityId; this.details = details;
    }
    public String getId() { return id; }
    public AppUser getUser() { return user; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
