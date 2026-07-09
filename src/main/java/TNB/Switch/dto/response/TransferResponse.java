package TNB.Switch.dto.response;

import TNB.Switch.entity.TransactionStatus;

import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        TransactionStatus status,      // SUCCESS ou FAILED
        String smsRaw,      // Le SMS brut de débit (preuve absolue de livraison)
        String errorCode
) {}