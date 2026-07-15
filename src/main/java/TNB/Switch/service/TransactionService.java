package TNB.Switch.service;

import TNB.Switch.dto.request.TransactionInitiationRequest;
import TNB.Switch.entity.DeviceSmsLog;
import TNB.Switch.entity.DeviceSmsLog.SmsProcessingStatus;
import TNB.Switch.entity.Operator;
import TNB.Switch.entity.Transaction;
import TNB.Switch.entity.TransactionStatus;
import TNB.Switch.entity.ServiceType;
import TNB.Switch.exception.InvalidOperatorException;
import TNB.Switch.exception.TransactionNotFoundException;
import TNB.Switch.repository.TransactionRepository;
import TNB.Switch.dto.request.DeviceIncomingSmsMessage;
import TNB.Switch.dto.request.TransferRequest;
import TNB.Switch.dto.response.ClientNotificationResponse;
import TNB.Switch.handler.ClientWebSocketHandler;
import TNB.Switch.handler.DeviceWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL DU SERVICE : COORDONNATEUR DES TRANSACTIONS (SWITCH)
 * =====================================================================================
 * Ce service orchestre le cycle de vie applicatif des flux financiers.
 * 1. Initialisation des requêtes de transfert et bascule en collecte.
 * 2. Réception et stockage brut des rapports de transactions (SMS) émanant du terrain.
 * 3. Routage vers le service de parsing textuel (Regex).
 * 4. Branchement métier : Réconciliation de Collecte ou de Transfert (Sortie).
 * 5. Acheminement automatique des ordres de virement vers la flotte Android.
 * =====================================================================================
 */
