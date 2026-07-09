package TNB.Switch.dto.response;

import TNB.Switch.entity.TransactionStatus;

import java.util.UUID;

public record CollectResponse(
        UUID transactionId,
        TransactionStatus status,      // SUCCESS ou FAILED
        String smsRaw,      // Le SMS brut de l'opérateur stocké pour l'audit admin
        String errorCode    // Raison de l'échec s'il y a lieu
) {}