package TNB.Switch.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(length = 36)
    private UUID id;

    @org.hibernate.annotations.Generated(org.hibernate.annotations.GenerationTime.INSERT)
    @Column(name = "sequence_number", insertable = false, updatable = false, columnDefinition = "BIGSERIAL")
    private Long sequenceNumber;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "sender_phone", nullable = false, length = 20)
    private String senderPhone;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    // Remplace l'ancien champ unique "operator" : la collecte (côté expéditeur) et le dépôt
    // (côté bénéficiaire) peuvent concerner deux opérateurs différents, donc deux colonnes
    // explicites au lieu de faire deviner l'opérateur de destination au moment du transfert.
    @Enumerated(EnumType.STRING)
    @Column(name = "operator_source", nullable = false, length = 20)
    private Operator operatorSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator_destination", nullable = false, length = 20)
    private Operator operatorDestination;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", length = 20)
    private ServiceType serviceType;

    @Column(name = "assigned_device_id", length = 36)
    private UUID assignedDeviceId;

    @Column(name = "collect_sms_log_id", length = 36)
    private UUID collectSmsLogId;

    @Column(name = "transfer_sms_log_id", length = 36)
    private UUID transferSmsLogId;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Transaction() {
    }

    public Transaction(String idempotencyKey, String senderPhone, String recipientPhone,
                       Operator operatorSource, Operator operatorDestination, BigDecimal amount,
                       TransactionStatus status, ServiceType serviceType) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.senderPhone = senderPhone;
        this.recipientPhone = recipientPhone;
        this.operatorSource = operatorSource;
        this.operatorDestination = operatorDestination;
        this.amount = amount;
        this.status = status;
        this.serviceType = serviceType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getSequenceNumber() { return sequenceNumber; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public Operator getOperatorSource() { return operatorSource; }
    public void setOperatorSource(Operator operatorSource) { this.operatorSource = operatorSource; }

    public Operator getOperatorDestination() { return operatorDestination; }
    public void setOperatorDestination(Operator operatorDestination) { this.operatorDestination = operatorDestination; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public UUID getAssignedDeviceId() { return assignedDeviceId; }
    public void setAssignedDeviceId(UUID assignedDeviceId) { this.assignedDeviceId = assignedDeviceId; }

    public UUID getCollectSmsLogId() { return collectSmsLogId; }
    public void setCollectSmsLogId(UUID collectSmsLogId) { this.collectSmsLogId = collectSmsLogId; }

    public UUID getTransferSmsLogId() { return transferSmsLogId; }
    public void setTransferSmsLogId(UUID transferSmsLogId) { this.transferSmsLogId = transferSmsLogId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}