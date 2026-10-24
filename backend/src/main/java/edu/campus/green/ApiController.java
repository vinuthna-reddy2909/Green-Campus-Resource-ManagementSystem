package edu.campus.green;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {
    private final CampusService service;
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final LocationRepository locations;

    public ApiController(CampusService service, AuthenticationManager authenticationManager, UserRepository users,
                         LocationRepository locations) {
        this.service = service; this.authenticationManager = authenticationManager; this.users = users;
        this.locations = locations;
    }

    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/auth/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        var authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(input.username(), input.password()));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return ResponseEntity.ok(userPayload(authentication));
    }

    @GetMapping("/auth/me")
    Map<String, Object> me(Authentication authentication) { return userPayload(authentication); }

    @GetMapping("/dashboard")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF','VIEWER')")
    Map<String, Object> dashboard(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String category, @RequestParam(required = false) String locationId,
        @RequestParam(required = false) String status, @RequestParam(required = false) String segregationStatus) {
        return service.dashboard(from, to, category, locationId, status, segregationStatus);
    }

    @GetMapping("/records")
    Page<RecordView> listRecords(@RequestParam(required = false) String q,
        @RequestParam(required = false) String category, @RequestParam(required = false) String status,
        @RequestParam(required = false) String locationId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "recordedAt,desc") String sort) {
        String[] sortParts = sort.split(",", 2);
        String sortField = Set.of("recordedAt", "category", "subtype", "quantity", "collectionStatus").contains(sortParts[0])
            ? sortParts[0] : "recordedAt";
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return service.search(q, category, status, locationId, PageRequest.of(page, size, Sort.by(direction, sortField))).map(RecordView::from);
    }

    @PostMapping("/records")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    ResponseEntity<RecordView> createRecord(@Valid @RequestBody RecordRequest input, Authentication auth) {
        var created = service.create(input.toInput(), auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecordView.from(created));
    }

    @GetMapping("/records/{id}")
    RecordView getRecord(@PathVariable String id) { return RecordView.from(service.getRecord(id)); }

    @GetMapping("/ewaste")
    List<EWasteView> eWasteInventory() { return service.eWasteInventory().stream().map(EWasteView::from).toList(); }

    @PostMapping("/ewaste")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    ResponseEntity<EWasteView> createEWaste(@Valid @RequestBody EWasteRequest input, Authentication auth) {
        var created = service.createEWaste(input.toInput(), auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(EWasteView.from(service.eWasteInventory().stream()
            .filter(item -> item.getRecord().getId().equals(created.getId())).findFirst().orElseThrow()));
    }

    @PutMapping("/ewaste/{recordId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    EWasteView updateEWaste(@PathVariable String recordId, @Valid @RequestBody EWasteUpdateRequest input, Authentication auth) {
        return EWasteView.from(service.updateEWaste(recordId, input.toInput(), auth));
    }

    @DeleteMapping("/ewaste/{recordId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> deactivateEWaste(@PathVariable String recordId, Authentication auth) {
        service.deactivateEWaste(recordId, auth); return ResponseEntity.noContent().build();
    }

    @PutMapping("/records/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    RecordView updateRecord(@PathVariable String id, @Valid @RequestBody RecordRequest input, Authentication auth) {
        return RecordView.from(service.update(id, input.toInput(), auth));
    }

    @DeleteMapping("/records/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> deactivateRecord(@PathVariable String id, Authentication auth) {
        service.deactivate(id, auth); return ResponseEntity.noContent().build();
    }

    @PostMapping("/records/{id}/schedule")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    ResponseEntity<Void> schedule(@PathVariable String id, Authentication auth) {
        service.schedule(id, auth); return ResponseEntity.noContent().build();
    }

    @GetMapping("/records/{id}/history")
    List<HistoryView> history(@PathVariable String id) {
        return service.history(id).stream().map(HistoryView::from).toList();
    }

    @PostMapping("/records/{id}/collections")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    ResponseEntity<HistoryView> collect(@PathVariable String id, @Valid @RequestBody ActionRequest input, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(HistoryView.from(service.addCollection(id, input.toInput(), auth)));
    }

    @PostMapping("/records/{id}/handovers")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    ResponseEntity<HistoryView> handover(@PathVariable String id, @Valid @RequestBody ActionRequest input, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(HistoryView.from(service.addHandover(id, input.toInput(), auth)));
    }

    @GetMapping("/locations")
    List<LocationView> locations() { return this.locations.findAllByOrderByNameAsc().stream().map(LocationView::from).toList(); }

    @PostMapping("/locations")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<LocationView> createLocation(@Valid @RequestBody LocationRequest input, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(LocationView.from(service.createLocation(input.toInput(), auth)));
    }

    @PutMapping("/locations/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    LocationView updateLocation(@PathVariable String id, @Valid @RequestBody LocationRequest input, Authentication auth) {
        return LocationView.from(service.updateLocation(id, input.toInput(), auth));
    }

    @DeleteMapping("/locations/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> deactivateLocation(@PathVariable String id, Authentication auth) {
        service.deactivateLocation(id, auth); return ResponseEntity.noContent().build();
    }

    @GetMapping("/alerts")
    List<AlertView> alerts() { return service.currentAlerts().stream().map(AlertView::from).toList(); }

    @PostMapping("/alerts/{id}/resolve")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    ResponseEntity<Void> resolveAlert(@PathVariable String id, Authentication auth) {
        service.resolveAlert(id, auth); return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports.csv")
    ResponseEntity<String> csv(@RequestParam(required = false) String category, @RequestParam(required = false) String status,
                               @RequestParam(required = false) String locationId, @RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(required = false) String segregationStatus,
                               @RequestParam(required = false) String itemType) {
        var values = service.reportRecords(from, to, category, locationId, status, segregationStatus, itemType);
        StringBuilder csv = new StringBuilder("# report_generated_at_utc,").append(java.time.Instant.now()).append('\n')
            .append("# report_date_range,").append(from == null ? "ALL" : from).append(',').append(to == null ? "ALL" : to).append('\n')
            .append("id,category,subtype,quantity,unit,location,recorded_at,collection_status,segregation_status,demo\n");
        for (var r : values) csv.append(quote(r.getId())).append(',').append(r.getCategory()).append(',')
            .append(quote(r.getSubtype())).append(',').append(r.getQuantity()).append(',').append(r.getUnit()).append(',')
            .append(quote(r.getLocation().getName())).append(',').append(r.getRecordedAt()).append(',')
            .append(r.getCollectionStatus()).append(',').append(r.getSegregationStatus()).append(',').append(r.isDemo()).append('\n');
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"green-campus-report.csv\"")
            .contentType(new MediaType("text", "csv")).body(csv.toString());
    }

    @GetMapping("/reports/summary")
    Map<String, Object> reportSummary(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String category, @RequestParam(required = false) String locationId,
        @RequestParam(required = false) String status, @RequestParam(required = false) String segregationStatus,
        @RequestParam(required = false) String itemType,
        @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var values = service.reportRecords(from, to, category, locationId, status, segregationStatus, itemType);
        var totals = new TreeMap<String, BigDecimal>();
        var locations = new TreeMap<String, BigDecimal>();
        var months = new TreeMap<String, BigDecimal>();
        for (var value : values) {
            String unit = value.getUnit().name();
            totals.merge(value.getCategory() + " (" + unit + ")", value.getQuantity(), BigDecimal::add);
            locations.merge(value.getLocation().getName() + " (" + unit + ")", value.getQuantity(), BigDecimal::add);
            months.merge(value.getRecordedAt().toString().substring(0, 7) + " · " + unit, value.getQuantity(), BigDecimal::add);
        }
        int start = (int) Math.min((long) page * size, values.size());
        int end = Math.min(start + size, values.size());
        return Map.of("recordCount", values.size(), "page", page, "size", size,
            "totalPages", (int) Math.ceil((double) values.size() / size),
            "totalsByCategory", totals, "totalsByLocation", locations, "totalsByMonth", months,
            "records", values.subList(start, end).stream().map(RecordView::from).toList());
    }

    @GetMapping("/users")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    List<Map<String, Object>> users() {
        return users.findAll().stream().map(u -> Map.<String, Object>of("id", u.getId(), "username", u.getUsername(),
            "displayName", u.getDisplayName(), "role", u.getRole(), "active", u.isActive())).toList();
    }

    @PostMapping("/users")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<UserView> createUser(@Valid @RequestBody CreateUserRequest input, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.from(service.createUser(input.toInput(), auth)));
    }

    @PutMapping("/users/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    UserView updateUser(@PathVariable String id, @Valid @RequestBody UpdateUserRequest input, Authentication auth) {
        return UserView.from(service.updateUser(id, input.toInput(), auth));
    }

    @GetMapping("/audit-logs")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    Page<AuditView> auditLogs(@RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.auditLogs(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))).map(AuditView::from);
    }

    @GetMapping("/awareness")
    Map<String, Object> awareness() {
        return Map.of("title", "Everyday actions for a cleaner campus", "sections", List.of(
            Map.of("title", "Reduce plastic", "items", List.of("Choose reusable bottles and containers.", "Avoid unnecessary single-use packaging.", "Keep recyclable plastics clean and dry where local guidance requires it.")),
            Map.of("title", "Handle e-waste responsibly", "items", List.of("Keep batteries and electronics out of general waste.", "Store devices safely and securely erase data before handover.", "Use only an authorized recycler or disposal provider.")),
            Map.of("title", "Segregate at source", "items", List.of("Keep plastic and electronic waste in separate, labelled containers.", "Do not mix e-waste with ordinary recyclables.", "Ask campus facilities staff if a waste stream is unclear."))));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class, jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validation
            ? validation.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("Invalid request.")
            : exception instanceof HttpMessageNotReadableException ? "Request contains invalid or missing values."
            : exception instanceof jakarta.validation.ConstraintViolationException ? "Request parameters are outside the allowed range."
            : exception.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", message == null ? "Invalid request." : message));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(Exception exception) { return ResponseEntity.status(404).body(Map.of("error", exception.getMessage())); }

    private Map<String, Object> userPayload(Authentication auth) {
        var user = users.findByUsername(auth.getName()).orElseThrow();
        return Map.of("username", user.getUsername(), "displayName", user.getDisplayName(), "role", user.getRole().name());
    }
    private static String quote(String value) { return "\"" + Objects.toString(value, "").replace("\"", "\"\"") + "\""; }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    record RecordRequest(@NotNull WasteRecord.Category category, @NotBlank @Size(max = 120) String subtype,
        @NotNull @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
        @NotNull WasteRecord.Unit unit, @NotBlank String locationId, @NotNull LocalDate recordedAt,
        WasteRecord.Segregation segregationStatus, @Size(max = 1000) String notes, boolean demo) {
        CampusService.RecordInput toInput() { return new CampusService.RecordInput(category, subtype, quantity, unit, locationId, recordedAt, segregationStatus, notes, demo); }
    }
    record ActionRequest(@NotNull @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
        @Size(max = 160) String recipient, @Size(max = 1000) String notes) {
        CampusService.ActionInput toInput() { return new CampusService.ActionInput(quantity, recipient, notes); }
    }
    record LocationRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description, boolean demo) {
        CampusService.LocationInput toInput() { return new CampusService.LocationInput(name, description, demo); }
    }
    record CreateUserRequest(@NotBlank @Size(max = 80) String username, @NotBlank @Size(min = 12, max = 100) String password,
        @NotBlank @Size(max = 120) String displayName, @NotNull Role role) {
        CampusService.UserInput toInput() { return new CampusService.UserInput(username, password, displayName, role); }
    }
    record UpdateUserRequest(@Size(min = 12, max = 100) String password, @NotBlank @Size(max = 120) String displayName,
        @NotNull Role role, boolean active) {
        CampusService.UserUpdateInput toInput() { return new CampusService.UserUpdateInput(password, displayName, role, active); }
    }
    record EWasteRequest(@NotBlank @Size(max = 120) String itemType,
        @NotNull @DecimalMin(value = "1") @Digits(integer = 9, fraction = 0) BigDecimal quantity,
        @NotBlank String locationId, @NotNull LocalDate reportedAt, @NotNull EWasteItem.Condition condition,
        @NotBlank @Size(max = 160) String storageLocation, WasteRecord.Segregation segregationStatus,
        @Size(max = 1000) String notes, boolean demo) {
        CampusService.EWasteInput toInput() { return new CampusService.EWasteInput(itemType, quantity, locationId,
            reportedAt, condition, storageLocation, segregationStatus, notes, demo); }
    }
    record EWasteUpdateRequest(@NotBlank @Size(max = 120) String itemType,
        @NotNull @DecimalMin(value = "1") @Digits(integer = 9, fraction = 0) BigDecimal quantity,
        @NotBlank String locationId, @NotNull LocalDate reportedAt, @NotNull EWasteItem.Condition condition,
        @NotBlank @Size(max = 160) String storageLocation, @NotNull WasteRecord.Segregation segregationStatus,
        @Size(max = 1000) String notes) {
        CampusService.EWasteUpdateInput toInput() { return new CampusService.EWasteUpdateInput(itemType, quantity,
            locationId, reportedAt, condition, storageLocation, segregationStatus, notes); }
    }
    record LocationView(String id, String name, String description, boolean active, boolean demo) {
        static LocationView from(CampusLocation v) { return new LocationView(v.getId(), v.getName(), v.getDescription(), v.isActive(), v.isDemo()); }
    }
    record RecordView(String id, WasteRecord.Category category, String subtype, BigDecimal quantity,
        BigDecimal collectedQuantity, BigDecimal handedOverQuantity, BigDecimal remainingQuantity,
        BigDecimal awaitingHandoverQuantity, WasteRecord.Unit unit, String locationId, String location,
        LocalDate recordedAt, WasteRecord.Segregation segregationStatus, WasteRecord.Collection collectionStatus,
        String recordedBy, String notes, boolean demo, java.time.Instant createdAt, java.time.Instant updatedAt) {
        static RecordView from(WasteRecord v) {
            return new RecordView(v.getId(), v.getCategory(), v.getSubtype(), v.getQuantity(), v.getCollectedQuantity(),
                v.getHandedOverQuantity(), v.getRemainingQuantity(), v.getAwaitingHandoverQuantity(), v.getUnit(),
                v.getLocation().getId(), v.getLocation().getName(), v.getRecordedAt(), v.getSegregationStatus(),
                v.getCollectionStatus(), v.getRecordedBy().getDisplayName(), v.getNotes(), v.isDemo(), v.getCreatedAt(), v.getUpdatedAt());
        }
    }
    record HistoryView(String id, CollectionHistory.Action action, BigDecimal quantity, java.time.Instant occurredAt,
                       String performedBy, String recipient, String notes) {
        static HistoryView from(CollectionHistory v) {
            return new HistoryView(v.getId(), v.getAction(), v.getQuantity(), v.getOccurredAt(),
                v.getPerformedBy().getDisplayName(), v.getRecipient(), v.getNotes());
        }
    }
    record AlertView(String id, String recordId, Alert.Type type, String message, boolean resolved, java.time.Instant createdAt, java.time.Instant resolvedAt) {
        static AlertView from(Alert v) { return new AlertView(v.getId(), v.getRecord() == null ? null : v.getRecord().getId(),
            v.getType(), v.getMessage(), v.isResolved(), v.getCreatedAt(), v.getResolvedAt()); }
    }
    record UserView(String id, String username, String displayName, Role role, boolean active) {
        static UserView from(AppUser user) { return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isActive()); }
    }
    record EWasteView(String id, String recordId, String itemType, BigDecimal quantity, String locationId,
        String location, LocalDate reportedAt, EWasteItem.Condition condition, String storageLocation,
        WasteRecord.Segregation segregationStatus, WasteRecord.Collection collectionStatus,
        BigDecimal collectedQuantity, BigDecimal handedOverQuantity, LocalDate handoverDate,
        String authorizedRecipient, String recordedBy, String notes, boolean demo) {
        static EWasteView from(EWasteItem item) {
            var record = item.getRecord();
            return new EWasteView(item.getId(), record.getId(), item.getItemType(), record.getQuantity(),
                record.getLocation().getId(), record.getLocation().getName(), record.getRecordedAt(), item.getCondition(),
                item.getStorageLocation(), record.getSegregationStatus(), record.getCollectionStatus(),
                record.getCollectedQuantity(), record.getHandedOverQuantity(), item.getHandoverDate(),
                item.getAuthorizedRecipient(), record.getRecordedBy().getDisplayName(), record.getNotes(), record.isDemo());
        }
    }
    record AuditView(String id, String username, String displayName, String action, String entityType, String entityId,
                     String details, java.time.Instant occurredAt) {
        static AuditView from(AuditLog log) { return new AuditView(log.getId(), log.getUser().getUsername(),
            log.getUser().getDisplayName(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails(), log.getOccurredAt()); }
    }
}
