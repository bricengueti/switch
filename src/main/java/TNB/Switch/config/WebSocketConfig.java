package TNB.Switch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.WebSocketHandler;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL DE LA CONFIGURATION : MULTI-CANAL WEBSOCKET INFRASTRUCTURE
 * =====================================================================================
 * Cette classe configure et expose les points de terminaison (Endpoints) bidirectionnels du Switch.
 * 1. Elle isole le trafic de supervision de la flotte (terminaux Android) sur un canal dédié.
 * 2. Elle isole le trafic de streaming temps réel pour les interfaces utilisateurs (clients/browsers).
 * 3. Elle applique la politique de sécurité de partage de ressources (CORS) définie dans l'application.
 * =====================================================================================
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler deviceWebSocketHandler;
    private final WebSocketHandler clientWebSocketHandler;

    @Value("${tnb.websocket.allowed-origins:*}")
    private String allowedOrigins;

    public WebSocketConfig(WebSocketHandler deviceWebSocketHandler, WebSocketHandler clientWebSocketHandler) {
        this.deviceWebSocketHandler = deviceWebSocketHandler;
        this.clientWebSocketHandler = clientWebSocketHandler;
    }

    /**
     * WORKFLOW INITIALISATION : registerWebSocketHandlers (Démarrage du Serveur)
     * ---------------------------------------------------------------------------------
     * [Spring Boot Startup] ──(Scan des configurations)──> [Ici]
     * │
     * ├── 1. Lit et découpe la chaîne des origines autorisées (CORS) depuis la configuration de l'environnement.
     * ├── 2. ENREGISTREMENT CANAL 1 (Flotte Android) :
     * │        ├── Map l'URI d'écoute stricte sur "/ws/device".
     * │        └── Assigne `deviceWebSocketHandler` pour manager les connexions/déconnexions et commandes des téléphones.
     * │
     * └── 3. ENREGISTREMENT CANAL 2 (Clients Web / Dashboards) :
     * ├── Map l'URI d'écoute sur "/ws/client".
     * └── Assigne `clientWebSocketHandler` pour diffuser en temps réel l'évolution des transactions aux interfaces Angular.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOrigins.split(",");

        // 1. Mapping du canal des téléphones Android (Devices)
        registry.addHandler(deviceWebSocketHandler, "/ws/device")
                .setAllowedOrigins(origins);

        // 2. Mapping du canal des navigateurs / applications (Clients)
        registry.addHandler(clientWebSocketHandler, "/ws/client")
                .setAllowedOrigins(origins);
    }
}