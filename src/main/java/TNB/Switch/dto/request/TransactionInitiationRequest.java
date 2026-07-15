package TNB.Switch.dto.request;

import TNB.Switch.entity.Operator;
import TNB.Switch.entity.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransactionInitiationRequest(
        @NotBlank(message = "Le numéro de l'expéditeur est obligatoire")
        String senderPhone,

        @NotBlank(message = "Le numéro du bénéficiaire est obligatoire")
        String recipientPhone,

        @NotNull(message = "Le montant est obligatoire")
        BigDecimal amount,

        @NotNull(message = "Le type de service (AIRTIME/MONEY_TRANSFER) est obligatoire")
        ServiceType serviceType,

        @NotNull(message = "l'operateur source est obligatoire")
        Operator source_operator,

        @NotNull(message = "l'operateur destinataire est obligatoire")
        Operator destination_operator,

        // Optionnelle : si le client (ou le middleware) la fournit, une relance avec la même
        // clé renverra la transaction déjà créée au lieu d'en créer une seconde.
        // Si absente, le serveur en génère une automatiquement.
        String idempotencyKey
) {}