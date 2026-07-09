package TNB.Switch.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record DeviceRegistrationRequest(
        @NotBlank(message = "Le modèle du smartphone est obligatoire")
        String deviceModel,

        // Informations SIM 1 : MTN
        String mtnSimNumber,
        BigDecimal mtnMomoBalance,
        BigDecimal mtnAirtimeBalance,

        // Informations SIM 2 : ORANGE
        String orangeSimNumber,
        BigDecimal orangeOmBalance,
        BigDecimal orangeAirtimeBalance,

        // Informations SIM 3 : CAMTEL
        String camtelSimNumber,
        BigDecimal camtelAirtimeBalance
) {}