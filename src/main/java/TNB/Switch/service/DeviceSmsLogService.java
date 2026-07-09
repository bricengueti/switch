package TNB.Switch.service;

import TNB.Switch.entity.DeviceSmsLog;
import TNB.Switch.entity.DeviceSmsLog.SmsProcessingStatus;
import TNB.Switch.repository.DeviceSmsLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL DU SERVICE : COMPTABILITÉ DES TRACES SMS (TRAÇABILITÉ / AUDIT SAV)
 * =====================================================================================
 * Ce service fait office de boîte noire pour le Switch technologique.
 * 1. Il persiste de façon immuable l'historique complet des SMS émis par le réseau GSM.
 * 2. Il trace l'état de traitement de chaque log pour offrir un diagnostic transparent
 * sur le Dashboard d'administration (Rejeté, Traité, En cours).
 * =====================================================================================
 */
@Service
public class DeviceSmsLogService {

    private final DeviceSmsLogRepository smsLogRepository;

    public DeviceSmsLogService(DeviceSmsLogRepository smsLogRepository) {
        this.smsLogRepository = smsLogRepository;
    }

    /**
     * WORKFLOW : logIncomingSms
     * ---------------------------------------------------------------------------------
     * [TransactionService] ──(Payload brut reçu du smartphone)──> [Ici]
     * │
     * ├── 1. Instancie un nouvel objet DeviceSmsLog (Statut initial par défaut : 'PENDING').
     * ├── 2. Persiste immédiatement la ligne en BDD pour parer à toute coupure électrique/serveur.
     * └── 3. Renvoie l'entité créée dotée de son ID généré pour la suite de la réconciliation.
     */
    @Transactional
    public DeviceSmsLog logIncomingSms(UUID deviceId, String senderId, String smsRaw, LocalDateTime deviceReceivedAt) {
        DeviceSmsLog log = new DeviceSmsLog(deviceId, senderId, smsRaw, deviceReceivedAt);
        return smsLogRepository.save(log);
    }

    /**
     * WORKFLOW : isDuplicate (Anti-rejeu)
     * ---------------------------------------------------------------------------------
     * ├── 1. Recherche un log déjà marqué PROCESSED avec le même expéditeur et le même
     * │      contenu brut (donc déjà réconcilié avec une transaction).
     * └── 2. Si trouvé, le SMS courant est un doublon/rejeu : il ne doit pas être réconcilié
     *        une seconde fois (risque de double crédit/débit).
     */
    public boolean isDuplicate(String senderId, String smsRaw) {
        return smsLogRepository.existsBySenderIdAndSmsRawAndProcessingStatus(
                senderId, smsRaw, SmsProcessingStatus.PROCESSED);
    }

    /**
     * WORKFLOW : updateStatus
     * ---------------------------------------------------------------------------------
     * [TransactionService] ──(Statut de fin de traitement + Raison éventuelle)──> [Ici]
     * │
     * ├── 1. Extrait l'historique d'audit (Log SMS) depuis la BDD via son identifiant unique.
     * ├── 2. Enregistre le nouvel état de cycle de vie (PROCESSED ou REJECTED).
     * ├── 3. En cas de succès : Attache formellement l'UUID de la transaction réconciliée.
     * ├── 4. En cas d'échec : Injecte la trace textuelle de l'erreur pour la vue d'audit.
     * └── 5. Commit et sauvegarde le nouvel état en Base de données.
     */
    @Transactional
    public void updateStatus(UUID logId, SmsProcessingStatus status, UUID associatedTxnId, String rejectionReason) {
        smsLogRepository.findById(logId).ifPresent(log -> {
            log.setProcessingStatus(status);
            log.setAssociatedTransactionId(associatedTxnId);
            log.setRejectionReason(rejectionReason);
            smsLogRepository.save(log);
        });
    }
}