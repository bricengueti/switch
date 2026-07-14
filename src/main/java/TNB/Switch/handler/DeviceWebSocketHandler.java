package TNB.Switch.handler;

import TNB.Switch.service.ConnectedDeviceSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import TNB.Switch.dto.request.DeviceIncomingSmsMessage;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.Operator;
import TNB.Switch.repository.DeviceRepository;
import TNB.Switch.service.DeviceQueueService;
import TNB.Switch.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL DU HANDLER : CENTRE DE COMMANDE DES AGENTS DE TRANSFERT (ANDROID)
 * =====================================================================================
 * Ce composant est la passerelle de communication temps réel avec les terminaux physiques.
 * 1. Il authentifie les téléphones via le couple Nom + Token Secret provisionné en BDD.
 * 2. Il maintient les sessions WebSocket actives sous l'UUID unique métier du Device.
 * 3. Au démarrage, il synchronise la mémoire vive avec les vrais soldes de la BDD.
 * 4. Il route les événements asynchrones (SMS reçus, mises à jour de soldes).
 * 5. Il pousse les ordres USSD/SMS du Switch vers les cartes SIM physiques Android.
 * =====================================================================================
 */
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DeviceWebSocketHandler.class);

    // Clé d'identification interne stockée dans la session de transport pour l'optimisation
    private static final String SESSION_ATTR_DEVICE_ID = "DEVICE_ID";

    // Table de routage mémoire : lie l'UUID unique d'un téléphone à sa session TCP ouverte
    private final Map<UUID, WebSocketSession> activeDeviceSessions = new ConcurrentHashMap<>();

    private final TransactionService transactionService;
    private final DeviceQueueService deviceQueueService;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    public DeviceWebSocketHandler(TransactionService transactionService,
                                  DeviceQueueService deviceQueueService,
                                  DeviceRepository deviceRepository,
                                  ObjectMapper objectMapper) {
        this.transactionService = transactionService;
        this.deviceQueueService = deviceQueueService;
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * WORKFLOW : afterConnectionEstablished (Authentification, Enrôlement et Synchro de la flotte)
     * ---------------------------------------------------------------------------------
     * [App Android Gateway] ──(ws://.../ws/device?name=XXX&secret=YYY)──> [Ici]
     * │
     * ├── 1. Extraction des identifiants (name et secret) depuis les paramètres de l'URI.
     * ├── 2. VALIDATION EN BDD : Rejet immédiat si le couple est inconnu ou invalide (POLICY_VIOLATION).
     * ├── 3. CAPTURE DE L'UUID : Récupération du vrai ID métier généré par la base de données.
     * ├── 4. OPTIMISATION : Injection de l'UUID dans les attributs de session (évite les parsing URI futurs).
     * ├── 5. ENREGISTREMENT RESEAU : Liaison de l'UUID à la session active dans le registre.
     * └── 6. SYNCHRONISATION METIER : Charge les vrais soldes de la BDD et enregistre le terminal
     * auprès du `deviceQueueService` pour le rendre éligible à la distribution.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        var queryParams = UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams();

        // On extrait les paramètres que l'app mobile va envoyer dans l'URL
        // Exemple : ws://.../ws/device?model=Infinix_Hot_30&secret=mon_secret_token_123
        String deviceModel = queryParams.getFirst("model");
        String secretToken = queryParams.getFirst("secret");

        if (deviceModel == null || secretToken == null) {
            logger.warn("Connexion WebSocket rejetée : Paramètres d'authentification manquants.");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // Requête alignée sur ton entité
        Optional<Device> deviceInDb = deviceRepository.findByDeviceModelAndSecretToken(deviceModel, secretToken);

        if (deviceInDb.isEmpty()) {
            logger.warn("Connexion WebSocket refusée : Identifiants invalides pour le modèle '{}'.", deviceModel);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Device device = deviceInDb.get();
        UUID deviceId = device.getId();
        logger.warn("Device [{}], Modèle [{}], connecté avec succès et synchronisé.", deviceId, deviceModel);

        // Le reste du workflow reste identique et propre :
        session.getAttributes().put("DEVICE_ID", deviceId);
        activeDeviceSessions.put(deviceId, session);

        ConnectedDeviceSession initialSession = new ConnectedDeviceSession(
                deviceId,
                true,
                device.getMtnMomoBalance(),
                device.getMtnAirtimeBalance(),
                device.getOrangeOmBalance(),
                device.getOrangeAirtimeBalance(),
                device.getCamtelAirtimeBalance()
        );

        deviceQueueService.registerDevice(initialSession);

        logger.warn("Device [{}], Modèle [{}], connecté avec succès et synchronisé.", deviceId, deviceModel);
    }
    /**
     * WORKFLOW : handleTextMessage (Routeur d'événements matériels asynchrones)
     * ---------------------------------------------------------------------------------
     * [App Android Gateway] ──(Envoi JSON Event : Payload)──> [Ici]
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID deviceId = extractDeviceIdFromSession(session);
        String payload = message.getPayload();

        if (deviceId == null) {
            logger.error("Message intercepté sur une session non-identifiée. Rejet du flux.");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        logger.debug("Message reçu du Device [{}] : {}", deviceId, payload);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String action = jsonNode.has("action") ? jsonNode.get("action").asText() : "";

            switch (action) {
                case "UNSOLICITED_SMS_RECEIVED" -> {
                    DeviceIncomingSmsMessage smsDto = objectMapper.readValue(payload, DeviceIncomingSmsMessage.class);
                    Operator smsOperator = detectOperatorFromSmsSender(smsDto.senderId());
                    transactionService.processIncomingDeviceSms(deviceId, smsOperator, smsDto);
                }

                case "BALANCE_UPDATE" -> {
                    ConnectedDeviceSession updatedSession = objectMapper.readValue(payload, ConnectedDeviceSession.class);
                    // Sécurité : On s'assure que le device mobile ne falsifie pas l'UUID envoyé dans le corps JSON
                    if (!deviceId.equals(updatedSession.deviceId())) {
                        logger.warn("Tentative de falsification d'identité détectée pour le Device [{}]", deviceId);
                        return;
                    }
                    deviceQueueService.registerDevice(updatedSession);
                    logger.info("Mise à jour à chaud des portefeuilles pour le Device [{}]", deviceId);
                }

                default -> logger.warn("Action '{}' non supportée reçue du périphérique {}", action, deviceId);
            }
        } catch (Exception e) {
            logger.error("Erreur critique lors du traitement du payload JSON venant du device {}", deviceId, e);
        }
    }

    /**
     * WORKFLOW : afterConnectionClosed (Déconnexion, Isolation et Sécurisation de la file)
     * ---------------------------------------------------------------------------------
     * [App Android Gateway] ──(Coupure Internet / Crash App)──> [Ici]
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID deviceId = extractDeviceIdFromSession(session);

        if (deviceId != null) {
            activeDeviceSessions.remove(deviceId);
            deviceQueueService.disconnectDevice(deviceId);

            logger.warn("Device [{}] déconnecté. Raison: {}. Restants en ligne : {}",
                    deviceId, status.getReason(), activeDeviceSessions.size());
        }
    }

    /**
     * WORKFLOW OUTBOUND : sendCommand (Émission d'ordres d'exécution matériels vers Android)
     * ---------------------------------------------------------------------------------
     */
    public void sendCommand(UUID deviceId, Object requestDto) throws IOException {
        WebSocketSession session = activeDeviceSessions.get(deviceId);
        if (session != null && session.isOpen()) {
            String jsonPayload = objectMapper.writeValueAsString(requestDto);
            session.sendMessage(new TextMessage(jsonPayload));
            logger.info("Commande poussée vers le Device [{}].", deviceId);
        } else {
            logger.error("Impossible d'envoyer la commande : Le périphérique [{}] est hors ligne.", deviceId);
        }
    }

    // --- UTILITAIRES DE SÉCURITÉ ET PARSING OMEGA ---

    /**
     * Récupère l'UUID directement depuis la mémoire cache de la session sans parser l'URL de façon répétée.
     */
    private UUID extractDeviceIdFromSession(WebSocketSession session) {
        Object attribute = session.getAttributes().get(SESSION_ATTR_DEVICE_ID);
        return attribute instanceof UUID ? (UUID) attribute : null;
    }

    /**
     * Analyse syntaxique de la provenance des notifications SMS
     */
    private Operator detectOperatorFromSmsSender(String senderId) {
        String upperSender = senderId.toUpperCase();
        if (upperSender.contains("ORANGE") || upperSender.contains("OM")) {
            return Operator.ORANGE;
        } else if (upperSender.contains("MTN") || upperSender.contains("MOMO")) {
            return Operator.MTN;
        } else if (upperSender.contains("CAMTEL")) {
            return Operator.CAMTEL;
        }
        throw new IllegalArgumentException("Impossible de déterminer l'opérateur pour l'émetteur du SMS : " + senderId);
    }
}