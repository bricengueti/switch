package TNB.Switch.dto.request;

import TNB.Switch.entity.Operator;
import TNB.Switch.entity.ServiceType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        String action,
        UUID transactionId,
        String recipientPhone,
        BigDecimal amount,
        ServiceType serviceType,
        Operator operator // NOUVEAU : opérateur de destination — évite à l'app Android
        // de devoir deviner via le préfixe du numéro du bénéficiaire.
) {
    public TransferRequest(UUID transactionId, String recipientPhone, BigDecimal amount,
                           ServiceType serviceType, Operator operator) {
        this("EXECUTE_TRANSFER", transactionId, recipientPhone, amount, serviceType, operator);
    }
}