package edu.campus.green;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campus_locations")
public class CampusLocation {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private boolean demo = false;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CampusLocation() {}
    public CampusLocation(String name, String description, boolean demo) {
        this.name = name;
        this.description = description;
        this.demo = demo;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public boolean isDemo() { return demo; }
    public void update(String name, String description) { this.name = name; this.description = description; }
    public void deactivate() { this.active = false; }
}
