package TNB.Switch.dto.request;

import TNB.Switch.entity.Operator;

import java.math.BigDecimal;
import java.util.UUID;

public record CollectRequest(
        String action, // Valeur fixe : "INITIATE_COLLECT"
        UUID transactionId,
        String customerPhone,
        BigDecimal amount,
        Operator operator
) {
    // Constructeur compact pour forcer l'action par défaut
    public CollectRequest(UUID transactionId, String customerPhone, BigDecimal amount, Operator operator) {
        this("INITIATE_COLLECT", transactionId, customerPhone, amount, operator);
    }
}