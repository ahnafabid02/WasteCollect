package com.wastecollect.pickup;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "waste_categories")
public class WasteCategory {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false, unique = true) private String name;
    @Column(name = "allowed_unit", nullable = false) private String allowedUnit;
    @Column(nullable = false) private boolean active;
    protected WasteCategory() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getAllowedUnit() { return allowedUnit; }
}
