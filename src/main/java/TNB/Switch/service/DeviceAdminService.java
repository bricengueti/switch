package TNB.Switch.service;

import TNB.Switch.dto.request.DeviceRegistrationRequest;
import TNB.Switch.dto.response.DeviceRegistrationResponse;
import TNB.Switch.entity.Device;
import TNB.Switch.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL : PROVISIONNING ET ENRÔLEMENT DES TERMINAUX D'INFRASTRUCTURE
 * =====================================================================================
 * Ce service permet à l'administrateur du Switch d'enregistrer à l'avance un smartphone.
 * 1. Il génère un identifiant immuable (UUID).
 * 2. Il forge un jeton cryptographique secret unique pour l'appareil.
 * 3. Il initialise la matrice comptable des soldes (Momo, OM, Airtime) saisis par l'admin.
 * 4. Il fournit les métadonnées requises pour l'enrôlement par QR Code sur Android.
 * =====================================================================================
 */
@Service
public class DeviceAdminService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceAdminService.class);
    private final DeviceRepository deviceRepository;

    public DeviceAdminService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * WORKFLOW : provisionNewDevice (Enregistrement initial avec soldes par l'Admin)
     * ---------------------------------------------------------------------------------
     * [Dashboard Admin] ──(Payload: Modèle, Numéros SIM, Soldes)──> [Ici]
     * │
     * ├── 1. Génération d'un UUID de type 4 pour l'identifiant immuable.
     * ├── 2. Génération d'un `secretToken` sécurisé (Base64 URL-Safe).
     * ├── 3. INSTANCIATION & SÉCURITÉ : Hydratation des numéros et soldes initiaux.
     * │    └── Si une SIM est présente mais son solde est omis, il est forcé à 0.
     * ├── 4. PERSISTANCE : Sauvegarde dans la table 'devices'.
     * └── 5. DTO OUTPUT : Renvoie les identifiants requis pour générer le QR Code d'enrôlement.
     */
    @Transactional
    public DeviceRegistrationResponse provisionNewDevice(DeviceRegistrationRequest request) {
        // 1. Génération de la clé primaire métier
        UUID newDeviceId = UUID.randomUUID();

        // 2. Forge du Token Secret unique pour l'authentification WebSocket future
        String generatedSecret = generateSecureRandomToken();

        // 3. Construction et hydratation de l'entité Device
        Device device = new Device();
        device.setId(newDeviceId);
        device.setDeviceModel(request.deviceModel());
        device.setSecretToken(generatedSecret);

        // Assignation des numéros de cartes SIM
        device.setMtnSimNumber(request.mtnSimNumber());
        device.setOrangeSimNumber(request.orangeSimNumber());
        device.setCamtelSimNumber(request.camtelSimNumber());

        // --- ENREGISTREMENT DES SOLDES COMMERCIAUX ENTRANTS (MTN) ---
        device.setMtnMomoBalance(request.mtnSimNumber() != null ?
                (request.mtnMomoBalance() != null ? request.mtnMomoBalance() : BigDecimal.ZERO) : null);

        device.setMtnAirtimeBalance(request.mtnSimNumber() != null ?
                (request.mtnAirtimeBalance() != null ? request.mtnAirtimeBalance() : BigDecimal.ZERO) : null);

        // --- ENREGISTREMENT DES SOLDES COMMERCIAUX ENTRANTS (ORANGE) ---
        device.setOrangeOmBalance(request.orangeSimNumber() != null ?
                (request.orangeOmBalance() != null ? request.orangeOmBalance() : BigDecimal.ZERO) : null);

        device.setOrangeAirtimeBalance(request.orangeSimNumber() != null ?
                (request.orangeAirtimeBalance() != null ? request.orangeAirtimeBalance() : BigDecimal.ZERO) : null);

        // --- ENREGISTREMENT DES SOLDES COMMERCIAUX ENTRANTS (CAMTEL) ---
        device.setCamtelAirtimeBalance(request.camtelSimNumber() != null ?
                (request.camtelAirtimeBalance() != null ? request.camtelAirtimeBalance() : BigDecimal.ZERO) : null);

        // 4. Persistance physique dans la base de données
        Device savedDevice = deviceRepository.save(device);

        logger.info("[ADMIN] Nouvelle passerelle multi-SIM enregistrée. ID: {} | Modèle: {} | Soldes configurés.",
                newDeviceId, request.deviceModel());

        // 5. Retour des informations pour l'affichage ou la génération du QR Code
        return new DeviceRegistrationResponse(
                savedDevice.getId(),
                savedDevice.getDeviceModel(),
                savedDevice.getSecretToken()
        );
    }

    /**
     * Forge un token de sécurité cryptographique aléatoire de 24 octets (URL-safe).
     * Plus robuste qu'un UUID standard pour sécuriser le handshake avec l'application Android.
     */
    private String generateSecureRandomToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}