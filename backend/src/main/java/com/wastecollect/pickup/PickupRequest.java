package com.wastecollect.pickup;

import com.wastecollect.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "pickup_requests")
public class PickupRequest {
    @Id private UUID id;
    @Column(name = "public_code", nullable = false, unique = true) private String publicCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resident_id") private User resident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_zone_id") private ServiceZone zone;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "waste_category_id") private WasteCategory category;
    @Column(nullable = false) private String address;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal quantity;
    @Column(nullable = false) private String unit;
    @Column(name = "preferred_date", nullable = false) private LocalDate preferredDate;
    private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PickupStatus status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected PickupRequest() {}
    public PickupRequest(User resident, ServiceZone zone, WasteCategory category, String address, BigDecimal quantity,
                         String unit, LocalDate preferredDate, String notes) {
        this.id = UUID.randomUUID(); this.publicCode = "WC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.resident = resident; this.zone = zone; this.category = category; this.address = address;
        this.quantity = quantity; this.unit = unit; this.preferredDate = preferredDate; this.notes = notes;
        this.status = PickupStatus.PENDING; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; } public String getPublicCode() { return publicCode; }
    public User getResident() { return resident; } public ServiceZone getZone() { return zone; }
    public WasteCategory getCategory() { return category; } public String getAddress() { return address; }
    public BigDecimal getQuantity() { return quantity; } public String getUnit() { return unit; }
    public LocalDate getPreferredDate() { return preferredDate; } public String getNotes() { return notes; }
    public PickupStatus getStatus() { return status; } public Instant getCreatedAt() { return createdAt; }
    public void cancel() { if (status != PickupStatus.PENDING && status != PickupStatus.GROUPED && status != PickupStatus.SCHEDULED) throw new IllegalStateException("Request cannot be cancelled"); status = PickupStatus.CANCELLED; updatedAt = Instant.now(); }
}
