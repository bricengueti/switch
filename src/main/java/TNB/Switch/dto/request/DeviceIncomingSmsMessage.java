package TNB.Switch.dto.request;

import java.time.LocalDateTime;

/**
 * DTO envoyé par l'application Android en temps réel au serveur 
 * pour chaque SMS officiel capté sur le terrain.
 */
public record DeviceIncomingSmsMessage(
        String action,                  // Valeur fixe : "UNSOLICITED_SMS_RECEIVED"
        String senderId,                // L'expéditeur officiel (ex: "OrangeMoney", "MTNMomo")
        String smsRaw,                  // Le contenu textuel intégral et brut du SMS
        LocalDateTime deviceReceivedAt  // Date et heure précise où la puce a reçu le SMS
) {
    public DeviceIncomingSmsMessage(String senderId, String smsRaw, LocalDateTime deviceReceivedAt) {
        this("UNSOLICITED_SMS_RECEIVED", senderId, smsRaw, deviceReceivedAt);
    }
}