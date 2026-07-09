package TNB.Switch.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fleet_alerts")
public class FleetAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    private Operator operator;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    private BigDecimal remainingBalance;
    private LocalDateTime createdAt;
    private boolean resolved;
    private LocalDateTime resolvedAt;

    // Constructeurs, Getters, Setters
    public FleetAlert() {}

    public FleetAlert(UUID deviceId, Operator operator, ServiceType serviceType, BigDecimal remainingBalance) {
        this.deviceId = deviceId;
        this.operator = operator;
        this.serviceType = serviceType;
        this.remainingBalance = remainingBalance;
        this.createdAt = LocalDateTime.now();
        this.resolved = false;
    }

    // Getters / Setters...
    public Long getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public Operator getOperator() { return operator; }
    public ServiceType getServiceType() { return serviceType; }
    public BigDecimal getRemainingBalance() { return remainingBalance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}