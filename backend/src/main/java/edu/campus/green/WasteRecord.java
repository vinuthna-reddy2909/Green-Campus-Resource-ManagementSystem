package edu.campus.green;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "waste_records", indexes = {
    @Index(name = "idx_record_date", columnList = "recorded_at"),
    @Index(name = "idx_record_category_status", columnList = "category,collection_status")
})
public class WasteRecord {
    public enum Category { PLASTIC, E_WASTE }
    public enum Unit { KG, ITEM }
    public enum Segregation { NOT_VERIFIED, PROPER, NEEDS_CORRECTION }
    public enum Collection { PENDING, SCHEDULED, COLLECTED, HANDED_OVER }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Category category;
    @Column(nullable = false, length = 120)
    private String subtype;
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;
    @Column(name = "collected_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal collectedQuantity = BigDecimal.ZERO;
    @Column(name = "handed_over_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal handedOverQuantity = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Unit unit;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "location_id")
    private CampusLocation location;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "recorded_by")
    private AppUser recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;
    @Enumerated(EnumType.STRING) @Column(name = "segregation_status", nullable = false, length = 24)
    private Segregation segregationStatus = Segregation.NOT_VERIFIED;
    @Enumerated(EnumType.STRING) @Column(name = "collection_status", nullable = false, length = 24)
    private Collection collectionStatus = Collection.PENDING;
    @Column(length = 1000)
    private String notes;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private boolean demo = false;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WasteRecord() {}
    public WasteRecord(Category category, String subtype, BigDecimal quantity, Unit unit,
                       CampusLocation location, AppUser recordedBy, LocalDate recordedAt,
                       Segregation segregationStatus, String notes, boolean demo) {
        this.category = category;
        this.subtype = subtype;
        this.quantity = quantity;
        this.unit = unit;
        this.location = location;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.segregationStatus = segregationStatus;
        this.notes = notes;
        this.demo = demo;
    }
    public String getId() { return id; }
    public Category getCategory() { return category; }
    public String getSubtype() { return subtype; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getCollectedQuantity() { return collectedQuantity; }
    public BigDecimal getHandedOverQuantity() { return handedOverQuantity; }
    public Unit getUnit() { return unit; }
    public CampusLocation getLocation() { return location; }
    public AppUser getRecordedBy() { return recordedBy; }
    public LocalDate getRecordedAt() { return recordedAt; }
    public Segregation getSegregationStatus() { return segregationStatus; }
    public Collection getCollectionStatus() { return collectionStatus; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public boolean isDemo() { return demo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public BigDecimal getRemainingQuantity() { return quantity.subtract(collectedQuantity); }
    public BigDecimal getAwaitingHandoverQuantity() { return collectedQuantity.subtract(handedOverQuantity); }
    public void update(String subtype, BigDecimal quantity, Unit unit, CampusLocation location,
                       LocalDate recordedAt, Segregation segregation, String notes) {
        if (quantity.compareTo(collectedQuantity) < 0) throw new IllegalArgumentException("Quantity cannot be less than the amount already collected.");
        this.subtype = subtype; this.quantity = quantity; this.unit = unit; this.location = location;
        this.recordedAt = recordedAt; this.segregationStatus = segregation; this.notes = notes; this.updatedAt = Instant.now();
    }
    public void setSegregationStatus(Segregation value) { this.segregationStatus = value; this.updatedAt = Instant.now(); }
    public void schedule() { this.collectionStatus = Collection.SCHEDULED; this.updatedAt = Instant.now(); }
    public void addCollection(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(getRemainingQuantity()) > 0) throw new IllegalArgumentException("Collection quantity must be positive and no greater than the remaining quantity.");
        collectedQuantity = collectedQuantity.add(amount);
        collectionStatus = collectedQuantity.compareTo(quantity) == 0 ? Collection.COLLECTED : Collection.PENDING;
        updatedAt = Instant.now();
    }
    public void addHandover(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(getAwaitingHandoverQuantity()) > 0) throw new IllegalArgumentException("Handover quantity must be positive and no greater than collected waste awaiting handover.");
        handedOverQuantity = handedOverQuantity.add(amount);
        if (handedOverQuantity.compareTo(quantity) == 0) collectionStatus = Collection.HANDED_OVER;
        updatedAt = Instant.now();
    }
    public void deactivate() { active = false; updatedAt = Instant.now(); }
}
