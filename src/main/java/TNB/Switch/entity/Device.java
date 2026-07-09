package TNB.Switch.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(length = 36)
    private UUID id;

    // Colonne technique auto-incrémentée par PostgreSQL (BIGSERIAL), utile pour un tri chronologique
    // strict d'enregistrement. Ce n'est PAS la clé primaire (qui reste l'UUID métier ci-dessus) :
    // @GeneratedValue ne peut légalement s'appliquer qu'à l'attribut @Id selon la spec JPA, donc on
    // laisse la base générer la valeur (colonne non "insertable") et Hibernate la relit après INSERT.
    @org.hibernate.annotations.Generated(org.hibernate.annotations.GenerationTime.INSERT)
    @Column(name = "registration_sequence", insertable = false, updatable = false, columnDefinition = "BIGSERIAL")
    private Long registrationSequence;

    @Column(name = "device_model", length = 50)
    private String deviceModel;

    @Column(name = "secret_token", nullable = false, length = 100)
    private String secretToken;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    // --- EMPLACEMENT SIM 1 : MTN ---
    @Column(name = "mtn_sim_number", length = 20)
    private String mtnSimNumber;

    @Column(name = "mtn_momo_balance", precision = 12, scale = 2) // Nullable par défaut si pas de SIM MTN
    private BigDecimal mtnMomoBalance;

    @Column(name = "mtn_airtime_balance", precision = 12, scale = 2)
    private BigDecimal mtnAirtimeBalance;


    // --- EMPLACEMENT SIM 2 : ORANGE ---
    @Column(name = "orange_sim_number", length = 20)
    private String orangeSimNumber;

    @Column(name = "orange_om_balance", precision = 12, scale = 2) // Nullable par défaut si pas de SIM Orange
    private BigDecimal orangeOmBalance;

    @Column(name = "orange_airtime_balance", precision = 12, scale = 2)
    private BigDecimal orangeAirtimeBalance;


    // --- EMPLACEMENT SIM 3 : CAMTEL ---
    @Column(name = "camtel_sim_number", length = 20)
    private String camtelSimNumber;

    @Column(name = "camtel_airtime_balance", precision = 12, scale = 2) // Nullable par défaut si pas de SIM Camtel
    private BigDecimal camtelAirtimeBalance;


    public Device() {
    }

    // --- GETTERS ET SETTERS ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getRegistrationSequence() { return registrationSequence; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getSecretToken() { return secretToken; }
    public void setSecretToken(String secretToken) { this.secretToken = secretToken; }

    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    // Getters/Setters MTN
    public String getMtnSimNumber() { return mtnSimNumber; }
    public void setMtnSimNumber(String mtnSimNumber) { this.mtnSimNumber = mtnSimNumber; }
    public BigDecimal getMtnMomoBalance() { return mtnMomoBalance; }
    public void setMtnMomoBalance(BigDecimal mtnMomoBalance) { this.mtnMomoBalance = mtnMomoBalance; }
    public BigDecimal getMtnAirtimeBalance() { return mtnAirtimeBalance; }
    public void setMtnAirtimeBalance(BigDecimal mtnAirtimeBalance) { this.mtnAirtimeBalance = mtnAirtimeBalance; }

    // Getters/Setters Orange
    public String getOrangeSimNumber() { return orangeSimNumber; }
    public void setOrangeSimNumber(String orangeSimNumber) { this.orangeSimNumber = orangeSimNumber; }
    public BigDecimal getOrangeOmBalance() { return orangeOmBalance; }
    public void setOrangeOmBalance(BigDecimal orangeOmBalance) { this.orangeOmBalance = orangeOmBalance; }
    public BigDecimal getOrangeAirtimeBalance() { return orangeAirtimeBalance; }
    public void setOrangeAirtimeBalance(BigDecimal orangeAirtimeBalance) { this.orangeAirtimeBalance = orangeAirtimeBalance; }

    // Getters/Setters Camtel
    public String getCamtelSimNumber() { return camtelSimNumber; }
    public void setCamtelSimNumber(String camtelSimNumber) { this.camtelSimNumber = camtelSimNumber; }
    public BigDecimal getCamtelAirtimeBalance() { return camtelAirtimeBalance; }
    public void setCamtelAirtimeBalance(BigDecimal camtelAirtimeBalance) { this.camtelAirtimeBalance = camtelAirtimeBalance; }
}