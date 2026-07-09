package TNB.Switch.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "device_sms_logs", indexes = {
        @Index(name = "idx_sms_device", columnList = "deviceId"),
        @Index(name = "idx_sms_server_created_at", columnList = "serverReceivedAt"),
        @Index(name = "idx_sms_device_received_at", columnList = "deviceReceivedAt"),
        @Index(name = "idx_sms_status", columnList = "processingStatus")
})
public class DeviceSmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID deviceId; // Le téléphone de la flotte qui a capté le SMS

    @Column(nullable = false)
    private String senderId; // L'expéditeur officiel (ex: OrangeMoney, MTNMomo)

    @Column(columnDefinition = "TEXT", nullable = false)
    private String smsRaw; // Le contenu textuel brut et complet du SMS

    @Column(nullable = false)
    private LocalDateTime deviceReceivedAt; // Date/Heure où le téléphone a REÇU le SMS (fourni par Android)

    @Column(nullable = false)
    private LocalDateTime serverReceivedAt; // Date/Heure où le serveur a REÇU le message (géré par Java)

    // Liage optionnel : Si le serveur réussit à l'associer à une transaction, on stocke l'ID ici
    @Column(nullable = true)
    private UUID associatedTransactionId;

    // Statut technique du traitement du SMS sur le serveur
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SmsProcessingStatus processingStatus;

    @Column(nullable = true)
    private String rejectionReason; // Ex: "REGEX_MISMATCH", "DUPLICATE_REFERENCE"

    // --- ENUM INTERNE POUR LE STATUT DU SMS ---
    public enum SmsProcessingStatus {
        PROCESSED,   // Le SMS a été analysé et associé avec succès à une transaction
        UNMATCHED,   // SMS officiel valide, mais aucune transaction correspondante trouvée (ex: dépôt direct sur la SIM)
        REJECTED     // Le format du texte n'a pas pu être analysé par nos Regex
    }

    // --- CONSTRUCTEURS ---
    public DeviceSmsLog() {
    }

    public DeviceSmsLog(UUID deviceId, String senderId, String smsRaw, LocalDateTime deviceReceivedAt) {
        this.deviceId = deviceId;
        this.senderId = senderId;
        this.smsRaw = smsRaw;
        this.deviceReceivedAt = deviceReceivedAt;
        this.serverReceivedAt = LocalDateTime.now(); // Enregistré à la milliseconde de l'arrivée sur le back
        this.processingStatus = SmsProcessingStatus.UNMATCHED;
    }

    // --- GETTERS ET SETTERS ---
    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSmsRaw() { return smsRaw; }
    public void setSmsRaw(String smsRaw) { this.smsRaw = smsRaw; }
    public LocalDateTime getDeviceReceivedAt() { return deviceReceivedAt; }
    public void setDeviceReceivedAt(LocalDateTime deviceReceivedAt) { this.deviceReceivedAt = deviceReceivedAt; }
    public LocalDateTime getServerReceivedAt() { return serverReceivedAt; }
    public void setServerReceivedAt(LocalDateTime serverReceivedAt) { this.serverReceivedAt = serverReceivedAt; }
    public UUID getAssociatedTransactionId() { return associatedTransactionId; }
    public void setAssociatedTransactionId(UUID associatedTransactionId) { this.associatedTransactionId = associatedTransactionId; }
    public SmsProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(SmsProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}