@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final DeviceSmsLogService smsLogService;
    private final SmsParserService smsParserService;
    private final DeviceQueueService deviceQueueService;
    private final DeviceWebSocketHandler deviceWebSocketHandler;
    private final ClientWebSocketHandler clientWebSocketHandler;

    public TransactionService(TransactionRepository transactionRepository,
                              DeviceSmsLogService smsLogService,
                              SmsParserService smsParserService,
                              DeviceQueueService deviceQueueService,
                              @Lazy DeviceWebSocketHandler deviceWebSocketHandler,
                              ClientWebSocketHandler clientWebSocketHandler) {
        this.transactionRepository = transactionRepository;
        this.smsLogService = smsLogService;
        this.smsParserService = smsParserService;
        this.deviceQueueService = deviceQueueService;
        this.deviceWebSocketHandler = deviceWebSocketHandler;
        this.clientWebSocketHandler = clientWebSocketHandler;
    }

    /**
     * Pousse un événement de statut au navigateur en train de suivre cette transaction.
     * Silencieux si aucun client n'est connecté (usage normal via API pure, sans dashboard).
     */
    private void notifyClient(Transaction transaction, String message) {
        clientWebSocketHandler.sendNotification(
                transaction.getId(),
                new ClientNotificationResponse(transaction.getId(), transaction.getStatus(), message)
        );
    }

    /**
     * WORKFLOW : createAndInitiateTransaction (Point d'entrée d'un nouveau transfert - Collecte Active via Device)
     * ---------------------------------------------------------------------------------------------------------
     * [Client/Front] ──(Initier)──> [Ici]
     * │
     * ├── 1. Idempotence : si une transaction existe déjà pour cette clé, la renvoie telle quelle (aucun doublon).
     * ├── 2. Résout l'opérateur du client (Expéditeur) d'après son indicatif téléphonique.
     * │      SI l'opérateur est inconnu : rejette la requête (400) AVANT toute écriture en base,
     * │      car 'operator' est une colonne obligatoire de l'entité Transaction.
     * ├── 3. Génère et persiste la Transaction initiale au statut 'COLLECT_PROCESSING'.
     * ├── 4. Sollicite le Load Balancer pour obtenir un smartphone connecté et apte à collecter.
     * └── 5. Expédie l'ordre 'INITIATE_COLLECT' (CollectRequest) au terminal Android via le canal WebSocket.
     */
    @Transactional
    public Transaction createAndInitiateTransaction(TransactionInitiationRequest request) {
        String idempotencyKey = (request.idempotencyKey() != null && !request.idempotencyKey().isBlank())
                ? request.idempotencyKey()
                : UUID.randomUUID().toString();

        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            logger.info("[IDEMPOTENCE] Clé [{}] déjà connue. Renvoi de la transaction existante [{}].",
                    idempotencyKey, existing.get().getId());
            return existing.get();
        }

        Operator operatorSource = request.source_operator();
        Operator operatorDestination = request.destination_operator();

        // Règle métier : Camtel n'a pas de portefeuille Mobile Money, uniquement de l'Airtime.
        if (operatorDestination == Operator.CAMTEL && request.serviceType() == ServiceType.MONEY_TRANSFER) {
            throw new InvalidOperatorException(
                    "Camtel ne supporte pas les transferts Mobile Money (MONEY_TRANSFER), uniquement l'Airtime.");
        }

        Transaction transaction = new Transaction(
                idempotencyKey, request.senderPhone(), request.recipientPhone(),
                operatorSource, operatorDestination, request.amount(),
                TransactionStatus.COLLECT_PROCESSING, request.serviceType()
        );

        Transaction savedTx = transactionRepository.save(transaction);
        logger.info("[SWITCH] Nouvelle transaction enregistrée en BDD. ID: {} | Statut: COLLECT_PROCESSING", savedTx.getId());
        notifyClient(savedTx, "Transaction initiée, collecte en cours de routage.");

        UUID receivingDeviceId = null;
        try {
            receivingDeviceId = deviceQueueService.acquireAvailableDevice(
                    operatorSource,
                    request.serviceType(),
                    BigDecimal.ZERO
            );

            TNB.Switch.dto.request.CollectRequest collectRequest = new TNB.Switch.dto.request.CollectRequest(
                    savedTx.getId(),
                    savedTx.getSenderPhone(),
                    savedTx.getAmount(),
                    operatorSource
            );

            deviceWebSocketHandler.sendCommand(receivingDeviceId, collectRequest);
            logger.info("[ROUTAGE COLLECTE] Ordre [INITIATE_COLLECT] transmis avec succès au Device [{}] pour le client {} ({})",
                    receivingDeviceId, savedTx.getSenderPhone(), operatorSource);

        } catch (Exception e) {
            logger.error("[ALERTE COLLECTE] Rupture de flotte temporaire pour l'opérateur {} : Aucun périphérique disponible pour initier la collecte. Passage de la transaction [{}] en SUSPENDED.",
                    operatorSource, savedTx.getId(), e);

            if (receivingDeviceId != null) {
                deviceQueueService.releaseDevice(receivingDeviceId);
            }

            handleNoDeviceAvailableFallback(savedTx, "NO_DEVICE_AVAILABLE_FOR_COLLECT");
        }

        return savedTx;
    }

    /**
     * WORKFLOW : processIncomingDeviceSms
     * ---------------------------------------------------------------------------------
     * [Android App] ──(Envoi SMS intercepté via WS)──> [Ici]
     */
    @Transactional
    public void processIncomingDeviceSms(UUID deviceId, Operator operator, DeviceIncomingSmsMessage smsMessage) {
        // Anti-rejeu : un SMS identique déjà traité avec succès ne doit jamais être réconcilié une seconde fois
        if (smsLogService.isDuplicate(smsMessage.senderId(), smsMessage.smsRaw())) {
            logger.warn("[SECURITE] SMS en double détecté (rejeu ou renvoi réseau) depuis le Device [{}]. Expéditeur : {}. Ignoré.",
                    deviceId, smsMessage.senderId());
            DeviceSmsLog duplicateLog = smsLogService.logIncomingSms(
                    deviceId, smsMessage.senderId(), smsMessage.smsRaw(), smsMessage.deviceReceivedAt());
            smsLogService.updateStatus(duplicateLog.getId(), SmsProcessingStatus.REJECTED, null, "DUPLICATE_SMS_REPLAY");
            return;
        }

        DeviceSmsLog smsLog = smsLogService.logIncomingSms(
                deviceId,
                smsMessage.senderId(),
                smsMessage.smsRaw(),
                smsMessage.deviceReceivedAt()
        );

        try {
            SmsParserService.ParsedSmsData parsedData = smsParserService.parse(operator, smsMessage.senderId(), smsMessage.smsRaw());

            if (parsedData.isCollect()) {
                reconcileCollect(parsedData, smsLog, operator);
            } else {
                reconcileTransfer(parsedData, smsLog, operator);
            }

        } catch (Exception e) {
            logger.error("Erreur lors du traitement du SMS Log ID [{}]: {}", smsLog.getId(), e.getMessage());
            smsLogService.updateStatus(smsLog.getId(), SmsProcessingStatus.REJECTED, null, e.getMessage());
        }
    }

    /**
     * WORKFLOW : retrySuspendedTransaction
     * ---------------------------------------------------------------------------------
     * [Dashboard Admin] ──(Clic bouton Relance)──> [Ici]
     */
    @Transactional
    public void retrySuspendedTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction introuvable avec l'ID : " + transactionId
                ));

        if (transaction.getStatus() != TransactionStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Action interdite. La transaction n'est pas suspendue. Statut actuel : " + transaction.getStatus()
            );
        }

        logger.info("[AUDIT] Relance manuelle métier initiée pour la transaction [{}]", transactionId);
        transaction.setErrorCode(null);

        triggerAutomaticTransfer(transaction);
    }

    /**
     * WORKFLOW INTERNE : reconcileCollect (Phase 1 d'un transfert international)
     */
    private void reconcileCollect(SmsParserService.ParsedSmsData parsedData, DeviceSmsLog smsLog, Operator operator) {
        Transaction transaction = transactionRepository
                .findByStatusAndSenderPhoneAndAmount(TransactionStatus.COLLECT_PROCESSING, parsedData.phoneNumber(), parsedData.amount())
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Aucune transaction en cours de collecte ne correspond aux critères."
                ));

        transaction.setCollectSmsLogId(smsLog.getId());
        transaction.setStatus(TransactionStatus.COLLECT_DONE);
        transactionRepository.save(transaction);
        notifyClient(transaction, "Fonds collectés avec succès. Démarrage du transfert vers le bénéficiaire.");

        deviceQueueService.incrementMomoBalance(smsLog.getDeviceId(), operator, parsedData.amount());
        smsLogService.updateStatus(smsLog.getId(), SmsProcessingStatus.PROCESSED, transaction.getId(), null);

        // La collecte est terminée : le device qui l'a exécutée redevient disponible
        // pour le Load Balancer AVANT de router le transfert (qui peut d'ailleurs le
        // re-sélectionner lui-même si c'est le meilleur candidat).
        deviceQueueService.releaseDevice(smsLog.getDeviceId());

        triggerAutomaticTransfer(transaction);
    }

    /**
     * WORKFLOW INTERNE : reconcileTransfer (Phase 2 / Clôture finale de l'opération)
     */
    private void reconcileTransfer(SmsParserService.ParsedSmsData parsedData, DeviceSmsLog smsLog, Operator operator) {
        Transaction transaction = transactionRepository
                .findByStatusAndRecipientPhoneAndAmount(TransactionStatus.TRANSFER_PROCESSING, parsedData.phoneNumber(), parsedData.amount())
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Aucune transaction en cours de transfert ne correspond aux critères."
                ));

        transaction.setTransferSmsLogId(smsLog.getId());
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);
        notifyClient(transaction, "Transfert réussi. Le bénéficiaire a reçu les fonds.");

        deviceQueueService.decrementBalance(
                smsLog.getDeviceId(),
                operator,
                transaction.getServiceType(),
                transaction.getAmount()
        );

        smsLogService.updateStatus(smsLog.getId(), SmsProcessingStatus.PROCESSED, transaction.getId(), null);

        // Le transfert est confirmé : le device qui l'a exécuté redevient disponible.
        deviceQueueService.releaseDevice(smsLog.getDeviceId());
    }

    /**
     * WORKFLOW INTERNE : triggerAutomaticTransfer (Le Moteur d'acheminement / Routage intelligent)
     */
    private void triggerAutomaticTransfer(Transaction transaction) {
        transaction.setStatus(TransactionStatus.TRANSFER_PROCESSING);

        Operator targetOperator = transaction.getOperatorDestination();

        UUID availableDeviceId = null;
        try {
            availableDeviceId = deviceQueueService.acquireAvailableDevice(
                    targetOperator,
                    transaction.getServiceType(),
                    transaction.getAmount()
            );

            transaction.setAssignedDeviceId(availableDeviceId);
            transactionRepository.save(transaction);

            TransferRequest transferRequest = new TransferRequest(
                    transaction.getId(),
                    transaction.getRecipientPhone(),
                    transaction.getAmount(),
                    transaction.getServiceType(),
                    targetOperator
            );
            deviceWebSocketHandler.sendCommand(availableDeviceId, transferRequest);

        } catch (Exception e) {
            logger.warn("Échec d'acheminement sur le périphérique [{}]. Tentative de bascule...", availableDeviceId, e);

            if (availableDeviceId != null) {
                deviceQueueService.disconnectDevice(availableDeviceId);
            }

            UUID backupDeviceId = null;
            try {
                backupDeviceId = deviceQueueService.acquireAvailableDevice(
                        targetOperator,
                        transaction.getServiceType(),
                        transaction.getAmount()
                );

                transaction.setAssignedDeviceId(backupDeviceId);
                transactionRepository.save(transaction);

                TransferRequest transferRequest = new TransferRequest(
                        transaction.getId(),
                        transaction.getRecipientPhone(),
                        transaction.getAmount(),
                        transaction.getServiceType(),
                        targetOperator
                );
                deviceWebSocketHandler.sendCommand(backupDeviceId, transferRequest);
                logger.info("[ROUTAGE] Transaction [{}] déportée avec succès sur le périphérique de secours [{}]", transaction.getId(), backupDeviceId);

            } catch (Exception ex) {
                logger.error("[ALERTE] Rupture de flotte ou de flot pour l'opérateur {}. Gel de la transaction [{}].", targetOperator, transaction.getId());

                if (backupDeviceId != null) {
                    deviceQueueService.releaseDevice(backupDeviceId);
                }

                handleNoDeviceAvailableFallback(transaction, "NO_DEVICE_OR_FLOT_AVAILABLE");
            }
        }
    }

    /**
     * WORKFLOW INTERNE : handleNoDeviceAvailableFallback
     */
    private void handleNoDeviceAvailableFallback(Transaction transaction, String reason) {
        transaction.setStatus(TransactionStatus.SUSPENDED);
        transaction.setErrorCode(reason);
        transactionRepository.save(transaction);
        notifyClient(transaction, "Transaction mise en attente : " + reason);
    }
}