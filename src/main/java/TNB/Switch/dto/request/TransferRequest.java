package TNB.Switch.dto.request;

import TNB.Switch.entity.ServiceType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        String action,         // Par défaut "EXECUTE_TRANSFER"
        UUID transactionId,
        String recipientPhone,  // Le bénéficiaire du crédit/momo
        BigDecimal amount,
        ServiceType serviceType     // AIRTIME ou CASH_DEPOSIT
) {
    public TransferRequest(UUID transactionId, String recipientPhone, BigDecimal amount, ServiceType serviceType) {
        this("EXECUTE_TRANSFER", transactionId, recipientPhone, amount, serviceType);
    }
}