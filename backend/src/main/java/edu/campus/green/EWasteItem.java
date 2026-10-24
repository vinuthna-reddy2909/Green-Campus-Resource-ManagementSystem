package edu.campus.green;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "e_waste_items", indexes = @Index(name = "idx_ewaste_type_condition", columnList = "item_type,item_condition"))
public class EWasteItem {
    public enum Condition { FUNCTIONAL, DAMAGED, UNUSABLE }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    private WasteRecord record;
    @Column(name = "item_type", nullable = false, length = 120)
    private String itemType;
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20)
    private Condition condition;
    @Column(name = "storage_location", nullable = false, length = 160)
    private String storageLocation;
    @Column(name = "handover_date")
    private LocalDate handoverDate;
    @Column(name = "authorized_recipient", length = 160)
    private String authorizedRecipient;

    protected EWasteItem() {}
    public EWasteItem(WasteRecord record, Condition condition, String storageLocation) {
        this.record = record;
        this.itemType = record.getSubtype();
        this.condition = condition;
        this.storageLocation = storageLocation;
    }
    public String getId() { return id; }
    public WasteRecord getRecord() { return record; }
    public String getItemType() { return itemType; }
    public Condition getCondition() { return condition; }
    public String getStorageLocation() { return storageLocation; }
    public LocalDate getHandoverDate() { return handoverDate; }
    public String getAuthorizedRecipient() { return authorizedRecipient; }
    public void update(Condition condition, String storageLocation) {
        this.itemType = record.getSubtype();
        this.condition = condition;
        this.storageLocation = storageLocation;
    }
    public void recordHandover(LocalDate date, String recipient) {
        this.handoverDate = date;
        this.authorizedRecipient = recipient;
    }
}
