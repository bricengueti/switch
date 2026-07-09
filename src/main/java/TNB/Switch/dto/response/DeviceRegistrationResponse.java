package TNB.Switch.dto.response;

import java.util.UUID;

/**
 * DTO retourné à l'administrateur après la création du Device.
 * Ces données servent généralement à générer le QR Code d'enrôlement pour l'application Android.
 */
public record DeviceRegistrationResponse(
        UUID id,
        String deviceModel,
        String secretToken
) {}