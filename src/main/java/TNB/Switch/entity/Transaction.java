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

    // Colonne technique auto-incrémentée par PostgreSQL (BIGSERIAL) : sert de base au tri FIFO
    // strict utilisé par TransactionRepository.findOldestAvailableJob. Ce n'est PAS la clé primaire
    // (qui reste l'UUID métier ci-dessus) : @GeneratedValue ne peut légalement s'appliquer qu'à
    // l'attribut @Id selon la spec JPA, donc on laisse la base générer la valeur et Hibernate la
    // relit après INSERT.
    @org.hibernate.annotations.Generated(org.hibernate.annotations.GenerationTime.INSERT)
    @Column(name = "sequence_number", insertable = false, updatable = false, columnDefinition = "BIGSERIAL")
    private Long sequenceNumber;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "sender_phone", nullable = false, length = 20)
    private String senderPhone;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Operator operator; // Correction rigoureuse : Utilisation de l'enum Operator au lieu de String

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

    // --- CORRECTION OPTIMISATION : Stockage des IDs des logs au lieu du texte brut ---
    @Column(name = "collect_sms_log_id", length = 36)
    private UUID collectSmsLogId;

    @Column(name = "transfer_sms_log_id", length = 36)
    private UUID transferSmsLogId;
    // ---------------------------------------------------------------------------------

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructeurs
    public Transaction() {
    }

    public Transaction(String idempotencyKey, String senderPhone, String recipientPhone,
                       Operator operator, BigDecimal amount, TransactionStatus status, ServiceType serviceType) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.senderPhone = senderPhone;
        this.recipientPhone = recipientPhone;
        this.operator = operator;
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

    // Getters et Setters Corrigés
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getSequenceNumber() { return sequenceNumber; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public Operator getOperator() { return operator; }
    public void setOperator(Operator operator) { this.operator = operator; }

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