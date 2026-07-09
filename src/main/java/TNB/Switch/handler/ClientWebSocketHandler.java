package TNB.Switch.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL DU HANDLER : GESTIONNAIRE DES FLUX PASSIFS CLIENTS (NAVIGATEURS)
 * =====================================================================================
 * Ce composant gère le cycle de vie des connexions WebSocket initiées par les utilisateurs.
 * 1. Il écoute les ouvertures de sessions asynchrones issues du point d'entrée "/ws/client".
 * 2. Il indexe et maintient en mémoire vive (`ConcurrentHashMap`) les tunnels de communication.
 * 3. L'indexation est basée sur l'identifiant unique de la transaction (`transactionId`).
 * 4. Il offre un point d'accès public permettant au reste du système d'émettre des pushs
 * événementiels (ex: "Statut mis à jour") en temps réel vers le client en attente.
 * =====================================================================================
 */
@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientWebSocketHandler.class);

    // Table en mémoire vive : associe un UUID de Transaction à la session de l'utilisateur qui l'attend
    private final Map<UUID, WebSocketSession> activeClientSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public ClientWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * WORKFLOW : afterConnectionEstablished (Handshake réussi)
     * ---------------------------------------------------------------------------------
     * [Navigateur Client] ──(ws://.../ws/client?transactionId=XXX)──> [Ici]
     * │
     * ├── 1. Invoque extractTransactionIdFromUri(...) pour isoler l'UUID transmis en paramètre d'URL.
     * ├── 2. VALIDATION : Si l'identifiant est absent ou malformé :
     * │        ├── Log un avertissement technique de sécurité.
     * │        └── Termine la connexion prématurément avec le code de statut 'BAD_DATA' (400).
     * │
     * └── 3. STOCKAGE : Si l'UUID est valide, référence la session de communication dans la Map
     * `activeClientSessions` avec pour clé l'ID de la transaction.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID transactionId = extractTransactionIdFromUri(session);

        if (transactionId == null) {
            logger.warn("Connexion WebSocket Client rejetée : 'transactionId' manquant dans l'URL.");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        activeClientSessions.put(transactionId, session);
        logger.info("Navigateur Client connecté pour suivre la transaction : {}", transactionId);
    }

    /**
     * WORKFLOW : handleTextMessage (Écoute des requêtes montantes)
     * ---------------------------------------------------------------------------------
     * [Navigateur Client] ──(Envoi d'un message textuel)──> [Ici]
     * │
     * ├── 1. Extrait l'identifiant de la transaction rattaché à la session WebSocket émettrice.
     * └── 2. Journalise le contenu de la charge utile (Payload) reçue.
     * [Note] : Les clients consomment ce canal de manière passive (uniquement en réception).
     * Cette fonction sert de réceptacle d'audit (ex: si le client demande une annulation).
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID transactionId = extractTransactionIdFromUri(session);
        logger.info("Message reçu du client pour la transaction [{}]. Payload : {}", transactionId, message.getPayload());
    }

    /**
     * WORKFLOW : afterConnectionClosed (Fermeture du socket / Nettoyage du cache)
     * ---------------------------------------------------------------------------------
     * [Navigateur Client] ──(Fermeture d'onglet / Perte réseau)──> [Ici]
     * │
     * ├── 1. Identifie l'UUID de la transaction liée à la session en cours de coupure.
     * └── 2. SI l'ID est identifié :
     * └── Purge l'entrée correspondante de la Map `activeClientSessions` pour libérer la mémoire vive (RAM).
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID transactionId = extractTransactionIdFromUri(session);

        if (transactionId != null) {
            activeClientSessions.remove(transactionId);
            logger.info("Navigateur Client déconnecté de la transaction : {}", transactionId);
        }
    }

    /**
     * WORKFLOW : getClientSession (Passerelle métier pour couches externes)
     * ---------------------------------------------------------------------------------
     * [Service Interne (ex: TransactionService)] ──(ID de Transaction)──> [Ici]
     * │
     * ├── 1. Interroge la table de hachage concurrente `activeClientSessions`.
     * └── 2. Extrait et retourne l'instance active du canal WebSocket correspondant.
     * Permet à l'appelant d'y injecter un message de notification d'état de paiement (JSON).
     */
    public WebSocketSession getClientSession(UUID transactionId) {
        return activeClientSessions.get(transactionId);
    }

    /**
     * WORKFLOW OUTBOUND : sendNotification (Émission d'un événement de statut vers le navigateur)
     * ---------------------------------------------------------------------------------
     * [TransactionService] ──(ID de Transaction + Payload)──> [Ici]
     * │
     * ├── 1. Récupère la session active correspondant à l'UUID de la transaction.
     * ├── 2. SI absente ou fermée : ne fait rien (le client n'écoute simplement pas / plus).
     * └── 3. SINON : Sérialise le DTO fourni en JSON et le pousse sur le canal WebSocket.
     */
    public void sendNotification(UUID transactionId, Object payload) {
        WebSocketSession session = activeClientSessions.get(transactionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(jsonPayload));
        } catch (IOException e) {
            logger.error("Impossible de pousser la notification pour la transaction [{}]", transactionId, e);
        }
    }

    /**
     * WORKFLOW INTERNE : extractTransactionIdFromUri (Parsing utilitaire de l'URI)
     * ---------------------------------------------------------------------------------
     * ├── 1. Récupère la chaîne de requête (Query String) native attachée au protocole.
     * ├── 2. Utilise `UriComponentsBuilder` pour isoler proprement le paramètre "transactionId".
     * ├── 3. SI le paramètre est localisé : Tente la conversion de la String vers l'objet typé java.util.UUID.
     * └── 4. [Exception] : Si une erreur de format ou de manipulation survient, l'intercepte et retourne un résultat protecteur 'null'.
     */
    private UUID extractTransactionIdFromUri(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            String transactionIdStr = UriComponentsBuilder.fromUriString("?" + query)
                    .build()
                    .getQueryParams()
                    .getFirst("transactionId");

            return transactionIdStr != null ? UUID.fromString(transactionIdStr) : null;
        } catch (Exception e) {
            logger.error("Erreur lors de l'extraction du transactionId depuis l'URI", e);
            return null;
        }
    }
}