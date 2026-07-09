package TNB.Switch.repository;

import TNB.Switch.entity.DeviceSmsLog;
import TNB.Switch.entity.DeviceSmsLog.SmsProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceSmsLogRepository extends JpaRepository<DeviceSmsLog, UUID> {

    /**
     * Anti-rejeu : détecte si un SMS strictement identique (même expéditeur, même contenu brut)
     * a déjà été traité avec succès, tous devices confondus. Empêche qu'un même SMS renvoyé deux
     * fois (bug réseau, rejeu volontaire) ne déclenche une double réconciliation de solde.
     */
    boolean existsBySenderIdAndSmsRawAndProcessingStatus(String senderId, String smsRaw, SmsProcessingStatus processingStatus);
}