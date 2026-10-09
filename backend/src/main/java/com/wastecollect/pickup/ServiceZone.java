package com.wastecollect.pickup;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "service_zones")
public class ServiceZone {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false, unique = true) private String name;
    @Column(nullable = false) private boolean active;
    protected ServiceZone() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
