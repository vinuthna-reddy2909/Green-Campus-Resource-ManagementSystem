package edu.campus.green;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusService {
    private final WasteRecordRepository records;
    private final LocationRepository locations;
    private final UserRepository users;
    private final CollectionHistoryRepository history;
    private final AlertRepository alerts;
    private final AuditLogRepository audit;
    private final EWasteItemRepository ewasteItems;
    private final PasswordEncoder passwordEncoder;
    private final int overdueDays;
    private final int staleScheduleDays;
    private final int eWasteHandoverOverdueDays;

    public CampusService(WasteRecordRepository records, LocationRepository locations, UserRepository users,
                         CollectionHistoryRepository history, AlertRepository alerts, AuditLogRepository audit,
                         EWasteItemRepository ewasteItems, PasswordEncoder passwordEncoder,
                         @Value("${campus.alerts.collection-overdue-days:7}") int overdueDays,
                         @Value("${campus.alerts.collection-status-stale-days:2}") int staleScheduleDays,
                         @Value("${campus.alerts.ewaste-handover-overdue-days:14}") int eWasteHandoverOverdueDays) {
        this.records = records; this.locations = locations; this.users = users; this.history = history;
        this.alerts = alerts; this.audit = audit; this.ewasteItems = ewasteItems;
        this.passwordEncoder = passwordEncoder; this.overdueDays = overdueDays; this.staleScheduleDays = staleScheduleDays;
        this.eWasteHandoverOverdueDays = eWasteHandoverOverdueDays;
    }

    AppUser currentUser(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow(() -> new EntityNotFoundException("User not found."));
    }
    private WasteRecord record(String id) {
        return records.findById(id).filter(WasteRecord::isActive).orElseThrow(() -> new EntityNotFoundException("Waste record not found."));
    }
    @Transactional(readOnly = true)
    WasteRecord getRecord(String id) { return record(id); }
    private CampusLocation activeLocation(String id) {
        return locations.findById(id).filter(CampusLocation::isActive).orElseThrow(() -> new IllegalArgumentException("Choose an active campus location."));
    }
    private void log(AppUser user, String action, String type, String id, String details) {
        audit.save(new AuditLog(user, action, type, id, details));
    }

    @Transactional(readOnly = true)
    Page<WasteRecord> search(String query, String category, String status, String locationId, Pageable pageable) {
        Specification<WasteRecord> spec = (root, q, cb) -> cb.isTrue(root.get("active"));
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("subtype")), pattern),
                cb.like(cb.lower(root.get("notes")), pattern), cb.like(cb.lower(root.get("id")), pattern)));
        }
        if (category != null && !category.isBlank()) {
            var value = WasteRecord.Category.valueOf(category.toUpperCase(Locale.ROOT));
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category"), value));
        }
        if (status != null && !status.isBlank()) {
            var value = WasteRecord.Collection.valueOf(status.toUpperCase(Locale.ROOT));
            spec = spec.and((root, q, cb) -> cb.equal(root.get("collectionStatus"), value));
        }
        if (locationId != null && !locationId.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("location").get("id"), locationId));
        }
        return records.findAll(spec, pageable);
    }

    @Transactional
    WasteRecord create(RecordInput input, Authentication authentication) {
        if (input.category() == WasteRecord.Category.E_WASTE)
            throw new IllegalArgumentException("Use the e-waste inventory endpoint so item condition and storage location are recorded.");
        return createRecord(input, authentication);
    }

    private WasteRecord createRecord(RecordInput input, Authentication authentication) {
        validateUnits(input.category(), input.unit());
        var user = currentUser(authentication);
        var value = new WasteRecord(input.category(), input.subtype().trim(), input.quantity(), input.unit(),
            activeLocation(input.locationId()), user, input.recordedAt(),
            Objects.requireNonNullElse(input.segregationStatus(), WasteRecord.Segregation.NOT_VERIFIED),
            input.notes(), input.demo());
        records.save(value);
        log(user, "CREATE", "WASTE_RECORD", value.getId(), "Created " + value.getCategory() + " record.");
        return value;
    }

    @Transactional
    WasteRecord update(String id, RecordInput input, Authentication authentication) {
        var value = record(id);
        validateUnits(input.category(), input.unit());
        if (value.getCategory() != input.category()) throw new IllegalArgumentException("A record's waste category cannot be changed after creation.");
        value.update(input.subtype().trim(), input.quantity(), input.unit(), activeLocation(input.locationId()),
            input.recordedAt(), Objects.requireNonNullElse(input.segregationStatus(), WasteRecord.Segregation.NOT_VERIFIED), input.notes());
        log(currentUser(authentication), "UPDATE", "WASTE_RECORD", id, "Updated waste record.");
        return value;
    }

    private void validateUnits(WasteRecord.Category category, WasteRecord.Unit unit) {
        if (category == WasteRecord.Category.PLASTIC && unit != WasteRecord.Unit.KG)
            throw new IllegalArgumentException("Plastic waste quantities must be recorded in kg.");
        if (category == WasteRecord.Category.E_WASTE && unit != WasteRecord.Unit.ITEM)
            throw new IllegalArgumentException("E-waste inventory counts must be recorded as items. Record weights separately.");
    }

    @Transactional
    void deactivate(String id, Authentication authentication) {
        var value = record(id);
        value.deactivate();
        log(currentUser(authentication), "DEACTIVATE", "WASTE_RECORD", id, "Deactivated waste record.");
    }

    @Transactional
    CollectionHistory addCollection(String id, ActionInput input, Authentication authentication) {
        var value = record(id);
        if (value.getCategory() == WasteRecord.Category.E_WASTE) validateItemCount(input.quantity());
        value.addCollection(input.quantity());
        var user = currentUser(authentication);
        var event = history.save(new CollectionHistory(value, CollectionHistory.Action.COLLECTION, input.quantity(), user, null, input.notes()));
        log(user, "COLLECTION", "WASTE_RECORD", id, "Recorded collection quantity " + input.quantity() + " " + value.getUnit() + ".");
        return event;
    }

    @Transactional
    CollectionHistory addHandover(String id, ActionInput input, Authentication authentication) {
        if (input.recipient() == null || input.recipient().isBlank()) throw new IllegalArgumentException("An authorized recipient must be recorded for handover.");
        var value = record(id);
        if (value.getCategory() == WasteRecord.Category.E_WASTE) validateItemCount(input.quantity());
        value.addHandover(input.quantity());
        var user = currentUser(authentication);
        var event = history.save(new CollectionHistory(value, CollectionHistory.Action.HANDOVER, input.quantity(), user, input.recipient().trim(), input.notes()));
        if (value.getCategory() == WasteRecord.Category.E_WASTE && value.getAwaitingHandoverQuantity().signum() == 0) {
            ewasteItems.findByRecordId(id).ifPresent(item -> item.recordHandover(LocalDate.now(), input.recipient().trim()));
        }
        log(user, "HANDOVER", "WASTE_RECORD", id, "Recorded authorized handover.");
        return event;
    }

    @Transactional
    void schedule(String id, Authentication authentication) {
        var value = record(id);
        if (value.getRemainingQuantity().signum() <= 0) throw new IllegalArgumentException("There is no remaining quantity to schedule.");
        value.schedule();
        log(currentUser(authentication), "SCHEDULE_COLLECTION", "WASTE_RECORD", id, "Collection scheduled.");
    }

    @Transactional(readOnly = true)
    List<CollectionHistory> history(String id) { record(id); return history.findByRecordIdOrderByOccurredAtDesc(id); }

    @Transactional
    CampusLocation createLocation(LocationInput input, Authentication authentication) {
        var location = locations.save(new CampusLocation(input.name().trim(), input.description(), input.demo()));
        log(currentUser(authentication), "CREATE", "CAMPUS_LOCATION", location.getId(), "Created campus location.");
        return location;
    }
    @Transactional
    CampusLocation updateLocation(String id, LocationInput input, Authentication authentication) {
        var value = locations.findById(id).orElseThrow(() -> new EntityNotFoundException("Location not found."));
        value.update(input.name().trim(), input.description());
        log(currentUser(authentication), "UPDATE", "CAMPUS_LOCATION", id, "Updated campus location.");
        return value;
    }
    @Transactional
    void deactivateLocation(String id, Authentication authentication) {
        var value = locations.findById(id).orElseThrow(() -> new EntityNotFoundException("Location not found."));
        value.deactivate();
        log(currentUser(authentication), "DEACTIVATE", "CAMPUS_LOCATION", id, "Deactivated campus location.");
    }

    @Transactional
    AppUser createUser(UserInput input, Authentication authentication) {
        String username = input.username().trim();
        if (users.findByUsername(username).isPresent()) throw new IllegalArgumentException("That username is already in use.");
        var user = users.save(new AppUser(username, passwordEncoder.encode(input.password()), input.displayName().trim(), input.role()));
        log(currentUser(authentication), "CREATE", "USER", user.getId(), "Created account with role " + user.getRole() + ".");
        return user;
    }

    @Transactional
    AppUser updateUser(String id, UserUpdateInput input, Authentication authentication) {
        var actor = currentUser(authentication);
        var user = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found."));
        if (user.getId().equals(actor.getId()) && (!input.active() || input.role() != Role.ADMIN))
            throw new IllegalArgumentException("You cannot deactivate your own administrator account or remove your own administrator role.");
        if (user.getRole() == Role.ADMIN && (input.role() != Role.ADMIN || !input.active()) && countActiveAdmins() <= 1)
            throw new IllegalArgumentException("At least one active administrator account must remain.");
        user.update(input.displayName().trim(), input.role(), input.active());
        if (input.password() != null && !input.password().isBlank()) user.changePassword(passwordEncoder.encode(input.password()));
        log(actor, "UPDATE", "USER", user.getId(), "Updated account role or status.");
        return user;
    }

    private long countActiveAdmins() {
        return users.findAll().stream().filter(AppUser::isActive).filter(user -> user.getRole() == Role.ADMIN).count();
    }

    @Transactional
    WasteRecord createEWaste(EWasteInput input, Authentication authentication) {
        validateItemCount(input.quantity());
        var user = currentUser(authentication);
        var recordInput = new RecordInput(WasteRecord.Category.E_WASTE, input.itemType(), input.quantity(),
            WasteRecord.Unit.ITEM, input.locationId(), input.reportedAt(), input.segregationStatus(),
            input.notes(), input.demo());
        var record = createRecord(recordInput, authentication);
        ewasteItems.save(new EWasteItem(record, input.condition(), input.storageLocation().trim()));
        log(user, "CREATE", "E_WASTE_ITEM", record.getId(), "Created e-waste inventory item.");
        return record;
    }

    @Transactional
    EWasteItem updateEWaste(String recordId, EWasteUpdateInput input, Authentication authentication) {
        validateItemCount(input.quantity());
        var record = records.findById(recordId).filter(WasteRecord::isActive)
            .orElseThrow(() -> new EntityNotFoundException("E-waste inventory item not found."));
        if (record.getCategory() != WasteRecord.Category.E_WASTE)
            throw new IllegalArgumentException("The selected waste record is not an e-waste item.");
        record.update(input.itemType().trim(), input.quantity(), WasteRecord.Unit.ITEM,
            activeLocation(input.locationId()), input.reportedAt(), input.segregationStatus(), input.notes());
        var item = ewasteItems.findByRecordId(recordId).orElseGet(() -> new EWasteItem(record, input.condition(), input.storageLocation().trim()));
        item.update(input.condition(), input.storageLocation().trim());
        ewasteItems.save(item);
        log(currentUser(authentication), "UPDATE", "E_WASTE_ITEM", recordId, "Updated e-waste inventory details.");
        return item;
    }

    private void validateItemCount(BigDecimal quantity) {
        if (quantity.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("E-waste quantity must be a whole number of items.");
    }

    @Transactional(readOnly = true)
    List<EWasteItem> eWasteInventory() { return ewasteItems.findAllByRecordActiveTrueOrderByRecordRecordedAtDesc(); }

    @Transactional
    void deactivateEWaste(String recordId, Authentication authentication) {
        var item = ewasteItems.findByRecordId(recordId).orElseThrow(() -> new EntityNotFoundException("E-waste inventory item not found."));
        item.getRecord().deactivate();
        log(currentUser(authentication), "DEACTIVATE", "E_WASTE_ITEM", recordId, "Deactivated e-waste inventory item.");
    }

    @Transactional
    void resolveAlert(String id, Authentication authentication) {
        var alert = alerts.findById(id).orElseThrow(() -> new EntityNotFoundException("Alert not found."));
        if (alert.isResolved()) return;
        var user = currentUser(authentication);
        alert.resolve(user);
        log(user, "RESOLVE", "ALERT", id, "Resolved alert.");
    }

    @Transactional
    List<Alert> currentAlerts() {
        var open = alerts.findByResolvedFalseOrderByCreatedAtDesc();
        var existing = alerts.findAll();
        LocalDate today = LocalDate.now();
        for (var value : records.findByActiveTrueAndCollectionStatus(WasteRecord.Collection.PENDING)) {
            if (ChronoUnit.DAYS.between(value.getRecordedAt(), today) >= overdueDays)
                createAlertIfMissing(open, existing, value, Alert.Type.COLLECTION_OVERDUE,
                    "Collection overdue: awaiting collection for at least " + overdueDays + " days.");
        }
        for (var value : records.findByActiveTrueAndCollectionStatus(WasteRecord.Collection.SCHEDULED)) {
            LocalDate lastUpdated = value.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (ChronoUnit.DAYS.between(lastUpdated, today) >= staleScheduleDays)
                createAlertIfMissing(open, existing, value, Alert.Type.COLLECTION_STATUS_STALE,
                    "Collection is scheduled but its status has not been updated for " + staleScheduleDays + " days.");
        }
        for (var value : records.findByActiveTrueAndSegregationStatus(WasteRecord.Segregation.NEEDS_CORRECTION)) {
            createAlertIfMissing(open, existing, value, Alert.Type.SEGREGATION_CORRECTION, "Segregation issue requires correction.");
        }
        var inventory = ewasteItems.findAllByRecordActiveTrueOrderByRecordRecordedAtDesc();
        for (var item : inventory) {
            var value = item.getRecord();
            if (value.getAwaitingHandoverQuantity().signum() > 0
                && ChronoUnit.DAYS.between(value.getRecordedAt(), today) >= eWasteHandoverOverdueDays)
                createAlertIfMissing(open, existing, value, Alert.Type.E_WASTE_HANDOVER_OVERDUE,
                    "E-waste is awaiting authorized handover for at least " + eWasteHandoverOverdueDays + " days.");
        }
        Set<String> inventoryIds = inventory.stream().map(item -> item.getRecord().getId()).collect(java.util.stream.Collectors.toSet());
        for (var value : records.findByActiveTrueOrderByRecordedAtDesc()) {
            if (value.getCategory() == WasteRecord.Category.E_WASTE && !inventoryIds.contains(value.getId()))
                createAlertIfMissing(open, existing, value, Alert.Type.MISSING_REQUIRED_INFO,
                    "E-waste inventory is missing condition and storage details.");
        }
        return alerts.findAllByOrderByCreatedAtDesc();
    }

    private void createAlertIfMissing(List<Alert> open, List<Alert> existing, WasteRecord record, Alert.Type type, String message) {
        boolean exists = existing.stream().anyMatch(alert -> alert.getRecord() != null
            && alert.getRecord().getId().equals(record.getId()) && alert.getType() == type);
        if (!exists) {
            var alert = alerts.save(new Alert(record, type, message));
            existing.add(alert);
            open.add(alert);
        }
    }

    @Transactional(readOnly = true)
    Map<String, Object> dashboard(LocalDate from, LocalDate to, String category, String locationId, String status, String segregationStatus) {
        var all = reportRecords(from, to, category, locationId, status, segregationStatus, null);
        Map<String, BigDecimal> totals = new TreeMap<>();
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        Map<String, Long> byStatus = new TreeMap<>();
        Map<String, BigDecimal> byLocation = new TreeMap<>();
        Map<String, BigDecimal> byMonth = new TreeMap<>();
        for (var value : all) {
            String unitKey = value.getCategory() + " (" + value.getUnit() + ")";
            totals.merge(unitKey, value.getQuantity(), BigDecimal::add);
            String categoryUnit = value.getCategory() + " (" + value.getUnit() + ")";
            byCategory.merge(categoryUnit, value.getQuantity(), BigDecimal::add);
            byLocation.merge(value.getLocation().getName() + " (" + value.getUnit() + ")", value.getQuantity(), BigDecimal::add);
            byMonth.merge(value.getRecordedAt().toString().substring(0, 7) + " · " + value.getUnit(), value.getQuantity(), BigDecimal::add);
            byStatus.merge(value.getCollectionStatus().name(), 1L, Long::sum);
        }
        long awaiting = all.stream().filter(value -> value.getCollectionStatus() == WasteRecord.Collection.PENDING).count();
        long needsCorrection = all.stream().filter(value -> value.getSegregationStatus() == WasteRecord.Segregation.NEEDS_CORRECTION).count();
        long demoCount = all.stream().filter(WasteRecord::isDemo).count();
        return Map.of("recordCount", all.size(), "totalsByCategoryAndUnit", totals,
            "totalsByCategory", byCategory, "recordsByStatus", byStatus, "totalsByLocation", byLocation,
            "totalsByMonth", byMonth, "awaitingCollection", awaiting,
            "segregationNeedsCorrection", needsCorrection, "demoRecordCount", demoCount);
    }

    @Transactional(readOnly = true)
    List<WasteRecord> reportRecords(LocalDate from, LocalDate to, String category, String locationId,
                                    String status, String segregationStatus, String itemType) {
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("Report start date must be on or before the end date.");
        Specification<WasteRecord> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("recordedAt"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("recordedAt"), to));
        if (category != null && !category.isBlank()) {
            var value = WasteRecord.Category.valueOf(category.toUpperCase(Locale.ROOT));
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), value));
        }
        if (locationId != null && !locationId.isBlank())
            spec = spec.and((root, query, cb) -> cb.equal(root.get("location").get("id"), locationId));
        if (status != null && !status.isBlank()) {
            var value = WasteRecord.Collection.valueOf(status.toUpperCase(Locale.ROOT));
            spec = spec.and((root, query, cb) -> cb.equal(root.get("collectionStatus"), value));
        }
        if (segregationStatus != null && !segregationStatus.isBlank()) {
            var value = WasteRecord.Segregation.valueOf(segregationStatus.toUpperCase(Locale.ROOT));
            spec = spec.and((root, query, cb) -> cb.equal(root.get("segregationStatus"), value));
        }
        if (itemType != null && !itemType.isBlank())
            spec = spec.and((root, query, cb) -> cb.and(
                cb.equal(root.get("category"), WasteRecord.Category.E_WASTE),
                cb.equal(cb.lower(root.get("subtype")), itemType.trim().toLowerCase(Locale.ROOT))));
        return records.findAll(spec, Sort.by(Sort.Direction.DESC, "recordedAt"));
    }

    @Transactional(readOnly = true)
    Page<AuditLog> auditLogs(Pageable pageable) { return audit.findAll(pageable); }

    record UserInput(String username, String password, String displayName, Role role) {}
    record UserUpdateInput(String password, String displayName, Role role, boolean active) {}
    record EWasteInput(String itemType, BigDecimal quantity, String locationId, LocalDate reportedAt,
        EWasteItem.Condition condition, String storageLocation, WasteRecord.Segregation segregationStatus,
        String notes, boolean demo) {}
    record EWasteUpdateInput(String itemType, BigDecimal quantity, String locationId, LocalDate reportedAt,
        EWasteItem.Condition condition, String storageLocation, WasteRecord.Segregation segregationStatus, String notes) {}

    record RecordInput(WasteRecord.Category category, String subtype, BigDecimal quantity,
        WasteRecord.Unit unit, String locationId, LocalDate recordedAt, WasteRecord.Segregation segregationStatus,
        String notes, boolean demo) {}
    record ActionInput(BigDecimal quantity, String recipient, String notes) {}
    record LocationInput(String name, String description, boolean demo) {}
}
